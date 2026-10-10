/**
 * A markdown table in Preview (SPEC 3.3, mockup Editor-Preview): a bordered r16 box that scrolls
 * sideways when the table is wider than the screen. Cells have 10/12 dp padding; rows are
 * separated by 1 dp `line`; header cells are medium weight; the first column is `ink`, the
 * others `inkMuted`.
 *
 * Columns line up: a small custom [Layout] makes each column as wide as its widest cell (up to a
 * limit, then the text wraps) and each row as tall as its tallest cell.
 */
package com.kunotes.ui.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.kunotes.ui.theme.Dotline
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow

/** Widest a column gets before its text wraps. */
private val MaxColumnWidth = 240.dp

/** Draws [table] (a commonmark `TableBlock`). */
@Composable
internal fun PreviewTable(
    table: TableBlock,
    styles: MarkdownStyles,
    modifier: Modifier = Modifier,
) {
    val colors = Dotline.colors
    // Rows of cells, header first; `isHeader` per row.
    val rows = mutableListOf<Pair<Boolean, List<TableCell>>>()
    for (section in PreviewMarkdown.children(table)) {
        for (row in PreviewMarkdown.children(section)) {
            if (row is TableRow) rows += (section is TableHead) to PreviewMarkdown.children(row).filterIsInstance<TableCell>()
        }
    }
    val columns = rows.maxOfOrNull { it.second.size } ?: return

    Box(
        modifier
            .clip(Dotline.radius.m)
            .border(1.dp, colors.line, Dotline.radius.m)
            .horizontalScroll(rememberScrollState()),
    ) {
        Layout(
            content = {
                rows.forEachIndexed { rowIndex, (isHeader, cells) ->
                    for (column in 0 until columns) {
                        val cell = cells.getOrNull(column)
                        val style =
                            styles.body.copy(
                                fontSize = Dotline.type.body.fontSize,
                                fontWeight = if (isHeader) FontWeight.Medium else FontWeight.Normal,
                                color = if (isHeader || column == 0) colors.ink else colors.inkMuted,
                            )
                        Text(
                            text =
                                if (cell != null) {
                                    PreviewMarkdown.inline(cell, styles)
                                } else {
                                    androidx.compose.ui.text
                                        .AnnotatedString("")
                                },
                            style = style,
                            modifier =
                                Modifier
                                    // A 1 dp line above every row but the first.
                                    .drawBehind {
                                        if (rowIndex > 0) drawLine(colors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                                    }.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            },
        ) { measurables, _ ->
            val rowCount = rows.size
            val maxWidth = MaxColumnWidth.roundToPx()
            // Column width = widest cell's natural width (capped).
            val widths =
                IntArray(columns) { column ->
                    (0 until rowCount)
                        .maxOf { row ->
                            measurables[row * columns + column].maxIntrinsicWidth(Constraints.Infinity)
                        }.coerceAtMost(maxWidth)
                }
            // Row height = tallest cell at its column's width.
            val heights =
                IntArray(rowCount) { row ->
                    (0 until columns).maxOf { column -> measurables[row * columns + column].minIntrinsicHeight(widths[column]) }
                }
            val placeables =
                measurables.mapIndexed { index, measurable ->
                    measurable.measure(Constraints.fixed(widths[index % columns], heights[index / columns]))
                }
            layout(widths.sum(), heights.sum()) {
                var y = 0
                for (row in 0 until rowCount) {
                    var x = 0
                    for (column in 0 until columns) {
                        placeables[row * columns + column].place(x, y)
                        x += widths[column]
                    }
                    y += heights[row]
                }
            }
        }
    }
}
