package com.rr.numio.files.ui

import android.text.Html
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rr.numio.files.FilesViewModel
import com.rr.numio.files.Overlay
import com.rr.numio.files.data.TextFiles
import com.rr.numio.files.data.TextKind
import com.rr.numio.files.ui.theme.Numio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File

private enum class Mode(val label: String) { NICE("Formatted"), SOURCE("Source"), EDIT("Edit") }

private class Loaded(val text: String, val cut: Boolean, val size: Long)

/** TXT, Markdown, CSV, JSON, HTML and code files, shown inside Files. */
@Composable
fun TextViewerScreen(vm: FilesViewModel, open: Overlay.Text) {
    val file = File(open.path)
    val kind = TextFiles.kindOf(file.name) ?: TextKind.PLAIN
    var reload by remember { mutableStateOf(0) }
    val loaded by produceState<Loaded?>(null, open.path, reload) {
        value = withContext(Dispatchers.IO) {
            runCatching { TextFiles.read(file).let { (t, cut) -> Loaded(t, cut, file.length()) } }.getOrNull()
                ?: Loaded("", false, -1)
        }
    }
    val canEdit = !open.outside && loaded?.cut == false && (loaded?.size ?: 0) <= TextFiles.MAX_EDIT_BYTES && file.canWrite()
    val modes = buildList {
        if (kind != TextKind.PLAIN) add(Mode.NICE)
        add(Mode.SOURCE)
        if (canEdit) add(Mode.EDIT)
    }
    var mode by remember(open.path) { mutableStateOf(if (kind != TextKind.PLAIN) Mode.NICE else Mode.SOURCE) }
    var draft by remember(open.path, reload) { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        TitleHeader(file.name, onBack = vm::closeOverlay) {
            if (kind == TextKind.SVG && mode == Mode.NICE) {
                TextButton(onClick = { vm.saveSvgPng(open.path, open.outside) }) {
                    Text("Save PNG", color = Numio.AmberText, fontWeight = FontWeight.Bold)
                }
            }
            if (mode == Mode.EDIT && draft != null && draft != loaded?.text) {
                TextButton(onClick = {
                    val text = draft ?: return@TextButton
                    vm.saveText(open.path, text) {
                        reload++
                        mode = Mode.SOURCE
                    }
                }) { Text("Save", color = Numio.AmberText, fontWeight = FontWeight.Bold) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            modes.forEach { m ->
                val on = m == mode
                Text(
                    if (m == Mode.NICE && kind == TextKind.SVG) "Picture" else m.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (on) Numio.OnAmber else Numio.Text,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) Numio.Amber else Numio.Surface)
                        .clickable {
                            if (m == Mode.EDIT && draft == null) draft = loaded?.text
                            mode = m
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        val l = loaded
        when {
            l == null -> EmptyNote("Opening…", "")
            l.size < 0 -> EmptyNote("Can't read this file", "It may have been moved, or it isn't text.")
            else -> {
                if (l.cut) {
                    Text(
                        "Big file: showing the first 4 MB.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                when (mode) {
                    Mode.EDIT -> Editor(draft ?: l.text) { draft = it }
                    Mode.SOURCE -> Lines(l.text)
                    Mode.NICE -> when (kind) {
                        TextKind.MARKDOWN -> Markdown(l.text)
                        TextKind.CSV -> CsvTable(l.text)
                        TextKind.JSON -> Lines(prettyJson(l.text) ?: l.text)
                        TextKind.HTML -> Readable(l.text)
                        TextKind.SVG -> SvgPicture(open.path, l.text)
                        TextKind.PLAIN -> Lines(l.text)
                    }
                }
            }
        }
    }
}

private val mono = FontFamily.Monospace

/** Source view: line numbers on the left, long lines wrap. */
@Composable
private fun Lines(text: String) {
    val lines = remember(text) { text.split('\n') }
    val numberWidth = (lines.size.toString().length * 9 + 10).dp
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        itemsIndexed(lines) { i, line ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text(
                    "${i + 1}",
                    fontFamily = mono,
                    fontSize = 11.sp,
                    color = Numio.TextDim.copy(alpha = 0.7f),
                    modifier = Modifier.width(numberWidth).padding(top = 2.dp),
                )
                Text(line.trimEnd('\r'), fontFamily = mono, fontSize = 13.sp, color = Numio.Text, modifier = Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun Editor(text: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = text,
        onValueChange = onChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = mono, fontSize = 13.sp, color = Numio.Text),
        cursorBrush = SolidColor(Numio.Amber),
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Numio.Surface)
            .padding(14.dp),
    )
}

private fun prettyJson(text: String): String? = try {
    when (val v = JSONTokener(text.trim()).nextValue()) {
        is JSONObject -> v.toString(2)
        is JSONArray -> v.toString(2)
        else -> null
    }?.replace("\\/", "/")
} catch (e: Exception) {
    null
}

/** HTML as plain readable text. Nothing is loaded from the internet. */
@Composable
private fun Readable(html: String) {
    val text = remember(html) {
        Html.fromHtml(html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), ""), Html.FROM_HTML_MODE_COMPACT)
            .toString().trim()
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = Numio.Text,
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    )
}

/** CSV as a table: first row is the header; scroll both ways. */
@Composable
private fun CsvTable(text: String) {
    val rows = remember(text) { TextFiles.csv(text, maxRows = 500) }
    TableView(rows)
}

/** A table: first row is the header in amber; scrolls both ways. Also used for Excel sheets. */
@Composable
fun TableView(rows: List<List<String>>) {
    if (rows.isEmpty()) {
        EmptyNote("Empty", "Nothing in here.")
        return
    }
    val cols = rows.maxOf { it.size }.coerceAtLeast(1)
    // Measure the real text (with the phone's own font size), so short values like
    // dates never wrap; long notes are capped and wrap within their cell.
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val widths = remember(rows, density) {
        val style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
        (0 until cols).map { c ->
            val widest = rows.take(200).maxOf { r ->
                val t = r.getOrNull(c).orEmpty()
                if (t.isEmpty()) 0 else measurer.measure(t, style, softWrap = false, maxLines = 1).size.width
            }
            with(density) { widest.toDp() }.coerceIn(48.dp, 260.dp) + 21.dp
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState()),
    ) {
        Text(
            "${rows.size - 1} rows · $cols columns" + if (rows.size >= 500) " (first 500 shown)" else "",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 8.dp),
        )
        Column(Modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, Numio.Line, RoundedCornerShape(14.dp))) {
            rows.forEachIndexed { r, row ->
                val header = r == 0
                Row(Modifier.background(if (header) Numio.AmberSoft else if (r % 2 == 0) Numio.Surface else Color.Transparent)) {
                    (0 until cols).forEach { c ->
                        Text(
                            row.getOrNull(c).orEmpty(),
                            fontSize = 13.sp,
                            fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                            color = Numio.Text,
                            maxLines = 3,
                            modifier = Modifier.width(widths[c]).padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** A small Markdown reader: headings, lists, quotes, code, bold, italic, links. */
@Composable
private fun Markdown(text: String) {
    val blocks = remember(text) { mdBlocks(text) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        itemsIndexed(blocks) { _, b ->
            when (b) {
                is Md.Heading -> Text(
                    mdInline(b.text),
                    fontSize = when (b.level) { 1 -> 26.sp; 2 -> 21.sp; 3 -> 18.sp; else -> 16.sp },
                    fontWeight = FontWeight.Bold,
                    color = Numio.Text,
                    modifier = Modifier.padding(top = if (b.level <= 2) 18.dp else 12.dp, bottom = 6.dp),
                )
                is Md.Para -> Text(mdInline(b.text), style = MaterialTheme.typography.bodyLarge, color = Numio.Text, modifier = Modifier.padding(vertical = 5.dp))
                is Md.Item -> Row(Modifier.padding(start = (b.depth * 16).dp, top = 2.dp, bottom = 2.dp)) {
                    Text(b.mark, color = Numio.AmberText, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                    Text(mdInline(b.text), style = MaterialTheme.typography.bodyLarge, color = Numio.Text)
                }
                is Md.Quote -> Row(Modifier.padding(vertical = 6.dp)) {
                    Box(Modifier.width(3.dp).height(22.dp).background(Numio.Amber))
                    Text(mdInline(b.text), style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = Numio.TextDim, modifier = Modifier.padding(start = 12.dp))
                }
                is Md.Code -> Text(
                    b.text,
                    fontFamily = mono,
                    fontSize = 12.5.sp,
                    color = Numio.Text,
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Numio.Surface)
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                )
                Md.Rule -> Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(Numio.Line))
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

private sealed interface Md {
    data class Heading(val level: Int, val text: String) : Md
    data class Para(val text: String) : Md
    data class Item(val depth: Int, val mark: String, val text: String) : Md
    data class Quote(val text: String) : Md
    data class Code(val text: String) : Md
    data object Rule : Md
}

private fun mdBlocks(text: String): List<Md> {
    val out = ArrayList<Md>()
    val para = StringBuilder()
    fun flush() {
        if (para.isNotBlank()) out.add(Md.Para(para.toString().trim()))
        para.clear()
    }
    val lines = text.replace("\r", "").split('\n')
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val t = line.trim()
        when {
            t.startsWith("```") || t.startsWith("~~~") -> {
                flush()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```") && !lines[i].trim().startsWith("~~~")) {
                    code.append(lines[i]).append('\n')
                    i++
                }
                out.add(Md.Code(code.toString().trimEnd()))
            }
            t.isEmpty() -> flush()
            Regex("^#{1,6}\\s").containsMatchIn(t) -> {
                flush()
                val level = t.takeWhile { it == '#' }.length
                out.add(Md.Heading(level, t.drop(level).trim().trimEnd('#').trim()))
            }
            Regex("^(-{3,}|\\*{3,}|_{3,})$").matches(t) -> { flush(); out.add(Md.Rule) }
            t.startsWith(">") -> { flush(); out.add(Md.Quote(t.trimStart('>').trim())) }
            Regex("^[-*+]\\s").containsMatchIn(t) -> {
                flush()
                val depth = (line.length - line.trimStart().length) / 2
                val body = t.drop(2)
                val mark = when {
                    body.startsWith("[x] ", true) -> "☑"
                    body.startsWith("[ ] ") -> "☐"
                    else -> "•"
                }
                out.add(Md.Item(depth, mark, if (mark == "•") body else body.drop(4)))
            }
            Regex("^\\d+[.)]\\s").containsMatchIn(t) -> {
                flush()
                val num = t.takeWhile { it.isDigit() }
                out.add(Md.Item((line.length - line.trimStart().length) / 2, "$num.", t.substringAfter(' ').trim()))
            }
            t.startsWith("<") && t.endsWith(">") -> Unit // HTML tags in READMEs (badges, pictures) are skipped
            else -> para.append(t).append(' ')
        }
        i++
    }
    flush()
    return out
}

/** **bold**, *italic*, `code`, [links](…) and ![images](…) shown as their text. */
@Composable
private fun mdInline(s: String): AnnotatedString {
    val code = Numio.Surface
    val link = Numio.AmberText
    return remember(s, code, link) {
        buildAnnotatedString {
            val re = Regex("""!?\[([^\]]*)]\(([^)]*)\)|\*\*(.+?)\*\*|__(.+?)__|`([^`]+)`|\*(.+?)\*|_(.+?)_""")
            var last = 0
            for (m in re.findAll(s)) {
                append(s.substring(last, m.range.first))
                val g = m.groupValues
                when {
                    m.value.startsWith("![") -> if (g[1].isNotBlank()) withStyle(SpanStyle(color = link)) { append("[${g[1]}]") }
                    g[1].isNotEmpty() || m.value.startsWith("[") -> withStyle(SpanStyle(color = link, textDecoration = TextDecoration.Underline)) { append(g[1]) }
                    g[3].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[3]) }
                    g[4].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[4]) }
                    g[5].isNotEmpty() -> withStyle(SpanStyle(fontFamily = mono, background = code)) { append(" ${g[5]} ") }
                    g[6].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[6]) }
                    g[7].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[7]) }
                }
                last = m.range.last + 1
            }
            append(s.substring(last))
        }
    }
}

/** An SVG drawn as a picture, on a light checkerboard so transparent parts show. */
@Composable
private fun SvgPicture(path: String, text: String) {
    val bmp by produceState<android.graphics.Bitmap?>(null, path, text) {
        value = withContext(Dispatchers.IO) { runCatching { com.rr.numio.files.data.SvgFiles.render(File(path), 1600) }.getOrNull() }
    }
    val b = bmp
    if (b == null) {
        EmptyNote("Drawing…", "If this stays, the SVG may use features Files can't draw. Source shows its code.")
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        androidx.compose.foundation.Image(
            b.asImageBitmap(),
            contentDescription = "SVG picture",
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF6F4EE)),
        )
        Text(
            "${b.width} × ${b.height} preview",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
