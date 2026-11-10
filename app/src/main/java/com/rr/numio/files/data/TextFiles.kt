package com.rr.numio.files.data

import java.io.File

/** How Files shows a text file. */
enum class TextKind { PLAIN, MARKDOWN, CSV, JSON, HTML, SVG }

/** Reading text files for the built-in viewer. Pure Kotlin, so it is easy to test. */
object TextFiles {
    /** Biggest file the viewer reads (the rest is cut off with a note). */
    const val MAX_BYTES = 4L * 1024 * 1024
    /** Biggest file that can be edited and saved. */
    const val MAX_EDIT_BYTES = 1L * 1024 * 1024

    private val plain = setOf(
        "txt", "log", "ini", "cfg", "conf", "properties", "yaml", "yml", "toml", "xml", "srt", "vtt",
        "kt", "kts", "java", "gradle", "py", "js", "ts", "css", "sh", "c", "h", "cpp", "go", "rs", "swift",
        "sql", "tsv", "env", "gitignore", "nfo", "rtf", "tex", "vcf", "ics",
    )

    fun kindOf(name: String): TextKind? = when (extOf(name)) {
        "md", "markdown" -> TextKind.MARKDOWN
        "csv" -> TextKind.CSV
        "json", "geojson" -> TextKind.JSON
        "html", "htm", "xhtml" -> TextKind.HTML
        "svg" -> TextKind.SVG
        in plain -> TextKind.PLAIN
        else -> if (name.equals("README", true) || name.equals("LICENSE", true)) TextKind.PLAIN else null
    }

    fun isText(name: String) = kindOf(name) != null

    /** Text of the file as UTF-8 (a byte-order mark is dropped); true if it was cut off. */
    fun read(f: File): Pair<String, Boolean> {
        val cut = f.length() > MAX_BYTES
        val bytes = f.inputStream().use { input ->
            val buf = ByteArray(minOf(f.length(), MAX_BYTES).toInt())
            var off = 0
            while (off < buf.size) {
                val n = input.read(buf, off, buf.size - off)
                if (n < 0) break
                off += n
            }
            buf.copyOf(off)
        }
        val text = String(bytes, Charsets.UTF_8).removePrefix("﻿")
        return text to cut
    }

    /** Splits CSV into rows and cells: quotes, "" inside quotes, and ; or tab files too. */
    fun csv(text: String, maxRows: Int = 1000): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val sep = listOf(',', ';', '\t').maxByOrNull { c -> firstLine.count { it == c } } ?: ','
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length && rows.size < maxRows) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(c)
            } else when (c) {
                '"' -> quoted = true
                sep -> { row.add(cell.toString()); cell.clear() }
                '\r' -> Unit
                '\n' -> {
                    row.add(cell.toString()); cell.clear()
                    rows.add(row); row = ArrayList()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows
    }
}
