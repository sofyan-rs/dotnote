package com.dotnote.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiveTableTest {
    private fun texts(line: String): List<String> = LiveTable.cells(line).map { line.substring(it) }

    @Test
    fun `cells drop outer pipes and spaces`() {
        assertEquals(listOf("Name", "Age"), texts("| Name | Age |"))
        assertEquals(listOf("Name", "Age"), texts("Name | Age"))
        assertEquals(listOf("", ""), texts("|  |  |"))
        assertEquals(listOf("a \\| b", "日本"), texts("| a \\| b | 日本 |"))
    }

    @Test
    fun `cells with emoji keep utf-16 ranges`() {
        val line = "| 👩‍💻 | 🇮🇩 x | \\😀|y |"
        assertEquals(listOf("👩‍💻", "🇮🇩 x", "\\😀", "y"), texts(line))
        assertEquals(TextRange(2, 7), LiveTable.cells(line)[0])
    }

    @Test
    fun `delimiter rows`() {
        assertTrue(LiveTable.isDelimiterRow("| --- | :---: |"))
        assertTrue(LiveTable.isDelimiterRow("---|---"))
        assertFalse(LiveTable.isDelimiterRow("| a | b |"))
        assertFalse(LiveTable.isDelimiterRow("---"), "a rule isn't a table delimiter")
    }

    @Test
    fun `tables are found with their body rows`() {
        val text = "intro\n| A | B |\n| --- | --- |\n| 1 | 2 |\n| 3 | 4 |\n\nafter"
        assertEquals(listOf(1 until 5), LiveTable.tables(text, LiveView.lines(text)))
    }

    @Test
    fun `tables with cjk and emoji`() {
        val text = "😀\n| 名前 | 🎉 |\r\n|---|---|\r\n| 日本 | 👍🏽 |"
        assertEquals(listOf(1 until 4), LiveTable.tables(text, LiveView.lines(text)))
    }

    @Test
    fun `a header without a matching delimiter is not a table`() {
        val text = "| A | B |\n| --- |\n"
        assertTrue(LiveTable.tables(text, LiveView.lines(text)).isEmpty())
        val plain = "a | b\nplain"
        assertTrue(LiveTable.tables(plain, LiveView.lines(plain)).isEmpty())
    }
}
