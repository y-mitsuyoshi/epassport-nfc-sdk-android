package com.example.epassport.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MrzExtractorTest {

    private fun repeat44(prefix: String, fill: String = "A"): String {
        val remaining = 44 - prefix.length
        return prefix + fill.repeat(remaining)
    }

    @Test
    fun `extractFromLines returns MRZ when valid P less-than prefix`() {
        val line1 = repeat44("P<")
        val line2 = repeat44("B", "B")
        val result = MrzExtractor.extractFromLines(listOf(line1, line2))
        assertEquals("$line1\n$line2", result)
    }

    @Test
    fun `extractFromLines returns MRZ when R less-than prefix normalized to P less-than`() {
        val line1 = repeat44("R<")
        val line2 = repeat44("B", "B")
        val result = MrzExtractor.extractFromLines(listOf(line1, line2))
        val expectedLine1 = "P" + line1.substring(1)
        assertEquals("$expectedLine1\n$line2", result)
        assertEquals(44, expectedLine1.length)
    }

    @Test
    fun `extractFromLines returns MRZ when F less-than prefix normalized to P less-than`() {
        val line1 = repeat44("F<")
        val line2 = repeat44("B", "B")
        val result = MrzExtractor.extractFromLines(listOf(line1, line2))
        val expectedLine1 = "P" + line1.substring(1)
        assertEquals("$expectedLine1\n$line2", result)
        assertEquals(44, expectedLine1.length)
    }

    @Test
    fun `extractFromLines returns MRZ when P prefix without less-than`() {
        // OCR で '<' が欠落した場合をシミュレート
        val line1 = "P" + "A".repeat(43) // P で始まる44文字、ただし2文字目は '<' でない
        val line2 = "B".repeat(44)
        val result = MrzExtractor.extractFromLines(listOf(line1, line2))
        val expectedLine1 = "P<" + "A".repeat(42)
        assertEquals("$expectedLine1\n$line2", result)
        assertEquals(44, expectedLine1.length)
    }

    @Test
    fun `extractFromLines returns null when lines are not 44 chars`() {
        val line1 = "P<" + "A".repeat(40) // 42文字
        val line2 = "B".repeat(44)
        val result = MrzExtractor.extractFromLines(listOf(line1, line2))
        assertNull(result)
    }

    @Test
    fun `extractFromLines returns null when only one line`() {
        val line1 = repeat44("P<")
        val result = MrzExtractor.extractFromLines(listOf(line1))
        assertNull(result)
    }

    @Test
    fun `extractFromLines returns null when no valid prefix`() {
        val line1 = repeat44("X<") // X< はパスポートMRZの正当な接頭辞ではない
        val line2 = repeat44("B", "B")
        val result = MrzExtractor.extractFromLines(listOf(line1, line2))
        assertNull(result)
    }

    @Test
    fun `extractFromLines skips invalid single line and finds valid pair`() {
        val noise = "NOISE"
        val line1 = repeat44("P<")
        val line2 = repeat44("B", "B")
        val result = MrzExtractor.extractFromLines(listOf(noise, line1, line2))
        assertEquals("$line1\n$line2", result)
    }

    @Test
    fun `normalizeLine1 returns original for P less-than`() {
        val line = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
        assertEquals(line, MrzExtractor.normalizeLine1(line))
    }

    @Test
    fun `normalizeLine1 replaces R less-than with P less-than keeping length`() {
        val line = "R<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
        val result = MrzExtractor.normalizeLine1(line)
        assertEquals("P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<", result)
        assertEquals(44, result!!.length)
    }

    @Test
    fun `normalizeLine1 replaces F less-than with P less-than keeping length`() {
        val line = "F<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
        val result = MrzExtractor.normalizeLine1(line)
        assertEquals("P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<", result)
        assertEquals(44, result!!.length)
    }

    @Test
    fun `normalizeLine1 inserts less-than for P without less-than`() {
        val line = "PUTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<<" // P の後に '<' がない
        val result = MrzExtractor.normalizeLine1(line)
        assertEquals("P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<", result)
        assertEquals(44, result!!.length)
    }

    @Test
    fun `normalizeLine1 returns null for unsupported prefix`() {
        assertNull(MrzExtractor.normalizeLine1("X<ABC"))
    }

    @Test
    fun `extractFromLines returns MRZ for Visa V less-than prefix`() {
        val line1 = repeat44("V<")
        val line2 = repeat44("B", "B")
        val result = MrzExtractor.extractFromLines(listOf(line1, line2))
        assertEquals("$line1\n$line2", result)
    }

    @Test
    fun `extractFromLines returns MRZ for Identity I less-than prefix`() {
        val line1 = repeat44("I<")
        val line2 = repeat44("B", "B")
        val result = MrzExtractor.extractFromLines(listOf(line1, line2))
        assertEquals("$line1\n$line2", result)
    }

    @Test
    fun `extractFromLines returns MRZ for Collective C less-than prefix`() {
        val line1 = repeat44("C<")
        val line2 = repeat44("B", "B")
        val result = MrzExtractor.extractFromLines(listOf(line1, line2))
        assertEquals("$line1\n$line2", result)
    }

    @Test
    fun `extractFromLines returns MRZ for Admittance A less-than prefix`() {
        val line1 = repeat44("A<")
        val line2 = repeat44("B", "B")
        val result = MrzExtractor.extractFromLines(listOf(line1, line2))
        assertEquals("$line1\n$line2", result)
    }

    @Test
    fun `extractFromLines returns null for unsupported document code X`() {
        val line1 = repeat44("X<")
        val line2 = repeat44("B", "B")
        val result = MrzExtractor.extractFromLines(listOf(line1, line2))
        assertNull(result)
    }

    @Test
    fun `normalizeLine1 returns original for Visa V less-than`() {
        val line = "V<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
        assertEquals(line, MrzExtractor.normalizeLine1(line))
    }

    @Test
    fun `normalizeLine1 returns original for Identity I less-than`() {
        val line = "I<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
        assertEquals(line, MrzExtractor.normalizeLine1(line))
    }
}
