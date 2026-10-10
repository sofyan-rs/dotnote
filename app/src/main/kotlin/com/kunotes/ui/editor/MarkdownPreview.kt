/**
 * Preview mode (SPEC 3.3, mockup Editor-Preview, ARCHITECTURE §6.4): the note rendered read-only.
 *
 * commonmark-java parses the note on `Dispatchers.Default`, 150 ms after the last change (desktop
 * `PREVIEW_DELAY`). Each top-level block is one `LazyColumn` item: headings, paragraphs, lists and
 * task lists, quotes with a 2 dp `lineStrong` rule, fenced code on `surface` r16, tables, images
 * and rules.
 */
package com.kunotes.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kunotes.ui.dotline.DotlineIcons
import com.kunotes.ui.theme.Dotline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.ThematicBreak
import org.commonmark.node.Text as TextNode

/** Desktop `PREVIEW_DELAY`: wait this long after a change before parsing again. */
private const val PREVIEW_DELAY_MILLIS = 150L

/**
 * The rendered note.
 *
 * @param text the note's markdown.
 * @param contentPadding room around the blocks (e.g. for the floating Edit pill).
 */
@Composable
fun MarkdownPreview(
    text: String,
    readImage: ImageReader,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 120.dp),
) {
    val styles = rememberMarkdownStyles()
    var blocks by remember { mutableStateOf<List<Node>?>(null) }
    // Restarts on every change of `text`; the delay makes it wait until typing pauses.
    LaunchedEffect(text) {
        if (blocks != null) delay(PREVIEW_DELAY_MILLIS)
        blocks = withContext(Dispatchers.Default) { PreviewMarkdown.children(PreviewMarkdown.parse(text)) }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Dotline.space.s4),
    ) {
        itemsIndexed(blocks.orEmpty()) { _, block ->
            PreviewBlock(block, styles, readImage, textColor = Dotline.colors.ink)
        }
    }
}

/** One block, and (for lists and quotes) the blocks inside it. */
@Composable
private fun PreviewBlock(
    node: Node,
    styles: MarkdownStyles,
    readImage: ImageReader,
    textColor: Color,
) {
    val colors = Dotline.colors
    when (node) {
        is Heading -> Text(PreviewMarkdown.inline(node, styles), style = styles.heading(node.level).copy(color = textColor))
        is Paragraph -> {
            val image = soleImage(node)
            if (image != null) {
                PreviewImage(image, readImage, styles)
            } else {
                Text(PreviewMarkdown.inline(node, styles), style = styles.body.copy(color = textColor))
            }
        }
        is ListBlock -> PreviewList(node, styles, readImage, textColor)
        is BlockQuote ->
            // IntrinsicSize.Min: the row is as tall as the quote text, so the rule can fill it.
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(colors.lineStrong))
                Column(
                    Modifier.padding(start = Dotline.space.s4, top = Dotline.space.s1, bottom = Dotline.space.s1),
                    verticalArrangement = Arrangement.spacedBy(Dotline.space.s2),
                ) {
                    PreviewMarkdown.children(node).forEach { PreviewBlock(it, styles, readImage, colors.inkMuted) }
                }
            }
        is FencedCodeBlock -> CodeBox(node.literal.orEmpty(), styles)
        is IndentedCodeBlock -> CodeBox(node.literal.orEmpty(), styles)
        is HtmlBlock -> CodeBox(node.literal.orEmpty(), styles)
        is ThematicBreak ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dotline.space.s2)
                    .height(1.dp)
                    .background(colors.line),
            )
        is TableBlock -> PreviewTable(node, styles)
        else -> PreviewMarkdown.children(node).forEach { PreviewBlock(it, styles, readImage, textColor) }
    }
}

/** Bullets, numbers, or task boxes, with each item's blocks next to its marker. */
@Composable
private fun PreviewList(
    list: ListBlock,
    styles: MarkdownStyles,
    readImage: ImageReader,
    textColor: Color,
) {
    val start = (list as? OrderedList)?.markerStartNumber ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(Dotline.space.s1)) {
        PreviewMarkdown.children(list).filterIsInstance<ListItem>().forEachIndexed { index, item ->
            val task = taskMarker(item)
            Row(horizontalArrangement = Arrangement.spacedBy(if (task != null) Dotline.space.s3 else Dotline.space.s2)) {
                when {
                    task != null -> PreviewTaskBox(task.isChecked, Modifier.padding(top = 3.dp))
                    list is BulletList -> Text("•", style = styles.body.copy(color = textColor), modifier = Modifier.width(12.dp))
                    else -> Text("${start + index}.", style = styles.body.copy(color = textColor))
                }
                Column(verticalArrangement = Arrangement.spacedBy(Dotline.space.s1)) {
                    // A ticked task's text is faint (mockup: #808080).
                    val color = if (task?.isChecked == true) Dotline.colors.inkFaint else textColor
                    PreviewMarkdown
                        .children(item)
                        .filter { it !is TaskListItemMarker }
                        .forEach { PreviewBlock(it, styles, readImage, color) }
                }
            }
        }
    }
}

/** The task marker of a list item (commonmark puts it first in the item or its paragraph). */
private fun taskMarker(item: ListItem): TaskListItemMarker? =
    (item.firstChild as? TaskListItemMarker) ?: (item.firstChild?.firstChild as? TaskListItemMarker)

/** The image, if [paragraph] is only an image (spaces around it allowed). */
private fun soleImage(paragraph: Paragraph): Image? {
    val parts = PreviewMarkdown.children(paragraph).filterNot { it is TextNode && it.literal.isBlank() }
    return parts.singleOrNull() as? Image
}

@Composable
private fun PreviewTaskBox(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Dotline.colors
    val shape = RoundedCornerShape(4.dp)
    if (checked) {
        Box(modifier.size(20.dp).clip(shape).background(colors.ink), contentAlignment = Alignment.Center) {
            Icon(DotlineIcons.Check, contentDescription = "Done", tint = colors.onInk, modifier = Modifier.size(16.dp))
        }
    } else {
        Box(modifier.size(20.dp).border(Dotline.size.stroke, colors.lineStrong, shape))
    }
}

/** Fenced code: Space Mono 13/20 on `surface`, r16, scrolls sideways for long lines. */
@Composable
private fun CodeBox(
    code: String,
    styles: MarkdownStyles,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(Dotline.radius.m)
            .background(Dotline.colors.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Dotline.space.s4, vertical = Dotline.space.s3),
    ) {
        Text(code.trimEnd('\n'), style = styles.codeBlock, softWrap = false)
    }
}

/** An image on its own line, loaded through the vault; its alt text while loading or if missing. */
@Composable
private fun PreviewImage(
    image: Image,
    readImage: ImageReader,
    styles: MarkdownStyles,
) {
    val bitmap by rememberNoteImage(image.destination.orEmpty(), readImage)
    val alt = PreviewMarkdown.plainText(image)
    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = alt.ifEmpty { null },
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).clip(Dotline.radius.m),
        )
    } else if (alt.isNotEmpty()) {
        Text(alt, style = styles.body.copy(color = Dotline.colors.inkFaint))
    }
}
