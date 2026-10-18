/**
 * Markdown tables for Live mode: finding them and splitting rows into cells.
 * A table is a header row, a delimiter row (`| --- | :---: |`), then body rows that contain `|`.
 * Port of desktop `live_table.rs` (UTF-16 positions here).
 */
package com.dotnote.core

/**
 * Table helpers. Example of a table:
 * ```
 * | Name | Age |
 * | ---  | --- |
 * | Ann  | 31  |
 * ```
 */
object LiveTable {
    /**
     * Which lines form tables in [text]. [lines] comes from [LiveView.lines] for the same text.
     * Each result is a range of line indexes (header row first, last body row included).
     */
    fun tables(
        text: String,
        lines: List<LiveLine>,
    ): List<IntRange> {
        val found = mutableListOf<IntRange>()
        var index = 0
        while (index + 1 < lines.size) {
            val header = text.substring(lines[index].range)
            val delimiter = text.substring(lines[index + 1].range)
            val columns = cells(header).size
            if ('|' in header && isDelimiterRow(delimiter) && cells(delimiter).size == columns) {
                var end = index + 2
                while (end < lines.size && isBodyRow(text.substring(lines[end].range))) end++
                found.add(index until end)
                index = end
            } else {
                index++
            }
        }
        return found
    }

    /**
     * The cells of a table row: each cell's range in [line], without the `|`s and the spaces
     * around it. A `\|` inside a cell is part of the text. A leading or trailing `|` doesn't
     * make an extra empty cell.
     */
    fun cells(line: String): List<TextRange> {
        val pipes = mutableListOf<Int>()
        var i = 0
        while (i < line.length) {
            when (line[i]) {
                // Skip the escaped char. If it's an emoji, this lands on its second half, which is never `|`.
                '\\' -> i += 2
                '|' -> {
                    pipes.add(i)
                    i++
                }
                else -> i++
            }
        }

        // Cell boundaries: the start, every pipe, the end.
        val bounds = listOf(0) + pipes + listOf(line.length)
        val trimmedStart = LiveText.leadingWhitespace(line)
        val trimmedEnd = LiveText.endWithoutWhitespace(line)
        val result = mutableListOf<TextRange>()
        for (k in 0 until bounds.size - 1) {
            val left = bounds[k]
            val right = bounds[k + 1]
            val start = if (left == 0 && 0 !in pipes) 0 else left + 1
            val isOuterEdge =
                (pipes.firstOrNull() == right && right == trimmedStart) ||
                    (pipes.lastOrNull() == left && left + 1 == trimmedEnd)
            if (start > right || isOuterEdge) continue
            val cell = line.substring(start, right)
            val lead = LiveText.leadingWhitespace(cell)
            val content = LiveText.trim(cell)
            result.add(TextRange(start + lead, start + lead + content.length))
        }
        return result
    }

    /** `| --- | :---: |`: every cell is dashes, optionally with `:` at either end. */
    fun isDelimiterRow(line: String): Boolean {
        if ('-' !in line || '|' !in line) return false
        val row = cells(line)
        return row.isNotEmpty() &&
            row.all { range ->
                val cell = line.substring(range).trimStart(':').trimEnd(':')
                cell.isNotEmpty() && cell.all { it == '-' }
            }
    }

    private fun isBodyRow(line: String): Boolean = '|' in line && LiveText.trim(line).isNotEmpty()
}
