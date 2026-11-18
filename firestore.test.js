const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_shop_owner";
const BOB_UID = "bob_staff_member";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write user tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t1").set({
      title: "Unauthorized",
      isCompleted: false,
      isDeleted: false,
      createdAt: 1000,
      userId: ALICE_UID,
    })
  );
});

test("Authenticated user cannot read or write another user's checklist items or photos", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(ALICE_UID).collection("checklist_items").doc("c1").set({
      title: "Milk Cartons",
      category: "Dairy & Cold",
      isChecked: false,
      isDeleted: false,
      createdAt: 1000,
      userId: ALICE_UID,
    });
    await db.collection("users").doc(ALICE_UID).collection("photos").doc("p1").set({
      title: "Counter Rack",
      category: "Display Shelves",
      createdAt: 1000,
      userId: ALICE_UID,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("checklist_items").doc("c1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("photos").doc("p1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("checklist_items").doc("c2").set({
      title: "Steal",
      category: "Dairy & Cold",
      isChecked: false,
      isDeleted: false,
      createdAt: 1000,
      userId: ALICE_UID,
    })
  );
});

test("Owner can create and read their own tasks, checklist items, and categories", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t1").set({
      title: "Restock Rice Bags",
      description: "25kg bags from supplier",
      category: "Restock",
      priority: "URGENT_IMPORTANT",
      isCompleted: false,
      isDeleted: false,
      dueDateMillis: null,
      reminderTimeMillis: null,
      createdAt: 2000,
      updatedAt: 2000,
      userId: ALICE_UID,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("tasks").get());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("checklist_categories").doc("cat1").set({
      name: "Beverages & Drinks",
      isHidden: false,
      sortOrder: 0,
      createdAt: 2000,
      userId: ALICE_UID,
    })
  );
});

test("Shared checklist allows reading by shareId, authenticated member collaboration, and denies unauthenticated writes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertSucceeds(
    aliceDb.collection("shared_checklists").doc("SHOP1234").set({
      shareCode: "SHOP1234",
      title: "Morning Restock",
      ownerId: ALICE_UID,
      ownerName: "Alice",
      memberIds: [ALICE_UID],
      categories: ["Beverages", "Snacks"],
      createdAt: 3000,
      updatedAt: 3000,
    })
  );

  await assertSucceeds(unauthDb.collection("shared_checklists").doc("SHOP1234").get());
  await assertFails(unauthDb.collection("shared_checklists").get());
  await assertFails(
    unauthDb.collection("shared_checklists").doc("SHOP1234").collection("items").doc("i1").set({
      title: "Hack",
      category: "Snacks",
      isChecked: false,
      addedByUid: "anon",
      createdAt: 3000,
    })
  );

  await assertSucceeds(
    bobDb.collection("shared_checklists").doc("SHOP1234").update({
      memberIds: [ALICE_UID, BOB_UID],
      updatedAt: 3005,
    })
  );
  await assertSucceeds(
    bobDb.collection("shared_checklists").doc("SHOP1234").collection("items").doc("i1").set({
      title: "Sparkling Water",
      quantity: "2 crates",
      category: "Beverages",
      notes: "",
      isChecked: false,
      addedByUid: BOB_UID,
      addedByName: "Bob",
      createdAt: 3010,
      updatedAt: 3010,
    })
  );

  await assertFails(bobDb.collection("shared_checklists").doc("SHOP1234").delete());
  await assertSucceeds(aliceDb.collection("shared_checklists").doc("SHOP1234").delete());
});
