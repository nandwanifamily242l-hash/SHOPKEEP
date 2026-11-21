import { initializeApp } from "https://www.gstatic.com/firebasejs/11.6.0/firebase-app.js";
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signOut,
  onAuthStateChanged
} from "https://www.gstatic.com/firebasejs/11.6.0/firebase-auth.js";
import {
  initializeFirestore,
  doc,
  getDoc,
  collection,
  onSnapshot,
  setDoc,
  updateDoc,
  deleteDoc,
  query,
  where
} from "https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js";

const firebaseConfig = {
  apiKey: "AIzaSyCx1exH2EGd5Bw4Uveude6aaDmnMj-9wVM",
  authDomain: "gen-lang-client-0080616262.firebaseapp.com",
  projectId: "gen-lang-client-0080616262",
  storageBucket: "gen-lang-client-0080616262.firebasestorage.app",
  messagingSenderId: "713438404659",
  appId: "1:713438404659:web:shopkeep"
};

const DATABASE_ID = "ai-studio-android-homekeep-8502e84b-2339-4853-b108-eaf44e3bbe94";
const PUBLIC_DEFAULT_DOC = "PUBLIC_DEFAULT";
const KNOWN_BOARDS_STORAGE_KEY = "shopkeep_known_boards_v1";
const FIRESTORE_REST_BASE =
  `https://firestore.googleapis.com/v1/projects/${firebaseConfig.projectId}/databases/${DATABASE_ID}/documents`;

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = initializeFirestore(
  app,
  { experimentalForceLongPolling: true },
  DATABASE_ID
);
const provider = new GoogleAuthProvider();

const codeInput = document.getElementById("codeInput");
const openCodeBtn = document.getElementById("openCodeBtn");
const boardSection = document.getElementById("boardSection");
const emptyState = document.getElementById("emptyState");
const emptyTitle = document.getElementById("emptyTitle");
const emptyDesc = document.getElementById("emptyDesc");
const retryLoadBtn = document.getElementById("retryLoadBtn");
const boardTitle = document.getElementById("boardTitle");
const boardMeta = document.getElementById("boardMeta");
const boardSwitcherCard = document.getElementById("boardSwitcherCard");
const boardSwitcherBar = document.getElementById("boardSwitcherBar");
const boardSelect = document.getElementById("boardSelect");
const categoriesContainer = document.getElementById("categoriesContainer");
const categoryFilterBar = document.getElementById("categoryFilterBar");
const viewCategoriesBtn = document.getElementById("viewCategoriesBtn");
const viewAllGridBtn = document.getElementById("viewAllGridBtn");
const densityStandardBtn = document.getElementById("densityStandardBtn");
const densityCompactBtn = document.getElementById("densityCompactBtn");
const addItemForm = document.getElementById("addItemForm");
const itemPhotoInput = document.getElementById("itemPhotoInput");
const attachPhotoBtn = document.getElementById("attachPhotoBtn");
const draftPhotoPreview = document.getElementById("draftPhotoPreview");
const draftPhotoImg = document.getElementById("draftPhotoImg");
const draftPhotoLabel = document.getElementById("draftPhotoLabel");
const clearDraftPhotoBtn = document.getElementById("clearDraftPhotoBtn");
const photoLightbox = document.getElementById("photoLightbox");
const lightboxImg = document.getElementById("lightboxImg");
const lightboxTitle = document.getElementById("lightboxTitle");
const toggleLightboxBtn = document.getElementById("toggleLightboxBtn");
const closeLightboxBtn = document.getElementById("closeLightboxBtn");
const signInBtn = document.getElementById("signInBtn");
const signOutBtn = document.getElementById("signOutBtn");
const accessBadge = document.getElementById("accessBadge");

let currentUser = null;
let currentBoardData = null;
let latestItemsList = [];
let hasItemsLoadedForBoard = false;
let unsubDefault = null;
let unsubOwned = null;
let unsubBoard = null;
let unsubItems = null;
let activeCode = null;
let draftPhotos = [];
let lightboxCurrentItemId = null;
let viewMode = "categories"; // "categories" | "all-grid"
let gridDensity = "standard"; // "standard" | "compact"
let selectedCategoryFilter = "ALL";
let userSelectedBoardManually = false;
let publicDirectoryBoards = [];
let ownedBoards = [];
let localKnownBoards = loadLocalBoards();

function normalizeShareCode(raw) {
  return String(raw || "")
    .trim()
    .replace(/^\/+|\/+$/g, "")
    .toUpperCase();
}

function decodeFirestoreValue(val) {
  if (!val || typeof val !== "object") return undefined;
  if ("stringValue" in val) return val.stringValue;
  if ("booleanValue" in val) return Boolean(val.booleanValue);
  if ("integerValue" in val) return Number(val.integerValue);
  if ("doubleValue" in val) return Number(val.doubleValue);
  if ("timestampValue" in val) return Date.parse(val.timestampValue) || 0;
  if ("nullValue" in val) return null;
  if ("arrayValue" in val) {
    const arr = val.arrayValue && Array.isArray(val.arrayValue.values) ? val.arrayValue.values : [];
    return arr.map(decodeFirestoreValue);
  }
  if ("mapValue" in val) {
    const fields = (val.mapValue && val.mapValue.fields) || {};
    const out = {};
    for (const [k, v] of Object.entries(fields)) {
      out[k] = decodeFirestoreValue(v);
    }
    return out;
  }
  return undefined;
}

function decodeFirestoreDoc(docObj) {
  if (!docObj || !docObj.fields) return null;
  const id = String(docObj.name || "").split("/").pop() || "";
  const data = decodeFirestoreValue({ mapValue: { fields: docObj.fields } }) || {};
  return { id, ...data };
}

