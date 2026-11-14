const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const CHARLIE_UID = "charlie_uid_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validUserPayload(uid, username) {
  const now = new Date();
  return {
    uid,
    username,
    displayName: username,
    bio: "Hola soy " + username,
    accountStatus: "active",
    followersCount: 0,
    followingCount: 0,
    allowMessageRequests: true,
    isPrivateProfile: false,
    showIdentityInComments: false,
    createdAt: now,
    updatedAt: now,
  };
}

function validPostPayload(text = "Confesión anónima") {
  const now = new Date();
  return {
    text,
    mediaType: "none",
    mediaDurationMs: 0,
    reactionsCount: 0,
    likeCount: 0,
    funnyCount: 0,
    surprisedCount: 0,
    sadCount: 0,
    interestedCount: 0,
    loveCount: 0,
    commentsCount: 0,
    status: "active",
    createdAt: now,
    updatedAt: now,
  };
}

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

test("Unauthenticated user cannot read posts, users, or private collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("posts").get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("postAuthors").get());
  await assertFails(unauthDb.collection("conversations").get());
});

test("User profile creation & PII isolation: Bob cannot read Alice's private info or savedPosts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "alice_m"))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("info").set({
      email: "alice@example.com",
      ageConfirmed: true,
      acceptedTermsVersion: "1.0",
      themeMode: "dark",
      updatedAt: new Date(),
    })
  );

  // Bob can read Alice's public profile (contains no email), but CANNOT read Alice's private info
  await assertSucceeds(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("private").doc("info").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("savedPosts").get()
  );
});

test("Shadow update & identity spoofing on users are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "alice_m"))
  );

  // Ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isVerified: true,
      updatedAt: new Date(),
    })
  );

  // Bob cannot overwrite Alice's profile
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      bio: "hacked",
      updatedAt: new Date(),
    })
  );
});

test("Anonymous post creation enforces split postAuthors and rejects authorUid on public /posts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Direct post creation WITH authorUid must fail
  await assertFails(
    aliceDb.collection("posts").doc("post_bad_1").set({
      ...validPostPayload("Bad post"),
      authorUid: ALICE_UID,
    })
  );

  // Post creation WITHOUT postAuthors batch must fail
  await assertFails(
    aliceDb.collection("posts").doc("post_no_author_1").set(validPostPayload("Orphan post"))
  );

  // Atomic batch creating both posts/{id} and postAuthors/{id} succeeds
  const batch = aliceDb.batch();
  batch.set(aliceDb.collection("posts").doc("post_1"), validPostPayload("Mi secreto"));
  batch.set(aliceDb.collection("postAuthors").doc("post_1"), {
    postId: "post_1",
    authorUid: ALICE_UID,
    createdAt: new Date(),
  });
  await assertSucceeds(batch.commit());

  // Bob can read /posts/post_1 and list /posts ordered by createdAt; sees NO authorUid
  const publicSnap = await assertSucceeds(bobDb.collection("posts").doc("post_1").get());
  assert.equal(publicSnap.data().authorUid, undefined);
  await assertSucceeds(bobDb.collection("posts").orderBy("createdAt", "desc").limit(15).get());
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").collection("comments").orderBy("createdAt", "asc").get()
  );

  // Bob CANNOT read postAuthors/post_1 or query postAuthors for Alice
  await assertFails(bobDb.collection("postAuthors").doc("post_1").get());
  await assertFails(
    bobDb.collection("postAuthors").where("authorUid", "==", ALICE_UID).get()
  );

  // Alice CAN query her own postAuthors
  await assertSucceeds(
    aliceDb.collection("postAuthors").where("authorUid", "==", ALICE_UID).get()
  );

  // Bob CANNOT delete Alice's post
  await assertFails(bobDb.collection("posts").doc("post_1").delete());

  // Legacy post without videoChunkCount can still receive reaction updates
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").update({
      reactionsCount: 1,
      loveCount: 1,
      updatedAt: new Date(),
    })
  );

  // Author Alice CAN upload & delete videoChunks for her post; Bob can read/list but CANNOT write or delete
  await assertSucceeds(
    aliceDb.collection("posts").doc("post_1").collection("videoChunks").doc("chunk_0").set({
      postId: "post_1",
      chunkIndex: 0,
      data: "AAAAIGZ0eXBtcDQyAAAAAG1wNDFpc29t",
      createdAt: new Date(),
    })
  );
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").collection("videoChunks").orderBy("chunkIndex", "asc").get()
  );
  await assertFails(
    bobDb.collection("posts").doc("post_1").collection("videoChunks").doc("chunk_1").set({
      postId: "post_1",
      chunkIndex: 1,
      data: "hackedchunk",
      createdAt: new Date(),
    })
  );
  await assertFails(
    bobDb.collection("posts").doc("post_1").collection("videoChunks").doc("chunk_0").delete()
  );
  await assertSucceeds(
    aliceDb.collection("posts").doc("post_1").collection("videoChunks").doc("chunk_0").delete()
  );
});

