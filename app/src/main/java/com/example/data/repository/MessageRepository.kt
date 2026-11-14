package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.util.OperationType
import com.example.core.util.handleFirestoreError
import com.example.domain.model.Conversation
import com.example.domain.model.DirectMessage
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MessageRepository(
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
            ?: throw IllegalStateException("Debes iniciar sesión para usar la mensajería.")

    fun conversationIdFor(uidA: String, uidB: String): String =
        listOf(uidA, uidB).sorted().joinToString("_")

    fun observeConversations(myUid: String): Flow<List<Conversation>> = flow {
        val path = "conversations"
        emitAll(
            db.collection(path)
                .whereArrayContains("participantUids", myUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                        .filter { myUid !in it.hiddenForUids }
                        .sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == myUid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(conversationId: String, myUid: String): Flow<List<DirectMessage>> = flow {
        val path = "conversations/$conversationId/messages"
        emitAll(
            db.collection("conversations").document(conversationId).collection("messages")
                .whereArrayContains("participantUids", myUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DirectMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(Long.MAX_VALUE / 1000, 0) }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == myUid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendMessage(
        targetUid: String,
        text: String,
        mediaData: String? = null,
        myUsername: String = ""
    ): String {
        val myUid = requireUserId()
        require(myUid != targetUid) { "No puedes enviarte mensajes a ti mismo." }

        val convId = conversationIdFor(myUid, targetUid)
        val participants = listOf(myUid, targetUid).sorted()
        val convRef = db.collection("conversations").document(convId)
        val msgId = UUID.randomUUID().toString()
        val msgRef = convRef.collection("messages").document(msgId)

        // Check if recipient blocked sender or sender blocked recipient
        val blockedByTarget = db.collection("blocks").document("${targetUid}_${myUid}").get().await().exists()
        val blockedByMe = db.collection("blocks").document("${myUid}_${targetUid}").get().await().exists()
        if (blockedByTarget || blockedByMe) {
            throw IllegalStateException("No es posible enviar mensajes a este usuario debido a un bloqueo.")
        }

        val convSnap = convRef.get().await()
        val preview = if (text.isNotBlank()) text.trim().take(120) else "Fotografía"

        val batch = db.batch()
        if (!convSnap.exists()) {
            // Check if target follows sender; if not, mark as message request
            val targetFollowsMe = db.collection("followers")
                .document("${targetUid}_${myUid}").get().await().exists()
            batch.set(
                convRef,
                mapOf(
                    "participantUids" to participants,
                    "lastMessageText" to preview,
                    "lastMessageSenderUid" to myUid,
                    "isRequest" to !targetFollowsMe,
                    "requestAccepted" to targetFollowsMe,
                    "unreadMap" to mapOf(targetUid to 1L, myUid to 0L),
                    "hiddenForUids" to emptyList<String>(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            val existingUnread = (convSnap.get("unreadMap") as? Map<*, *>)
                ?.let { (it[targetUid] as? Number)?.toLong() ?: 0L } ?: 0L
            batch.update(
                convRef,
                mapOf(
                    "lastMessageText" to preview,
                    "lastMessageSenderUid" to myUid,
                    "unreadMap" to mapOf(targetUid to (existingUnread + 1L), myUid to 0L),
                    "hiddenForUids" to emptyList<String>(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val msgPayload = mutableMapOf<String, Any>(
            "conversationId" to convId,
            "participantUids" to participants,
            "senderUid" to myUid,
            "text" to text.trim().take(1000),
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (!mediaData.isNullOrBlank()) {
            msgPayload["mediaData"] = mediaData
        }
        batch.set(msgRef, msgPayload)

        val notifId = UUID.randomUUID().toString()
        val notifRef = db.collection("users").document(targetUid)
            .collection("notifications").document(notifId)
        val senderLabel = if (myUsername.isNotBlank()) "@$myUsername" else "Nuevo mensaje"
        batch.set(
            notifRef,
            mapOf(
                "recipientUid" to targetUid,
                "actorUid" to myUid,
                "actorLabel" to senderLabel.take(40),
                "type" to "message",
                "referenceId" to convId,
                "previewText" to "$senderLabel: $preview".take(200),
                "read" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )

        return try {
            batch.commit().await()
            convId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, msgRef.path)
            throw e
        }
    }

    suspend fun acceptMessageRequest(conversationId: String) {
        requireUserId()
        val convRef = db.collection("conversations").document(conversationId)
        try {
            convRef.update(
                mapOf(
                    "isRequest" to false,
                    "requestAccepted" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path)
            throw e
        }
    }

    suspend fun markConversationRead(conversationId: String) {
        val myUid = requireUserId()
        val convRef = db.collection("conversations").document(conversationId)
        try {
            val snap = convRef.get().await()
            if (!snap.exists()) return
            val map = (snap.get("unreadMap") as? Map<*, *>)?.entries
                ?.associate { (k, v) -> k.toString() to ((v as? Number)?.toLong() ?: 0L) }
                ?.toMutableMap() ?: mutableMapOf()
            if ((map[myUid] ?: 0L) == 0L) return
            map[myUid] = 0L
            convRef.update(
                mapOf(
                    "unreadMap" to map,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path)
        }
    }

    suspend fun hideConversationForMe(conversationId: String) {
        val myUid = requireUserId()
        val convRef = db.collection("conversations").document(conversationId)
        try {
            val snap = convRef.get().await()
            if (!snap.exists()) return
            val currentHidden = (snap.get("hiddenForUids") as? List<*>)
                ?.mapNotNull { it?.toString() }?.toMutableSet() ?: mutableSetOf()
            currentHidden.add(myUid)
            convRef.update(
                mapOf(
                    "hiddenForUids" to currentHidden.toList().take(2),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path)
            throw e
        }
    }
}
