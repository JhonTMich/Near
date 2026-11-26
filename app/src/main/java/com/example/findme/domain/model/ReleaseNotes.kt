package com.example.findme.domain.model

/** Turns a release's Markdown notes into short plain text for the update dialog. */
object ReleaseNotes {
    const val MAX_CHARS = 1200
    private val generic = Regex("""^Actualización de Near [0-9.]+\.?$""")
    private val link = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val emphasis = Regex("""\*\*|__|`""")
    private val bullet = Regex("""^\s*[-*+]\s+""")
    private val heading = Regex("""^#{1,6}\s+""")
    private val reference = Regex("""\s*\((?:[\w.-]+/[\w.-]+)?#\d+\)""")

    /** Empty when the notes only repeat the generic line, so the dialog does not show them. */
    fun plain(markdown: String?): String {
        val text = markdown.orEmpty().replace("\r\n", "\n").lineSequence()
            .map { line ->
                line.replace(link, "$1").replace(reference, "").replace(emphasis, "")
                    .replace(heading, "").replace(bullet, "• ").trimEnd()
            }
            .joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
        if (generic.matches(text)) return ""
        if (text.length <= MAX_CHARS) return text
        return text.take(MAX_CHARS).substringBeforeLast('\n').trimEnd() + "\n…"
    }

    /** One new thing: a short [headline] to scan, and the rest of the note, shown on demand. */
    data class Item(val headline: String, val detail: String?)

    private val clauseEnd = Regex(""":\s|\.\s|,\s""")
    private val trailingJoin = Regex("""\s+(?:y|e|o|u|que|con|para)$""")
    private val leadingJoin = Regex("""^(?:y|e|o|u)\s+""")

    /**
     * The notes from [plain] as items: one per bullet, or per paragraph when there are no bullets. A title
     * line («Near 1.8.4») and the cut mark are left out, since the dialog already names the version.
     */
    fun items(plain: String): List<Item> {
        val blocks = mutableListOf<String>()
        plain.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.isEmpty() || line == "…" -> blocks.add("")
                line.startsWith("• ") -> blocks.add(line.removePrefix("• "))
                blocks.isNotEmpty() && blocks.last().isNotEmpty() -> blocks[blocks.lastIndex] = blocks.last() + " " + line
                else -> blocks.add(line)
            }
        }
        return blocks.map { it.trim() }.filter { it.isNotEmpty() && !isTitle(it) }.map(::item)
    }

    /** «Tu lugar se actualiza al momento y, al guardar…» → «Tu lugar se actualiza al momento» + the rest. */
    internal fun item(text: String): Item {
        val cut = clauseEnd.find(text)?.takeIf { it.range.first >= 12 }
            ?: return Item(text.removeSuffix("."), null)
        val headline = text.substring(0, cut.range.first).replace(trailingJoin, "").trim()
        val detail = text.substring(cut.range.last + 1).trim().removeSuffix(".").replace(leadingJoin, "")
            .replaceFirstChar { it.titlecase() }.takeIf { it.isNotEmpty() }
        return Item(headline, detail)
    }

    private fun isTitle(text: String) = text.length <= 40 && !text.endsWith(".") && text.split(' ').size <= 4 &&
        text.any { it.isDigit() }
}
