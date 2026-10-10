/**
 * Text styles of rendered markdown, shared by Live (SPEC 3.1) and Preview (3.3) so both look the
 * same: body 16/26, H1 30/36 bold, H2 22/28 semibold, inline code in Space Mono 14 on
 * `surfaceRaised`, quotes muted, code blocks in Space Mono 13/20 (sizes from the mockups).
 *
 * Also the Source-mode (3.2) styles: Space Mono 13/24, markers `inkFaint`, headings bold.
 */
package com.kunotes.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.kunotes.core.SpanKind
import com.kunotes.ui.theme.Dotline
import com.kunotes.ui.theme.DotlineColors
import com.kunotes.ui.theme.SpaceGrotesk
import com.kunotes.ui.theme.SpaceMono

@Immutable
class MarkdownStyles(
    val colors: DotlineColors,
) {
    /** Paragraph text. */
    val body = TextStyle(fontFamily = SpaceGrotesk, fontSize = 16.sp, lineHeight = 26.sp, color = colors.ink)

    /** Fenced code (Preview: inside a `surface` box). */
    val codeBlock = TextStyle(fontFamily = SpaceMono, fontSize = 13.sp, lineHeight = 20.sp, color = colors.ink)

    /** Source mode: the whole text. */
    val source = TextStyle(fontFamily = SpaceMono, fontSize = 13.sp, lineHeight = 24.sp, color = colors.ink)

    /** Size and line height of a heading [level]. */
    fun headingSize(level: Int): Pair<TextUnit, TextUnit> =
        when (level) {
            1 -> 30.sp to 36.sp
            2 -> 22.sp to 28.sp
            3 -> 18.sp to 26.sp
            else -> 16.sp to 26.sp
        }

    fun headingWeight(level: Int): FontWeight = if (level == 1) FontWeight.Bold else FontWeight.SemiBold

    /** A heading as a full text style (Preview). */
    fun heading(level: Int): TextStyle {
        val (size, height) = headingSize(level)
        return body.copy(fontSize = size, lineHeight = height, fontWeight = headingWeight(level))
    }

    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    val italic = SpanStyle(fontStyle = FontStyle.Italic)
    val strike = SpanStyle(textDecoration = TextDecoration.LineThrough)
    val inlineCode = SpanStyle(fontFamily = SpaceMono, fontSize = 14.sp, background = colors.surfaceRaised)
    val link = SpanStyle(color = colors.ink, textDecoration = TextDecoration.Underline)

    /** Markers (`#`, `**`) on the line being edited in Live mode (mockup `#6b6b6b`). */
    val liveMarker = SpanStyle(color = colors.lineStrong, fontWeight = FontWeight.Medium)

    /** Live style of a markdown part (heading size comes from the line, see the Live field). */
    fun live(kind: SpanKind): SpanStyle? =
        when (kind) {
            is SpanKind.Heading -> SpanStyle(fontSize = headingSize(kind.level).first, fontWeight = headingWeight(kind.level))
            SpanKind.Bold -> bold
            SpanKind.Italic -> italic
            SpanKind.BoldItalic -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            SpanKind.Strike -> strike
            SpanKind.Code -> inlineCode
            SpanKind.CodeBlock -> SpanStyle(fontFamily = SpaceMono, fontSize = 13.sp)
            SpanKind.LinkText -> link
            SpanKind.LinkUrl, SpanKind.Marker, SpanKind.Rule -> liveMarker
            SpanKind.ListMarker -> SpanStyle(color = colors.inkMuted)
            SpanKind.Quote -> SpanStyle(color = colors.inkMuted)
        }

    /** Source style of a markdown part: markers faint, headings bold, code muted. Nothing is hidden. */
    fun source(kind: SpanKind): SpanStyle? =
        when (kind) {
            SpanKind.Marker, SpanKind.ListMarker, SpanKind.Rule, SpanKind.LinkUrl -> SpanStyle(color = colors.inkFaint)
            is SpanKind.Heading, SpanKind.Bold -> bold
            SpanKind.BoldItalic -> bold
            SpanKind.Code, SpanKind.CodeBlock, SpanKind.Quote -> SpanStyle(color = colors.inkMuted)
            else -> null
        }
}

/** The [MarkdownStyles] for the current theme. */
@Composable
fun rememberMarkdownStyles(): MarkdownStyles {
    val colors = Dotline.colors
    return remember(colors) { MarkdownStyles(colors) }
}
