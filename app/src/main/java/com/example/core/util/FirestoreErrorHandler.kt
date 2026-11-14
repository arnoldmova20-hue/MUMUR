package com.example.core.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = try {
        FirebaseAuth.getInstance().currentUser
    } catch (_: Exception) {
        null
    }

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

fun Throwable.extractFirestoreException(): FirebaseFirestoreException? {
    return generateSequence(this) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
}

fun Throwable.toFriendlySpanishError(): String {
    val fsEx = extractFirestoreException()
    if (fsEx != null) {
        return when (fsEx.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "No tienes permisos para realizar esta acción."
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Sin conexión con el servidor. Verifica tu internet e intenta de nuevo."
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
                "Se alcanzó el límite temporal de uso. Intenta más tarde."
            FirebaseFirestoreException.Code.NOT_FOUND ->
                "El contenido solicitado ya no está disponible."
            FirebaseFirestoreException.Code.ALREADY_EXISTS ->
                "Este registro ya existe."
            else -> "Error al sincronizar datos: ${fsEx.localizedMessage ?: "intenta de nuevo"}"
        }
    }
    return localizedMessage ?: "Ocurrió un error inesperado."
}
