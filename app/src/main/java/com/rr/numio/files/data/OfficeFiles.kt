package com.rr.numio.files.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/** A run of text with its look, inside a paragraph. */
data class DocSpan(val text: String, val bold: Boolean, val italic: Boolean, val underline: Boolean)

sealed interface DocBlock {
    /** [heading] 0 = normal text, 1–6 = heading level; [bullet] for list items. */
    data class Para(val spans: List<DocSpan>, val heading: Int, val bullet: Boolean) : DocBlock
    data class Table(val rows: List<List<String>>) : DocBlock
}

data class Sheet(val name: String, val rows: List<List<String>>)

/**
 * Word (.docx) and Excel (.xlsx) files are ZIPs of XML. Files reads the text and
 * tables straight from that XML with Android's own parser: offline, no library.
 * A reader, not an editor; page layout, fonts and pictures are left out.
 */
object OfficeFiles {
    fun isDocx(name: String) = extOf(name) == "docx"
    fun isXlsx(name: String) = extOf(name) == "xlsx"
    fun isOffice(name: String) = isDocx(name) || isXlsx(name)

    private fun parser(input: InputStream): XmlPullParser =
        Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, "UTF-8")
        }

    // Word

    fun readDocx(file: File, maxBlocks: Int = 3000): List<DocBlock> = ZipFile(file).use { zf ->
        val entry = zf.getEntry("word/document.xml") ?: return emptyList()
        zf.getInputStream(entry).use { input -> parseDocument(parser(input), maxBlocks) }
    }

    private fun parseDocument(p: XmlPullParser, maxBlocks: Int): List<DocBlock> {
        val out = ArrayList<DocBlock>()
        // Paragraph state
        var spans = ArrayList<DocSpan>()
        var heading = 0
        var bullet = false
        // Run state
        var bold = false
        var italic = false
        var underline = false
        var inText = false
        val text = StringBuilder()
        // Tables (cells collect their paragraphs' text)
        var tableDepth = 0
        var rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()

        fun flushRun() {
            if (text.isNotEmpty()) {
                spans.add(DocSpan(text.toString(), bold, italic, underline))
                text.clear()
            }
        }

        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT && out.size < maxBlocks) {
            val tag = p.name
            when (ev) {
                XmlPullParser.START_TAG -> when (tag) {
                    "w:p" -> { spans = ArrayList(); heading = 0; bullet = false }
                    "w:pStyle" -> {
                        val v = p.getAttributeValue(null, "w:val").orEmpty()
                        heading = when {
                            v.equals("Title", true) -> 1
                            v.startsWith("Heading", true) -> v.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 6) ?: 2
                            v.equals("Subtitle", true) -> 3
                            else -> heading
                        }
                        if (v.contains("List", true)) bullet = true
                    }
                    "w:numPr" -> bullet = true
                    "w:r" -> { bold = false; italic = false; underline = false }
                    "w:b" -> bold = p.getAttributeValue(null, "w:val").let { it == null || it == "1" || it == "true" }
                    "w:i" -> italic = p.getAttributeValue(null, "w:val").let { it == null || it == "1" || it == "true" }
                    "w:u" -> underline = p.getAttributeValue(null, "w:val").let { it != null && it != "none" }
                    "w:t" -> inText = true
                    "w:tab" -> text.append('\t')
                    "w:br", "w:cr" -> text.append('\n')
                    "w:tbl" -> { if (tableDepth == 0) rows = ArrayList(); tableDepth++ }
                    "w:tr" -> if (tableDepth == 1) row = ArrayList()
                    "w:tc" -> if (tableDepth == 1) cell.clear()
                }
                XmlPullParser.TEXT -> if (inText) text.append(p.text)
                XmlPullParser.END_TAG -> when (tag) {
                    "w:t" -> inText = false
                    "w:r" -> flushRun()
                    "w:p" -> {
                        flushRun()
                        if (tableDepth > 0) {
                            val t = spans.joinToString("") { it.text }.trim()
                            if (t.isNotEmpty()) { if (cell.isNotEmpty()) cell.append('\n'); cell.append(t) }
                        } else {
                            out.add(DocBlock.Para(spans, heading, bullet))
                        }
                    }
                    "w:tc" -> if (tableDepth == 1) row.add(cell.toString())
                    "w:tr" -> if (tableDepth == 1) rows.add(row)
                    "w:tbl" -> { tableDepth--; if (tableDepth == 0) out.add(DocBlock.Table(rows)) }
                }
            }
            ev = p.next()
        }
        // Drop runs of empty paragraphs (Word uses them for spacing)
        return out.filterIndexed { i, b ->
            !(b is DocBlock.Para && b.spans.all { it.text.isBlank() } && i > 0 &&
                out[i - 1].let { it is DocBlock.Para && it.spans.all { s -> s.text.isBlank() } })
        }
    }

    // Excel

    fun readXlsx(file: File, maxRows: Int = 500, maxCols: Int = 50): List<Sheet> = ZipFile(file).use { zf ->
        fun open(name: String) = zf.getEntry(name)?.let { zf.getInputStream(it) }

        val shared = ArrayList<String>()
        open("xl/sharedStrings.xml")?.use { input ->
            val p = parser(input)
            val sb = StringBuilder()
            var inT = false
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> when (p.name) { "si" -> sb.clear(); "t" -> inT = true }
                    XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                    XmlPullParser.END_TAG -> when (p.name) { "t" -> inT = false; "si" -> shared.add(sb.toString()) }
                }
                ev = p.next()
            }
        }

        // Sheet names and where each sheet's XML is
        val rels = HashMap<String, String>()
        open("xl/_rels/workbook.xml.rels")?.use { input ->
            val p = parser(input)
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && p.name == "Relationship") {
                    val id = p.getAttributeValue(null, "Id")
                    val target = p.getAttributeValue(null, "Target")
                    if (id != null && target != null) rels[id] = target.removePrefix("/xl/").removePrefix("/")
                }
                ev = p.next()
            }
        }
        val sheets = ArrayList<Pair<String, String>>()
        open("xl/workbook.xml")?.use { input ->
            val p = parser(input)
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && p.name == "sheet") {
                    val name = p.getAttributeValue(null, "name") ?: "Sheet"
                    val rid = p.getAttributeValue(null, "r:id")
                    val target = rels[rid] ?: "worksheets/sheet${sheets.size + 1}.xml"
                    sheets.add(name to "xl/" + target.removePrefix("xl/"))
                }
                ev = p.next()
            }
        }

        sheets.mapNotNull { (name, path) ->
            open(path)?.use { input -> Sheet(name, readSheet(parser(input), shared, maxRows, maxCols)) }
        }
    }

    /** "C12" → column index 2. */
    fun columnOf(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return n - 1
    }

    private fun readSheet(p: XmlPullParser, shared: List<String>, maxRows: Int, maxCols: Int): List<List<String>> {
        val rows = ArrayList<MutableList<String>>()
        var row: MutableList<String>? = null
        var col = 0
        var type: String? = null
        val value = StringBuilder()
        var inValue = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT && rows.size <= maxRows) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> {
                        // Keep blank rows in between, so row numbers match Excel
                        val r = p.getAttributeValue(null, "r")?.toIntOrNull()
                        if (r != null) while (rows.size < r - 1 && rows.size < maxRows) rows.add(ArrayList())
                        row = ArrayList()
                        col = 0
                    }
                    "c" -> {
                        type = p.getAttributeValue(null, "t")
                        p.getAttributeValue(null, "r")?.let { col = columnOf(it) }
                        value.clear()
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) value.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val r = row
                        if (r != null && col < maxCols) {
                            val v = value.toString()
                            val shown = when (type) {
                                "s" -> v.toIntOrNull()?.let { shared.getOrNull(it) } ?: v
                                "b" -> if (v == "1") "TRUE" else "FALSE"
                                else -> v.toDoubleOrNull()?.let { d ->
                                    if (d == Math.floor(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else v
                                } ?: v
                            }
                            while (r.size < col) r.add("")
                            r.add(shown)
                        }
                        col++
                    }
                    "row" -> row?.let { rows.add(it) }
                }
            }
            ev = p.next()
        }
        // Trim empty rows at the end
        while (rows.isNotEmpty() && rows.last().all { it.isBlank() }) rows.removeAt(rows.size - 1)
        return rows
    }
}
