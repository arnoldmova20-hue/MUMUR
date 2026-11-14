package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.util.OperationType
import com.example.core.util.SafetyValidator
import com.example.core.util.handleFirestoreError
import com.example.domain.model.BlockRelationship
import com.example.domain.model.FollowRelationship
import com.example.domain.model.LegalContent
import com.example.domain.model.UserPrivateInfo
import com.example.domain.model.UserPublicProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AuthUserRepository(
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

    fun observeProfile(uid: String): Flow<UserPublicProfile?> = flow {
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            UserPublicProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser != null) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observePrivateInfo(uid: String): Flow<UserPrivateInfo?> = flow {
        val docRef = db.collection("users").document(uid).collection("private").document("info")
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            UserPrivateInfo::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun getProfile(uid: String): UserPublicProfile? {
        val docRef = db.collection("users").document(uid)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(
                    UserPublicProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun isUsernameAvailable(rawUsername: String): Boolean {
        val clean = SafetyValidator.sanitizeUsername(rawUsername)
        if (!SafetyValidator.isValidUsernameFormat(clean)) return false
        val docRef = db.collection("usernames").document(clean)
        return try {
            !docRef.get().await().exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun completeOnboarding(
        username: String,
        displayName: String,
        bio: String,
        birthYear: Int,
        photoData: String? = null
    ) {
        val uid = requireUserId()
        val cleanUsername = SafetyValidator.sanitizeUsername(username)
        require(SafetyValidator.isValidUsernameFormat(cleanUsername)) {
            "El nombre de usuario debe tener entre 3 y 24 caracteres (letras minúsculas, números o _)."
        }
        require(SafetyValidator.isEligibleAge(birthYear)) {
            "Debes tener al menos 13 años para usar MURMUR."
        }

        val email = auth.currentUser?.email
        val userRef = db.collection("users").document(uid)
        val privateRef = userRef.collection("private").document("info")
        val usernameRef = db.collection("usernames").document(cleanUsername)

        val publicPayload = mutableMapOf<String, Any>(
            "uid" to uid,
            "username" to cleanUsername,
            "displayName" to displayName.trim().take(40),
            "bio" to bio.trim().take(200),
            "accountStatus" to "active",
            "followersCount" to 0L,
            "followingCount" to 0L,
            "allowMessageRequests" to true,
            "isPrivateProfile" to false,
            "showIdentityInComments" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!photoData.isNullOrBlank()) {
            publicPayload["photoData"] = photoData
        }

        val privatePayload = mutableMapOf<String, Any>(
            "ageConfirmed" to true,
            "birthYear" to birthYear.toLong(),
            "acceptedTermsVersion" to LegalContent.TERMS_VERSION,
            "acceptedTermsAt" to FieldValue.serverTimestamp(),
            "themeMode" to "dark",
            "notifyFollowers" to true,
            "notifyReactions" to true,
            "notifyComments" to true,
            "notifyReplies" to true,
            "notifyMessages" to true,
            "notifySecurity" to true,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!email.isNullOrBlank()) {
            privatePayload["email"] = email
        }

        val usernamePayload = mapOf(
            "uid" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )

        try {
            val existingName = usernameRef.get().await()
            if (existingName.exists() && existingName.getString("uid") != uid) {
                throw IllegalStateException("Este nombre de usuario ya está en uso.")
            }
            val batch = db.batch()
            if (!existingName.exists()) {
                batch.set(usernameRef, usernamePayload)
            }
            batch.set(userRef, publicPayload)
            batch.set(privateRef, privatePayload)
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            throw e
        }
    }

    suspend fun updateProfile(
        currentProfile: UserPublicProfile,
        newUsername: String,
        newDisplayName: String,
        newBio: String,
        newPhotoData: String?,
        allowMessageRequests: Boolean,
        isPrivateProfile: Boolean,
        showIdentityInComments: Boolean
    ) {
        val uid = requireUserId()
        val cleanNewUsername = SafetyValidator.sanitizeUsername(newUsername)
        require(SafetyValidator.isValidUsernameFormat(cleanNewUsername)) {
            "El nombre de usuario debe tener entre 3 y 24 caracteres válidos."
        }
        val userRef = db.collection("users").document(uid)

        try {
            val batch = db.batch()
            if (cleanNewUsername != currentProfile.username) {
                val newNameRef = db.collection("usernames").document(cleanNewUsername)
                val snap = newNameRef.get().await()
                if (snap.exists() && snap.getString("uid") != uid) {
                    throw IllegalStateException("El nombre @$cleanNewUsername ya está ocupado.")
                }
                if (currentProfile.username.isNotBlank()) {
                    batch.delete(db.collection("usernames").document(currentProfile.username))
                }
                if (!snap.exists()) {
                    batch.set(
                        newNameRef,
                        mapOf("uid" to uid, "createdAt" to FieldValue.serverTimestamp())
                    )
                }
            }

            val updates = mutableMapOf<String, Any>(
                "username" to cleanNewUsername,
                "displayName" to newDisplayName.trim().take(40),
                "bio" to newBio.trim().take(200),
                "allowMessageRequests" to allowMessageRequests,
                "isPrivateProfile" to isPrivateProfile,
                "showIdentityInComments" to showIdentityInComments,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!newPhotoData.isNullOrBlank()) {
                updates["photoData"] = newPhotoData
            }
            batch.set(userRef, updates, SetOptions.merge())
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            throw e
        }
    }

    suspend fun updatePrivateSettings(
        themeMode: String? = null,
        notifyFollowers: Boolean? = null,
        notifyReactions: Boolean? = null,
        notifyComments: Boolean? = null,
        notifyReplies: Boolean? = null,
        notifyMessages: Boolean? = null,
        notifySecurity: Boolean? = null
    ) {
        val uid = requireUserId()
        val privateRef = db.collection("users").document(uid).collection("private").document("info")
        val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
        if (themeMode != null) updates["themeMode"] = themeMode
        if (notifyFollowers != null) updates["notifyFollowers"] = notifyFollowers
        if (notifyReactions != null) updates["notifyReactions"] = notifyReactions
        if (notifyComments != null) updates["notifyComments"] = notifyComments
        if (notifyReplies != null) updates["notifyReplies"] = notifyReplies
        if (notifyMessages != null) updates["notifyMessages"] = notifyMessages
        if (notifySecurity != null) updates["notifySecurity"] = notifySecurity

        try {
            privateRef.set(updates, SetOptions.merge()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, privateRef.path)
            throw e
        }
    }

    suspend fun searchUsers(query: String): List<UserPublicProfile> {
        val myUid = requireUserId()
        val path = "users"
        return try {
            val snap = db.collection(path)
                .whereEqualTo("accountStatus", "active")
                .limit(60)
                .get()
                .await()
            val blocked = getBlockedUidSet(myUid)
            val all = snap.toObjects(
                UserPublicProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).filter { it.uid != myUid && it.uid !in blocked }
            val q = query.trim().lowercase()
            if (q.isBlank()) {
                all.take(20)
            } else {
                all.filter {
                    it.username.contains(q, ignoreCase = true) ||
                        it.displayName.contains(q, ignoreCase = true) ||
                        it.bio.contains(q, ignoreCase = true)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun isFollowing(targetUid: String): Boolean {
        val myUid = requireUserId()
        val relId = "${myUid}_${targetUid}"
        val docRef = db.collection("followers").document(relId)
        return try {
            docRef.get().await().exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            false
        }
    }

    suspend fun followUser(targetUid: String, myUsername: String) {
        val myUid = requireUserId()
        require(myUid != targetUid) { "No puedes seguirte a ti mismo." }
        val relId = "${myUid}_${targetUid}"
        val relRef = db.collection("followers").document(relId)
        val targetRef = db.collection("users").document(targetUid)
        val myRef = db.collection("users").document(myUid)

        try {
            if (relRef.get().await().exists()) return
            val batch = db.batch()
            batch.set(
                relRef,
                mapOf(
                    "followerUid" to myUid,
                    "followingUid" to targetUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                targetRef,
                mapOf(
                    "followersCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                myRef,
                mapOf(
                    "followingCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val notifId = UUID.randomUUID().toString()
            val notifRef = targetRef.collection("notifications").document(notifId)
            batch.set(
                notifRef,
                mapOf(
                    "recipientUid" to targetUid,
                    "actorUid" to myUid,
                    "actorLabel" to "@$myUsername",
                    "type" to "follower",
                    "referenceId" to myUid,
                    "previewText" to "@$myUsername comenzó a seguirte",
                    "read" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, relRef.path)
            throw e
        }
    }

    suspend fun unfollowUser(targetUid: String) {
        val myUid = requireUserId()
        val relId = "${myUid}_${targetUid}"
        val relRef = db.collection("followers").document(relId)
        val targetRef = db.collection("users").document(targetUid)
        val myRef = db.collection("users").document(myUid)

        try {
            if (!relRef.get().await().exists()) return
            val batch = db.batch()
            batch.delete(relRef)
            batch.update(
                targetRef,
                mapOf(
                    "followersCount" to FieldValue.increment(-1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                myRef,
                mapOf(
                    "followingCount" to FieldValue.increment(-1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, relRef.path)
            throw e
        }
    }

    suspend fun getFollowersProfiles(uid: String): List<UserPublicProfile> {
        val path = "followers"
        return try {
            val rels = db.collection(path)
                .whereEqualTo("followingUid", uid)
                .get()
                .await()
                .toObjects(FollowRelationship::class.java)
            rels.mapNotNull { rel ->
                runCatching { getProfile(rel.followerUid) }.getOrNull()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun getFollowingProfiles(uid: String): List<UserPublicProfile> {
        val path = "followers"
        return try {
            val rels = db.collection(path)
                .whereEqualTo("followerUid", uid)
                .get()
                .await()
                .toObjects(FollowRelationship::class.java)
            rels.mapNotNull { rel ->
                runCatching { getProfile(rel.followingUid) }.getOrNull()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun blockUser(targetUid: String) {
        val myUid = requireUserId()
        require(myUid != targetUid)
        val blockId = "${myUid}_${targetUid}"
        val blockRef = db.collection("blocks").document(blockId)
        try {
            blockRef.set(
                mapOf(
                    "blockerUid" to myUid,
                    "blockedUid" to targetUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            // Remove follow if present
            if (isFollowing(targetUid)) {
                runCatching { unfollowUser(targetUid) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, blockRef.path)
            throw e
        }
    }

    suspend fun unblockUser(targetUid: String) {
        val myUid = requireUserId()
        val blockId = "${myUid}_${targetUid}"
        val blockRef = db.collection("blocks").document(blockId)
        try {
            blockRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, blockRef.path)
            throw e
        }
    }

    suspend fun getBlockedUidSet(myUid: String): Set<String> {
        val path = "blocks"
        return try {
            db.collection(path)
                .whereEqualTo("blockerUid", myUid)
                .get()
                .await()
                .toObjects(BlockRelationship::class.java)
                .map { it.blockedUid }
                .toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            emptySet()
        }
    }

    suspend fun getBlockedProfiles(): List<UserPublicProfile> {
        val myUid = requireUserId()
        val blockedUids = getBlockedUidSet(myUid)
        return blockedUids.mapNotNull { uid ->
            runCatching { getProfile(uid) }.getOrNull()
        }
    }

    suspend fun deleteAccount(currentUsername: String) {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        val privateRef = userRef.collection("private").document("info")
        try {
            val batch = db.batch()
            batch.delete(privateRef)
            batch.delete(userRef)
            if (currentUsername.isNotBlank()) {
                batch.delete(db.collection("usernames").document(currentUsername))
            }
            batch.commit().await()
            auth.currentUser?.delete()?.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userRef.path)
            throw e
        }
    }
}
