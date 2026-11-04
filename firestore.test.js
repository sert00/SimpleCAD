const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

function validProjectPayload(uid, now = new Date()) {
  return {
    userId: uid,
    name: "Scatola Derivazione IP65",
    templateType: "JUNCTION_BOX_TPU",
    description: "120x80x50mm con coperchio e guarnizione TPU",
    componentsJson: "[{\"id\":\"base\",\"type\":\"BOX\"}]",
    explodeFactor: 0.35,
    multiObject3mf: true,
    partCount: 3,
    createdAt: now,
    updatedAt: now,
  };
}

function validPrinterPayload(uid, now = new Date()) {
  return {
    userId: uid,
    name: "Voron 2.4 Klipper",
    protocol: "MOONRAKER",
    endpointUrl: "http://192.168.1.50:7125",
    apiKey: "my-secret-key",
    bedWidthMm: 350,
    bedDepthMm: 350,
    nozzleMm: 0.4,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user: cannot read or create cad_projects", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("cad_projects").get());
  await assertFails(
    unauthDb.collection("cad_projects").doc("proj_1").set(validProjectPayload(ALICE_UID))
  );
});

test("Authenticated user: can create and read own cad_project", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("cad_projects").doc("proj_alice").set(validProjectPayload(ALICE_UID))
  );
  await assertSucceeds(aliceDb.collection("cad_projects").doc("proj_alice").get());
});

test("Authenticated user: cannot read or delete another user's cad_project", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("cad_projects")
      .doc("bob_proj")
      .set(validProjectPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("cad_projects").doc("bob_proj").get());
  await assertFails(aliceDb.collection("cad_projects").doc("bob_proj").delete());
});

test("Authenticated user: can list own cad_projects with userId filter, fails without filter", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("cad_projects")
      .doc("alice_proj")
      .set(validProjectPayload(ALICE_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("cad_projects").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(aliceDb.collection("cad_projects").get());
});

test("Shadow update & identity spoofing rejected on cad_projects", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  // Spoofing Bob's UID
  await assertFails(
    aliceDb.collection("cad_projects").doc("spoof_proj").set(validProjectPayload(BOB_UID))
  );
  // Ghost field injection
  await assertFails(
    aliceDb.collection("cad_projects").doc("ghost_proj").set({
      ...validProjectPayload(ALICE_UID),
      isAdminHack: true,
    })
  );
});

test("Authenticated user: can create and query own printer_profiles", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("printer_profiles").doc("printer_1").set(validPrinterPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("printer_profiles").where("userId", "==", ALICE_UID).get()
  );
});
