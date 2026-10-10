/**
 * Dotline type: Doto (dot-matrix display), Space Grotesk (reading), Space Mono (labels and data).
 *
 * Doto and Space Grotesk are variable fonts, so each weight is the same file with a different
 * `FontVariation.weight`. Never use Doto under 24 sp or for body text.
 */
package com.kunotes.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kunotes.R

/** One `Font` entry of a variable font file, pinned to [weight]. */
private fun variableFont(
    resId: Int,
    weight: FontWeight,
) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Doto =
    FontFamily(
        variableFont(R.font.doto, FontWeight.Normal),
        variableFont(R.font.doto, FontWeight.Bold),
        variableFont(R.font.doto, FontWeight.Black),
    )

val SpaceGrotesk =
    FontFamily(
        variableFont(R.font.space_grotesk, FontWeight.Normal),
        variableFont(R.font.space_grotesk, FontWeight.Medium),
        variableFont(R.font.space_grotesk, FontWeight.SemiBold),
        variableFont(R.font.space_grotesk, FontWeight.Bold),
    )

val SpaceMono =
    FontFamily(
        Font(R.font.space_mono_regular, FontWeight.Normal),
        Font(R.font.space_mono_bold, FontWeight.Bold),
    )

@Immutable
data class DotlineType(
    /** One hero number (sync "2 MIN AGO"). */
    val dotXl: TextStyle =
        TextStyle(fontFamily = Doto, fontSize = 72.sp, lineHeight = 72.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.02).em),
    /** Widget values. */
    val dotL: TextStyle = TextStyle(fontFamily = Doto, fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.Black),
    /** Screen titles in CAPS (`PERSONAL`, `SEARCH`). */
    val dotM: TextStyle = TextStyle(fontFamily = Doto, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    /** Sheet and dialog titles. */
    val title: TextStyle =
        TextStyle(
            fontFamily = SpaceGrotesk,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.01).em,
        ),
    /** Row titles, big buttons. */
    val headline: TextStyle = TextStyle(fontFamily = SpaceGrotesk, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    /** Paragraphs, buttons. */
    val body: TextStyle = TextStyle(fontFamily = SpaceGrotesk, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    /** Row subtitles, helper text. */
    val bodyS: TextStyle = TextStyle(fontFamily = SpaceGrotesk, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    /** Overlines, tab labels, chips. Always uppercase the string yourself. */
    val label: TextStyle =
        TextStyle(fontFamily = SpaceMono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.12.em),
    /** Paths, counts, status. */
    val data: TextStyle = TextStyle(fontFamily = SpaceMono, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
)
