package com.rr.numio.files.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rr.numio.files.FilesViewModel
import com.rr.numio.files.data.Category
import com.rr.numio.files.data.FileItem
import com.rr.numio.files.data.SortOrder
import com.rr.numio.files.data.formatSize
import com.rr.numio.files.ui.theme.Numio
import java.io.File

/** The list every screen shares: rows, selection, open on tap. */
@Composable
private fun Files(
    vm: FilesViewModel,
    items: List<FileItem>,
    onOpenFile: (String) -> Unit,
    header: @Composable () -> Unit = {},
    empty: @Composable () -> Unit = {},
) {
    val selecting = vm.selected.isNotEmpty()
    LazyColumn(Modifier.fillMaxSize()) {
        item { header() }
        if (items.isEmpty()) item { empty() }
        items(items, key = { it.path }) { f ->
            FileRow(
                item = f,
                selecting = selecting,
                selected = f.path in vm.selected,
                starred = f.path in vm.starred,
                onClick = {
                    when {
                        selecting -> vm.toggle(f.path)
                        f.isDir -> vm.openDir(File(f.path))
                        else -> onOpenFile(f.path)
                    }
                },
                onLongClick = { vm.toggle(f.path) },
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SortButton(vm: FilesViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconBtn(FIcons.Sort, "Sort: ${vm.sort.label}") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortOrder.entries.forEach { s ->
                DropdownMenuItem(
                    text = {
                        Text(
                            s.label,
                            fontWeight = if (s == vm.sort) FontWeight.Bold else FontWeight.Normal,
                            color = if (s == vm.sort) Numio.AmberText else Numio.Text,
                        )
                    },
                    onClick = {
                        vm.updateSort(s)
                        open = false
                    },
                )
            }
        }
    }
}

/** "Open with…" when exactly one file is picked, then the star. */
@Composable
private fun StarAction(vm: FilesViewModel) {
    val one = vm.selected.singleOrNull()
    val openWith = LocalOpenWith.current
    if (one != null && java.io.File(one).isFile) {
        LabeledBtn(FIcons.OpenWith, "Open with") {
            vm.clearSelection()
            openWith(one)
        }
    }
    // Illustrator files with a PDF inside: one tap to keep a .pdf copy
    if (one != null && com.rr.numio.files.data.extOf(one) == "ai") {
        val pdfInside by androidx.compose.runtime.produceState(false, one) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.rr.numio.files.data.Thumbs.isPdfInside(java.io.File(one))
            }
        }
        if (pdfInside) LabeledBtn(FIcons.SavePdf, "Save PDF", tint = Numio.AmberText) { vm.saveAsPdf(one) }
    }
    LabeledBtn(FIcons.Info, "Details") { vm.showDetails() }
    StarOnly(vm)
}

@Composable
private fun StarOnly(vm: FilesViewModel) {
    val all = vm.selected.all { it in vm.starred }
    LabeledBtn(if (all) FIcons.StarFilled else FIcons.Star, if (all) "Unstar" else "Star", tint = Numio.AmberText) {
        vm.toggleStarSelected()
    }
}

@Composable
fun BrowseScreen(vm: FilesViewModel, onOpenFile: (String) -> Unit, onNewFolder: () -> Unit) {
    val atRoot = vm.dir.absolutePath == vm.root.absolutePath
    Column {
        if (vm.selected.isNotEmpty()) {
            SelectionHeader(vm.selected.size, vm::clearSelection, { vm.selectAll(vm.dirItems.map { it.path }) }) { StarAction(vm) }
        } else {
            TitleHeader(if (atRoot) "Internal storage" else vm.dir.name, onBack = if (atRoot) null else ({ vm.up(); Unit })) {
                IconBtn(FIcons.Search, "Search files", onClick = vm::openSearch)
                IconBtn(FIcons.FolderPlus, "New folder", onClick = onNewFolder)
                SortButton(vm)
            }
        }
        Files(
            vm, vm.dirItems, onOpenFile,
            header = {
                Breadcrumbs(vm)
                val files = vm.dirItems.count { !it.isDir }
                val size = vm.dirItems.filter { !it.isDir }.sumOf { it.size }
                Text(
                    "${vm.dirItems.size} items" + if (files > 0) " · ${formatSize(size)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
                )
            },
            empty = { EmptyNote("This folder is empty", "Paste or create something here.") },
        )
    }
}

