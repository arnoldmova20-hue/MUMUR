package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.util.MediaProcessor
import com.example.core.util.OperationType
import com.example.core.util.handleFirestoreError
import com.example.domain.model.MediaType
import com.example.domain.model.AnonymousPost
import com.example.domain.model.Comment
import com.example.domain.model.FeedPostBundle
import com.example.domain.model.Poll
import com.example.domain.model.PollVote
import com.example.domain.model.PostAuthorRecord
import com.example.domain.model.PostReaction
import com.example.domain.model.ReactionType
import com.example.domain.model.SavedPost
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

class PostRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Debes iniciar sesión con Google para continuar.")

    fun observeFeedPosts(limit: Long = 20L): Flow<List<AnonymousPost>> = flow {
        requireUserId()
        val path = "posts"
        emitAll(
            db.collection(path)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AnonymousPost::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { it.status == "active" }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser != null) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun loadPostsPage(
        limit: Long = 15L,
        startAfterCreatedAt: Timestamp? = null
    ): List<AnonymousPost> {
        requireUserId()
        val path = "posts"
        return try {
            var query = db.collection(path)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
            if (startAfterCreatedAt != null) {
                query = query.startAfter(startAfterCreatedAt)
            }
            query.get().await()
                .toObjects(
                    AnonymousPost::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                .filter { it.status == "active" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun enrichPosts(posts: List<AnonymousPost>): List<FeedPostBundle> {
        val uid = requireUserId()
        val savedIds = getSavedPostIdSet(uid)
        return posts.map { post ->
            val myReaction = runCatching {
                val snap = db.collection("posts").document(post.id)
                    .collection("reactions").document(uid).get().await()
                if (snap.exists()) ReactionType.fromValue(snap.getString("reactionType")) else null
            }.getOrNull()

            val poll = if (!post.pollId.isNullOrBlank()) {
                runCatching { getPoll(post.pollId) }.getOrNull()
            } else null

            val myVote = if (poll != null) {
                runCatching {
                    val voteSnap = db.collection("polls").document(poll.id)
                        .collection("votes").document(uid).get().await()
                    if (voteSnap.exists()) (voteSnap.getLong("optionIndex"))?.toInt() else null
                }.getOrNull()
            } else null

            FeedPostBundle(
                post = post,
                myReaction = myReaction,
                isSaved = post.id in savedIds,
                poll = poll,
                myPollVoteIndex = myVote
            )
        }
    }

    /**
     * Crea una publicación SIEMPRE anónima mediante una escritura atómica:
     * 1) /posts/{postId} SIN authorUid ni ningún metadato del autor.
     * 2) /postAuthors/{postId} privado legible únicamente por el autor.
     * 3) /polls/{pollId} si se incluye encuesta.
     */
    suspend fun createAnonymousPost(
        text: String,
        mediaType: String = MediaType.NONE.value,
        mediaData: String? = null,
        mediaDurationMs: Long = 0L,
        videoChunks: List<String> = emptyList(),
        pollQuestion: String? = null,
        pollOptions: List<String> = emptyList(),
        pollExpiresAt: Timestamp? = null
    ): String {
        val uid = requireUserId()
        val postId = UUID.randomUUID().toString()
        val postRef = db.collection("posts").document(postId)
        val authorRef = db.collection("postAuthors").document(postId)

        var pollId: String? = null
        val batch = db.batch()

        if (!pollQuestion.isNullOrBlank() && pollOptions.size in 2..4) {
            pollId = UUID.randomUUID().toString()
            val pollRef = db.collection("polls").document(pollId)
            val pollPayload = mutableMapOf<String, Any>(
                "postId" to postId,
                "question" to pollQuestion.trim().take(200),
                "options" to pollOptions.map { it.trim().take(80) },
                "c0" to 0L,
                "c1" to 0L,
                "c2" to 0L,
                "c3" to 0L,
                "totalVotes" to 0L,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (pollExpiresAt != null) {
                pollPayload["expiresAt"] = pollExpiresAt
            }
            batch.set(pollRef, pollPayload)
        }

        val postPayload = mutableMapOf<String, Any>(
            "text" to text.trim().take(1000),
            "mediaType" to mediaType,
            "mediaDurationMs" to mediaDurationMs,
            "videoChunkCount" to videoChunks.size.toLong(),
            "reactionsCount" to 0L,
            "likeCount" to 0L,
            "funnyCount" to 0L,
            "surprisedCount" to 0L,
            "sadCount" to 0L,
            "interestedCount" to 0L,
            "loveCount" to 0L,
            "commentsCount" to 0L,
            "status" to "active",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!mediaData.isNullOrBlank()) {
            postPayload["mediaData"] = mediaData
        }
        if (!pollId.isNullOrBlank()) {
            postPayload["pollId"] = pollId
        }

        val authorPayload = mapOf(
            "postId" to postId,
            "authorUid" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )

        batch.set(postRef, postPayload)
        batch.set(authorRef, authorPayload)

        return try {
            batch.commit().await()
            if (videoChunks.isNotEmpty()) {
                val chunksColl = postRef.collection("videoChunks")
                videoChunks.chunked(4).forEachIndexed { batchIdx, group ->
                    val chunkBatch = db.batch()
                    group.forEachIndexed { localIdx, encodedChunk ->
                        val globalIdx = batchIdx * 4 + localIdx
                        val chunkDoc = chunksColl.document("chunk_$globalIdx")
                        chunkBatch.set(
                            chunkDoc,
                            mapOf(
                                "postId" to postId,
                                "chunkIndex" to globalIdx.toLong(),
                                "data" to encodedChunk,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    chunkBatch.commit().await()
                }
            }
            postId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, postRef.path)
            throw e
        }
    }

    suspend fun loadPostVideoFile(context: Context, postId: String): Result<File> {
        MediaProcessor.getCachedVideoFileIfPresent(context, postId)?.let { cached ->
            return Result.success(cached)
        }
        requireUserId()
        val path = "posts/$postId/videoChunks"
        return try {
            val snap = db.collection("posts").document(postId)
                .collection("videoChunks")
                .orderBy("chunkIndex", Query.Direction.ASCENDING)
                .get()
                .await()
            val chunks = snap.documents.mapNotNull { it.getString("data") }
            require(chunks.isNotEmpty()) {
                "El vídeo no tiene segmentos disponibles para reproducirse."
            }
            val file = MediaProcessor.writeVideoChunksToCacheFile(context, postId, chunks)
            Result.success(file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    /**
     * Consulta exclusivamente las publicaciones propias del usuario autenticado
     * a través de su colección privada /postAuthors. Nunca accesible para terceros.
     */
    suspend fun getMyAnonymousPosts(): List<AnonymousPost> {
        val uid = requireUserId()
        val path = "postAuthors"
        return try {
            val authorRecords = db.collection(path)
                .whereEqualTo("authorUid", uid)
                .get()
                .await()
                .toObjects(PostAuthorRecord::class.java)
            authorRecords.mapNotNull { record ->
                runCatching {
                    val snap = db.collection("posts").document(record.postId).get().await()
                    if (snap.exists()) {
                        snap.toObject(
                            AnonymousPost::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }.getOrNull()
            }.filter { it.status == "active" }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun deleteOwnPost(postId: String) {
        val uid = requireUserId()
        val postRef = db.collection("posts").document(postId)
        val authorRef = db.collection("postAuthors").document(postId)
        try {
            val authorSnap = authorRef.get().await()
            require(authorSnap.exists() && authorSnap.getString("authorUid") == uid) {
                "No tienes permiso para eliminar esta publicación."
            }
            val chunkDocs = runCatching { postRef.collection("videoChunks").get().await().documents }
                .getOrDefault(emptyList())
            val batch = db.batch()
            chunkDocs.forEach { batch.delete(it.reference) }
            batch.delete(postRef)
            batch.delete(authorRef)
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, postRef.path)
            throw e
        }
    }

    suspend fun toggleReaction(postId: String, reactionType: ReactionType) {
        val uid = requireUserId()
        val postRef = db.collection("posts").document(postId)
        val reactionRef = postRef.collection("reactions").document(uid)

        try {
            db.runTransaction { tx ->
                val postSnap = tx.get(postRef)
                if (!postSnap.exists()) throw IllegalStateException("Publicación no encontrada")
                val existingReactionSnap = tx.get(reactionRef)
                val existingType = if (existingReactionSnap.exists()) {
                    ReactionType.fromValue(existingReactionSnap.getString("reactionType"))
                } else null

                val updates = mutableMapOf<String, Any>(
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                fun fieldFor(type: ReactionType) = when (type) {
                    ReactionType.LIKE -> "likeCount"
                    ReactionType.FUNNY -> "funnyCount"
                    ReactionType.SURPRISED -> "surprisedCount"
                    ReactionType.SAD -> "sadCount"
                    ReactionType.INTERESTED -> "interestedCount"
                    ReactionType.LOVE -> "loveCount"
                }

                when {
                    existingType == null -> {
                        // Add new reaction
                        tx.set(
                            reactionRef,
                            mapOf(
                                "postId" to postId,
                                "userId" to uid,
                                "reactionType" to reactionType.value,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                        val oldCount = postSnap.getLong(fieldFor(reactionType)) ?: 0L
                        val oldTotal = postSnap.getLong("reactionsCount") ?: 0L
                        updates[fieldFor(reactionType)] = oldCount + 1L
                        updates["reactionsCount"] = oldTotal + 1L
                    }
                    existingType == reactionType -> {
                        // Remove existing reaction
                        tx.delete(reactionRef)
                        val oldCount = postSnap.getLong(fieldFor(reactionType)) ?: 0L
                        val oldTotal = postSnap.getLong("reactionsCount") ?: 0L
                        updates[fieldFor(reactionType)] = (oldCount - 1L).coerceAtLeast(0L)
                        updates["reactionsCount"] = (oldTotal - 1L).coerceAtLeast(0L)
                    }
                    else -> {
                        // Switch reaction type
                        tx.set(
                            reactionRef,
                            mapOf(
                                "postId" to postId,
                                "userId" to uid,
                                "reactionType" to reactionType.value,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                        val prevCount = postSnap.getLong(fieldFor(existingType)) ?: 0L
                        val nextCount = postSnap.getLong(fieldFor(reactionType)) ?: 0L
                        updates[fieldFor(existingType)] = (prevCount - 1L).coerceAtLeast(0L)
                        updates[fieldFor(reactionType)] = nextCount + 1L
                    }
                }
                tx.update(postRef, updates)
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, reactionRef.path)
            throw e
        }
    }

    suspend fun getPoll(pollId: String): Poll? {
        val docRef = db.collection("polls").document(pollId)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(
                    Poll::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            null
        }
    }

    suspend fun votePoll(pollId: String, optionIndex: Int) {
        val uid = requireUserId()
        require(optionIndex in 0..3)
        val pollRef = db.collection("polls").document(pollId)
        val voteRef = pollRef.collection("votes").document(uid)

        try {
            db.runTransaction { tx ->
                val pollSnap = tx.get(pollRef)
                if (!pollSnap.exists()) throw IllegalStateException("Encuesta no encontrada")
                val voteSnap = tx.get(voteRef)
                if (voteSnap.exists()) {
                    throw IllegalStateException("Ya has votado en esta encuesta.")
                }

                val c0 = pollSnap.getLong("c0") ?: 0L
                val c1 = pollSnap.getLong("c1") ?: 0L
                val c2 = pollSnap.getLong("c2") ?: 0L
                val c3 = pollSnap.getLong("c3") ?: 0L
                val total = pollSnap.getLong("totalVotes") ?: 0L

                tx.set(
                    voteRef,
                    mapOf(
                        "pollId" to pollId,
                        "userId" to uid,
                        "optionIndex" to optionIndex.toLong(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                val countField = "c$optionIndex"
                val currentFieldVal = when (optionIndex) {
                    0 -> c0
                    1 -> c1
                    2 -> c2
                    else -> c3
                }
                tx.update(
                    pollRef,
                    mapOf(
                        countField to currentFieldVal + 1L,
                        "totalVotes" to total + 1L
                    )
                )
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, voteRef.path)
            throw e
        }
    }

    fun observeComments(postId: String): Flow<List<Comment>> = flow {
        requireUserId()
        val path = "posts/$postId/comments"
        emitAll(
            db.collection("posts").document(postId).collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Comment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { it.status == "active" }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser != null) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addComment(
        postId: String,
        text: String,
        parentCommentId: String? = null,
        showUsername: Boolean = false,
        username: String = ""
    ): String {
        val uid = requireUserId()
        val commentId = UUID.randomUUID().toString()
        val postRef = db.collection("posts").document(postId)
        val commentRef = postRef.collection("comments").document(commentId)

        val label = if (showUsername && username.isNotBlank()) {
            "@$username"
        } else {
            val tagNum = (uid.hashCode() and 0x7FFFFFFF) % 9000 + 1000
            "Eco #$tagNum"
        }

        val payload = mutableMapOf<String, Any>(
            "postId" to postId,
            "authorUid" to uid,
            "authorLabel" to label.take(40),
            "isAnonymous" to !showUsername,
            "text" to text.trim().take(500),
            "status" to "active",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!parentCommentId.isNullOrBlank()) {
            payload["parentCommentId"] = parentCommentId
        }

        return try {
            val batch = db.batch()
            batch.set(commentRef, payload)
            batch.update(
                postRef,
                mapOf(
                    "commentsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            commentId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path)
            throw e
        }
    }

    suspend fun editComment(postId: String, commentId: String, newText: String) {
        requireUserId()
        val commentRef = db.collection("posts").document(postId)
            .collection("comments").document(commentId)
        try {
            commentRef.update(
                mapOf(
                    "text" to newText.trim().take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, commentRef.path)
            throw e
        }
    }

    suspend fun deleteComment(postId: String, commentId: String) {
        requireUserId()
        val postRef = db.collection("posts").document(postId)
        val commentRef = postRef.collection("comments").document(commentId)
        try {
            val batch = db.batch()
            batch.delete(commentRef)
            batch.update(
                postRef,
                mapOf(
                    "commentsCount" to FieldValue.increment(-1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, commentRef.path)
            throw e
        }
    }

    suspend fun getSavedPostIdSet(uid: String = requireUserId()): Set<String> {
        val path = "users/$uid/savedPosts"
        return try {
            db.collection("users").document(uid).collection("savedPosts")
                .get()
                .await()
                .toObjects(SavedPost::class.java)
                .map { it.postId }
                .toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            emptySet()
        }
    }

    suspend fun toggleSavePost(postId: String): Boolean {
        val uid = requireUserId()
        val savedRef = db.collection("users").document(uid)
            .collection("savedPosts").document(postId)
        return try {
            val snap = savedRef.get().await()
            if (snap.exists()) {
                savedRef.delete().await()
                false
            } else {
                savedRef.set(
                    mapOf(
                        "userId" to uid,
                        "postId" to postId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, savedRef.path)
            throw e
        }
    }

    suspend fun getSavedPosts(): List<FeedPostBundle> {
        val uid = requireUserId()
        val path = "users/$uid/savedPosts"
        return try {
            val savedRefs = db.collection("users").document(uid).collection("savedPosts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(SavedPost::class.java)
            val posts = savedRefs.mapNotNull { saved ->
                runCatching {
                    val snap = db.collection("posts").document(saved.postId).get().await()
                    if (snap.exists()) {
                        snap.toObject(
                            AnonymousPost::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }.getOrNull()
            }.filter { it.status == "active" }
            enrichPosts(posts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun searchPosts(query: String): List<FeedPostBundle> {
        requireUserId()
        val path = "posts"
        return try {
            val recent = db.collection(path)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(60)
                .get()
                .await()
                .toObjects(
                    AnonymousPost::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                .filter { it.status == "active" }

            val q = query.trim()
            val filtered = if (q.isBlank()) recent.take(20)
            else recent.filter { it.text.contains(q, ignoreCase = true) }
            enrichPosts(filtered)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }
}
