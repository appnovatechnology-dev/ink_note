package com.example

import com.example.inknote.util.FileNameSanitizer
import org.junit.Assert.*
import org.junit.Test

class FileNameSanitizationAndSharingTest {

    @Test
    fun testStandardNoteTitlePreservedWithoutTimestampsOrRandomNumbers() {
        val title = "Chemistry Notes"
        val fileName = FileNameSanitizer.formatExportFileName(title, "pdf")

        assertEquals("Chemistry Notes.pdf", fileName)
        assertFalse("Filename must not contain timestamps or random numbers", fileName.contains("\\d{5,}".toRegex()))
    }

    @Test
    fun testImportedPdfTitlePreservedWithoutDuplicateExtension() {
        // When imported from a PDF named "Quantum_Physics_Lecture.pdf"
        val titleWithExt = "Quantum_Physics_Lecture.pdf"
        val fileName1 = FileNameSanitizer.formatExportFileName(titleWithExt, "pdf")
        assertEquals("Quantum_Physics_Lecture.pdf", fileName1)

        val titleWithoutExt = "Quantum_Physics_Lecture"
        val fileName2 = FileNameSanitizer.formatExportFileName(titleWithoutExt, "pdf")
        assertEquals("Quantum_Physics_Lecture.pdf", fileName2)
    }

    @Test
    fun testSanitizesOnlySpecificInvalidCharactersAndPreservesValidCharacters() {
        // Note title with invalid characters (colon, slash, question mark, quotes)
        val title = "Bio 101: Intro / Lab \"Notes\"? (Fall 2024)"
        val fileName = FileNameSanitizer.formatExportFileName(title, "pdf")

        // Invalid characters \ / : * ? " < > | should be replaced by _,
        // but spaces, letters, numbers, parentheses, and dashes must be preserved
        assertEquals("Bio 101_ Intro _ Lab _Notes__ (Fall 2024).pdf", fileName)
    }

    @Test
    fun testPreservesInternationalUnicodeCharacters() {
        val title = "Matemáticas & Français - Épreuve 1"
        val fileName = FileNameSanitizer.formatExportFileName(title, "pdf")

        assertEquals("Matemáticas & Français - Épreuve 1.pdf", fileName)
    }

    @Test
    fun testMultiPageOrSinglePageNumberingWithoutRandomNumbers() {
        val title = "Chemistry Notes"
        val page1FileName = FileNameSanitizer.formatExportFileName(title, "pdf", pageNumber = 1)
        val page2FileName = FileNameSanitizer.formatExportFileName(title, "pdf", pageNumber = 2)

        assertEquals("Chemistry Notes - Page 1.pdf", page1FileName)
        assertEquals("Chemistry Notes - Page 2.pdf", page2FileName)
    }

    @Test
    fun testBlankOrFullyInvalidTitleFallsBackSafely() {
        val blankTitle = "   "
        val invalidTitle = "///:::***???"

        assertEquals("Note.pdf", FileNameSanitizer.formatExportFileName(blankTitle, "pdf"))
        assertEquals("Note.pdf", FileNameSanitizer.formatExportFileName(invalidTitle, "pdf"))
    }

    @Test
    fun testAudioRecordingExtensionFormatting() {
        val recordingTitle = "Lecture 3: Thermodynamics"
        val fileName = FileNameSanitizer.formatExportFileName(recordingTitle, "m4a")

        assertEquals("Lecture 3_ Thermodynamics.m4a", fileName)
    }
}
