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
const addItemForm = document.getElementById("addItemForm");
const itemPhotoInput = document.getElementById("itemPhotoInput");
const attachPhotoBtn = document.getElementById("attachPhotoBtn");
const draftPhotoPreview = document.getElementById("draftPhotoPreview");
const draftPhotoImg = document.getElementById("draftPhotoImg");
const clearDraftPhotoBtn = document.getElementById("clearDraftPhotoBtn");
const photoLightbox = document.getElementById("photoLightbox");
const lightboxImg = document.getElementById("lightboxImg");
const lightboxTitle = document.getElementById("lightboxTitle");
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
let draftPhotoBase64 = "";

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

function refreshAccessUi() {
  const canToggle = canToggleChecklist();
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

function renderItems(snap) {
  const grouped = new Map();
  let totalCount = 0;
  let pendingCount = 0;

  snap.forEach((docSnap) => {
    const item = { id: docSnap.id, ...docSnap.data() };
    totalCount += 1;
    if (!item.isChecked) pendingCount += 1;
    const cat = item.category || "Supplies";
    if (!grouped.has(cat)) grouped.set(cat, []);
    grouped.get(cat).push(item);
  });

  if (currentBoardData) {
    const modeText = currentBoardData.isPublicEditable === false ? "View Only" : "View & Mark Only";
    boardMeta.textContent = `${pendingCount} pending • ${totalCount} items • ${modeText}`;
  }

  const canToggle = canToggleChecklist();
  const canManage = canManageStock();
  categoriesContainer.innerHTML = "";
  const sortedCats = Array.from(grouped.keys()).sort((a, b) => a.localeCompare(b));
  if (sortedCats.length === 0) {
    categoriesContainer.innerHTML = '<p class="subtitle">No items on this checklist yet.</p>';
    return;
  }

  sortedCats.forEach((cat) => {
    const card = document.createElement("div");
    card.className = "cat-card";
    const h3 = document.createElement("h3");
    h3.className = "cat-title";
    h3.textContent = cat;
    card.appendChild(h3);

    const catItems = grouped.get(cat);
    const photoItems = catItems.filter((it) => it.imageBase64 && it.imageBase64.trim().length > 0);
    const textItems = catItems.filter((it) => !it.imageBase64 || it.imageBase64.trim().length === 0);

    if (photoItems.length > 0) {
      const grid = document.createElement("div");
      grid.className = "photo-notes-grid";
      photoItems.forEach((item) => {
        const pCard = document.createElement("div");
        pCard.className = `photo-note-card ${item.isChecked ? "checked" : ""}`;

        const thumbWrap = document.createElement("div");
        thumbWrap.className = "photo-note-thumb-wrap";
        const img = document.createElement("img");
        const rawB64 = item.imageBase64.trim();
        img.src = rawB64.startsWith("data:") ? rawB64 : `data:image/jpeg;base64,${rawB64}`;
        img.alt = item.title || "Photo Note";
        img.style.cursor = "pointer";
        img.addEventListener("click", () => {
          if (photoLightbox && lightboxImg && lightboxTitle) {
            lightboxImg.src = img.src;
            lightboxTitle.textContent = item.title || "Photo Note";
            photoLightbox.classList.remove("hidden");
          }
        });
        thumbWrap.appendChild(img);

        const circleBtn = document.createElement("button");
        circleBtn.type = "button";
        circleBtn.className = "photo-select-circle";
        circleBtn.title = item.isChecked ? "Unselect photo note" : "Select photo note";
        circleBtn.textContent = item.isChecked ? "✓" : "";
        circleBtn.disabled = !canToggle;
        circleBtn.addEventListener("click", async () => {
          if (!canToggleChecklist()) return;
          try {
            await updateDoc(doc(db, "shared_checklists", activeCode, "items", item.id), {
              isChecked: !item.isChecked,
              updatedAt: Date.now()
            });
          } catch (err) {
            alert("Could not update photo note: " + (err?.message || "Permission denied"));
          }
        });
        thumbWrap.appendChild(circleBtn);
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
      card.appendChild(grid);
    }

    textItems.forEach((item) => {
      const row = document.createElement("div");
      row.className = `item-row ${item.isChecked ? "checked" : ""}`;

      const cb = document.createElement("input");
      cb.type = "checkbox";
      cb.checked = !!item.isChecked;
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
      span.textContent = item.title;
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

      card.appendChild(row);
    });
    categoriesContainer.appendChild(card);
  });
}

function openBoard(rawCode) {
  const code = (rawCode || "").trim().toUpperCase();
  if (!code) return;
  if (activeCode === code && unsubBoard && unsubItems) return;

  activeCode = code;
  if (codeInput) codeInput.value = code;

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
        refreshAccessUi();
        return;
      }
      currentBoardData = snap.data();
      boardTitle.textContent = currentBoardData.title || "ShopKeep Checklist";
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

function listenToPublicDefault() {
  if (unsubDefault) unsubDefault();
  const defaultRef = doc(db, "shared_checklists", PUBLIC_DEFAULT_DOC);
  unsubDefault = onSnapshot(
    defaultRef,
    (snap) => {
      if (!snap.exists()) {
        boardSection.classList.add("hidden");
        emptyState.classList.remove("hidden");
        emptyTitle.textContent = "Public Checklist Not Published Yet";
        emptyDesc.textContent =
          "Open your ShopKeep Android app -> Checklists -> tap \"Publish & Share Public Live Link (No Code)\" once. It will appear here automatically!";
        return;
      }
      const data = snap.data();
      const targetCode = (data.activeShareCode || "").trim();
      if (!targetCode) {
        emptyTitle.textContent = "Waiting for Live Board...";
        emptyDesc.textContent = "Tap 'Publish & Share Public Live Link' in the ShopKeep app.";
        return;
      }
      openBoard(targetCode);
    },
    (err) => {
      emptyTitle.textContent = "Connection Issue";
      emptyDesc.textContent = err?.message || "Unable to connect to Firebase Firestore.";
    }
  );
}

openCodeBtn.addEventListener("click", () => {
  const val = codeInput.value.trim();
  if (val) {
    if (unsubDefault) {
      unsubDefault();
      unsubDefault = null;
    }
    openBoard(val);
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
    const file = itemPhotoInput.files && itemPhotoInput.files[0];
    if (!file) return;
    try {
      draftPhotoBase64 = await compressImageToBase64(file);
      if (draftPhotoImg && draftPhotoPreview) {
        draftPhotoImg.src = `data:image/jpeg;base64,${draftPhotoBase64}`;
        draftPhotoPreview.classList.remove("hidden");
      }
    } catch (err) {
      alert(err?.message || "Could not attach photo");
    }
  });
}

if (clearDraftPhotoBtn) {
  clearDraftPhotoBtn.addEventListener("click", () => {
    draftPhotoBase64 = "";
    if (itemPhotoInput) itemPhotoInput.value = "";
    if (draftPhotoPreview) draftPhotoPreview.classList.add("hidden");
  });
}

if (closeLightboxBtn && photoLightbox) {
  closeLightboxBtn.addEventListener("click", () => photoLightbox.classList.add("hidden"));
  photoLightbox.addEventListener("click", (e) => {
    if (e.target === photoLightbox) photoLightbox.classList.add("hidden");
  });
}

addItemForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  if (!canManageStock() || !activeCode) return;
  const rawTitle = document.getElementById("itemTitleInput").value.trim();
  const title = rawTitle || (draftPhotoBase64 ? "Photo Note" : "");
  const quantity = document.getElementById("itemQtyInput").value.trim();
  const category = document.getElementById("itemCatInput").value.trim() || "Supplies";
  if (!title) return;

  const id = crypto.randomUUID ? crypto.randomUUID() : `item_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  const now = Date.now();
  try {
    await setDoc(doc(db, "shared_checklists", activeCode, "items", id), {
      title,
      quantity,
      category,
      notes: "",
      isChecked: false,
      imageBase64: draftPhotoBase64 || "",
      addedByUid: currentUser ? currentUser.uid : "public",
      addedByName: currentUser ? (currentUser.displayName || "Collaborator") : "Public Visitor",
      createdAt: now,
      updatedAt: now
    });
    document.getElementById("itemTitleInput").value = "";
    document.getElementById("itemQtyInput").value = "";
    draftPhotoBase64 = "";
    if (itemPhotoInput) itemPhotoInput.value = "";
    if (draftPhotoPreview) draftPhotoPreview.classList.add("hidden");
  } catch (err) {
    alert("Could not add item: " + (err?.message || "Permission denied"));
  }
});

const params = new URLSearchParams(window.location.search);
const initialCode = params.get("checklist");
if (initialCode) {
  openBoard(initialCode);
} else {
  listenToPublicDefault();
}
