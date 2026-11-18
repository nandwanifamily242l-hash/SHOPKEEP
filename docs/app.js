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

function canEditCurrentBoard() {
  if (currentUser) return true;
  if (!currentBoardData) return true;
  return currentBoardData.isPublicEditable !== false;
}

function refreshAccessUi() {
  const editable = canEditCurrentBoard();
  const restricted = currentBoardData && currentBoardData.isPublicEditable === false;

  if (currentUser) {
    accessBadge.textContent = currentUser.displayName || currentUser.email || "Signed In";
    accessBadge.classList.remove("restricted");
    signInBtn.classList.add("hidden");
    signOutBtn.classList.remove("hidden");
  } else if (restricted) {
    accessBadge.textContent = "View Only (Restricted)";
    accessBadge.classList.add("restricted");
    signInBtn.classList.remove("hidden");
    signOutBtn.classList.add("hidden");
  } else {
    accessBadge.textContent = "Public Live Access (Open)";
    accessBadge.classList.remove("restricted");
    signInBtn.classList.add("hidden");
    signOutBtn.classList.add("hidden");
  }

  if (editable && activeCode) {
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
    const modeText = currentBoardData.isPublicEditable === false ? "Restricted Edit" : "Public Live Mode";
    boardMeta.textContent = `${pendingCount} pending • ${totalCount} items • ${modeText}`;
  }

  const editable = canEditCurrentBoard();
  categoriesContainer.innerHTML = "";
  const sortedCats = Array.from(grouped.keys()).sort((a, b) => a.localeCompare(b));
  if (sortedCats.length === 0) {
    categoriesContainer.innerHTML = '<p class="subtitle">No items on this checklist yet. Add one above!</p>';
    return;
  }

  sortedCats.forEach((cat) => {
    const card = document.createElement("div");
    card.className = "cat-card";
    const h3 = document.createElement("h3");
    h3.className = "cat-title";
    h3.textContent = cat;
    card.appendChild(h3);

    grouped.get(cat).forEach((item) => {
      const row = document.createElement("div");
      row.className = `item-row ${item.isChecked ? "checked" : ""}`;

      const cb = document.createElement("input");
      cb.type = "checkbox";
      cb.checked = !!item.isChecked;
      cb.disabled = !editable;
      cb.addEventListener("change", async () => {
        if (!canEditCurrentBoard()) return;
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

      if (editable) {
        const delBtn = document.createElement("button");
        delBtn.type = "button";
        delBtn.className = "btn-icon";
        delBtn.title = "Remove item";
        delBtn.textContent = "×";
        delBtn.addEventListener("click", async () => {
          if (!canEditCurrentBoard()) return;
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

addItemForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  if (!canEditCurrentBoard() || !activeCode) return;
  const title = document.getElementById("itemTitleInput").value.trim();
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
      addedByUid: currentUser ? currentUser.uid : "public",
      addedByName: currentUser ? (currentUser.displayName || "Collaborator") : "Public Visitor",
      createdAt: now,
      updatedAt: now
    });
    document.getElementById("itemTitleInput").value = "";
    document.getElementById("itemQtyInput").value = "";
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
