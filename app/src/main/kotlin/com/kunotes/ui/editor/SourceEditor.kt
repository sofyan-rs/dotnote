/**
 * Source mode (SPEC 3.2, mockup Editor-Source): the raw markdown in Space Mono 13/24 with a
 * line-number gutter. The cursor's line number is `ink` and its line has a `surface` background.
 * Markers are drawn `inkFaint`, heading text bold, code muted; nothing is hidden.
 *
 * Edits the same `TextFieldState` as Live mode. Styling is an `OutputTransformation` that only
 * adds styles, so what you see is exactly what is in the file.
 */
package com.kunotes.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.kunotes.core.Live
import com.kunotes.ui.theme.Dotline

/** Gutter width and the gap to the text (mockup: numbers right-aligned in ~28 dp, 12 dp gap). */
private val GutterWidth = 32.dp
private val GutterGap = 12.dp

/**
 * The Source editor.
 *
 * @param state the note's buffer (shared with Live mode).
 */
@Composable
fun SourceEditor(
    state: TextFieldState,
    modifier: Modifier = Modifier,
) {
    val styles = rememberMarkdownStyles()
    val colors = Dotline.colors
    val transformation = remember(styles) { SourceOutputTransformation(styles) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val measurer = rememberTextMeasurer()
    val numberStyle = styles.source.copy(textAlign = TextAlign.End)
    val text = state.text
    val cursor = state.selection.start

    BoxWithConstraints(modifier) {
        val viewport = maxHeight
        Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Row(
                Modifier
                    .padding(top = 20.dp, start = Dotline.space.s2, end = Dotline.space.s4, bottom = 20.dp)
                    // Gutter and current-line background, drawn from the text layout.
                    .drawBehind {
                        val current = layout ?: return@drawBehind
                        if (current.layoutInput.text.length != text.length) return@drawBehind
                        val lines = logicalLines(text)
                        val currentLine = lines.indexOfLast { it <= cursor }.coerceAtLeast(0)
                        lines.forEachIndexed { index, start ->
                            val first = current.getLineForOffset(start)
                            val top = current.getLineTop(first)
                            if (index == currentLine) {
                                val end = lines.getOrNull(index + 1)?.minus(1) ?: text.length
                                val bottom = current.getLineBottom(current.getLineForOffset(end))
                                drawRect(colors.surface, Offset(0f, top), Size(size.width, bottom - top))
                            }
                            val number =
                                measurer.measure(
                                    (index + 1).toString(),
                                    numberStyle.copy(color = if (index == currentLine) colors.ink else colors.inkFaint),
                                    constraints = Constraints.fixedWidth(GutterWidth.roundToPx()),
                                )
                            drawText(number, topLeft = Offset(0f, top))
                        }
                    },
            ) {
                Box(Modifier.width(GutterWidth + GutterGap))
                BasicTextField(
                    state = state,
                    modifier = Modifier.weight(1f).heightIn(min = (viewport - 40.dp).coerceAtLeast(0.dp)),
                    textStyle = styles.source,
                    cursorBrush = SolidColor(colors.accent),
                    inputTransformation = ContinueLists,
                    outputTransformation = transformation,
                    onTextLayout = { getResult -> layout = getResult() },
                )
            }
        }
    }
}

/** Start offsets of every line of [text] (a line per `\n`). */
private fun logicalLines(text: CharSequence): List<Int> {
    val starts = mutableListOf(0)
    for (index in text.indices) if (text[index] == '\n') starts += index + 1
    return starts
}

/** Styles markdown parts for Source mode; never changes the text. */
private class SourceOutputTransformation(
    private val styles: MarkdownStyles,
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        for (span in Live.spans(originalText.toString())) {
            styles.source(span.kind)?.let { addStyle(it, span.range.start, span.range.end) }
        }
    }
}
