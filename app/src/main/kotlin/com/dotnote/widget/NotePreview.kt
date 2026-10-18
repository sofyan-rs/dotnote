/**
 * Turns a note's markdown into the plain lines the home-screen widget shows.
 *
 * A widget can't run the editor, so this is a small, forgiving cleanup: headings lose their
 * `#`, task boxes become ☐ / ☑, bullets become •, table rows become "cell · cell", and
 * inline markers (`**`, `*`, `` ` ``, `~~`, links, images) are stripped. It's for reading
 * at a glance, not a full renderer.
 */
package com.dotnote.widget

/** What kind of line it is, so the widget can style it. */
enum class PreviewLineKind { Heading, Task, DoneTask, Bullet, Text }

data class PreviewLine(
    val text: String,
    val kind: PreviewLineKind,
)

object NotePreview {
    /** Most lines a widget ever shows; the 4×4 size scrolls, so this only caps the work. */
    const val MAX_LINES = 60

    private val heading = Regex("""^#{1,6}\s+(.*)$""")
    private val task = Regex("""^[-*+]\s+\[([ xX])]\s+(.*)$""")
    private val bullet = Regex("""^[-*+]\s+(.*)$""")
    private val numbered = Regex("""^(\d+)[.)]\s+(.*)$""")
    private val image = Regex("""!\[([^\]]*)]\([^)]*\)""")
    private val link = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val tableSeparator = Regex("""^\|?[\s:|-]*-[\s:|-]*\|?$""")
    private val inlineMarkers = Regex("""\*\*|__|~~|`|(?<!\w)[*_](?=\S)|(?<=\S)[*_](?!\w)""")

    /**
     * The preview lines of [markdown]. A first heading that only repeats the note's [title]
     * (new notes start with `# Title`) is dropped, since the widget already shows the title.
     */
    fun lines(
        markdown: String,
        title: String,
    ): List<PreviewLine> {
        val result = mutableListOf<PreviewLine>()
        var inCodeBlock = false
        var lastWasBlank = true // no blank line at the very top
        for (raw in markdown.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                continue
            }
            if (line.isEmpty() || line == "---" || line == "***") {
                // Keep at most one blank line in a row, as a gap between paragraphs.
                if (!lastWasBlank && result.isNotEmpty()) result += PreviewLine("", PreviewLineKind.Text)
                lastWasBlank = true
                continue
            }
            if (!inCodeBlock && tableSeparator.matches(line)) continue // the |---|---| under a table header
            val previewLine = if (inCodeBlock) PreviewLine(line, PreviewLineKind.Text) else parse(line)
            if (previewLine.text.isBlank()) continue // e.g. a table row with only empty cells
            val isTitleRepeat =
                result.isEmpty() && previewLine.kind == PreviewLineKind.Heading && previewLine.text == title
            if (!isTitleRepeat) {
                result += previewLine
                lastWasBlank = false
            }
            if (result.size >= MAX_LINES) break
        }
        return result.dropLastWhile { it.text.isEmpty() }
    }

    private fun parse(line: String): PreviewLine {
        heading.matchEntire(line)?.let { return PreviewLine(clean(it.groupValues[1]), PreviewLineKind.Heading) }
        task.matchEntire(line)?.let {
            val done = it.groupValues[1] != " "
            val kind = if (done) PreviewLineKind.DoneTask else PreviewLineKind.Task
            return PreviewLine((if (done) "☑ " else "☐ ") + clean(it.groupValues[2]), kind)
        }
        bullet.matchEntire(line)?.let { return PreviewLine("• " + clean(it.groupValues[1]), PreviewLineKind.Bullet) }
        numbered.matchEntire(line)?.let {
            return PreviewLine("${it.groupValues[1]}. " + clean(it.groupValues[2]), PreviewLineKind.Bullet)
        }
        // A quote ("> text") reads fine without its marker.
        return PreviewLine(clean(line.removePrefix(">").trim()), PreviewLineKind.Text)
    }

    /** A table row (`| a | b |`) as "a · b"; other text unchanged. */
    private fun tableCells(text: String): String {
        if (!text.startsWith("|")) return text
        return text
            .trim('|')
            .split('|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
    }

    /** Removes inline markdown: `![alt](src)` → `alt`, `[text](url)` → `text`, and `**`, `*`, `` ` ``, `~~`. */
    private fun clean(text: String): String =
        tableCells(text)
            .replace(image) { it.groupValues[1].ifBlank { "image" } }
            .replace(link) { it.groupValues[1] }
            .replace(inlineMarkers, "")
            .trim()
}
