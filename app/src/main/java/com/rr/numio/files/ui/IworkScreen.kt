package com.rr.numio.files.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rr.numio.files.FilesViewModel
import com.rr.numio.files.Overlay
import com.rr.numio.files.data.IworkPreview
import com.rr.numio.files.ui.theme.Numio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Apple Pages / Numbers / Keynote: shows the preview Apple saved inside the file. */
@Composable
fun IworkScreen(vm: FilesViewModel, open: Overlay.Iwork, onOpen: (String) -> Unit) {
    val name = File(open.path).name
    Column(Modifier.fillMaxSize()) {
        TitleHeader(name, onBack = vm::closeOverlay)
        when (val p = vm.iwork) {
            null -> EmptyNote("Opening…", "")
            is IworkPreview.Pdf -> {
                PdfPages(p.file, Modifier.weight(1f))
                Bar(
                    note = "Every page, from the PDF Apple saved inside.",
                    save = "Save as PDF",
                    onSave = vm::saveIworkPreview,
                    other = "Open in PDF app",
                    onOther = { onOpen(p.file.absolutePath) },
                )
            }
            is IworkPreview.Picture -> {
                val bmp by produceState<Bitmap?>(null, p.file) {
                    value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(p.file.path) }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    item {
                        bmp?.let {
                            Image(
                                it.asImageBitmap(),
                                contentDescription = "First page of $name",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White),
                            )
                        }
                    }
                }
                Bar(
                    note = "Newer Apple files only keep a picture of the first page. For every page, ask the sender to share it as PDF (Pages › Share › Export › PDF).",
                    save = "Save picture",
                    onSave = vm::saveIworkPreview,
                )
            }
            IworkPreview.Nothing -> EmptyNote(
                "No preview inside",
                "This file was saved without a preview, so phones can't show it. Ask the sender to share it as PDF (Pages › Share › Export › PDF).",
            )
        }
    }
}

@Composable
private fun Bar(note: String, save: String, onSave: () -> Unit, other: String? = null, onOther: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(note, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = Numio.Amber, contentColor = Numio.OnAmber),
                modifier = Modifier.weight(1f).height(48.dp),
            ) { Text(save, fontWeight = FontWeight.Bold, color = Numio.OnAmber) }
            if (other != null) {
                OutlinedButton(onClick = onOther, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(other, color = Numio.Text, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Each page drawn by Android's own PDF renderer, one at a time as you scroll. */
@Composable
private fun PdfPages(pdf: File, modifier: Modifier) {
    val count by produceState(0, pdf) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } }
            }.getOrDefault(0)
        }
    }
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items((0 until count).toList()) { i ->
            val page by produceState<Bitmap?>(null, pdf, i) {
                value = withContext(Dispatchers.IO) { runCatching { renderPage(pdf, i) }.getOrNull() }
            }
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .aspectRatio(page?.let { it.width.toFloat() / it.height } ?: 0.707f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White),
            ) {
                page?.let { Image(it.asImageBitmap(), contentDescription = "Page ${i + 1}", modifier = Modifier.fillMaxSize()) }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

private fun renderPage(pdf: File, index: Int): Bitmap =
    ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { r ->
            r.openPage(index).use { page ->
                val w = 1080
                val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(android.graphics.Color.WHITE)
                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }
