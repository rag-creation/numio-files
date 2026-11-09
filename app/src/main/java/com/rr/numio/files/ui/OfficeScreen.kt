package com.rr.numio.files.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rr.numio.files.FilesViewModel
import com.rr.numio.files.Overlay
import com.rr.numio.files.data.DocBlock
import com.rr.numio.files.data.OfficeFiles
import com.rr.numio.files.data.Sheet
import com.rr.numio.files.ui.theme.Numio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface Office {
    data object Loading : Office
    data object Failed : Office
    data class Doc(val blocks: List<DocBlock>) : Office
    data class Book(val sheets: List<Sheet>) : Office
}

/** Word and Excel files, read inside Files. A reader: layout, fonts and pictures are left out. */
@Composable
fun OfficeScreen(vm: FilesViewModel, open: Overlay.Office) {
    val file = File(open.path)
    val content by produceState<Office>(Office.Loading, open.path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (OfficeFiles.isDocx(file.name)) Office.Doc(OfficeFiles.readDocx(file))
                else Office.Book(OfficeFiles.readXlsx(file))
            }.getOrDefault(Office.Failed)
        }
    }
    Column(Modifier.fillMaxSize()) {
        TitleHeader(file.name, onBack = vm::closeOverlay)
        when (val c = content) {
            Office.Loading -> EmptyNote("Opening…", "")
            Office.Failed -> EmptyNote("Can't read this file", "It may be damaged or password-protected. Long-press › Open with to try another app.")
            is Office.Doc -> DocView(c.blocks)
            is Office.Book -> BookView(c.sheets)
        }
    }
}

@Composable
private fun DocView(blocks: List<DocBlock>) {
    if (blocks.isEmpty()) {
        EmptyNote("No text found", "This document may only have pictures. Long-press › Open with to see it in another app.")
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Text(
                "Text view: layout, fonts and pictures are left out.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        items(blocks) { b ->
            when (b) {
                is DocBlock.Para -> {
                    val text = buildAnnotatedString {
                        b.spans.forEach { s ->
                            withStyle(
                                SpanStyle(
                                    fontWeight = if (s.bold) FontWeight.Bold else null,
                                    fontStyle = if (s.italic) FontStyle.Italic else null,
                                    textDecoration = if (s.underline) TextDecoration.Underline else null,
                                ),
                            ) { append(s.text) }
                        }
                    }
                    when {
                        text.isBlank() -> Spacer(Modifier.height(10.dp))
                        b.heading > 0 -> Text(
                            text,
                            fontSize = when (b.heading) { 1 -> 24.sp; 2 -> 20.sp; 3 -> 18.sp; else -> 16.sp },
                            fontWeight = FontWeight.Bold,
                            color = Numio.Text,
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                        )
                        b.bullet -> Row(Modifier.padding(vertical = 2.dp)) {
                            Text("•", color = Numio.AmberText, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                            Text(text, style = MaterialTheme.typography.bodyLarge, color = Numio.Text)
                        }
                        else -> Text(text, style = MaterialTheme.typography.bodyLarge, color = Numio.Text, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                is DocBlock.Table -> Column(
                    Modifier
                        .padding(vertical = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Numio.Line, RoundedCornerShape(12.dp))
                        .horizontalScroll(rememberScrollState()),
                ) {
                    b.rows.forEachIndexed { r, row ->
                        Row(Modifier.background(if (r % 2 == 0) Numio.Surface else androidx.compose.ui.graphics.Color.Transparent)) {
                            row.forEach { cell ->
                                Text(
                                    cell,
                                    fontSize = 14.sp,
                                    color = Numio.Text,
                                    modifier = Modifier.width(150.dp).padding(horizontal = 10.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun BookView(sheets: List<Sheet>) {
    if (sheets.isEmpty()) {
        EmptyNote("No sheets found", "")
        return
    }
    var pick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        if (sheets.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sheets.forEachIndexed { i, s ->
                    val on = i == pick
                    Text(
                        s.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (on) Numio.OnAmber else Numio.Text,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) Numio.Amber else Numio.Surface)
                            .clickable { pick = i }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        TableView(sheets[pick.coerceIn(0, sheets.size - 1)].rows)
    }
}
