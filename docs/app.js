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
const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app, DATABASE_ID);
const provider = new GoogleAuthProvider();

const codeInput = document.getElementById("codeInput");
const openCodeBtn = document.getElementById("openCodeBtn");
const boardSection = document.getElementById("boardSection");
const emptyState = document.getElementById("emptyState");
const boardTitle = document.getElementById("boardTitle");
const boardMeta = document.getElementById("boardMeta");
const categoriesContainer = document.getElementById("categoriesContainer");
const addItemForm = document.getElementById("addItemForm");
const signInBtn = document.getElementById("signInBtn");
const signOutBtn = document.getElementById("signOutBtn");
const userLabel = document.getElementById("userLabel");

let currentUser = null;
let unsubBoard = null;
let unsubItems = null;
let activeCode = null;

onAuthStateChanged(auth, (user) => {
  currentUser = user;
  if (user) {
    userLabel.textContent = user.displayName || user.email;
    signInBtn.classList.add("hidden");
    signOutBtn.classList.remove("hidden");
    addItemForm.classList.remove("hidden");
  } else {
    userLabel.textContent = "View Mode (Sign in to Edit)";
    signInBtn.classList.remove("hidden");
    signOutBtn.classList.add("hidden");
    addItemForm.classList.add("hidden");
  }
});

signInBtn.addEventListener("click", async () => {
  try {
    await signInWithPopup(auth, provider);
  } catch (err) {
    if (err && err.code === "auth/unauthorized-domain") {
      alert(
        "This domain (" + window.location.hostname + ") needs to be added under Firebase Console -> Authentication -> Settings -> Authorized domains to allow editing. Live viewing works without signing in."
      );
    } else {
      alert("Sign-in error: " + (err?.message || "Please try again."));
    }
  }
});
signOutBtn.addEventListener("click", () => signOut(auth));

function openBoard(rawCode) {
  const code = (rawCode || "").trim().toUpperCase();
  if (!code) return;
  activeCode = code;
  codeInput.value = code;

  if (unsubBoard) unsubBoard();
  if (unsubItems) unsubItems();

  boardSection.classList.remove("hidden");
  emptyState.classList.add("hidden");

  const boardRef = doc(db, "shared_checklists", code);
  unsubBoard = onSnapshot(
    boardRef,
    (snap) => {
      if (!snap.exists()) {
        boardTitle.textContent = `Board "${code}" not found`;
        boardMeta.textContent = "Verify the share code from your ShopKeep app.";
        return;
      }
      const data = snap.data();
      boardTitle.textContent = data.title || "ShopKeep Checklist";
      boardMeta.textContent = `Code: ${data.shareCode} • Owner: ${data.ownerName || "Shop Owner"}`;
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
  unsubItems = onSnapshot(itemsQuery, (snap) => {
    const grouped = new Map();
    snap.forEach((docSnap) => {
      const item = { id: docSnap.id, ...docSnap.data() };
      const cat = item.category || "Supplies";
      if (!grouped.has(cat)) grouped.set(cat, []);
      grouped.get(cat).push(item);
    });

    categoriesContainer.innerHTML = "";
    const sortedCats = Array.from(grouped.keys()).sort((a, b) => a.localeCompare(b));
    if (sortedCats.length === 0) {
      categoriesContainer.innerHTML = "<p class=\"subtitle\">No items on this board yet.</p>";
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
        const row = document.createElement("label");
        row.className = `item-row ${item.isChecked ? "checked" : ""}`;
        const cb = document.createElement("input");
        cb.type = "checkbox";
        cb.checked = !!item.isChecked;
        cb.disabled = !currentUser;
        cb.addEventListener("change", async () => {
          if (!currentUser) return;
          await updateDoc(doc(db, "shared_checklists", code, "items", item.id), {
            isChecked: cb.checked,
            updatedAt: Date.now()
          });
        });
        const span = document.createElement("span");
        span.textContent = item.title;
        row.appendChild(cb);
        row.appendChild(span);
        if (item.quantity) {
          const pill = document.createElement("span");
          pill.className = "qty-pill";
          pill.textContent = item.quantity;
          row.appendChild(pill);
        }
        card.appendChild(row);
      });
      categoriesContainer.appendChild(card);
    });
  });
}

openCodeBtn.addEventListener("click", () => openBoard(codeInput.value));

addItemForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  if (!currentUser || !activeCode) return;
  const title = document.getElementById("itemTitleInput").value.trim();
  const quantity = document.getElementById("itemQtyInput").value.trim();
  const category = document.getElementById("itemCatInput").value.trim() || "Supplies";
  if (!title) return;

  const id = crypto.randomUUID();
  const now = Date.now();
  await setDoc(doc(db, "shared_checklists", activeCode, "items", id), {
    title,
    quantity,
    category,
    notes: "",
    isChecked: false,
    addedByUid: currentUser.uid,
    addedByName: currentUser.displayName || "Web Collaborator",
    createdAt: now,
    updatedAt: now
  });
  document.getElementById("itemTitleInput").value = "";
  document.getElementById("itemQtyInput").value = "";
});

const params = new URLSearchParams(window.location.search);
const initialCode = params.get("checklist");
if (initialCode) {
  openBoard(initialCode);
}
