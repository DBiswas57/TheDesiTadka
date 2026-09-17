package com.thedesitadka.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameSanitizerTest {

    @Test
    fun testSanitizeRemovesIllegalCharacters() {
        val dangerous = "Video: Title / Part 1 * 2024? <HD> | [Special] \"Edition\""
        val cleaned = FileNameSanitizer.sanitize(dangerous)

        assertFalse(cleaned.contains(":"))
        assertFalse(cleaned.contains("/"))
        assertFalse(cleaned.contains("*"))
        assertFalse(cleaned.contains("?"))
        assertFalse(cleaned.contains("<"))
        assertFalse(cleaned.contains(">"))
        assertFalse(cleaned.contains("|"))
        assertFalse(cleaned.contains("\""))
        assertTrue(cleaned.isNotBlank())
    }

    @Test
    fun testSanitizeHandlesEmptyAndBlank() {
        assertEquals("media_download", FileNameSanitizer.sanitize(null))
        assertEquals("media_download", FileNameSanitizer.sanitize(""))
        assertEquals("media_download", FileNameSanitizer.sanitize("    "))
        assertEquals("media_download", FileNameSanitizer.sanitize("???///:::***"))
    }

    @Test
    fun testSanitizeEnforcesLengthLimit() {
        val veryLong = "A".repeat(150)
        val cleaned = FileNameSanitizer.sanitize(veryLong)
        assertTrue(cleaned.length <= 60)
    }

    @Test
    fun testSanitizeHandlesWindowsReservedNames() {
        val conFile = FileNameSanitizer.sanitize("CON")
        assertEquals("CON_file", conFile)

        val nulFile = FileNameSanitizer.sanitize("nul.mp4")
        assertEquals("nul.mp4_file", nulFile)
    }

    @Test
    fun testSanitizeHandlesUnicodeAndAccents() {
        val accented = "Épisode spécial résumé & divertissement"
        val cleaned = FileNameSanitizer.sanitize(accented)
        assertTrue(cleaned.isNotBlank())
        assertFalse(cleaned.contains("&"))
    }

    @Test
    fun testBuildSafeFileName() {
        val fileName = FileNameSanitizer.buildSafeFileName("Cool Movie: Part 1", "item/123", "mp4")
        assertEquals("Cool_Movie_Part_1_item_123.mp4", fileName)
    }
}
