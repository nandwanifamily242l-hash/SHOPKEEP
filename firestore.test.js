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
});

test("Public editable shared board allows open link access and toggling without login, while restricted board denies unauth writes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertSucceeds(
    aliceDb.collection("shared_checklists").doc("SHOP-PUB1").set({
      shareCode: "SHOP-PUB1",
      title: "Public Shop Board",
      ownerId: ALICE_UID,
      ownerName: "Alice",
      memberIds: [ALICE_UID],
      categories: ["Beverages", "Snacks"],
      isPublicEditable: true,
      createdAt: 3000,
      updatedAt: 3000,
    })
  );

  // Unauthenticated visitor can read and edit when isPublicEditable is true
  await assertSucceeds(unauthDb.collection("shared_checklists").doc("SHOP-PUB1").get());
  await assertSucceeds(
    unauthDb.collection("shared_checklists").doc("SHOP-PUB1").collection("items").doc("i1").set({
      title: "Orange Juice",
      quantity: "3 packs",
      category: "Beverages",
      notes: "",
      isChecked: false,
      addedByUid: "public",
      addedByName: "Public Link",
      createdAt: 3010,
      updatedAt: 3010,
    })
  );

  // Unauthenticated visitor still CANNOT list all boards or delete board
  await assertFails(unauthDb.collection("shared_checklists").get());
  await assertFails(unauthDb.collection("shared_checklists").doc("SHOP-PUB1").delete());

  // Owner puts restrictions on later
  await assertSucceeds(
    aliceDb.collection("shared_checklists").doc("SHOP-PUB1").update({
      isPublicEditable: false,
      updatedAt: 3020,
    })
  );

  // Now unauthenticated writes are rejected, while reads still work
  await assertFails(
    unauthDb.collection("shared_checklists").doc("SHOP-PUB1").collection("items").doc("i2").set({
      title: "Blocked Item",
      quantity: "1",
      category: "Snacks",
      notes: "",
      isChecked: false,
      addedByUid: "public",
      createdAt: 3030,
      updatedAt: 3030,
    })
  );
  await assertSucceeds(unauthDb.collection("shared_checklists").doc("SHOP-PUB1").collection("items").get());
});

test("PUBLIC_DEFAULT pointer resolves activeShareCode without auth and blocks non-owner updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertSucceeds(
    aliceDb.collection("shared_checklists").doc("PUBLIC_DEFAULT").set({
      shareCode: "PUBLIC_DEFAULT",
      activeShareCode: "SHOP-PUB1",
      title: "Shop Stock Checklist",
      ownerId: ALICE_UID,
      ownerName: "Alice",
      memberIds: [ALICE_UID],
      isPublicEditable: true,
      createdAt: 4000,
      updatedAt: 4000,
    })
  );

  // Public visitor reads PUBLIC_DEFAULT directly with zero code
  await assertSucceeds(unauthDb.collection("shared_checklists").doc("PUBLIC_DEFAULT").get());

  // Owner updates activeShareCode
  await assertSucceeds(
    aliceDb.collection("shared_checklists").doc("PUBLIC_DEFAULT").update({
      activeShareCode: "SHOP-NEW2",
      updatedAt: 4010,
    })
  );

  // Non-owner cannot hijack PUBLIC_DEFAULT title or activeShareCode
  await assertFails(
    bobDb.collection("shared_checklists").doc("PUBLIC_DEFAULT").update({
      activeShareCode: "HIJACKED",
      updatedAt: 4020,
    })
  );
});
