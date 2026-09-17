package com.example.inknote.util

data class PageRangeParseResult(
    val isValid: Boolean,
    val selectedPageIndices: List<Int> = emptyList(), // 0-based indices, sorted, unique
    val selectedPageNumbers: List<Int> = emptyList(), // 1-based page numbers, sorted, unique
    val errorMessage: String? = null,
    val confirmationText: String = ""
)

object PageRangeParser {

    fun parse(input: String, totalPages: Int): PageRangeParseResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return PageRangeParseResult(
                isValid = false,
                errorMessage = "Please enter page numbers or ranges (e.g. 1-3, 5)"
            )
        }

        if (totalPages <= 0) {
            return PageRangeParseResult(
                isValid = false,
                errorMessage = "Note has no pages to export"
            )
        }

        // Normalize spaces around hyphens: e.g. "3 - 5" -> "3-5"
        val normalized = trimmed.replace(Regex("\\s*-\\s*"), "-")
        // Split by commas, semicolons, or whitespace
        val tokens = normalized.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }

        if (tokens.isEmpty()) {
            return PageRangeParseResult(
                isValid = false,
                errorMessage = "Please enter a valid page range (e.g. 1, 3-5, 8)"
            )
        }

        val resultSet = mutableSetOf<Int>()

        for (token in tokens) {
            if (token.contains('-')) {
                val parts = token.split('-')
                if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                    return PageRangeParseResult(
                        isValid = false,
                        errorMessage = "Malformed range '$token'. Use format like 3-5."
                    )
                }
                val start = parts[0].toIntOrNull()
                val end = parts[1].toIntOrNull()
                if (start == null || end == null) {
                    return PageRangeParseResult(
                        isValid = false,
                        errorMessage = "Range '$token' must contain numbers only."
                    )
                }
                if (start < 1) {
                    return PageRangeParseResult(
                        isValid = false,
                        errorMessage = "Page numbers must be 1 or greater (found $start in '$token')."
                    )
                }
                if (end < 1) {
                    return PageRangeParseResult(
                        isValid = false,
                        errorMessage = "Page numbers must be 1 or greater (found $end in '$token')."
                    )
                }
                if (start > end) {
                    return PageRangeParseResult(
                        isValid = false,
                        errorMessage = "Invalid range '$token': start page ($start) cannot be greater than end page ($end)."
                    )
                }
                if (start > totalPages) {
                    return PageRangeParseResult(
                        isValid = false,
                        errorMessage = "Page $start does not exist (note has $totalPages ${if (totalPages == 1) "page" else "pages"})."
                    )
                }
                if (end > totalPages) {
                    return PageRangeParseResult(
                        isValid = false,
                        errorMessage = "Page $end does not exist (note has $totalPages ${if (totalPages == 1) "page" else "pages"})."
                    )
                }
                for (p in start..end) {
                    resultSet.add(p)
                }
            } else {
                val num = token.toIntOrNull()
                if (num == null) {
                    return PageRangeParseResult(
                        isValid = false,
                        errorMessage = "Invalid page entry '$token'. Please enter numbers only."
                    )
                }
                if (num < 1) {
                    return PageRangeParseResult(
                        isValid = false,
                        errorMessage = "Page numbers must be 1 or greater (found $num)."
                    )
                }
                if (num > totalPages) {
                    return PageRangeParseResult(
                        isValid = false,
                        errorMessage = "Page $num does not exist (note has $totalPages ${if (totalPages == 1) "page" else "pages"})."
                    )
                }
                resultSet.add(num)
            }
        }

        val sortedNumbers = resultSet.sorted()
        if (sortedNumbers.isEmpty()) {
            return PageRangeParseResult(
                isValid = false,
                errorMessage = "No pages selected."
            )
        }

        val sortedIndices = sortedNumbers.map { it - 1 }
        val count = sortedNumbers.size
        val summary = formatPageList(sortedNumbers)

        return PageRangeParseResult(
            isValid = true,
            selectedPageIndices = sortedIndices,
            selectedPageNumbers = sortedNumbers,
            confirmationText = "$count ${if (count == 1) "page" else "pages"} selected ($summary)"
        )
    }

    /**
     * Formats a sorted list of page numbers into a compact human-readable range string:
     * e.g. [1, 3, 4, 5, 8, 11, 12, 13, 14] -> "1, 3-5, 8, 11-14"
     */
    fun formatPageList(pages: List<Int>): String {
        if (pages.isEmpty()) return ""
        val sb = StringBuilder()
        var start = pages[0]
        var prev = pages[0]

        for (i in 1 until pages.size) {
            val curr = pages[i]
            if (curr == prev + 1) {
                prev = curr
            } else {
                if (start == prev) {
                    sb.append(start).append(", ")
                } else if (prev == start + 1) {
                    sb.append(start).append(", ").append(prev).append(", ")
                } else {
                    sb.append(start).append("-").append(prev).append(", ")
                }
                start = curr
                prev = curr
            }
        }
        if (start == prev) {
            sb.append(start)
        } else if (prev == start + 1) {
            sb.append(start).append(", ").append(prev)
        } else {
            sb.append(start).append("-").append(prev)
        }
        return sb.toString()
    }
}