/** Internal › Download › Telegram, each part tappable. */
@Composable
private fun Breadcrumbs(vm: FilesViewModel) {
    val rootPath = vm.root.absolutePath
    val parts = buildList {
        var f: File? = vm.dir
        while (f != null && f.absolutePath.startsWith(rootPath)) {
            add(f)
            if (f.absolutePath == rootPath) break
            f = f.parentFile
        }
    }.reversed()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        parts.forEachIndexed { i, f ->
            val last = i == parts.size - 1
            val label = if (f.absolutePath == rootPath) "Internal" else f.name
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (last) FontWeight.Bold else FontWeight.Normal,
                color = if (last) Numio.AmberText else Numio.TextDim,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (last) Numio.AmberSoft else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(enabled = !last) { vm.openDir(f) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
            if (!last) Icon(FIcons.Chevron, null, tint = Numio.TextDim, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun CategoryScreen(vm: FilesViewModel, category: Category, onOpenFile: (String) -> Unit) {
    val items = vm.categoryItems(category)
    Column {
        if (vm.selected.isNotEmpty()) {
            SelectionHeader(vm.selected.size, vm::clearSelection, { vm.selectAll(items.map { it.path }) }) { StarAction(vm) }
        } else {
            TitleHeader(category.label, onBack = vm::closeOverlay) { SortButton(vm) }
        }
        Files(
            vm, items, onOpenFile,
            header = {
                Text(
                    "${items.size} files · ${formatSize(items.sumOf { it.size })}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
                )
            },
            empty = { EmptyNote("No ${category.label.lowercase()} yet", "They'll show up here as soon as there are some.") },
        )
    }
}

@Composable
fun TrashScreen(vm: FilesViewModel, onEmpty: () -> Unit) {
    val items = vm.trashItems.map {
        FileItem(it.file.absolutePath, it.name, it.file.isDirectory, it.file.length(), it.deletedAt)
    }
    val from = vm.trashItems.associate { it.file.absolutePath to it.originalPath.substringBeforeLast('/').substringAfterLast('/') }
    val selecting = vm.selected.isNotEmpty()
    Column {
        if (selecting) {
            SelectionHeader(vm.selected.size, vm::clearSelection, { vm.selectAll(items.map { it.path }) })
        } else {
            TitleHeader("Trash", onBack = vm::closeOverlay) {
                if (items.isNotEmpty()) TextButton(onClick = onEmpty) { Text("Empty", color = Numio.AmberText, fontWeight = FontWeight.Bold) }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    "Things here are deleted for good after 30 days.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
                )
            }
            if (items.isEmpty()) item { EmptyNote("Trash is empty", "Deleted files wait here before they're gone.") }
            items(items, key = { it.path }) { f ->
                FileRow(
                    item = f,
                    meta = "Deleted ${whenText(f.modified)} · from ${from[f.path].orEmpty()}",
                    selecting = selecting,
                    selected = f.path in vm.selected,
                    starred = false,
                    onClick = { vm.toggle(f.path) },
                    onLongClick = { vm.toggle(f.path) },
                )
            }
        }
    }
}

@Composable
fun StarredScreen(vm: FilesViewModel, onOpenFile: (String) -> Unit) {
    Column {
        if (vm.selected.isNotEmpty()) {
            SelectionHeader(vm.selected.size, vm::clearSelection, { vm.selectAll(vm.starredItems.map { it.path }) }) { StarAction(vm) }
        } else {
            TitleHeader("Starred") { SortButton(vm) }
        }
        Files(
            vm, vm.starredItems, onOpenFile,
            empty = { EmptyNote("Nothing starred yet", "Long-press a file or folder, then tap the star.") },
        )
    }
}

@Composable
fun SearchScreen(vm: FilesViewModel, onOpenFile: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column {
        if (vm.selected.isNotEmpty()) {
            SelectionHeader(vm.selected.size, vm::clearSelection, { vm.selectAll(vm.results.map { it.path }) }) { StarAction(vm) }
        } else {
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(FIcons.Back, "Back", onClick = vm::closeOverlay)
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Numio.Surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (vm.query.isEmpty()) Text("Search all files", style = MaterialTheme.typography.bodyLarge, color = Numio.TextDim)
                    BasicTextField(
                        value = vm.query,
                        onValueChange = vm::search,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Numio.Text),
                        cursorBrush = SolidColor(Numio.Amber),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { vm.commitSearch() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (vm.query.isNotEmpty()) IconBtn(FIcons.Close, "Clear") { vm.search("") }
            }
        }
        if (vm.query.isEmpty() && vm.selected.isEmpty()) {
            SearchStart(vm)
            return@Column
        }
        Files(
            vm, vm.results, { path -> vm.commitSearch(); onOpenFile(path) },
            header = {
                if (vm.searching) Text("Searching…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 20.dp, bottom = 6.dp))
                else if (vm.query.isNotBlank()) Text(
                    "${vm.results.size} found",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
                )
            },
            empty = {
                if (vm.query.isNotBlank() && !vm.searching) EmptyNote("Nothing found", "Try a shorter part of the name.")
            },
        )
    }
}

@Composable
fun ZipScreen(vm: FilesViewModel, zip: com.rr.numio.files.Overlay.Zip, onOpenEntry: (String) -> Unit) {
    val selecting = vm.selected.isNotEmpty()
    val zipName = zip.path.substringAfterLast('/')
    Column {
        if (selecting) {
            SelectionHeader(vm.selected.size, vm::clearSelection, { vm.selectAll(vm.zipItems.map { it.path }) })
        } else {
            TitleHeader(zipName, onBack = { if (!vm.zipUp()) vm.closeOverlay() }) { SortButton(vm) }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    (if (zip.inner.isEmpty()) "Inside the ZIP" else "ZIP › " + zip.inner.replace("/", " › ")) +
                        " · ${vm.zipItems.size} items",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
                )
            }
            vm.zipError?.let { err -> item { EmptyNote("Can't open", err) } }
            items(vm.zipItems, key = { it.path }) { f ->
                FileRow(
                    item = f,
                    meta = if (f.isDir) metaFor(f) else formatSize(f.size) + if (f.modified > 0) " · ${whenText(f.modified)}" else "",
                    selecting = selecting,
                    selected = f.path in vm.selected,
                    starred = false,
                    onClick = {
                        when {
                            selecting -> vm.toggle(f.path)
                            f.isDir -> vm.zipInto(f.path)
                            else -> onOpenEntry(f.path)
                        }
                    },
                    onLongClick = { vm.toggle(f.path) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun CleanupScreen(vm: FilesViewModel, onOpenFile: (String) -> Unit) {
    val rootPath = vm.root.absolutePath + "/"
    val tab = vm.cleanTab
    val rows: List<Pair<FileItem, String>> = when (tab) {
        com.rr.numio.files.CleanTab.EMPTY -> vm.emptyFolders.map { e ->
            val name = e.path.substringAfterLast('/')
            val where = e.path.removePrefix(rootPath).substringBeforeLast('/', "").ifEmpty { "Internal" }
            FileItem(e.path, name, true, 0, 0, 0) to
                (where + if (e.nested > 0) " · with ${e.nested} empty folder${if (e.nested == 1) "" else "s"} inside" else "")
        }
        com.rr.numio.files.CleanTab.LARGE -> vm.largeFiles.map { it to "${formatSize(it.size)} · ${it.path.removePrefix(rootPath).substringBeforeLast('/', "Internal")}" }
        com.rr.numio.files.CleanTab.APKS -> vm.apkFiles().map { it to metaFor(it) }
    }
    Column {
        TitleHeader("Clean up", onBack = vm::closeOverlay)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            com.rr.numio.files.CleanTab.entries.forEach { t ->
                val on = t == tab
                Text(
                    t.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (on) Numio.OnAmber else Numio.Text,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) Numio.Amber else Numio.Surface)
                        .clickable { vm.pickCleanTab(t) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                val note = when (tab) {
                    com.rr.numio.files.CleanTab.EMPTY ->
                        "Only folders with nothing at all inside, hidden files included. Folders Files can't look into, Android/ and standard folders like DCIM are never touched."
                    com.rr.numio.files.CleanTab.LARGE -> "Files over 100 MB. Picked files go to Trash, so you can restore them."
                    com.rr.numio.files.CleanTab.APKS -> "Install files you may not need any more. Picked files go to Trash."
                }
                Text(note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp))
                if (rows.isNotEmpty()) {
                    val all = rows.all { it.first.path in vm.selected }
                    Text(
                        if (all) "Unselect all" else "Select all ${rows.size}",
                        color = Numio.AmberText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { if (all) vm.clearSelection() else vm.selectAll(rows.map { it.first.path }) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
            if (vm.cleanScanning) {
                item { EmptyNote("Looking through your folders…", "This can take a moment on a full phone.") }
            } else if (rows.isEmpty()) {
                item {
                    EmptyNote(
                        when (tab) {
                            com.rr.numio.files.CleanTab.EMPTY -> "No empty folders"
                            com.rr.numio.files.CleanTab.LARGE -> "No files over 100 MB"
                            com.rr.numio.files.CleanTab.APKS -> "No APK files"
                        },
                        "Nothing to clean here.",
                    )
                }
            }
            if (!vm.cleanScanning) {
                items(rows, key = { it.first.path }) { (f, meta) ->
                    FileRow(
                        item = f,
                        meta = meta,
                        selecting = true,
                        selected = f.path in vm.selected,
                        starred = false,
                        onClick = { vm.toggle(f.path) },
                        onLongClick = { if (!f.isDir) onOpenFile(f.path) },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Before typing: recent searches and a few quick file types. */
@Composable
private fun SearchStart(vm: FilesViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        if (vm.history.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent searches", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::clearHistory) { Text("Clear", color = Numio.AmberText, fontWeight = FontWeight.Bold) }
            }
            Chips(vm.history) { vm.search(it) }
            Spacer(Modifier.height(20.dp))
        }
        Text("Quick", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 10.dp))
        Chips(listOf(".pdf", ".jpg", ".mp4", ".apk", ".zip", ".docx", ".xlsx", "Screenshot", "WhatsApp")) { vm.search(it) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Chips(items: List<String>, onPick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { t ->
            Text(
                t,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Numio.Text,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Numio.Surface)
                    .clickable { onPick(t) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}