test("Polls enforce single immutable vote per user", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("polls").doc("poll_1").set({
      postId: "post_1",
      question: "¿Prefieres noche o día?",
      options: ["Noche", "Día"],
      c0: 0,
      c1: 0,
      c2: 0,
      c3: 0,
      totalVotes: 0,
      createdAt: new Date(),
    });
  });

  // Bob votes option 0 atomically
  const voteBatch = bobDb.batch();
  voteBatch.set(bobDb.collection("polls").doc("poll_1").collection("votes").doc(BOB_UID), {
    pollId: "poll_1",
    userId: BOB_UID,
    optionIndex: 0,
    createdAt: new Date(),
  });
  voteBatch.update(bobDb.collection("polls").doc("poll_1"), {
    c0: 1,
    totalVotes: 1,
  });
  await assertSucceeds(voteBatch.commit());

  // Bob CANNOT vote a second time or modify his vote
  await assertFails(
    bobDb.collection("polls").doc("poll_1").collection("votes").doc(BOB_UID).set({
      pollId: "poll_1",
      userId: BOB_UID,
      optionIndex: 1,
      createdAt: new Date(),
    })
  );

  // Alice cannot manipulate counts without voting for that exact index
  const badBatch = aliceDb.batch();
  badBatch.set(aliceDb.collection("polls").doc("poll_1").collection("votes").doc(ALICE_UID), {
    pollId: "poll_1",
    userId: ALICE_UID,
    optionIndex: 1,
    createdAt: new Date(),
  });
  badBatch.update(aliceDb.collection("polls").doc("poll_1"), {
    c1: 10,
    totalVotes: 11,
  });
  await assertFails(badBatch.commit());
});

test("Private conversations isolate access to participants and enforce blocks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Participants can check non-existent block/conversation docs before creating chat
  await assertSucceeds(bobDb.collection("blocks").doc(`${ALICE_UID}_${BOB_UID}`).get());
  await assertSucceeds(bobDb.collection("blocks").doc(`${BOB_UID}_${ALICE_UID}`).get());
  await assertSucceeds(bobDb.collection("conversations").doc(`${ALICE_UID}_${BOB_UID}`).get());
  await assertFails(charlieDb.collection("blocks").doc(`${ALICE_UID}_${BOB_UID}`).get());
  await assertFails(charlieDb.collection("conversations").doc(`${ALICE_UID}_${BOB_UID}`).get());

  const convId = `${ALICE_UID}_${BOB_UID}`;
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set({
      participantUids: [ALICE_UID, BOB_UID],
      lastMessageText: "Hola",
      lastMessageSenderUid: ALICE_UID,
      isRequest: false,
      requestAccepted: true,
      unreadMap: { [BOB_UID]: 1 },
      hiddenForUids: [],
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      senderUid: ALICE_UID,
      text: "Hola Bob",
      createdAt: new Date(),
    })
  );

  // Bob can read conversation & messages; Charlie CANNOT
  await assertSucceeds(bobDb.collection("conversations").doc(convId).get());
  await assertSucceeds(
    bobDb
      .collection("conversations")
      .doc(convId)
      .collection("messages")
      .where("participantUids", "array-contains", BOB_UID)
      .get()
  );
  await assertFails(charlieDb.collection("conversations").doc(convId).get());
  await assertFails(
    charlieDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").get()
  );
  await assertFails(
    charlieDb.collection("conversations").doc(convId).collection("messages").get()
  );

  // Bob blocks Alice -> Alice cannot send new messages to conversation
  await assertSucceeds(
    bobDb.collection("blocks").doc(`${BOB_UID}_${ALICE_UID}`).set({
      blockerUid: BOB_UID,
      blockedUid: ALICE_UID,
      createdAt: new Date(),
    })
  );
  await assertFails(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_2").set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      senderUid: ALICE_UID,
      text: "¿Sigues ahí?",
      createdAt: new Date(),
    })
  );
});
