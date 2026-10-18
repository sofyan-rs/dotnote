// Dotline design system for Jetpack Compose.
// Generated from design-system/tokens.json. Put the fonts in res/font first:
//   doto_regular.ttf, doto_bold.ttf, doto_black.ttf          (Google Fonts: Doto)
//   space_grotesk_variable.ttf or regular/medium/semibold/bold (Google Fonts: Space Grotesk)
//   space_mono_regular.ttf, space_mono_bold.ttf               (Google Fonts: Space Mono)
// Android res/font can't use .woff2, so download the TTF versions.

package com.dotnote.ui.theme // change to your package

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
// import com.dotnote.R

// ---------- Colour ----------

@Immutable
data class DotlineColors(
    val bg: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val line: Color,
    val lineStrong: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val onInk: Color,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val positive: Color,
    val focus: Color,
    val scrim: Color,
)

val DotlineDark = DotlineColors(
    bg = Color(0xFF000000),
    surface = Color(0xFF111111),
    surfaceRaised = Color(0xFF1C1C1C),
    line = Color(0xFF262626),
    lineStrong = Color(0xFF6B6B6B),
    ink = Color(0xFFFFFFFF),
    inkMuted = Color(0xFFA6A6A6),
    inkFaint = Color(0xFF808080),
    onInk = Color(0xFF000000),
    accent = Color(0xFFE5211C),
    onAccent = Color(0xFFFFFFFF),
    accentText = Color(0xFFFF5A52),
    positive = Color(0xFF4AD991),
    focus = Color(0xFFFFFFFF),
    scrim = Color(0xB8000000), // rgba(0,0,0,0.72)
)

val DotlineLight = DotlineColors(
    bg = Color(0xFFF2F2F2),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFE8E8E8),
    line = Color(0xFFDCDCDC),
    lineStrong = Color(0xFF7A7A7A),
    ink = Color(0xFF000000),
    inkMuted = Color(0xFF4D4D4D),
    inkFaint = Color(0xFF6B6B6B),
    onInk = Color(0xFFFFFFFF),
    accent = Color(0xFFD70015),
    onAccent = Color(0xFFFFFFFF),
    accentText = Color(0xFFD70015),
    positive = Color(0xFF0B6B3C),
    focus = Color(0xFF000000),
    scrim = Color(0x66000000), // rgba(0,0,0,0.4)
)

// ---------- Type ----------

// Replace the placeholders with your R.font ids.
val Doto = FontFamily.Default /* FontFamily(
    Font(R.font.doto_regular, FontWeight.Normal),
    Font(R.font.doto_bold, FontWeight.Bold),
    Font(R.font.doto_black, FontWeight.Black),
) */
val SpaceGrotesk = FontFamily.Default /* FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
) */
val SpaceMono = FontFamily.Monospace /* FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
) */

@Immutable
data class DotlineType(
    val dotXl: TextStyle = TextStyle(fontFamily = Doto, fontSize = 72.sp, lineHeight = 72.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.02).em),
    val dotL: TextStyle = TextStyle(fontFamily = Doto, fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.Black),
    val dotM: TextStyle = TextStyle(fontFamily = Doto, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    val title: TextStyle = TextStyle(fontFamily = SpaceGrotesk, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.01).em),
    val headline: TextStyle = TextStyle(fontFamily = SpaceGrotesk, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    val body: TextStyle = TextStyle(fontFamily = SpaceGrotesk, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    val bodyS: TextStyle = TextStyle(fontFamily = SpaceGrotesk, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    /** Always uppercase the string yourself. */
    val label: TextStyle = TextStyle(fontFamily = SpaceMono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.12.em),
    val data: TextStyle = TextStyle(fontFamily = SpaceMono, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
)

// ---------- Spacing, radius, size ----------

object DotlineSpace {
    val s1: Dp = 4.dp   // label ↔ value, dot-meter gaps
    val s2: Dp = 8.dp   // chips, icon ↔ label
    val s3: Dp = 12.dp  // between widgets, compact row padding
    val s4: Dp = 16.dp  // screen side margin, group padding
    val s5: Dp = 24.dp  // between sections
    val s6: Dp = 32.dp  // under a dot-m screen title
    val s7: Dp = 48.dp  // above a hero numeral
}

object DotlineRadius {
    val s = RoundedCornerShape(8.dp)    // segments, thumbnails
    val m = RoundedCornerShape(16.dp)   // list groups, inputs, cards
    val l = RoundedCornerShape(28.dp)   // widgets, dialogs
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val pill = RoundedCornerShape(percent = 50)
}

object DotlineSize {
    val touchTarget: Dp = 48.dp
    val dot: Dp = 4.dp
    val dotPitch: Dp = 8.dp
    val stroke: Dp = 1.5.dp
    val tabBarHeight: Dp = 72.dp
    val rowMin: Dp = 56.dp
    val icon: Dp = 24.dp
}

// ---------- Theme ----------

val LocalDotlineColors = staticCompositionLocalOf { DotlineDark }
val LocalDotlineType = staticCompositionLocalOf { DotlineType() }

object Dotline {
    val colors: DotlineColors @Composable get() = LocalDotlineColors.current
    val type: DotlineType @Composable get() = LocalDotlineType.current
    val space = DotlineSpace
    val radius = DotlineRadius
    val size = DotlineSize
}

@Composable
fun DotlineTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val c = if (dark) DotlineDark else DotlineLight
    val t = DotlineType()
    // Map onto Material 3 so stock components pick up the right colours.
    // Depth comes from surface steps: keep tonal/shadow elevation at 0.
    val scheme = if (dark) darkColorScheme(
        background = c.bg, onBackground = c.ink,
        surface = c.bg, onSurface = c.ink,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceRaised,
        surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.inkMuted,
        primary = c.ink, onPrimary = c.onInk,
        secondary = c.inkMuted, onSecondary = c.onInk,
        error = c.accent, onError = c.onAccent,
        outline = c.lineStrong, outlineVariant = c.line,
        scrim = c.scrim,
    ) else lightColorScheme(
        background = c.bg, onBackground = c.ink,
        surface = c.bg, onSurface = c.ink,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceRaised,
        surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.inkMuted,
        primary = c.ink, onPrimary = c.onInk,
        secondary = c.inkMuted, onSecondary = c.onInk,
        error = c.accent, onError = c.onAccent,
        outline = c.lineStrong, outlineVariant = c.line,
        scrim = c.scrim,
    )
    CompositionLocalProvider(LocalDotlineColors provides c, LocalDotlineType provides t) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(
                small = DotlineRadius.s,
                medium = DotlineRadius.m,
                large = DotlineRadius.l,
                extraLarge = DotlineRadius.l,
            ),
            content = content,
        )
    }
}
