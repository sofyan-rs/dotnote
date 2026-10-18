/**
 * Preview helpers for commonmark-java: the parser with the GitHub extensions
 * (tables, ~~strikethrough~~, task lists), and inline content (emphasis, code, links) as an
 * `AnnotatedString` for one `Text`.
 */
package com.dotnote.ui.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.HardLineBreak
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.parser.Parser

/** Prefix of note links in the preview's text; [PreviewMarkdown] adds it, the preview pane handles it. */
internal const val NOTE_LINK_SCHEME = "dotnote-note:"

internal object PreviewMarkdown {
    /** Building a parser is not free, and it's safe to share between threads. */
    private val parser: Parser =
        Parser
            .builder()
            .extensions(listOf(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create()))
            .build()

    /** Parses the note. Call it on `Dispatchers.Default` (a big note takes a moment). */
    fun parse(text: String): Node = parser.parse(text)

    /** The children of [node], as a list (commonmark links them as firstChild / next). */
    fun children(node: Node): List<Node> = generateSequence(node.firstChild) { it.next }.toList()

    /** The inline content of a paragraph, heading or table cell, styled. */
    fun inline(
        node: Node,
        styles: MarkdownStyles,
    ): AnnotatedString = buildAnnotatedString { appendChildren(node, styles) }

    /** Plain text of [node] (e.g. an image's alt text). */
    fun plainText(node: Node): String =
        buildString {
            fun walk(current: Node) {
                when (current) {
                    is Text -> append(current.literal)
                    is Code -> append(current.literal)
                    else -> children(current).forEach(::walk)
                }
            }
            walk(node)
        }

    private fun AnnotatedString.Builder.appendChildren(
        node: Node,
        styles: MarkdownStyles,
    ) {
        for (child in children(node)) appendInline(child, styles)
    }

    private fun AnnotatedString.Builder.appendInline(
        node: Node,
        styles: MarkdownStyles,
    ) {
        when (node) {
            is Text -> append(node.literal)
            is Code -> withStyle(styles.inlineCode) { append(node.literal) }
            is Emphasis -> withStyle(styles.italic) { appendChildren(node, styles) }
            is StrongEmphasis -> withStyle(styles.bold) { appendChildren(node, styles) }
            is Strikethrough -> withStyle(styles.strike) { appendChildren(node, styles) }
            is Link -> appendLink(node, styles)
            // An image inside a line of text shows its alt text; an image on its own line is drawn.
            is Image -> append(plainText(node))
            is SoftLineBreak -> append(' ')
            is HardLineBreak -> append('\n')
            is HtmlInline -> append(node.literal)
            is TaskListItemMarker -> Unit
            else -> appendChildren(node, styles)
        }
    }

    /**
     * Web and mail links open in the browser. Relative links to notes (`Plan.md`,
     * `../Projects/Plan.md`) open that note: they get a `dotnote-note:` address that the preview
     * catches (see [NOTE_LINK_SCHEME]). Other links are only styled.
     */
    private fun AnnotatedString.Builder.appendLink(
        link: Link,
        styles: MarkdownStyles,
    ) {
        val url = link.destination.orEmpty()
        val opensOutside = url.startsWith("https://") || url.startsWith("http://") || url.startsWith("mailto:")
        if (opensOutside) {
            withLink(LinkAnnotation.Url(url, TextLinkStyles(style = styles.link))) { appendChildren(link, styles) }
        } else if (isNoteLink(url)) {
            withLink(LinkAnnotation.Url(NOTE_LINK_SCHEME + url, TextLinkStyles(style = styles.link))) { appendChildren(link, styles) }
        } else {
            withStyle(styles.link) { appendChildren(link, styles) }
        }
    }

    private fun isNoteLink(url: String): Boolean {
        val target = url.substringBefore('#').lowercase()
        return !url.contains("://") && (target.endsWith(".md") || target.endsWith(".md.age"))
    }
}
