package com.example.shoplog.core.util

import java.security.SecureRandom

/**
 * Utility for generating short, unambiguous, human-readable share codes (e.g. `AUG123D`).
 */
object ShareCodeGenerator {

    // Uppercase alphanumeric character set omitting confusing characters (0, O, 1, I)
    private const val ALLOWED_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val CODE_LENGTH = 7
    private val random = SecureRandom()

    /**
     * Generates a random 7-character alphanumeric share code.
     */
    fun generateCode(): String {
        val sb = StringBuilder(CODE_LENGTH)
        for (i in 0 until CODE_LENGTH) {
            val randomIndex = random.nextInt(ALLOWED_CHARS.length)
            sb.append(ALLOWED_CHARS[randomIndex])
        }
        return sb.toString()
    }

    /**
     * Validates whether a user-entered code matches the expected format (case-insensitive, 5-10 chars).
     */
    fun isValidCode(code: String): Boolean {
        val trimmed = code.trim()
        if (trimmed.length !in 5..10) return false
        return trimmed.all { char ->
            char.isLetterOrDigit()
        }
    }

    /**
     * Normalizes a user-input code into standard uppercase format.
     */
    fun normalize(code: String): String {
        return code.trim().uppercase()
    }
}
