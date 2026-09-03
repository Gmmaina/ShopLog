package com.example.shoplog

import com.example.shoplog.core.util.ShareCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCodeGeneratorTest {

    @Test
    fun generateCode_returnsSevenCharAlphanumericCode() {
        val code = ShareCodeGenerator.generateCode()
        assertEquals(7, code.length)
        assertTrue(code.all { it.isLetterOrDigit() && it.isUpperCase() })
    }

    @Test
    fun isValidCode_validatesCorrectCodeFormats() {
        assertTrue(ShareCodeGenerator.isValidCode("AUG123D"))
        assertTrue(ShareCodeGenerator.isValidCode("aug123d"))
        assertTrue(ShareCodeGenerator.isValidCode("ABC12"))

        assertFalse(ShareCodeGenerator.isValidCode("AB")) // too short
        assertFalse(ShareCodeGenerator.isValidCode("123456789012")) // too long
        assertFalse(ShareCodeGenerator.isValidCode("AUG 123D")) // contains space
    }

    @Test
    fun normalize_convertsToTrimmedUppercase() {
        assertEquals("AUG123D", ShareCodeGenerator.normalize(" aug123d "))
        assertEquals("CODE123", ShareCodeGenerator.normalize("Code123"))
    }
}
