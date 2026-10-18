/**
 * Markdown formatting for the editor's formatter bar (bold, headings, links, ...).
 * Every function takes the full text and the current selection and returns an [Edit]
 * that the editor applies as one undo step. Port of desktop `format.rs`.
 */
package com.dotnote.core

/**
 * "Replace [range] with [replacement], then select [newSelection]."
 *
 * [newSelection] is given in the text *after* the edit. An empty range means
 * "put the cursor there". All positions are UTF-16 indexes (see [TextRange]).
 */
data class Edit(
    val range: TextRange,
    val replacement: String,
    val newSelection: TextRange,
) {
    /**
     * Returns [text] with this edit applied. The app uses the editor's own replace
     * instead (so it becomes one undo step); this is mainly for tests.
     */
    fun apply(text: String): String = text.replaceRange(range.start, range.end, replacement)
}

/**
 * The formatter-bar transforms. Each function is safe to call with any selection:
 * out-of-range or mid-emoji positions are clamped first, so they never throw.
 */
object Format {
    /** `**selection**` */
    fun bold(
        text: String,
        selection: TextRange,
    ): Edit = wrap(text, selection, "**", "**")

    /** `*selection*` */
    fun italic(
        text: String,
        selection: TextRange,
    ): Edit = wrap(text, selection, "*", "*")

    /** `~~selection~~` */
    fun strikethrough(
        text: String,
        selection: TextRange,
    ): Edit = wrap(text, selection, "~~", "~~")

    /** `` `selection` `` */
    fun inlineCode(
        text: String,
        selection: TextRange,
    ): Edit = wrap(text, selection, "`", "`")

    /**
     * Puts [prefix] before and [suffix] after the selection and keeps the original text
     * selected (or puts the cursor between them if nothing was selected).
     */
    fun wrap(
        text: String,
        selection: TextRange,
        prefix: String,
        suffix: String,
    ): Edit {
        val range = clamp(text, selection)
        val selected = text.substring(range)
        val innerStart = range.start + prefix.length
        return Edit(
            range = range,
            replacement = prefix + selected + suffix,
            newSelection = TextRange(innerStart, innerStart + selected.length),
        )
    }

    /**
     * Turns every selected line into a heading of [level] (1-6, other values are clamped),
     * replacing any existing heading marker instead of stacking them.
     */
    fun heading(
        text: String,
        selection: TextRange,
        level: Int,
    ): Edit {
        val marker = "#".repeat(level.coerceIn(1, 6)) + " "
        return LineFormat.transformLines(text, clamp(text, selection)) { _, line ->
            marker + LineFormat.stripHeading(line)
        }
    }

    /** Prefixes every selected line with `> `. */
    fun quote(
        text: String,
        selection: TextRange,
    ): Edit = LineFormat.transformLines(text, clamp(text, selection)) { _, line -> "> $line" }

    /** Prefixes every selected line with `- `. */
    fun bulletList(
        text: String,
        selection: TextRange,
    ): Edit = LineFormat.transformLines(text, clamp(text, selection)) { _, line -> "- $line" }

    /** Prefixes every selected line with `- [ ] ` (an unticked task). */
    fun taskList(
        text: String,
        selection: TextRange,
    ): Edit = LineFormat.transformLines(text, clamp(text, selection)) { _, line -> "- [ ] $line" }

    /** Prefixes the selected lines with `1. `, `2. `, `3. `, ... */
    fun numberedList(
        text: String,
        selection: TextRange,
    ): Edit = LineFormat.transformLines(text, clamp(text, selection)) { index, line -> "${index + 1}. $line" }

    /**
     * `[selection](url)` with "url" selected so the user can type the link target.
     * With nothing selected, the cursor goes between the brackets instead.
     */
    fun link(
        text: String,
        selection: TextRange,
    ): Edit {
        val range = clamp(text, selection)
        val selected = text.substring(range)
        val newSelection =
            if (selected.isEmpty()) {
                TextRange.at(range.start + 1) // inside "[]"
            } else {
                val urlStart = range.start + "[".length + selected.length + "](".length
                TextRange(urlStart, urlStart + "url".length)
            }
        return Edit(range = range, replacement = "[$selected](url)", newSelection = newSelection)
    }

