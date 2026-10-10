/**
 * Live mode (SPEC 3.1, mockup Editor-Live): the note's markdown, drawn formatted. Markers show
 * (dimmed) only on the line(s) holding the cursor; task boxes toggle on tap; the caret is a 2 dp
 * `accent` bar.
 *
 * The field edits the same `TextFieldState` as Source mode; [LiveOutputTransformation] only
 * changes how it's drawn. Task boxes, rules and images are drawn on top of the text, at the
 * positions the text layout reports for their placeholder characters.
 */
package com.kunotes.ui.editor.live

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kunotes.core.LineKind
import com.kunotes.ui.dotline.DotlineIcons
import com.kunotes.ui.editor.ContinueLists
import com.kunotes.ui.editor.ImageReader
import com.kunotes.ui.editor.rememberMarkdownStyles
import com.kunotes.ui.editor.rememberNoteImage
import com.kunotes.ui.theme.Dotline
import kotlin.math.roundToInt

/** Task box size, border and the tap area around it (mockup: 20 dp box, 1.5 dp border, r4). */
private val TaskBoxSize = 20.dp
private val TaskTapWidth = 40.dp
private val TaskBoxRadius = 4.dp

/** Mockup: `padding: 24px 20px 0 20px`. */
private val LivePadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 24.dp)

/**
 * The Live editor.
 *
 * @param state the note's buffer (shared with Source mode).
 * @param onToggleTask called with the start of a task line when its box is tapped.
 * @param readImage loads an image by its link, for image lines.
 */
@Composable
fun LiveEditor(
    state: TextFieldState,
    onToggleTask: (lineStart: Int) -> Unit,
    readImage: ImageReader,
    modifier: Modifier = Modifier,
) {
    val styles = rememberMarkdownStyles()
    val transformation = remember(styles) { LiveOutputTransformation(styles) }
    // The latest text layout, set after each layout so the overlays can find their positions.
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val selection = state.selection
    // Same inputs as the transformation, so this is a cache hit.
    val render = LiveRenderer.render(state.text.toString(), selection.start, selection.end)

    BoxWithConstraints(modifier) {
        val viewport = maxHeight
        Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Box(Modifier.padding(LivePadding)) {
                BasicTextField(
                    state = state,
                    // At least the screen's height, so a tap below the text still focuses the field.
                    modifier = Modifier.fillMaxWidth().heightIn(min = (viewport - 48.dp).coerceAtLeast(0.dp)),
                    textStyle = styles.body,
                    cursorBrush = SolidColor(Dotline.colors.accent),
                    inputTransformation = ContinueLists,
                    outputTransformation = transformation,
                    onTextLayout = { getResult -> layout = getResult() },
                )
                val current = layout
                if (current != null) {
                    LiveOverlays(render, current, onToggleTask, readImage)
                }
            }
        }
    }
}

/** Task boxes, rules and images, placed over their placeholder characters. */
@Composable
private fun LiveOverlays(
    render: LiveRender,
    layout: TextLayoutResult,
    onToggleTask: (Int) -> Unit,
    readImage: ImageReader,
) {
    val density = LocalDensity.current
    val textLength = layout.layoutInput.text.length
    for (task in render.tasks) {
        if (task.display >= textLength) continue // layout not caught up with the text yet
        val line = layout.getLineForOffset(task.display)
        val top = layout.getLineTop(line)
        val height = layout.getLineBottom(line) - top
        val x = layout.getHorizontalPosition(task.display, usePrimaryDirection = true)
        TaskBox(
            checked = task.checked,
            onClick = { onToggleTask(task.lineStart) },
            modifier =
                Modifier
                    .offset { IntOffset(x.roundToInt(), top.roundToInt()) }
                    .size(width = TaskTapWidth, height = with(density) { height.toDp() }),
        )
    }
    for (block in render.blocks) {
        if (block.display > textLength) continue
        val line = layout.getLineForOffset(block.display)
        val top = layout.getLineTop(line)
        val bottom = layout.getLineBottom(line)
        val heightDp = with(density) { (bottom - top).toDp() }
        val offset = Modifier.offset { IntOffset(0, top.roundToInt()) }
        when (block.kind) {
            LineKind.Rule ->
                Box(offset.fillMaxWidth().height(heightDp), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Dotline.colors.line))
                }
            LineKind.Image ->
                block.link?.let { link ->
                    LiveImage(link, readImage, offset.fillMaxWidth().height(heightDp).padding(vertical = Dotline.space.s1))
                }
            else -> Unit
        }
    }
}

@Composable
private fun LiveImage(
    link: String,
    readImage: ImageReader,
    modifier: Modifier,
) {
    val bitmap by rememberNoteImage(link, readImage)
    val image = bitmap ?: return
    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        modifier = modifier.clip(Dotline.radius.s),
    )
}

/** The 20 dp task box: outlined when open, `ink` with a check when done. */
@Composable
private fun TaskBox(
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = Dotline.colors
    val shape =
        androidx.compose.foundation.shape
            .RoundedCornerShape(TaskBoxRadius)
    Box(
        modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onClick = onClick,
            ).semantics { contentDescription = if (checked) "Done task" else "Open task" },
        contentAlignment = Alignment.CenterStart,
    ) {
        val box = Modifier.size(TaskBoxSize).clip(shape)
        if (checked) {
            Box(box.background(colors.ink), contentAlignment = Alignment.Center) {
                Icon(DotlineIcons.Check, contentDescription = null, tint = colors.onInk, modifier = Modifier.size(16.dp))
            }
        } else {
            Box(box.background(colors.bg).border(Dotline.size.stroke, colors.lineStrong, shape))
        }
    }
}