async function fetchWithTimeout(url, options = {}, timeoutMs = 10000) {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } finally {
    window.clearTimeout(timer);
  }
}

async function fetchFirestoreDocRest(path) {
  const url = `${FIRESTORE_REST_BASE}/${path}?key=${encodeURIComponent(firebaseConfig.apiKey)}`;
  const res = await fetchWithTimeout(url, { method: "GET" });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const json = await res.json();
  return decodeFirestoreDoc(json);
}

async function fetchBoardItemsRest(code) {
  const url = `${FIRESTORE_REST_BASE}/shared_checklists/${encodeURIComponent(code)}:runQuery?key=${encodeURIComponent(firebaseConfig.apiKey)}`;
  const res = await fetchWithTimeout(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: "items" }]
      }
    })
  }, 15000);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const rows = await res.json();
  if (!Array.isArray(rows)) return [];
  const items = [];
  for (const row of rows) {
    if (row && row.document) {
      const decoded = decodeFirestoreDoc(row.document);
      if (decoded) items.push(decoded);
    }
  }
  return items;
}

function loadLocalBoards() {
  try {
    const raw = window.localStorage.getItem(KNOWN_BOARDS_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((entry) => entry && entry.shareCode)
      .map((entry) => ({
        shareCode: normalizeShareCode(entry.shareCode),
        title: String(entry.title || "Shop Checklist").trim()
      }))
      .filter((entry) => entry.shareCode && entry.shareCode !== PUBLIC_DEFAULT_DOC);
  } catch {
    return [];
  }
}

function rememberBoardLocally(shareCode, title) {
  const code = normalizeShareCode(shareCode);
  if (!code || code === PUBLIC_DEFAULT_DOC) return;
  const cleanTitle = String(title || "Shop Checklist").trim() || "Shop Checklist";
  const next = localKnownBoards.filter((b) => b.shareCode !== code);
  next.unshift({ shareCode: code, title: cleanTitle });
  localKnownBoards = next.slice(0, 20);
  try {
    window.localStorage.setItem(KNOWN_BOARDS_STORAGE_KEY, JSON.stringify(localKnownBoards));
  } catch {
    // Ignore storage quota / privacy mode errors
  }
}

function buildBoardOptionsList() {
  const byCode = new Map();

  const addEntry = (shareCode, title, sortOrder = 9999) => {
    const code = normalizeShareCode(shareCode);
    if (!code || code === PUBLIC_DEFAULT_DOC) return;
    const cleanTitle = String(title || "").trim() || code;
    if (!byCode.has(code)) {
      byCode.set(code, { shareCode: code, title: cleanTitle, sortOrder });
    } else {
      const existing = byCode.get(code);
      if (cleanTitle && cleanTitle !== code) existing.title = cleanTitle;
      if (typeof sortOrder === "number" && sortOrder < existing.sortOrder) {
        existing.sortOrder = sortOrder;
      }
    }
  };

  publicDirectoryBoards.forEach((b, idx) =>
    addEntry(b.shareCode, b.title, typeof b.sortOrder === "number" ? b.sortOrder : idx)
  );
  ownedBoards.forEach((b, idx) =>
    addEntry(b.shareCode, b.title, typeof b.sortOrder === "number" ? b.sortOrder : 100 + idx)
  );
  if (activeCode && currentBoardData) {
    addEntry(activeCode, currentBoardData.title || activeCode, 500);
  } else if (activeCode) {
    addEntry(activeCode, activeCode, 500);
  }
  localKnownBoards.forEach((b, idx) => addEntry(b.shareCode, b.title, 1000 + idx));

  return Array.from(byCode.values()).sort((a, b) => {
    if (a.sortOrder !== b.sortOrder) return a.sortOrder - b.sortOrder;
    return a.title.localeCompare(b.title);
  });
}

function renderBoardSwitcher() {
  if (!boardSwitcherBar || !boardSelect) return;
  if (boardSwitcherCard) boardSwitcherCard.classList.remove("hidden");
  boardSwitcherBar.classList.remove("hidden");

  const options = buildBoardOptionsList();
  boardSelect.innerHTML = "";
  if (options.length === 0) {
    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.textContent = "Loading live boards...";
    boardSelect.appendChild(placeholder);
    return;
  }

  options.forEach((entry) => {
    const opt = document.createElement("option");
    opt.value = entry.shareCode;
    opt.textContent = `${entry.title} (${entry.shareCode})`;
    if (activeCode && entry.shareCode === activeCode) {
      opt.selected = true;
    }
    boardSelect.appendChild(opt);
  });

  if (activeCode) {
    boardSelect.value = activeCode;
  }
}

if (boardSelect) {
  boardSelect.addEventListener("change", () => {
    const selected = normalizeShareCode(boardSelect.value);
    if (!selected || selected === activeCode) return;
    userSelectedBoardManually = true;
    selectedCategoryFilter = "ALL";
    openBoard(selected);
    try {
      const url = new URL(window.location.href);
      url.searchParams.set("checklist", selected);
      window.history.replaceState(null, "", url.toString());
    } catch {
      // Ignore history errors
    }
  });
}

function isItemChecked(item) {
  return Boolean(item && (item.isChecked === true || item.checked === true));
}

function toMillis(val) {
  if (typeof val === "number" && Number.isFinite(val)) return val;
  if (val && typeof val.toMillis === "function") return val.toMillis();
  if (typeof val === "string") {
    const parsed = Number(val);
    if (Number.isFinite(parsed)) return parsed;
  }
  return 0;
}

function hasPhoto(item) {
  const b64 = (item.imageBase64 || "").trim();
  const url = (item.imageUrl || "").trim();
  return b64.length > 0 || url.length > 0;
}

function resolvePhotoSrc(item) {
  const rawB64 = (item.imageBase64 || "").trim();
  if (rawB64.length > 0) {
    return rawB64.startsWith("data:") ? rawB64 : `data:image/jpeg;base64,${rawB64}`;
  }
  return (item.imageUrl || "").trim();
}

function canToggleChecklist() {
  if (canManageStock()) return true;
  if (!currentBoardData) return true;
  return currentBoardData.isPublicEditable !== false;
}

function canManageStock() {
  if (!currentUser) return false;
  if (!currentBoardData) return true;
  const members = Array.isArray(currentBoardData.memberIds) ? currentBoardData.memberIds : [];
  return currentBoardData.ownerId === currentUser.uid || members.includes(currentUser.uid);
}

function updateToolbarButtons() {
  if (viewCategoriesBtn) viewCategoriesBtn.classList.toggle("active", viewMode === "categories");
  if (viewAllGridBtn) viewAllGridBtn.classList.toggle("active", viewMode === "all-grid");
  if (densityStandardBtn) densityStandardBtn.classList.toggle("active", gridDensity === "standard");
  if (densityCompactBtn) densityCompactBtn.classList.toggle("active", gridDensity === "compact");
}

if (viewCategoriesBtn) {
  viewCategoriesBtn.addEventListener("click", () => {
    viewMode = "categories";
    updateToolbarButtons();
    if (hasItemsLoadedForBoard) renderItems(latestItemsList);
  });
}
if (viewAllGridBtn) {
  viewAllGridBtn.addEventListener("click", () => {
    viewMode = "all-grid";
    updateToolbarButtons();
    if (hasItemsLoadedForBoard) renderItems(latestItemsList);
  });
}
if (densityStandardBtn) {
  densityStandardBtn.addEventListener("click", () => {
    gridDensity = "standard";
    updateToolbarButtons();
    if (hasItemsLoadedForBoard) renderItems(latestItemsList);
  });
}
if (densityCompactBtn) {
  densityCompactBtn.addEventListener("click", () => {
    gridDensity = "compact";
    updateToolbarButtons();
    if (hasItemsLoadedForBoard) renderItems(latestItemsList);
  });
}

function refreshAccessUi() {
  const canManage = canManageStock();
  const restricted = currentBoardData && currentBoardData.isPublicEditable === false;

  if (currentUser) {
    accessBadge.textContent = currentUser.displayName || currentUser.email || "Owner";
    accessBadge.classList.remove("restricted");
    signInBtn.classList.add("hidden");
    signOutBtn.classList.remove("hidden");
  } else if (restricted) {
    accessBadge.textContent = "View Only (Restricted)";
    accessBadge.classList.add("restricted");
    signInBtn.classList.remove("hidden");
    signOutBtn.classList.add("hidden");
  } else {
    accessBadge.textContent = "Public View & Mark Only";
    accessBadge.classList.remove("restricted");
    signInBtn.classList.add("hidden");
    signOutBtn.classList.add("hidden");
  }

  if (canManage && activeCode) {
    addItemForm.classList.remove("hidden");
  } else {
    addItemForm.classList.add("hidden");
  }

  if (hasItemsLoadedForBoard) {
    renderItems(latestItemsList);
  }
}

function subscribeToOwnedBoards(uid) {
  if (unsubOwned) {
    unsubOwned();
    unsubOwned = null;
  }
  if (!uid) {
    ownedBoards = [];
    renderBoardSwitcher();
    return;
  }
  const ownedQuery = query(
    collection(db, "shared_checklists"),
    where("memberIds", "array-contains", uid)
  );
  unsubOwned = onSnapshot(
    ownedQuery,
    (snap) => {
      const list = [];
      snap.forEach((docSnap) => {
        const data = docSnap.data() || {};
        const code = normalizeShareCode(data.shareCode || docSnap.id);
        if (!code || code === PUBLIC_DEFAULT_DOC) return;
        list.push({
          shareCode: code,
          title: String(data.title || "Shop Checklist").trim(),
          sortOrder: typeof data.sortOrder === "number" ? data.sortOrder : 0
        });
      });
      ownedBoards = list;
      renderBoardSwitcher();
    },
    () => {
      // Ignore transient query errors; public directory still works
    }
  );
}

onAuthStateChanged(auth, (user) => {
  currentUser = user;
  subscribeToOwnedBoards(user ? user.uid : null);
  refreshAccessUi();
});

signInBtn.addEventListener("click", async () => {
  try {
    await signInWithPopup(auth, provider);
  } catch (err) {
    if (err && err.code === "auth/unauthorized-domain") {
      alert(
        "To enable restricted Google Sign-In on " +
          window.location.hostname +
          ", add this domain in Firebase Console -> Authentication -> Settings -> Authorized domains."
      );
    } else {
      alert("Sign-in error: " + (err?.message || "Please try again."));
    }
  }
});

signOutBtn.addEventListener("click", () => signOut(auth));

function updateLocalItemCheckedState(itemId, nextChecked, updatedAt) {
  latestItemsList = latestItemsList.map((it) =>
    it.id === itemId ? { ...it, isChecked: nextChecked, updatedAt } : it
  );
  renderItems(latestItemsList);
}

function buildPhotoGridElement(list, { showCategoryBadge = false, canToggle, canManage }) {
  const grid = document.createElement("div");
  grid.className = `photo-notes-grid${gridDensity === "compact" ? " compact" : ""}`;

  list.forEach((item) => {
    const checked = isItemChecked(item);
    const pCard = document.createElement("div");
    pCard.className = `photo-note-card ${checked ? "checked" : ""}`;

    const thumbWrap = document.createElement("div");
    thumbWrap.className = "photo-note-thumb-wrap";
    const img = document.createElement("img");
    img.src = resolvePhotoSrc(item);
    img.alt = item.title || "Photo Note";
    img.loading = "lazy";
    img.style.cursor = "pointer";
    img.addEventListener("click", () => {
      if (photoLightbox && lightboxImg && lightboxTitle) {
        lightboxImg.src = img.src;
        lightboxTitle.textContent = item.title || "Photo Note";
        lightboxCurrentItemId = item.id;
        if (toggleLightboxBtn) {
          if (canToggleChecklist()) {
            toggleLightboxBtn.classList.remove("hidden");
            toggleLightboxBtn.textContent = checked ? "Unmark" : "Mark";
          } else {
            toggleLightboxBtn.classList.add("hidden");
          }
        }
        photoLightbox.classList.remove("hidden");
      }
    });
    thumbWrap.appendChild(img);

    const circleBtn = document.createElement("button");
    circleBtn.type = "button";
    circleBtn.className = "photo-select-circle";
    circleBtn.title = checked ? "Unselect photo note" : "Select photo note";
    circleBtn.textContent = checked ? "✓" : "";
    circleBtn.disabled = !canToggle;
    circleBtn.addEventListener("click", async (e) => {
      e.stopPropagation();
      if (!canToggleChecklist()) return;
      const now = Date.now();
      const targetBoardCode = activeCode;
      circleBtn.disabled = true;
      updateLocalItemCheckedState(item.id, !checked, now);
      try {
        await updateDoc(doc(db, "shared_checklists", targetBoardCode, "items", item.id), {
          isChecked: !checked,
          updatedAt: now
        });
      } catch (err) {
        updateLocalItemCheckedState(item.id, checked, item.updatedAt || 0);
        alert("Could not update photo note: " + (err?.message || "Permission denied"));
      }
    });
    thumbWrap.appendChild(circleBtn);

    if (showCategoryBadge && item.category) {
      const badge = document.createElement("span");
      badge.className = "photo-cat-badge";
      badge.textContent = item.category;
      thumbWrap.appendChild(badge);
    }

    pCard.appendChild(thumbWrap);

    const footer = document.createElement("div");
    footer.className = "photo-note-footer";
    const titleSpan = document.createElement("span");
    titleSpan.className = "photo-note-title";
    titleSpan.textContent = item.quantity
      ? `${item.title || "Photo Note"} • ${item.quantity}`
      : (item.title || "Photo Note");
    footer.appendChild(titleSpan);

    if (canManage) {
      const delBtn = document.createElement("button");
      delBtn.type = "button";
      delBtn.className = "btn-icon";
      delBtn.title = "Remove photo note";
      delBtn.textContent = "×";
      delBtn.addEventListener("click", async () => {
        if (!canManageStock()) return;
        try {
          await deleteDoc(doc(db, "shared_checklists", activeCode, "items", item.id));
        } catch (err) {
          alert("Could not remove item: " + (err?.message || "Permission denied"));
        }
      });
      footer.appendChild(delBtn);
    }

    pCard.appendChild(footer);
    grid.appendChild(pCard);
  });

  return grid;
}

function buildTextItemRow(item, { canToggle, canManage, showCategoryTag = false }) {
  const checked = isItemChecked(item);
  const row = document.createElement("div");
  row.className = `item-row ${checked ? "checked" : ""}`;

  const cb = document.createElement("input");
  cb.type = "checkbox";
  cb.checked = checked;
  cb.disabled = !canToggle;
  cb.addEventListener("change", async () => {
    if (!canToggleChecklist()) return;
    const next = cb.checked;
    const now = Date.now();
    const targetBoardCode = activeCode;
    updateLocalItemCheckedState(item.id, next, now);
    try {
      await updateDoc(doc(db, "shared_checklists", targetBoardCode, "items", item.id), {
        isChecked: next,
        updatedAt: now
      });
    } catch (err) {
      updateLocalItemCheckedState(item.id, !next, item.updatedAt || 0);
      alert("Could not update item: " + (err?.message || "Permission denied"));
    }
  });

  const span = document.createElement("span");
  span.className = "item-label";
  span.textContent = showCategoryTag && item.category
    ? `${item.title} (${item.category})`
    : item.title;
  span.addEventListener("click", () => {
    if (!cb.disabled) cb.click();
  });

  row.appendChild(cb);
  row.appendChild(span);

  if (item.quantity) {
    const pill = document.createElement("span");
    pill.className = "qty-pill";
    pill.textContent = item.quantity;
    row.appendChild(pill);
  }

  if (canManage) {
    const delBtn = document.createElement("button");
    delBtn.type = "button";
    delBtn.className = "btn-icon";
    delBtn.title = "Remove item";
    delBtn.textContent = "×";
    delBtn.addEventListener("click", async () => {
      if (!canManageStock()) return;
      try {
        await deleteDoc(doc(db, "shared_checklists", activeCode, "items", item.id));
      } catch (err) {
        alert("Could not remove item: " + (err?.message || "Permission denied"));
      }
    });
    row.appendChild(delBtn);
  }

  return row;
}

function renderCategoryFilterChips(sortedCats) {
  if (!categoryFilterBar) return;
  if (sortedCats.length <= 1) {
    categoryFilterBar.classList.add("hidden");
    categoryFilterBar.innerHTML = "";
    selectedCategoryFilter = "ALL";
    return;
  }
  if (selectedCategoryFilter !== "ALL" && !sortedCats.includes(selectedCategoryFilter)) {
    selectedCategoryFilter = "ALL";
  }

  categoryFilterBar.classList.remove("hidden");
  categoryFilterBar.innerHTML = "";

  const allChip = document.createElement("button");
  allChip.type = "button";
  allChip.className = `cat-chip ${selectedCategoryFilter === "ALL" ? "active" : ""}`;
  allChip.textContent = `All (${sortedCats.length})`;
  allChip.addEventListener("click", () => {
    selectedCategoryFilter = "ALL";
    if (hasItemsLoadedForBoard) renderItems(latestItemsList);
  });
  categoryFilterBar.appendChild(allChip);

  sortedCats.forEach((cat) => {
    const chip = document.createElement("button");
    chip.type = "button";
    chip.className = `cat-chip ${selectedCategoryFilter === cat ? "active" : ""}`;
    chip.textContent = cat;
    chip.addEventListener("click", () => {
      selectedCategoryFilter = cat;
      if (hasItemsLoadedForBoard) renderItems(latestItemsList);
    });
    categoryFilterBar.appendChild(chip);
  });
}

function normalizeSnapshotDocs(snapOrArray) {
  if (Array.isArray(snapOrArray)) {
    return snapOrArray.map((item) => ({ ...item }));
  }
  const list = [];
  if (snapOrArray && snapOrArray.docs) {
    snapOrArray.docs.forEach((docSnap) => {
      list.push({ id: docSnap.id, ...docSnap.data() });
    });
  }
  return list;
}

function renderItems(snapOrArray) {
  const rawItems = normalizeSnapshotDocs(snapOrArray);
  rawItems.sort((a, b) => toMillis(b.createdAt) - toMillis(a.createdAt));
  latestItemsList = rawItems;
  hasItemsLoadedForBoard = true;

  const allItems = [];
  const grouped = new Map();
  let totalCount = 0;
  let pendingCount = 0;

  rawItems.forEach((raw) => {
    const item = { ...raw };
    const cat = (item.category || "Supplies").trim() || "Supplies";
    item.category = cat;
    allItems.push(item);
    totalCount += 1;
    if (!isItemChecked(item)) pendingCount += 1;
    if (!grouped.has(cat)) grouped.set(cat, []);
    grouped.get(cat).push(item);
  });

  if (currentBoardData) {
    const modeText = currentBoardData.isPublicEditable === false ? "View Only" : "View & Mark Only";
    const photoCount = allItems.filter(hasPhoto).length;
    boardMeta.textContent = `${pendingCount} pending • ${totalCount} items (${photoCount} photos) • ${modeText}`;
  }

  const canToggle = canToggleChecklist();
  const canManage = canManageStock();
  categoriesContainer.innerHTML = "";
  const sortedCats = Array.from(grouped.keys()).sort((a, b) => a.localeCompare(b));
  renderCategoryFilterChips(sortedCats);

  if (sortedCats.length === 0) {
    categoriesContainer.innerHTML = '<p class="subtitle">No items on this checklist yet.</p>';
    return;
  }

  if (viewMode === "all-grid") {
    const visibleItems = selectedCategoryFilter === "ALL"
      ? allItems
      : (grouped.get(selectedCategoryFilter) || []);

    const unmarkedPhotos = visibleItems.filter((it) => hasPhoto(it) && !isItemChecked(it));
    const markedPhotos = visibleItems
      .filter((it) => hasPhoto(it) && isItemChecked(it))
      .sort((a, b) => toMillis(a.updatedAt) - toMillis(b.updatedAt));
    const unmarkedText = visibleItems.filter((it) => !hasPhoto(it) && !isItemChecked(it));
    const markedText = visibleItems
      .filter((it) => !hasPhoto(it) && isItemChecked(it))
      .sort((a, b) => toMillis(a.updatedAt) - toMillis(b.updatedAt));

    const wrapper = document.createElement("div");
    wrapper.className = "cat-card";

    const headerRow = document.createElement("div");
    headerRow.className = "cat-header-row";
    const h3 = document.createElement("h3");
    h3.className = "cat-title";
    h3.textContent = selectedCategoryFilter === "ALL" ? "All Photos Grid" : selectedCategoryFilter;
    const countSpan = document.createElement("span");
    countSpan.className = "cat-count";
    countSpan.textContent = `${unmarkedPhotos.length + markedPhotos.length} photos`;
    headerRow.appendChild(h3);
    headerRow.appendChild(countSpan);
    wrapper.appendChild(headerRow);

    if (unmarkedPhotos.length > 0) {
      wrapper.appendChild(
        buildPhotoGridElement(unmarkedPhotos, {
          showCategoryBadge: selectedCategoryFilter === "ALL",
          canToggle,
          canManage
        })
      );
    }

    const orderedText = [...unmarkedText, ...markedText];
    if (orderedText.length > 0) {
      const textHeader = document.createElement("div");
      textHeader.className = "marked-section-label";
      textHeader.textContent = `Items (${orderedText.length})`;
      wrapper.appendChild(textHeader);
      orderedText.forEach((item) => {
        wrapper.appendChild(
          buildTextItemRow(item, {
            canToggle,
            canManage,
            showCategoryTag: selectedCategoryFilter === "ALL"
          })
        );
      });
    }

    if (markedPhotos.length > 0) {
      const label = document.createElement("div");
      label.className = "marked-section-label";
      label.textContent = `Marked (${markedPhotos.length})`;
      wrapper.appendChild(label);
      wrapper.appendChild(
        buildPhotoGridElement(markedPhotos, {
          showCategoryBadge: selectedCategoryFilter === "ALL",
          canToggle,
          canManage
        })
      );
    }

    if (unmarkedPhotos.length === 0 && markedPhotos.length === 0 && orderedText.length === 0) {
      wrapper.innerHTML += '<p class="subtitle">No items in this category.</p>';
    }

    categoriesContainer.appendChild(wrapper);
    return;
  }

  const visibleCats = selectedCategoryFilter === "ALL"
    ? sortedCats
    : sortedCats.filter((c) => c === selectedCategoryFilter);

  visibleCats.forEach((cat) => {
    const catItems = grouped.get(cat) || [];
    const card = document.createElement("div");
    card.className = "cat-card";

    const headerRow = document.createElement("div");
    headerRow.className = "cat-header-row";
    const h3 = document.createElement("h3");
    h3.className = "cat-title";
    h3.textContent = cat;
    const countSpan = document.createElement("span");
    countSpan.className = "cat-count";
    countSpan.textContent = `${catItems.filter((it) => !isItemChecked(it)).length} pending • ${catItems.length}`;
    headerRow.appendChild(h3);
    headerRow.appendChild(countSpan);
    card.appendChild(headerRow);

    const unmarkedPhotos = catItems.filter((it) => hasPhoto(it) && !isItemChecked(it));
    const unmarkedText = catItems.filter((it) => !hasPhoto(it) && !isItemChecked(it));
    const markedText = catItems
      .filter((it) => !hasPhoto(it) && isItemChecked(it))
      .sort((a, b) => toMillis(a.updatedAt) - toMillis(b.updatedAt));
    const markedPhotos = catItems
      .filter((it) => hasPhoto(it) && isItemChecked(it))
      .sort((a, b) => toMillis(a.updatedAt) - toMillis(b.updatedAt));

    if (unmarkedPhotos.length > 0) {
      card.appendChild(buildPhotoGridElement(unmarkedPhotos, { canToggle, canManage }));
    }

    const orderedText = [...unmarkedText, ...markedText];
    orderedText.forEach((item) => {
      card.appendChild(buildTextItemRow(item, { canToggle, canManage }));
    });

    if (markedPhotos.length > 0) {
      const label = document.createElement("div");
      label.className = "marked-section-label";
      label.textContent = `Marked (${markedPhotos.length})`;
      card.appendChild(label);
      card.appendChild(buildPhotoGridElement(markedPhotos, { canToggle, canManage }));
    }
    categoriesContainer.appendChild(card);
  });
}

function applyBoardData(code, boardData) {
  if (activeCode !== code) return;
  if (!boardData) {
    currentBoardData = null;
    boardTitle.textContent = `Checklist "${code}" not found`;
    boardMeta.textContent = "Ask the shop owner to share an updated Public Link from ShopKeep.";
    renderBoardSwitcher();
    refreshAccessUi();
    return;
  }
  currentBoardData = boardData;
  boardTitle.textContent = currentBoardData.title || "ShopKeep Checklist";
  rememberBoardLocally(code, boardTitle.textContent);
  renderBoardSwitcher();
  refreshAccessUi();
}

async function openBoard(rawCode) {
  let code = normalizeShareCode(rawCode);
  if (!code) return;

  if (code === PUBLIC_DEFAULT_DOC) {
    try {
      const defaultData = await fetchFirestoreDocRest(`shared_checklists/${PUBLIC_DEFAULT_DOC}`);
      const resolved = defaultData ? normalizeShareCode(defaultData.activeShareCode) : "";
      if (resolved && resolved !== PUBLIC_DEFAULT_DOC) {
        code = resolved;
      } else {
        boardSection.classList.add("hidden");
        emptyState.classList.remove("hidden");
        emptyTitle.textContent = "Waiting for Live Board...";
        emptyDesc.textContent = "Open ShopKeep -> Checklists -> tap \"Public Link\" once.";
        return;
      }
    } catch {
      subscribeToPublicDirectory(true);
      return;
    }
  }

  if (activeCode === code && unsubBoard && unsubItems) return;

  activeCode = code;
  latestItemsList = [];
  hasItemsLoadedForBoard = false;
  if (codeInput) codeInput.value = code;
  renderBoardSwitcher();

  if (unsubBoard) unsubBoard();
  if (unsubItems) unsubItems();

  boardSection.classList.remove("hidden");
  emptyState.classList.add("hidden");
  if (retryLoadBtn) retryLoadBtn.classList.add("hidden");
  boardTitle.textContent = "Loading Live Checklist...";
  boardMeta.textContent = `Connecting to board ${code}...`;
  categoriesContainer.innerHTML = '<p class="subtitle">Loading items and photos...</p>';

  // Fast REST bootstrap so the board and photos render immediately on mobile networks
  fetchFirestoreDocRest(`shared_checklists/${encodeURIComponent(code)}`)
    .then((data) => applyBoardData(code, data))
    .catch((err) => {
      if (activeCode === code && !currentBoardData) {
        boardMeta.textContent = `Syncing (${err?.message || "network"})...`;
      }
    });

  fetchBoardItemsRest(code)
    .then((items) => {
      if (activeCode === code && !hasItemsLoadedForBoard) {
        renderItems(items);
      }
    })
    .catch((err) => {
      if (activeCode === code && !hasItemsLoadedForBoard) {
        categoriesContainer.innerHTML = `<p class="subtitle">Loading live stream (${err?.message || "retrying"})...</p>`;
      }
    });

  // Real-time SDK listeners for live mark/unmark & stock updates
  const boardRef = doc(db, "shared_checklists", code);
  unsubBoard = onSnapshot(
    boardRef,
    (snap) => {
      if (activeCode !== code) return;
      applyBoardData(code, snap.exists() ? snap.data() : null);
    },
    (err) => {
      if (activeCode === code && !currentBoardData) {
        boardTitle.textContent = "Unable to connect to live board";
        boardMeta.textContent = err?.message || "Check internet connection.";
      }
    }
  );

  const itemsColRef = collection(db, "shared_checklists", code, "items");
  unsubItems = onSnapshot(
    itemsColRef,
    (snap) => {
      if (activeCode !== code) return;
      renderItems(snap);
    },
    (err) => {
      if (activeCode === code && !hasItemsLoadedForBoard) {
        categoriesContainer.innerHTML = `<p class="subtitle">Unable to load items: ${err?.message || "Connection error"}</p>`;
      }
    }
  );
}

function handlePublicDirectoryData(data, autoOpenDefault) {
  if (!data) {
    publicDirectoryBoards = [];
    renderBoardSwitcher();
    if (autoOpenDefault && !activeCode) {
      boardSection.classList.add("hidden");
      emptyState.classList.remove("hidden");
      emptyTitle.textContent = "Public Checklist Not Published Yet";
      emptyDesc.textContent =
        "Open your ShopKeep Android app -> Checklists -> tap \"Public Link\" once. It will appear here automatically!";
    }
    return;
  }

  const dirRaw = Array.isArray(data.boardsDirectory) ? data.boardsDirectory : [];
  publicDirectoryBoards = dirRaw
    .filter((entry) => entry && entry.shareCode)
    .map((entry) => ({
      shareCode: normalizeShareCode(entry.shareCode),
      title: String(entry.title || "Shop Checklist").trim(),
      sortOrder: typeof entry.sortOrder === "number" ? entry.sortOrder : 0
    }));

  renderBoardSwitcher();

  const targetCode = normalizeShareCode(data.activeShareCode) ||
    (publicDirectoryBoards[0]?.shareCode || "");

  if (autoOpenDefault && !userSelectedBoardManually) {
    if (!targetCode) {
      if (!activeCode) {
        emptyTitle.textContent = "Waiting for Live Board...";
        emptyDesc.textContent = "Tap 'Public Link' in the ShopKeep app.";
      }
      return;
    }
    openBoard(targetCode);
  }
}

function subscribeToPublicDirectory(autoOpenDefault = true) {
  if (retryLoadBtn) retryLoadBtn.classList.add("hidden");
  if (unsubDefault) unsubDefault();

  // Fast REST bootstrap so dropdown + default board load immediately without waiting on WebChannel
  fetchFirestoreDocRest(`shared_checklists/${PUBLIC_DEFAULT_DOC}`)
    .then((data) => handlePublicDirectoryData(data, autoOpenDefault))
    .catch((err) => {
      if (autoOpenDefault && !activeCode) {
        emptyTitle.textContent = "Connection Issue";
        emptyDesc.textContent = `Could not reach ShopKeep server (${err?.message || "network error"}). Check internet and tap Retry.`;
        if (retryLoadBtn) retryLoadBtn.classList.remove("hidden");
      }
    });

  const defaultRef = doc(db, "shared_checklists", PUBLIC_DEFAULT_DOC);
  unsubDefault = onSnapshot(
    defaultRef,
    (snap) => {
      handlePublicDirectoryData(snap.exists() ? snap.data() : null, autoOpenDefault);
    },
    (err) => {
      if (autoOpenDefault && !activeCode) {
        emptyTitle.textContent = "Connection Issue";
        emptyDesc.textContent = err?.message || "Unable to connect to Firebase Firestore.";
        if (retryLoadBtn) retryLoadBtn.classList.remove("hidden");
      }
    }
  );
}

if (retryLoadBtn) {
  retryLoadBtn.addEventListener("click", () => {
    emptyTitle.textContent = "Reconnecting...";
    emptyDesc.textContent = "Fetching ShopKeep Live boards...";
    retryLoadBtn.classList.add("hidden");
    const code = extractInitialShareCodeFromUrl();
    if (code) {
      openBoard(code);
      subscribeToPublicDirectory(false);
    } else {
      subscribeToPublicDirectory(true);
    }
  });
}

openCodeBtn.addEventListener("click", () => {
  const val = normalizeShareCode(codeInput.value);
  if (val) {
    userSelectedBoardManually = true;
    selectedCategoryFilter = "ALL";
    openBoard(val);
    try {
      const url = new URL(window.location.href);
      url.searchParams.set("checklist", val);
      window.history.replaceState(null, "", url.toString());
    } catch {
      // Ignore history errors
    }
  }
});

function compressImageToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error("Failed to read photo"));
    reader.onload = () => {
      const img = new Image();
      img.onerror = () => reject(new Error("Invalid image"));
      img.onload = () => {
        const maxDim = 720;
        const scale = Math.min(1, maxDim / Math.max(img.width, img.height));
        const w = Math.max(1, Math.round(img.width * scale));
        const h = Math.max(1, Math.round(img.height * scale));
        const canvas = document.createElement("canvas");
        canvas.width = w;
        canvas.height = h;
        const ctx = canvas.getContext("2d");
        ctx.drawImage(img, 0, 0, w, h);
        const dataUrl = canvas.toDataURL("image/jpeg", 0.74);
        resolve(dataUrl.split(",")[1] || "");
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  });
}

