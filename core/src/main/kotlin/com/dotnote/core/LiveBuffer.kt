/**
 * The text and cursor of the Live editor, with undo/redo. Pure data: the UI turns key presses and
 * taps into calls here and draws the result. It's the fallback when the text field's own undo can't
 * be used (ARCHITECTURE §6.3). Port of desktop `live_buffer.rs` (UTF-16 offsets here).
 */
package com.dotnote.core

/**
 * An editable note text with a selection and undo/redo history.
 *
 * Offsets are UTF-16 indexes. Every offset given to this class is clamped into the text and moved
 * off the middle of a surrogate pair, so the buffer never splits an emoji. Left/right steps over
 * whole characters as a person sees them (👩‍💻 is one step).
 */
class LiveBuffer(
    text: String = "",
) {
    /** One change, remembered so it can be undone. */
    private data class Change(
        /** Where the change happened, and the text that was there before. */
        val rangeBefore: TextRange,
        val textBefore: String,
        /** The text that replaced it (`var` so typed characters can be added to it). */
        var textAfter: String,
        val selectionBefore: TextRange,
        var selectionAfter: TextRange,
    )

    /** The current text. */
    var text: String = text
        private set // readable from outside, changed only by this class

    /** The selected range; empty means just a cursor. */
    var selection: TextRange = TextRange.at(0)
        private set

    /** The fixed end of the selection (where a shift-selection started). */
    private var anchor = 0
    private val undoStack = mutableListOf<Change>()
    private val redoStack = mutableListOf<Change>()

    /** True while typing plain characters, so a word typed in a row undoes at once. */
    private var typingRun = false

    /** The moving end of the selection (where the caret is drawn). */
    val cursor: Int
        get() = if (anchor == selection.start) selection.end else selection.start

    // ----- Selection -----

    /** Puts the cursor at [offset], or extends the selection to it when [extend] (shift held). */
    fun moveTo(
        offset: Int,
        extend: Boolean,
    ) {
        val position = clamp(offset)
        if (extend) {
            selection = TextRange(minOf(anchor, position), maxOf(anchor, position))
        } else {
            selection = TextRange.at(position)
            anchor = position
        }
        typingRun = false
    }

    /** Selects [range]; the cursor goes to its end. */
    fun select(range: TextRange) {
        val clamped = TextRange(clamp(range.start), clamp(range.end))
        anchor = clamped.start
        selection = clamped
        typingRun = false
    }

    fun selectAll() = select(TextRange(0, text.length))

    /** Left arrow: collapses a selection, or moves one character left. */
    fun moveLeft(extend: Boolean) {
        if (!extend && !selection.isEmpty) {
            moveTo(selection.start, extend = false)
        } else {
            moveTo(previousBoundary(cursor), extend)
        }
    }

    /** Right arrow: collapses a selection, or moves one character right. */
    fun moveRight(extend: Boolean) {
        if (!extend && !selection.isEmpty) {
            moveTo(selection.end, extend = false)
        } else {
            moveTo(nextBoundary(cursor), extend)
        }
    }

    /** Alt/Ctrl + Left: to the start of the previous word. */
    fun moveWordLeft(extend: Boolean) = moveTo(previousWordStart(cursor), extend)

    /** Alt/Ctrl + Right: to the end of the next word. */
    fun moveWordRight(extend: Boolean) = moveTo(nextWordEnd(cursor), extend)

    /** Double tap: selects the word (or run of spaces, or punctuation) at [offset]. */
    fun selectWord(offset: Int) {
        val position = clamp(offset)
        val word =
            WordSegments.split(text).firstOrNull { range ->
                position in range && '\n' !in text.substring(range)
            }
        select(word ?: TextRange.at(position))
    }

    /** Triple tap: selects the whole line at [offset], including its line break. */
    fun selectLine(offset: Int) {
        val start = lineStart(offset)
        val end = lineEnd(offset)
        select(TextRange(start, if (end < text.length) end + 1 else end))
    }

    fun moveToLineStart(extend: Boolean) = moveTo(lineStart(cursor), extend)

    fun moveToLineEnd(extend: Boolean) = moveTo(lineEnd(cursor), extend)

    // ----- Editing -----

    /** Replaces the selection with [newText] (typing, paste, IME commit). */
    fun insert(newText: String) {
        // One character (one code point, so an emoji counts) without a line break is "typing".
        val isTyping = newText.codePointCount(0, newText.length) == 1 && '\n' !in newText
        replace(selection, newText, mergeWithTyping = isTyping)
    }

    /**
     * Replaces [range] with [newText], putting the cursor after it.
     * [mergeWithTyping] lets single typed characters undo together.
     */
    fun replace(
        range: TextRange,
        newText: String,
        mergeWithTyping: Boolean,
    ) {
        val start = clamp(range.start)
        val target = TextRange(start, maxOf(start, clamp(range.end)))
        val selectionBefore = selection
        val textBefore = text.substring(target)
        text = text.replaceRange(target.start, target.end, newText)
        val cursorAfter = target.start + newText.length
        selection = TextRange.at(cursorAfter)
        anchor = cursorAfter
        redoStack.clear()

        // Merge into the last change if it was typing that ended exactly where this starts.
        val last = undoStack.lastOrNull()
        val merges =
            mergeWithTyping &&
                typingRun &&
                last != null &&
                last.rangeBefore.start + last.textAfter.length == target.start &&
                target.isEmpty &&
                last.textBefore.isEmpty()
        if (merges) {
            // Kotlin knows `last` isn't null here, because `merges` checked it.
            last.textAfter += newText
            last.selectionAfter = selection
        } else {
            undoStack.add(Change(target, textBefore, newText, selectionBefore, selection))
        }
        typingRun = mergeWithTyping
    }

    /** Backspace: deletes the selection, or the character before the cursor. */
    fun backspace() {
        val range = if (selection.isEmpty) TextRange(previousBoundary(cursor), cursor) else selection
        if (!range.isEmpty) replace(range, "", mergeWithTyping = false)
    }

    /** Delete key: deletes the selection, or the character after the cursor. */
    fun deleteForward() {
        val range = if (selection.isEmpty) TextRange(cursor, nextBoundary(cursor)) else selection
        if (!range.isEmpty) replace(range, "", mergeWithTyping = false)
    }

    /** Alt/Ctrl + Backspace: deletes back to the start of the word. */
    fun deleteWordBack() {
        if (selection.isEmpty) select(TextRange(previousWordStart(cursor), cursor))
        backspace()
    }

    /** Cmd + Backspace: deletes back to the start of the line (at the start, joins with the line above). */
    fun deleteToLineStart() {
        if (selection.isEmpty) {
            val lineStart = lineStart(cursor)
            val start = if (lineStart == cursor) previousBoundary(lineStart) else lineStart
            select(TextRange(start, cursor))
        }
        backspace()
    }

    /**
     * Enter: a new line that continues a list (`- `, `1. ` → `2. `, `- [ ] `).
     * On an empty list item, ends the list instead (removes the marker).
     */
    fun newLine() {
        val lineStart = lineStart(selection.start)
        val line = text.substring(lineStart, lineEnd(selection.start))
        val continuation = LiveLists.continuation(line)
        when {
            continuation == null -> insert("\n")
            LiveText.trim(line.substring(continuation.markerLength)).isEmpty() ->
                replace(TextRange(lineStart, lineStart + line.length), "", mergeWithTyping = false) // empty item: end the list
            else -> insert("\n" + continuation.nextMarker)
        }
    }

    /** Ticks or unticks the task box on the line containing [offset]. Returns true if the line had one. */
    fun toggleTask(offset: Int): Boolean {
        val toggle = LiveLists.taskToggle(text, clamp(offset)) ?: return false
        val kept = selection
        replace(toggle.range, toggle.replacement, mergeWithTyping = false)
        // Ticking doesn't move the cursor. (The box keeps its length, so offsets stay valid.)
        selection = kept
        anchor = kept.start
        return true
    }

    /** Undoes the last change. Returns false if there was nothing to undo. */
    fun undo(): Boolean {
        val change = undoStack.removeLastOrNull() ?: return false
        val start = change.rangeBefore.start
        text = text.replaceRange(start, start + change.textAfter.length, change.textBefore)
        selection = change.selectionBefore
        anchor = selection.start
        redoStack.add(change)
        typingRun = false
        return true
    }

    /** Redoes the last undone change. Returns false if there was nothing to redo. */
    fun redo(): Boolean {
        val change = redoStack.removeLastOrNull() ?: return false
        text = text.replaceRange(change.rangeBefore.start, change.rangeBefore.end, change.textAfter)
        selection = change.selectionAfter
        anchor = selection.start
        undoStack.add(change)
        typingRun = false
        return true
    }

    // ----- Positions -----

    /** The start of the line containing [offset]. */
    fun lineStart(offset: Int): Int {
        val position = clamp(offset)
        return text.lastIndexOf('\n', position - 1) + 1
    }

    /** The end of the line containing [offset] (before its `\n`). */
    fun lineEnd(offset: Int): Int {
        val newline = text.indexOf('\n', clamp(offset))
        return if (newline == -1) text.length else newline
    }

    /** The start of the character before [offset] (a whole emoji or accented letter). */
    fun previousBoundary(offset: Int): Int = Graphemes.previousBoundary(text, clamp(offset))

    /** The end of the character after [offset]. */
    fun nextBoundary(offset: Int): Int = Graphemes.nextBoundary(text, clamp(offset))

    /** The start of the word before [offset], skipping spaces and punctuation. */
    fun previousWordStart(offset: Int): Int {
        val position = clamp(offset)
        val before = text.substring(0, position)
        val word = WordSegments.split(before).lastOrNull { WordSegments.hasAlphanumeric(before.substring(it)) }
        return word?.start ?: 0
    }

    /** The end of the word after [offset], skipping spaces and punctuation. */
    fun nextWordEnd(offset: Int): Int {
        val position = clamp(offset)
        val after = text.substring(position)
        val word = WordSegments.split(after).firstOrNull { WordSegments.hasAlphanumeric(after.substring(it)) }
        return if (word == null) text.length else position + word.end
    }

    /** Keeps an offset inside the text and off the middle of a surrogate pair (moves it back one). */
    private fun clamp(offset: Int): Int {
        val position = offset.coerceIn(0, text.length)
        val insidePair =
            position in 1 until text.length &&
                Character.isHighSurrogate(text[position - 1]) &&
                Character.isLowSurrogate(text[position])
        return if (insidePair) position - 1 else position
    }
}
