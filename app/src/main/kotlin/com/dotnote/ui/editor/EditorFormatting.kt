/**
 * Turns a formatter-bar button into the `:core` [Format] edit to apply, and tells
 * which buttons are "on" at the cursor. Pure functions, so they are easy to test.
 */
package com.dotnote.ui.editor

import com.dotnote.core.Edit
import com.dotnote.core.Format
import com.dotnote.core.TextRange
import com.dotnote.ui.dotline.FormatAction

object EditorFormatting {
    /**
     * The edit for [action] on [text] with [selection], or `null` for actions that need more than
     * the text (Image opens the photo picker first).
     *
     * Heading cycles through H1 → H2 → H3 → H1 on the cursor's line.
     */
    fun editFor(
        action: FormatAction,
        text: String,
        selection: TextRange,
    ): Edit? =
        when (action) {
            FormatAction.Bold -> Format.bold(text, selection)
            FormatAction.Italic -> Format.italic(text, selection)
            FormatAction.Strikethrough -> Format.strikethrough(text, selection)
            FormatAction.InlineCode -> Format.inlineCode(text, selection)
            FormatAction.Heading -> Format.heading(text, selection, nextHeadingLevel(text, selection.start))
            FormatAction.Link -> Format.link(text, selection)
            FormatAction.Checklist -> Format.taskList(text, selection)
            FormatAction.BulletList -> Format.bulletList(text, selection)
            FormatAction.NumberedList -> Format.numberedList(text, selection)
            FormatAction.CodeBlock -> Format.codeBlock(text, selection)
            FormatAction.Quote -> Format.quote(text, selection)
            FormatAction.Table -> Format.table(text, selection)
            FormatAction.Rule -> Format.horizontalRule(text, selection)
            FormatAction.Image -> null
        }

    /** Buttons to draw as "on": Heading while the cursor's line is a heading. */
    fun activeActions(
        text: String,
        cursor: Int,
    ): Set<FormatAction> = if (headingLevel(text, cursor) > 0) setOf(FormatAction.Heading) else emptySet()

    /** The heading level the Heading button gives next: none or H3 → H1, H1 → H2, H2 → H3. */
    fun nextHeadingLevel(
        text: String,
        cursor: Int,
    ): Int =
        when (headingLevel(text, cursor)) {
            1 -> 2
            2 -> 3
            else -> 1
        }

    /** The heading level (1–6) of the line holding [cursor], or 0 if it isn't a heading. */
    fun headingLevel(
        text: String,
        cursor: Int,
    ): Int {
        val position = cursor.coerceIn(0, text.length)
        val start = text.lastIndexOf('\n', position - 1) + 1
        val line = text.substring(start).substringBefore('\n').trimStart(' ')
        val hashes = line.takeWhile { it == '#' }.length
        val isHeading = hashes in 1..6 && (line.length == hashes || line[hashes] == ' ')
        return if (isHeading) hashes else 0
    }
}