if (attachPhotoBtn && itemPhotoInput) {
  attachPhotoBtn.addEventListener("click", () => itemPhotoInput.click());
  itemPhotoInput.addEventListener("change", async () => {
    const files = Array.from(itemPhotoInput.files || []);
    if (files.length === 0) return;
    try {
      const encoded = await Promise.all(
        files.map(async (f) => ({
          base64: await compressImageToBase64(f),
          name: (f.name || "").replace(/\.(jpe?g|png|webp|heic|heif|bmp|gif)$/i, "").trim()
        }))
      );
      draftPhotos = encoded.filter((entry) => entry.base64 && entry.base64.length > 0);
      if (draftPhotos.length > 0 && draftPhotoImg && draftPhotoPreview) {
        draftPhotoImg.src = `data:image/jpeg;base64,${draftPhotos[0].base64}`;
        if (draftPhotoLabel) {
          draftPhotoLabel.textContent =
            draftPhotos.length === 1
              ? (draftPhotos[0].name || "1 photo attached")
              : `${draftPhotos.length} photos ready to add`;
        }
        draftPhotoPreview.classList.remove("hidden");
      }
    } catch (err) {
      alert(err?.message || "Could not attach photo(s)");
    }
  });
}

if (clearDraftPhotoBtn) {
  clearDraftPhotoBtn.addEventListener("click", () => {
    draftPhotos = [];
    if (itemPhotoInput) itemPhotoInput.value = "";
    if (draftPhotoPreview) draftPhotoPreview.classList.add("hidden");
  });
}

