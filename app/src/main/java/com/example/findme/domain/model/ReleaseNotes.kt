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
}
