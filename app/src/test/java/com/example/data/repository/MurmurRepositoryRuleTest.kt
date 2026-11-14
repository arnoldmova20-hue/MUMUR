package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.domain.model.ReactionType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MurmurRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun uniqueSuffix() = UUID.randomUUID().toString().take(6).lowercase()

    @Test
    fun onboarding_and_anonymity_and_crossUserIsolation() = runBlocking {
        val suffix = uniqueSuffix()
        val aliceUid = signInTestUser("alice_$suffix@test.com")
        val aliceUserRepo = AuthUserRepository(firestore, auth)
        val alicePostRepo = PostRepository(firestore, auth)

        val aliceUsername = "alice_$suffix"
        assertTrue(withTimeout(TIMEOUT_MS) { aliceUserRepo.isUsernameAvailable(aliceUsername) })
        withTimeout(TIMEOUT_MS) {
            aliceUserRepo.completeOnboarding(
                username = aliceUsername,
                displayName = "Alice M",
                bio = "Hola",
                birthYear = 2006
            )
        }
        assertFalse(withTimeout(TIMEOUT_MS) { aliceUserRepo.isUsernameAvailable(aliceUsername) })

        val postId = withTimeout(TIMEOUT_MS) {
            alicePostRepo.createAnonymousPost(
                text = "Secreto anónimo $suffix",
                pollQuestion = "¿Café o té?",
                pollOptions = listOf("Café", "Té")
            )
        }

        // Verify raw public /posts/{postId} has NO authorUid or userId
        val rawPost = withTimeout(TIMEOUT_MS) {
            firestore.collection("posts").document(postId).get().await()
        }
        assertTrue(rawPost.exists())
        assertNull(rawPost.get("authorUid"))
        assertNull(rawPost.get("userId"))
        val pollId = rawPost.getString("pollId")!!

        // Switch to Bob
        val bobUid = signInTestUser("bob_$suffix@test.com")
        val bobUserRepo = AuthUserRepository(firestore, auth)
        val bobPostRepo = PostRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) {
            bobUserRepo.completeOnboarding(
                username = "bob_$suffix",
                displayName = "Bob",
                bio = "Bio",
                birthYear = 2005
            )
        }

        // Bob CANNOT read Alice's private info
        val privateReadError = runCatching {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("private").document("info").get().await()
            }
        }.exceptionOrNull()
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            privateReadError?.unwrapFirestoreException()?.code
        )

        // Bob CANNOT read postAuthors to unmask Alice
        val authorUnmaskError = runCatching {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("postAuthors").document(postId).get().await()
            }
        }.exceptionOrNull()
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            authorUnmaskError?.unwrapFirestoreException()?.code
        )

        // Bob CANNOT delete Alice's post
        val deleteError = runCatching {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("posts").document(postId).delete().await()
            }
        }.exceptionOrNull()
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            deleteError?.unwrapFirestoreException()?.code
        )

        // Bob reacts, votes once, comments, saves, and follows Alice
        withTimeout(TIMEOUT_MS) { bobPostRepo.toggleReaction(postId, ReactionType.LOVE) }
        withTimeout(TIMEOUT_MS) { bobPostRepo.votePoll(pollId, 1) }

        // Second poll vote must fail
        val secondVoteError = runCatching {
            withTimeout(TIMEOUT_MS) { bobPostRepo.votePoll(pollId, 0) }
        }.exceptionOrNull()
        assertTrue(secondVoteError != null)

        val commentId = withTimeout(TIMEOUT_MS) {
            bobPostRepo.addComment(postId, "Gran confesión", null, false, "bob_$suffix")
        }
        assertTrue(commentId.isNotBlank())
        assertTrue(withTimeout(TIMEOUT_MS) { bobPostRepo.toggleSavePost(postId) })

        withTimeout(TIMEOUT_MS) { bobUserRepo.followUser(aliceUid, "bob_$suffix") }
        assertTrue(withTimeout(TIMEOUT_MS) { bobUserRepo.isFollowing(aliceUid) })

        // Alice creates an anonymous video post with video chunks; Bob can read chunks & cannot forge chunks
        signInTestUser("alice_$suffix@test.com")
        val videoPostId = withTimeout(TIMEOUT_MS) {
            alicePostRepo.createAnonymousPost(
                text = "Vídeo anónimo $suffix",
                mediaType = "video",
                mediaData = "data:image/jpeg;base64,thumb",
                mediaDurationMs = 4500L,
                videoChunks = listOf("dmlkZW9fY2h1bmtfMA==", "dmlkZW9fY2h1bmtfMQ==")
            )
        }
        signInTestUser("bob_$suffix@test.com")
        val chunkSnap = withTimeout(TIMEOUT_MS) {
            firestore.collection("posts").document(videoPostId)
                .collection("videoChunks").orderBy("chunkIndex").get().await()
        }
        assertEquals(2, chunkSnap.size())

        val spoofChunkError = runCatching {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("posts").document(videoPostId)
                    .collection("videoChunks").document("chunk_2")
                    .set(
                        mapOf(
                            "postId" to videoPostId,
                            "chunkIndex" to 2L,
                            "data" to "spoof",
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                    ).await()
            }
        }.exceptionOrNull()
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            spoofChunkError?.unwrapFirestoreException()?.code
        )
    }

    @Test
    fun messages_and_blocks_and_unauthenticatedRejection() = runBlocking {
        val suffix = uniqueSuffix()
        val aliceUid = signInTestUser("m_alice_$suffix@test.com")
        val aliceUserRepo = AuthUserRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) {
            aliceUserRepo.completeOnboarding("ma_$suffix", "Alice", "", 2006)
        }

        val bobUid = signInTestUser("m_bob_$suffix@test.com")
        val bobUserRepo = AuthUserRepository(firestore, auth)
        val bobMsgRepo = MessageRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) {
            bobUserRepo.completeOnboarding("mb_$suffix", "Bob", "", 2005)
        }

        val convId = withTimeout(TIMEOUT_MS) {
            bobMsgRepo.sendMessage(aliceUid, "Hola Alice", null, "mb_$suffix")
        }

        // Charlie cannot read Alice-Bob conversation
        val charlieUid = signInTestUser("m_charlie_$suffix@test.com")
        val charlieConvError = runCatching {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("conversations").document(convId).get().await()
            }
        }.exceptionOrNull()
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            charlieConvError?.unwrapFirestoreException()?.code
        )

        // Alice blocks Bob -> Bob cannot send another message
        signInTestUser("m_alice_$suffix@test.com")
        withTimeout(TIMEOUT_MS) { aliceUserRepo.blockUser(bobUid) }

        signInTestUser("m_bob_$suffix@test.com")
        val blockedMsgError = runCatching {
            withTimeout(TIMEOUT_MS) {
                bobMsgRepo.sendMessage(aliceUid, "¿Sigues?", null, "mb_$suffix")
            }
        }.exceptionOrNull()
        assertTrue(blockedMsgError != null)

        // Unauthenticated query rejected with PERMISSION_DENIED
        auth.signOut()
        val unauthMsgError = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                MessageRepository(firestore, auth).observeConversations(charlieUid).first()
            }
        }.exceptionOrNull()
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            unauthMsgError?.unwrapFirestoreException()?.code
        )
    }

    private companion object {
        const val TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
