package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.util.OperationType
import com.example.core.util.handleFirestoreError
import com.example.domain.model.ContentReport
import com.example.domain.model.NotificationItem
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
import java.util.UUID

class NotificationAndSafetyRepository(
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
            ?: throw IllegalStateException("Debes iniciar sesión para continuar.")

    fun observeNotifications(myUid: String): Flow<List<NotificationItem>> = flow {
        val path = "users/$myUid/notifications"
        emitAll(
            db.collection("users").document(myUid).collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        NotificationItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
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

    suspend fun markNotificationRead(notificationId: String) {
        val myUid = requireUserId()
        val docRef = db.collection("users").document(myUid)
            .collection("notifications").document(notificationId)
        try {
            docRef.update("read", true).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        }
    }

    suspend fun markAllNotificationsRead() {
        val myUid = requireUserId()
        val colRef = db.collection("users").document(myUid).collection("notifications")
        try {
            val unread = colRef.whereEqualTo("read", false).get().await()
            if (unread.isEmpty) return
            val batch = db.batch()
            unread.documents.forEach { doc -> batch.update(doc.reference, "read", true) }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, colRef.path)
        }
    }

    suspend fun submitReport(
        contentType: String,
        contentId: String,
        reason: String,
        details: String
    ): String {
        val myUid = requireUserId()
        val reportId = UUID.randomUUID().toString()
        val docRef = db.collection("reports").document(reportId)
        val payload = mapOf(
            "reporterUid" to myUid,
            "contentType" to contentType,
            "contentId" to contentId,
            "reason" to reason.trim().take(100),
            "details" to details.trim().take(500),
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            reportId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun getMyReports(): List<ContentReport> {
        val myUid = requireUserId()
        val path = "reports"
        return try {
            db.collection(path)
                .whereEqualTo("reporterUid", myUid)
                .get()
                .await()
                .toObjects(
                    ContentReport::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }
}