if (toggleLightboxBtn && photoLightbox) {
  toggleLightboxBtn.addEventListener("click", async () => {
    if (!canToggleChecklist() || !activeCode || !lightboxCurrentItemId) return;
    const matchItem = latestItemsList.find((it) => it.id === lightboxCurrentItemId);
    if (!matchItem) return;
    const prevChecked = isItemChecked(matchItem);
    const nextChecked = !prevChecked;
    const now = Date.now();
    toggleLightboxBtn.disabled = true;
    updateLocalItemCheckedState(lightboxCurrentItemId, nextChecked, now);
    toggleLightboxBtn.textContent = nextChecked ? "Unmark" : "Mark";
    try {
      await updateDoc(doc(db, "shared_checklists", activeCode, "items", lightboxCurrentItemId), {
        isChecked: nextChecked,
        updatedAt: now
      });
    } catch (err) {
      updateLocalItemCheckedState(lightboxCurrentItemId, prevChecked, matchItem.updatedAt || 0);
      toggleLightboxBtn.textContent = prevChecked ? "Unmark" : "Mark";
      alert("Could not update photo note: " + (err?.message || "Permission denied"));
    } finally {
      toggleLightboxBtn.disabled = false;
    }
  });
}

if (closeLightboxBtn && photoLightbox) {
  closeLightboxBtn.addEventListener("click", () => {
    lightboxCurrentItemId = null;
    photoLightbox.classList.add("hidden");
  });
  photoLightbox.addEventListener("click", (e) => {
    if (e.target === photoLightbox) {
      lightboxCurrentItemId = null;
      photoLightbox.classList.add("hidden");
    }
  });
}

addItemForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  if (!canManageStock() || !activeCode) return;
  const rawTitle = document.getElementById("itemTitleInput").value.trim();
  const quantity = document.getElementById("itemQtyInput").value.trim();
  const category = document.getElementById("itemCatInput").value.trim() || "Supplies";
  if (!rawTitle && draftPhotos.length === 0) return;

  const baseTime = Date.now();
  try {
    if (draftPhotos.length > 0) {
      for (let i = 0; i < draftPhotos.length; i++) {
        const id = crypto.randomUUID ? crypto.randomUUID() : `item_${baseTime}_${i}_${Math.random().toString(36).slice(2, 8)}`;
        const resolvedTitle = rawTitle
          ? (draftPhotos.length === 1 ? rawTitle : `${rawTitle} ${i + 1}`)
          : (draftPhotos[i].name || (draftPhotos.length === 1 ? "Photo Note" : `Photo Note ${i + 1}`));
        await setDoc(doc(db, "shared_checklists", activeCode, "items", id), {
          title: resolvedTitle,
          quantity,
          category,
          notes: "",
          isChecked: false,
          imageBase64: draftPhotos[i].base64,
          addedByUid: currentUser ? currentUser.uid : "public",
          addedByName: currentUser ? (currentUser.displayName || "Collaborator") : "Public Visitor",
          createdAt: baseTime + i,
          updatedAt: baseTime + i
        });
      }
    } else {
      const id = crypto.randomUUID ? crypto.randomUUID() : `item_${baseTime}_${Math.random().toString(36).slice(2, 8)}`;
      await setDoc(doc(db, "shared_checklists", activeCode, "items", id), {
        title: rawTitle,
        quantity,
        category,
        notes: "",
        isChecked: false,
        imageBase64: "",
        addedByUid: currentUser ? currentUser.uid : "public",
        addedByName: currentUser ? (currentUser.displayName || "Collaborator") : "Public Visitor",
        createdAt: baseTime,
        updatedAt: baseTime
      });
    }
    document.getElementById("itemTitleInput").value = "";
    document.getElementById("itemQtyInput").value = "";
    draftPhotos = [];
    if (itemPhotoInput) itemPhotoInput.value = "";
    if (draftPhotoPreview) draftPhotoPreview.classList.add("hidden");
  } catch (err) {
    alert("Could not add item(s): " + (err?.message || "Permission denied"));
  }
});

function extractInitialShareCodeFromUrl() {
  const params = new URLSearchParams(window.location.search);
  const direct = normalizeShareCode(params.get("checklist") || params.get("board") || "");
  if (direct && direct !== PUBLIC_DEFAULT_DOC) return direct;
  const pathMatch = window.location.pathname.match(/\/checklist\/([^/?#]+)/i);
  if (pathMatch && pathMatch[1]) {
    const seg = normalizeShareCode(pathMatch[1]);
    if (seg && seg !== PUBLIC_DEFAULT_DOC) return seg;
  }
  return "";
}

window.addEventListener("error", (event) => {
  if (emptyDesc && !activeCode) {
    emptyTitle.textContent = "Script Error";
    emptyDesc.textContent = event?.message || "Unexpected error loading ShopKeep Live.";
  }
});

const initialCode = extractInitialShareCodeFromUrl();
renderBoardSwitcher();
if (initialCode) {
  userSelectedBoardManually = true;
  openBoard(initialCode);
  subscribeToPublicDirectory(false);
} else {
  subscribeToPublicDirectory(true);
}
