/**
 * Q2 spike: Live mode on Compose's `BasicTextField(TextFieldState)` + `OutputTransformation`.
 *
 * The stored text stays raw markdown. The output transformation only changes what is drawn:
 * it styles spans from `Live.spans` and hides marker characters on every line except the one
 * holding the cursor. Compose maps cursor positions across the hidden characters by itself.
 */
package com.dotnote.spike

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.dotnote.core.Live
import com.dotnote.core.SpanKind
import com.dotnote.ui.theme.Dotline
import com.dotnote.ui.theme.DotlineColors
import com.dotnote.ui.theme.GeistMono

@Composable
fun LivePreviewField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
) {
    val colors = Dotline.colors
    val transformation = remember(colors) { LiveOutputTransformation(colors) }
    BasicTextField(
        state = state,
        modifier = modifier,
        textStyle = Dotline.type.body.copy(color = colors.ink, fontSize = 16.sp, lineHeight = 26.sp),
        cursorBrush = SolidColor(colors.accent),
        outputTransformation = transformation,
    )
}

private class LiveOutputTransformation(
    private val colors: DotlineColors,
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val text = originalText.toString()
        val cursor = originalSelection.start
        // The line holding the cursor keeps its markers visible, so they can be edited.
        val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (cursor == 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }

        val spans = Live.spans(text)
        for (span in spans) {
            styleFor(span.kind)?.let { addStyle(it, span.range.start, span.range.end) }
        }
        // Hide markers from the end backwards, so earlier offsets stay valid.
        for (span in spans.asReversed()) {
            if (span.kind != SpanKind.Marker) continue
            val onCursorLine = span.range.start <= lineEnd && span.range.end >= lineStart
            if (!onCursorLine) replace(span.range.start, span.range.end, "")
        }
    }

    private fun styleFor(kind: SpanKind): SpanStyle? =
        when (kind) {
            is SpanKind.Heading ->
                when (kind.level) {
                    1 -> SpanStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    2 -> SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    else -> SpanStyle(fontWeight = FontWeight.SemiBold)
                }
            SpanKind.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
            SpanKind.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
            SpanKind.BoldItalic -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            SpanKind.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            SpanKind.Code -> SpanStyle(fontFamily = GeistMono, fontSize = 14.sp, background = colors.surfaceRaised)
            SpanKind.Marker -> SpanStyle(color = colors.lineStrong)
            SpanKind.LinkText -> SpanStyle(textDecoration = TextDecoration.Underline)
            SpanKind.Quote -> SpanStyle(color = colors.inkMuted)
            else -> null
        }
}
