package com.rr.numio.files.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rr.numio.files.FilesViewModel
import com.rr.numio.files.data.Category
import com.rr.numio.files.data.HomeStats
import com.rr.numio.files.data.formatSize
import com.rr.numio.files.ui.theme.Numio

@Composable
fun HomeScreen(vm: FilesViewModel, onOpen: (String) -> Unit) {
    val stats = vm.home
    val selecting = vm.selected.isNotEmpty()
    Column {
        if (selecting) {
            SelectionHeader(vm.selected.size, vm::clearSelection, { vm.selectAll(stats?.recent.orEmpty().map { it.path }) })
        } else {
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Wordmark()
                Spacer(Modifier.weight(1f))
                IconBtn(FIcons.Search, "Search files", onClick = vm::openSearch)
            }
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Text(
                    "Files",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp, letterSpacing = (-1).sp),
                    modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 18.dp),
                )
            }
            item { StorageCard(stats, onCleanUp = { vm.openCategory(Category.TRASH) }) }
            item { CategoryGrid(stats, vm::openCategory) }
            item {
                Text(
                    "Recent",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 4.dp),
                )
            }
            if (stats != null && stats.recent.isEmpty()) {
                item { Text("Nothing new yet", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 20.dp, top = 6.dp)) }
            }
            items(stats?.recent.orEmpty(), key = { it.path }) { f ->
                FileRow(
                    item = f,
                    meta = "${f.path.substringBeforeLast('/').substringAfterLast('/')} · ${formatSize(f.size)} · ${whenText(f.modified)}",
                    selecting = selecting,
                    selected = f.path in vm.selected,
                    starred = f.path in vm.starred,
                    onClick = { if (selecting) vm.toggle(f.path) else onOpen(f.path) },
                    onLongClick = { vm.toggle(f.path) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StorageCard(stats: HomeStats?, onCleanUp: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Numio.Surface)
            .border(1.dp, Numio.Line, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Internal storage", style = MaterialTheme.typography.bodySmall)
                Text(
                    buildAnnotatedString {
                        append(if (stats == null) "…" else formatSize(stats.used).substringBefore(' '))
                        withStyle(SpanStyle(fontSize = 15.sp, color = Numio.TextDim, fontWeight = FontWeight.Medium)) {
                            if (stats != null) append(" ${formatSize(stats.used).substringAfter(' ')} of ${formatSize(stats.total)}")
                        }
                    },
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Button(
                onClick = onCleanUp,
                colors = ButtonDefaults.buttonColors(containerColor = Numio.Amber, contentColor = Numio.OnAmber),
            ) { Text("Clean up", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
        if (stats != null && stats.total > 0) {
            val parts = listOf(
                stats.media to Numio.Amber,
                stats.otherFiles to Numio.Amber.copy(alpha = 0.5f),
                stats.system to Numio.TextDim.copy(alpha = 0.55f),
                stats.free to Numio.Line,
            ).filter { it.first > 0 }
            Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                parts.forEach { (bytes, color) ->
                    Box(
                        Modifier
                            .weight(bytes.toFloat().coerceAtLeast(stats.total * 0.01f))
                            .height(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend("Media", Numio.Amber)
                Legend("Files", Numio.Amber.copy(alpha = 0.5f))
                Legend("System", Numio.TextDim.copy(alpha = 0.55f))
                Spacer(Modifier.weight(1f))
                Text("${formatSize(stats.free)} free", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
    }
}

@Composable
private fun CategoryGrid(stats: HomeStats?, onOpen: (Category) -> Unit) {
    Column(
        Modifier.padding(start = 12.dp, end = 12.dp, top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Category.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { c ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOpen(c) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Numio.SurfaceHigh),
                            contentAlignment = Alignment.Center,
                        ) { Icon(FIcons.forCategory(c), null, tint = Numio.AmberText, modifier = Modifier.size(24.dp)) }
                        Spacer(Modifier.height(7.dp))
                        Text(c.label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = Numio.Text)
                        Text(
                            stats?.categorySizes?.get(c)?.let(::formatSize) ?: " ",
                            fontSize = 11.sp,
                            color = Numio.TextDim,
                        )
                    }
                }
            }
        }
    }
}
