package com.example.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyValidatorTest {

    @Test
    fun sanitizeAndValidateUsername_handlesValidAndInvalidFormats() {
        assertEquals("eco_nocturno1", SafetyValidator.sanitizeUsername("  Eco_Nocturno1! "))
        assertTrue(SafetyValidator.isValidUsernameFormat("eco_nocturno1"))
        assertFalse(SafetyValidator.isValidUsernameFormat("ab"))
        assertFalse(SafetyValidator.isValidUsernameFormat("con espacios"))
    }

    @Test
    fun ageCheck_enforcesMinimum13Years() {
        assertTrue(SafetyValidator.isEligibleAge(birthYear = 2010, currentYear = 2026))
        assertTrue(SafetyValidator.isEligibleAge(birthYear = 2013, currentYear = 2026))
        assertFalse(SafetyValidator.isEligibleAge(birthYear = 2015, currentYear = 2026))
        assertFalse(SafetyValidator.isEligibleAge(birthYear = 1900, currentYear = 2026))
    }

    @Test
    fun contentSafety_blocksPersonalInfoAndSevereAbuse() {
        val emailResult = SafetyValidator.validateContentSafety("Escríbeme a prueba@ejemplo.com")
        assertTrue(emailResult is ValidationResult.Blocked)

        val phoneResult = SafetyValidator.validateContentSafety("Mi número es +52 55 1234 5678")
        assertTrue(phoneResult is ValidationResult.Blocked)

        val abuseResult = SafetyValidator.validateContentSafety("te voy a matar si hablas")
        assertTrue(abuseResult is ValidationResult.Blocked)

        val okResult = SafetyValidator.validateContentSafety("Hoy me sentí escuchado por primera vez.")
        assertTrue(okResult is ValidationResult.Valid)
    }
}