    /** Wraps the selection in a fenced code block on its own lines. */
    fun codeBlock(
        text: String,
        selection: TextRange,
    ): Edit {
        val range = clamp(text, selection)
        val selected = text.substring(range)
        // Only add a line break where the selection doesn't already touch one.
        val before = if (range.start == LineFormat.lineStart(text, range.start)) "" else "\n"
        val after = if (range.end == LineFormat.lineEnd(text, range.end)) "" else "\n"

        val innerStart = range.start + before.length + "```\n".length
        return Edit(
            range = range,
            replacement = "$before```\n$selected\n```$after",
            newSelection = TextRange(innerStart, innerStart + selected.length),
        )
    }

    /** Replaces the selection with a `---` rule surrounded by blank lines, and puts the cursor after it. */
    fun horizontalRule(
        text: String,
        selection: TextRange,
    ): Edit {
        val edit = insertBlock(text, selection, "---").edit
        // `copy` makes a new data class instance with only the named fields changed.
        return edit.copy(newSelection = TextRange.at(edit.range.start + edit.replacement.length))
    }

    /** Inserts a two-column table (on its own lines) with "Column 1" selected. */
    fun table(
        text: String,
        selection: TextRange,
    ): Edit {
        val block = "| Column 1 | Column 2 |\n| --- | --- |\n|  |  |"
        val inserted = insertBlock(text, selection, block)
        val first = inserted.blockStart + "| ".length
        return inserted.edit.copy(newSelection = TextRange(first, first + "Column 1".length))
    }

    /**
     * An image to insert with [images]: `![alt](path)`.
     * [path] is relative to the note, e.g. `.img/cat.png`.
     */
    data class Image(
        val alt: String,
        val path: String,
    )

    /** Replaces the selection with [images], one `![alt](path)` per line. The cursor goes after them. */
    fun images(
        text: String,
        selection: TextRange,
        images: List<Image>,
    ): Edit {
        val range = clamp(text, selection)
        val replacement = images.joinToString("\n") { image -> "![${image.alt}](${image.path})" }
        return Edit(
            range = range,
            replacement = replacement,
            newSelection = TextRange.at(range.start + replacement.length),
        )
    }

    /** The result of [insertBlock]: the edit, and where the block starts in the new text. */
    private data class InsertedBlock(
        val edit: Edit,
        val blockStart: Int,
    )

    /**
     * Replaces the selection with [block], adding line breaks so the block has a blank line
     * before and after it. The edit's selection is the cursor right after the block.
     */
    private fun insertBlock(
        text: String,
        selection: TextRange,
        block: String,
    ): InsertedBlock {
        val range = clamp(text, selection)
        val textBefore = text.substring(0, range.start)
        val textAfter = text.substring(range.end)

        val before =
            when {
                textBefore.isEmpty() || textBefore.endsWith("\n\n") -> ""
                textBefore.endsWith("\n") -> "\n"
                else -> "\n\n"
            }
        val after =
            when {
                textAfter.startsWith("\n\n") -> ""
                textAfter.startsWith("\n") -> "\n"
                else -> "\n\n"
            }

        val blockStart = range.start + before.length
        val edit =
            Edit(
                range = range,
                replacement = before + block + after,
                newSelection = TextRange.at(blockStart + block.length),
            )
        return InsertedBlock(edit, blockStart)
    }

    /**
     * Keeps [range] inside [text] and off the middle of a surrogate pair, so cutting the
     * text there can never split an emoji. The start moves back and the end moves forward,
     * so a half-selected emoji becomes fully selected (desktop does the same with UTF-8 bytes).
     */
    internal fun clamp(
        text: String,
        range: TextRange,
    ): TextRange {
        var start = range.start.coerceAtMost(text.length)
        var end = range.end.coerceIn(start, text.length)
        if (splitsSurrogatePair(text, start)) start -= 1
        if (splitsSurrogatePair(text, end)) end += 1
        return TextRange(start, end)
    }

    private fun splitsSurrogatePair(
        text: String,
        index: Int,
    ): Boolean =
        index in 1 until text.length &&
            Character.isHighSurrogate(text[index - 1]) &&
            Character.isLowSurrogate(text[index])
}
