package com.thedesitadka.core.security

import java.text.Normalizer
import java.util.Locale

/**
 * Centralized, secure filename sanitizer for media downloads.
 * Prevents:
 * 1. Path traversal attacks (../ and ./)
 * 2. OS-forbidden characters (\ / : * ? " < > | null newline)
 * 3. Windows-reserved device names (CON, PRN, AUX, NUL, COM1-9, LPT1-9)
 * 4. Dangerous length overflows (> 60 chars)
 * 5. Unicode / Emoji / non-printable whitespace issues
 * 6. Empty or blank filenames
 */
object FileNameSanitizer {

    private const val MAX_FILENAME_LENGTH = 60
    private const val DEFAULT_FALLBACK_NAME = "media_download"

    private val RESERVED_NAMES = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    )

    private val ILLEGAL_CHARS_REGEX = Regex("""[\\/:*?"<>|\r\n\t\u0000]""")
    private val MULTI_UNDERSCORE_REGEX = Regex("""_{2,}""")

    /**
     * Sanitizes a title string into a safe, filesystem-compatible basename.
     * Does NOT append extensions (e.g. .mp4).
     */
    fun sanitize(title: String?): String {
        if (title.isNullOrBlank()) {
            return DEFAULT_FALLBACK_NAME
        }

        // Normalize unicode accents to standard ASCII where possible
        val normalized = Normalizer.normalize(title.trim(), Normalizer.Form.NFKD)

        // Replace illegal filesystem characters with underscores
        var cleaned = ILLEGAL_CHARS_REGEX.replace(normalized, "_")

        // Replace any remaining non-printable / control characters or symbols
        cleaned = cleaned.replace(Regex("""[^\w\s.-]"""), "_")

        // Collapse whitespace and multiple underscores
        cleaned = cleaned.replace(Regex("""\s+"""), "_")
        cleaned = MULTI_UNDERSCORE_REGEX.replace(cleaned, "_")

        // Strip leading/trailing periods, underscores, and spaces
        cleaned = cleaned.trim('.', '_', ' ')

        // Check if empty after cleaning
        if (cleaned.isBlank()) {
            return DEFAULT_FALLBACK_NAME
        }

        // Check for Windows reserved names
        val upperBase = cleaned.substringBefore('.').uppercase(Locale.US)
        if (upperBase in RESERVED_NAMES) {
            cleaned = "${cleaned}_file"
        }

        // Enforce maximum length constraint
        if (cleaned.length > MAX_FILENAME_LENGTH) {
            cleaned = cleaned.take(MAX_FILENAME_LENGTH).trimEnd('.', '_')
        }

        return cleaned.ifBlank { DEFAULT_FALLBACK_NAME }
    }

    /**
     * Constructs a safe, sanitized filename with an explicit ID and extension.
     */
    fun buildSafeFileName(title: String?, id: String, extension: String = "mp4"): String {
        val safeTitle = sanitize(title)
        val safeId = sanitize(id).take(20)
        val ext = extension.trimStart('.').lowercase(Locale.US)
        return "${safeTitle}_${safeId}.$ext"
    }
}
