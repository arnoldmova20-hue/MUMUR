package com.example.core.util

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Warning(val message: String) : ValidationResult()
    data class Blocked(val message: String) : ValidationResult()
}

object SafetyValidator {
    private val emailRegex = Regex("[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val phoneRegex = Regex("(\\+?\\d[\\d\\s\\-()]{7,}\\d)")
    private val usernameRegex = Regex("^[a-z0-9_]{3,24}$")

    private val severeAbuseKeywords = listOf(
        "te voy a matar", "voy a matarte", "suicidate", "suicídate",
        "mátate", "matate", "desnúdate", "pack íntimo", "nudes"
    )

    fun isValidUsernameFormat(username: String): Boolean =
        usernameRegex.matches(username.trim().lowercase())

    fun sanitizeUsername(input: String): String =
        input.trim().lowercase().replace(Regex("[^a-z0-9_]"), "")

    fun isEligibleAge(birthYear: Int, currentYear: Int = 2026): Boolean {
        val age = currentYear - birthYear
        return age in 13..105
    }

    fun validateContentSafety(text: String): ValidationResult {
        val normalized = text.trim().lowercase()
        if (severeAbuseKeywords.any { normalized.contains(it) }) {
            return ValidationResult.Blocked(
                "Tu mensaje infringe las normas de seguridad de MURMUR (amenazas, acoso o contenido íntimo no consentido)."
            )
        }
        if (emailRegex.containsMatchIn(text) || phoneRegex.containsMatchIn(text)) {
            return ValidationResult.Blocked(
                "Por tu seguridad y la de terceros, no se permite publicar correos electrónicos ni números de teléfono."
            )
        }
        if (text.length > 1000) {
            return ValidationResult.Blocked("El texto supera el límite de 1000 caracteres.")
        }
        return ValidationResult.Valid
    }

    fun formatRelativeTime(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0L) return "Ahora"
        val diff = (System.currentTimeMillis() - epochMillis).coerceAtLeast(0L)
        val minutes = diff / 60_000L
        val hours = minutes / 60L
        val days = hours / 24L
        return when {
            minutes < 1L -> "Ahora"
            minutes < 60L -> "Hace ${minutes}m"
            hours < 24L -> "Hace ${hours}h"
            days < 7L -> "Hace ${days}d"
            else -> {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
                val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
                val month = cal.get(java.util.Calendar.MONTH) + 1
                "$day/$month/${cal.get(java.util.Calendar.YEAR)}"
            }
        }
    }
}
