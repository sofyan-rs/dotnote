/**
 * Enter in a list continues it, like desktop: after `- item` the new line starts with `- `,
 * after `1. item` with `2. `, after `- [x] done` with `- [ ] `. Enter on an empty item
 * (`- ` and nothing else) ends the list instead: the marker is removed.
 *
 * An `InputTransformation` sees every edit the user makes before it reaches the text, and can
 * change it. Here it only acts on a single typed line break; everything else passes through.
 */
package com.kunotes.ui.editor

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.TextRange
import com.kunotes.core.LiveLists

object ContinueLists : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        // Only a single typed line break: one character longer, cursor right after a "\n",
        // and everything else unchanged (not a paste, not a replaced selection).
        val original = originalText
        if (length != original.length + 1 || !selection.collapsed) return
        val typedAt = selection.start - 1
        if (typedAt < 0 || asCharSequence()[typedAt] != '\n') return
        val now = toString()
        val before = original.toString()
        if (!now.regionMatches(0, before, 0, typedAt) || !now.regionMatches(typedAt + 1, before, typedAt, before.length - typedAt)) return

        val text = asCharSequence()
        val breakAt = typedAt
        val lineStart = text.lastIndexOf('\n', breakAt - 1).let { it + 1 } // -1 + 1 = 0 for the first line
        val line = text.substring(lineStart, breakAt)
        val continuation = LiveLists.continuation(line) ?: return

        if (line.substring(continuation.markerLength).isBlank()) {
            // Empty item: remove its marker and the new line break, ending the list.
            replace(lineStart, breakAt + 1, "")
            selection = TextRange(lineStart)
        } else {
            replace(breakAt + 1, breakAt + 1, continuation.nextMarker)
            selection = TextRange(breakAt + 1 + continuation.nextMarker.length)
        }
    }
}

private fun CharSequence.lastIndexOf(
    char: Char,
    startIndex: Int,
): Int {
    var index = startIndex
    while (index >= 0) {
        if (this[index] == char) return index
        index--
    }
    return -1
}
