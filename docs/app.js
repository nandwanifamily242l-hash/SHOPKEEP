import { initializeApp } from "https://www.gstatic.com/firebasejs/11.6.0/firebase-app.js";
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signOut,
  onAuthStateChanged
} from "https://www.gstatic.com/firebasejs/11.6.0/firebase-auth.js";
import {
  getFirestore,
  doc,
  collection,
  onSnapshot,
  setDoc,
  updateDoc,
  deleteDoc,
  query,
  orderBy
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

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app, DATABASE_ID);
const provider = new GoogleAuthProvider();

const codeInput = document.getElementById("codeInput");
const openCodeBtn = document.getElementById("openCodeBtn");
const boardSection = document.getElementById("boardSection");
const emptyState = document.getElementById("emptyState");
const emptyTitle = document.getElementById("emptyTitle");
const emptyDesc = document.getElementById("emptyDesc");
const boardTitle = document.getElementById("boardTitle");
const boardMeta = document.getElementById("boardMeta");
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
let latestItemsSnap = null;
let unsubDefault = null;
let unsubBoard = null;
let unsubItems = null;
let activeCode = null;
let draftPhotos = [];
let lightboxCurrentItemId = null;
let viewMode = "categories"; // "categories" | "all-grid"
let gridDensity = "standard"; // "standard" | "compact"
let selectedCategoryFilter = "ALL";

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
    if (latestItemsSnap) renderItems(latestItemsSnap);
  });
}
if (viewAllGridBtn) {
  viewAllGridBtn.addEventListener("click", () => {
    viewMode = "all-grid";
    updateToolbarButtons();
    if (latestItemsSnap) renderItems(latestItemsSnap);
  });
}
if (densityStandardBtn) {
  densityStandardBtn.addEventListener("click", () => {
    gridDensity = "standard";
    updateToolbarButtons();
    if (latestItemsSnap) renderItems(latestItemsSnap);
  });
}
if (densityCompactBtn) {
  densityCompactBtn.addEventListener("click", () => {
    gridDensity = "compact";
    updateToolbarButtons();
    if (latestItemsSnap) renderItems(latestItemsSnap);
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

  if (latestItemsSnap) {
    renderItems(latestItemsSnap);
  }
}

onAuthStateChanged(auth, (user) => {
  currentUser = user;
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
      circleBtn.disabled = true;
      try {
        await updateDoc(doc(db, "shared_checklists", activeCode, "items", item.id), {
          isChecked: !checked,
          updatedAt: Date.now()
        });
      } catch (err) {
        circleBtn.disabled = false;
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
    try {
      await updateDoc(doc(db, "shared_checklists", activeCode, "items", item.id), {
        isChecked: cb.checked,
        updatedAt: Date.now()
      });
    } catch (err) {
      cb.checked = !cb.checked;
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
    if (latestItemsSnap) renderItems(latestItemsSnap);
  });
  categoryFilterBar.appendChild(allChip);

  sortedCats.forEach((cat) => {
    const chip = document.createElement("button");
    chip.type = "button";
    chip.className = `cat-chip ${selectedCategoryFilter === cat ? "active" : ""}`;
    chip.textContent = cat;
    chip.addEventListener("click", () => {
      selectedCategoryFilter = cat;
      if (latestItemsSnap) renderItems(latestItemsSnap);
    });
    categoryFilterBar.appendChild(chip);
  });
}

function renderItems(snap) {
  const allItems = [];
  const grouped = new Map();
  let totalCount = 0;
  let pendingCount = 0;

  snap.forEach((docSnap) => {
    const item = { id: docSnap.id, ...docSnap.data() };
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

function openBoard(rawCode) {
  const code = (rawCode || "").trim().toUpperCase();
  if (!code) return;
  if (activeCode === code && unsubBoard && unsubItems) return;

  activeCode = code;
  if (codeInput) codeInput.value = code;
  renderBoardSwitcher();

  if (unsubBoard) unsubBoard();
  if (unsubItems) unsubItems();

  boardSection.classList.remove("hidden");
  emptyState.classList.add("hidden");

  const boardRef = doc(db, "shared_checklists", code);
  unsubBoard = onSnapshot(
    boardRef,
    (snap) => {
      if (!snap.exists()) {
        currentBoardData = null;
        boardTitle.textContent = `Checklist "${code}" not found`;
        boardMeta.textContent = "Ask the shop owner to tap 'Publish & Share Public Live Link' in ShopKeep.";
        renderBoardSwitcher();
        refreshAccessUi();
        return;
      }
      currentBoardData = snap.data();
      boardTitle.textContent = currentBoardData.title || "ShopKeep Checklist";
      rememberBoardLocally(code, boardTitle.textContent);
      renderBoardSwitcher();
      refreshAccessUi();
    },
    (err) => {
      boardTitle.textContent = "Unable to connect to live board";
      boardMeta.textContent = err?.message || "Check internet connection.";
    }
  );

  const itemsQuery = query(
    collection(db, "shared_checklists", code, "items"),
    orderBy("createdAt", "desc")
  );
  unsubItems = onSnapshot(
    itemsQuery,
    (snap) => {
      latestItemsSnap = snap;
      renderItems(snap);
    },
    (err) => {
      categoriesContainer.innerHTML = `<p class="subtitle">Unable to load items: ${err?.message || "Connection error"}</p>`;
    }
  );
}

function subscribeToPublicDirectory(autoOpenDefault = true) {
  if (unsubDefault) unsubDefault();
  const defaultRef = doc(db, "shared_checklists", PUBLIC_DEFAULT_DOC);
  unsubDefault = onSnapshot(
    defaultRef,
    (snap) => {
      if (!snap.exists()) {
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
      const data = snap.data() || {};
      const dirRaw = Array.isArray(data.boardsDirectory) ? data.boardsDirectory : [];
      publicDirectoryBoards = dirRaw
        .filter((entry) => entry && entry.shareCode)
        .map((entry) => ({
          shareCode: String(entry.shareCode).trim().toUpperCase(),
          title: String(entry.title || "Shop Checklist").trim(),
          sortOrder: typeof entry.sortOrder === "number" ? entry.sortOrder : 0
        }));

      renderBoardSwitcher();

      const targetCode = (data.activeShareCode || "").trim().toUpperCase() ||
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
    },
    (err) => {
      if (autoOpenDefault && !activeCode) {
        emptyTitle.textContent = "Connection Issue";
        emptyDesc.textContent = err?.message || "Unable to connect to Firebase Firestore.";
      }
    }
  );
}

openCodeBtn.addEventListener("click", () => {
  const val = codeInput.value.trim();
  if (val) {
    userSelectedBoardManually = true;
    selectedCategoryFilter = "ALL";
    openBoard(val);
    try {
      const url = new URL(window.location.href);
      url.searchParams.set("checklist", val.toUpperCase());
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
    if (!canToggleChecklist() || !activeCode || !lightboxCurrentItemId || !latestItemsSnap) return;
    const matchDoc = latestItemsSnap.docs.find((d) => d.id === lightboxCurrentItemId);
    if (!matchDoc) return;
    const nextChecked = !isItemChecked(matchDoc.data());
    toggleLightboxBtn.disabled = true;
    try {
      await updateDoc(doc(db, "shared_checklists", activeCode, "items", lightboxCurrentItemId), {
        isChecked: nextChecked,
        updatedAt: Date.now()
      });
      toggleLightboxBtn.textContent = nextChecked ? "Unmark" : "Mark";
    } catch (err) {
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

const params = new URLSearchParams(window.location.search);
const initialCode = (params.get("checklist") || "").trim();
if (initialCode) {
  userSelectedBoardManually = true;
  openBoard(initialCode);
  subscribeToPublicDirectory(false);
} else {
  subscribeToPublicDirectory(true);
}
