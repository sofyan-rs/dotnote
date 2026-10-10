/**
 * Dotline line icons: 24 dp, 1.5 dp stroke, square caps, mitred joins (design-system README).
 *
 * The path data is copied from the screen mockups in docs/ui/design/screens/ (circles and
 * rectangles turned into paths). Draw them with `Icon(DotlineIcons.Search, contentDescription)`;
 * the colour comes from `tint` (usually `Dotline.colors.ink` or `inkFaint`).
 *
 * To add an icon: draw it on a 24×24 grid in the same style and add one line here.
 */
package com.kunotes.ui.dotline

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object DotlineIcons {
    /** Back arrow (top bars). */
    val Back: ImageVector by lazy { lineIcon("Back", "M15 6l-6 6 6 6") }

    /** Show password. */
    val Eye: ImageVector by lazy { lineIcon("Eye", "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z") }

    /** Hide password (eye with a slash; not in the mockups, same style). */
    val EyeOff: ImageVector by lazy {
        lineIcon("EyeOff", "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0zM4 4l16 16")
    }

    val Check: ImageVector by lazy { lineIcon("Check", "M5 12l5 5 9-10") }

    /** ⋮ actions. */
    val More: ImageVector by lazy {
        lineIcon(
            "More",
            "M10.5 6a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0zM10.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0zM10.5 18a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0z",
        )
    }

    val Edit: ImageVector by lazy { lineIcon("Edit", "M4 20h4L19 9l-4-4L4 16z") }

    val Code: ImageVector by lazy { lineIcon("Code", "M8 8l-4 4 4 4M16 8l4 4-4 4") }

    val Link: ImageVector by lazy { lineIcon("Link", "M10 14l4-4M9 7l2-2a4 4 0 0 1 6 6l-2 2M15 17l-2 2a4 4 0 0 1-6-6l2-2") }

    val Image: ImageVector by lazy { lineIcon("Image", "M3 5h18v14h-18zM3 16l5-5 4 4 3-3 6 6") }

    val Checklist: ImageVector by lazy { lineIcon("Checklist", "M4 7l2 2 3-3M12 7h8M12 17h8M4 14h5v5h-5z") }

    val BulletList: ImageVector by lazy { lineIcon("BulletList", "M9 6h11M9 12h11M9 18h11M4 6h1M4 12h1M4 18h1") }

    val Quote: ImageVector by lazy { lineIcon("Quote", "M5 11h4v6H5zM5 11c0-3 1-5 4-6M14 11h4v6h-4zM14 11c0-3 1-5 4-6") }

    val Table: ImageVector by lazy { lineIcon("Table", "M3 5h18v14h-18zM3 10h18M9 5v14") }

    /** Move to folder. */
    val MoveTo: ImageVector by lazy { lineIcon("MoveTo", "M3 6h7l2 2h9v11H3zM9 13h6M12 10l3 3-3 3") }

    val Pin: ImageVector by lazy { lineIcon("Pin", "M12 16v5M7 4h10M9 4v6l-3 6h12l-3-6V4") }

    val Copy: ImageVector by lazy { lineIcon("Copy", "M8 8h12v12h-12zM16 8V4H4v12h4") }

    val Share: ImageVector by lazy { lineIcon("Share", "M12 4v11M7 9l5-5 5 5M4 14v6h16v-6") }

    val Lock: ImageVector by lazy { lineIcon("Lock", "M5 10h14v10h-14zM8 10V7a4 4 0 0 1 8 0v3") }

    val Trash: ImageVector by lazy { lineIcon("Trash", "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13") }

    val NumberedList: ImageVector by lazy { lineIcon("NumberedList", "M10 6h10M10 12h10M10 18h10M4 5h2v4M4 9h3") }

    /** Horizontal rule. */
    val Rule: ImageVector by lazy { lineIcon("Rule", "M4 12h16") }

    val ChevronDown: ImageVector by lazy { lineIcon("ChevronDown", "M6 9l6 6 6-6") }

    val ChevronRight: ImageVector by lazy { lineIcon("ChevronRight", "M9 6l6 6-6 6") }

    val Plus: ImageVector by lazy { lineIcon("Plus", "M12 5v14M5 12h14") }

    val Folder: ImageVector by lazy { lineIcon("Folder", "M3 6h7l2 2h9v11H3z") }

    val Search: ImageVector by lazy { lineIcon("Search", "M5 11a6 6 0 1 0 12 0a6 6 0 1 0 -12 0zM16 16l4 4") }

    /** SYNC tab. */
    val GitBranch: ImageVector by lazy {
        lineIcon(
            "GitBranch",
            "M4 5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0zM4 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0zM16 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0zM6 7v10M18 9c0 5-6 4-12 8",
        )
    }

    /** VAULTS tab. */
    val Sliders: ImageVector by lazy {
        lineIcon("Sliders", "M4 7h10M18 7h2M4 17h4M12 17h8M14 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0zM8 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z")
    }

    val NewNote: ImageVector by lazy { lineIcon("NewNote", "M6 3h8l4 4v14H6zM12 10v7M8.5 13.5h7") }

    val NewFolder: ImageVector by lazy { lineIcon("NewFolder", "M3 6h7l2 2h9v11H3zM12 11v5M9.5 13.5h5") }

    /** Sync now. */
    val Sync: ImageVector by lazy { lineIcon("Sync", "M4 12a8 8 0 0 1 14-5l2 2M20 4v5h-5M20 12a8 8 0 0 1-14 5l-2-2M4 20v-5h5") }

    /** A note file. */
    val Note: ImageVector by lazy { lineIcon("Note", "M6 3h8l4 4v14H6zM14 3v4h4") }

    val Close: ImageVector by lazy { lineIcon("Close", "M6 6l12 12M18 6L6 18") }

    val ArrowRight: ImageVector by lazy { lineIcon("ArrowRight", "M5 12h14M13 6l6 6-6 6") }

    /** Warning card. */
    val Warning: ImageVector by lazy { lineIcon("Warning", "M12 3l10 18H2zM12 10v5M12 18v.5") }

    /** A vault. */
    val Vault: ImageVector by lazy { lineIcon("Vault", "M3 4h18v16h-18zM3 9h18") }

    /** Remove a vault. */
    val RemoveVault: ImageVector by lazy { lineIcon("RemoveVault", "M5 12h14M3 4h18v16h-18z") }
}

/**
 * Builds a stroked 24×24 icon from SVG path data. The stroke is black here; `Icon` tints it
 * with the colour you pass. `by lazy` above means each icon is built once, on first use.
 */
private fun lineIcon(
    name: String,
    pathData: String,
): ImageVector =
    ImageVector
        .Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = addPathNodes(pathData),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Square,
            strokeLineJoin = StrokeJoin.Miter,
        ).build()
