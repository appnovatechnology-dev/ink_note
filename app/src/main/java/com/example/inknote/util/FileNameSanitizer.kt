package com.example.inknote.util

object FileNameSanitizer {

    /**
     * Regex matching characters that are invalid or problematic in filenames across
     * Linux, Android, FAT32, exFAT, NTFS, and common messaging/cloud share targets:
     * \ / : * ? " < > | and ASCII control characters (0x00 to 0x1F).
     */
    private val INVALID_CHARS_REGEX = "[\\\\/:*?\"<>|\\x00-\\x1F]".toRegex()

    /**
     * Sanitizes a string for use as a filename:
     * - Replaces ONLY specifically invalid filename characters with '_'
     * - Preserves spaces, international Unicode letters, numbers, dashes, parentheses, brackets, etc.
     * - Trims surrounding whitespace and trailing dots (problematic on FAT32/Windows)
     * - Never falls back to a generic name unless the string was completely blank or composed solely of invalid characters.
     */
    fun sanitize(rawName: String, fallback: String = "Note"): String {
        val sanitized = rawName
            .replace(INVALID_CHARS_REGEX, "_")
            .trim()
            .trimEnd('.')
        return if (sanitized.isBlank() || sanitized.all { it == '_' }) fallback else sanitized
    }

    /**
     * Generates a clean export/share filename that preserves the note's actual title
     * or original imported filename, with the appropriate extension and no random numbers,
     * timestamps, or UUID fragments.
     *
     * @param rawTitle The note title or original imported filename.
     * @param extension The file extension (e.g. "pdf" or "m4a").
     * @param pageNumber Optional page number when sharing/exporting an individual page.
     */
    fun formatExportFileName(
        rawTitle: String,
        extension: String = "pdf",
        pageNumber: Int? = null
    ): String {
        val ext = extension.removePrefix(".")
        var base = sanitize(rawTitle, fallback = "Note")

        // If the title already ends with the extension (e.g. imported as "Document.pdf"),
        // strip the extension first so we don't produce "Document.pdf.pdf"
        if (base.endsWith(".$ext", ignoreCase = true)) {
            base = base.substring(0, base.length - (ext.length + 1)).trim().trimEnd('.')
            if (base.isBlank()) {
                base = "Note"
            }
        }

        return if (pageNumber != null) {
            "$base - Page $pageNumber.$ext"
        } else {
            "$base.$ext"
        }
    }
}
