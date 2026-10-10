/**
 * The Live-mode look (SPEC 3.1) as a Compose `OutputTransformation`: it changes only what the
 * text field *draws*, never the stored markdown. Compose maps the cursor across hidden and
 * replaced characters by itself (ARCHITECTURE §6.3, decided in the Phase 0 spike).
 *
 * What to hide and style comes from [LiveRenderer]; this class only turns it into Compose
 * edits and styles.
 */
package com.kunotes.ui.editor.live

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.kunotes.core.LineKind
import com.kunotes.ui.editor.MarkdownStyles

/** Line height of an image line, so the picture has room (drawn by [LiveEditor]). */
internal val ImageLineHeight = 200.sp

internal class LiveOutputTransformation(
    private val styles: MarkdownStyles,
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val selection = originalSelection
        val render = LiveRenderer.render(originalText.toString(), selection.start, selection.end)

        // Replace from the end backwards, so the positions of earlier replacements stay valid.
        for (replacement in render.replacements.asReversed()) {
            replace(replacement.range.start, replacement.range.end, replacement.text)
        }

        // All positions below are in the drawn text (after the replacements).
        for (line in render.lines) {
            if (line.range.isEmpty) continue
            // A paragraph style covers its line break too, so it doesn't start an extra paragraph.
            val paragraphEnd = if (line.range.end < length && charAt(line.range.end) == '\n') line.range.end + 1 else line.range.end
            paragraphStyle(line.kind)?.let { addStyle(it, line.range.start, paragraphEnd) }
            lineSpanStyle(line.kind)?.let { addStyle(it, line.range.start, line.range.end) }
        }
        for (part in render.spans) {
            if (part.range.isEmpty) continue
            val style = if (part.revealed && isMarker(part.kind)) styles.liveMarker else styles.live(part.kind)
            style?.let { addStyle(it, part.range.start, part.range.end) }
        }
        for (task in render.tasks) {
            // The spacer must be wide enough for the box whatever the line's font.
            addStyle(SpanStyle(fontSize = 16.sp), task.display, task.display + LiveRenderer.TASK_SPACER.length)
        }
    }

    private fun isMarker(kind: com.kunotes.core.SpanKind) =
        kind == com.kunotes.core.SpanKind.Marker ||
            kind == com.kunotes.core.SpanKind.ListMarker ||
            kind == com.kunotes.core.SpanKind.LinkUrl

    /** Line height per kind of line: headings get their own, image lines room for the picture. */
    private fun paragraphStyle(kind: LineKind): ParagraphStyle? =
        when (kind) {
            is LineKind.Heading -> ParagraphStyle(lineHeight = styles.headingSize(kind.level).second)
            LineKind.Image ->
                ParagraphStyle(
                    lineHeight = ImageLineHeight,
                    // The (invisible) placeholder sits at the bottom; the picture fills the line.
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Bottom, LineHeightStyle.Trim.None),
                )
            else -> null
        }

    /** Styles that cover a whole line: heading size, quotes muted, ticked tasks faint, code muted. */
    private fun lineSpanStyle(kind: LineKind): SpanStyle? =
        when (kind) {
            // The whole heading line is big, so a revealed `#` matches its text (mockup).
            is LineKind.Heading -> SpanStyle(fontSize = styles.headingSize(kind.level).first)
            LineKind.Quote -> SpanStyle(color = styles.colors.inkMuted)
            is LineKind.Task -> if (kind.checked) SpanStyle(color = styles.colors.inkFaint) else null
            LineKind.CodeBlock, LineKind.Fence -> SpanStyle(color = styles.colors.inkMuted)
            else -> null
        }
}
