package com.rr.numio.files

import android.app.Application
import android.os.Build
import android.os.Environment
import android.content.pm.PackageManager
import android.Manifest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rr.numio.files.data.Category
import com.rr.numio.files.data.FileItem
import com.rr.numio.files.data.FileOps
import com.rr.numio.files.data.HomeStats
import com.rr.numio.files.data.Prefs
import com.rr.numio.files.data.SortOrder
import com.rr.numio.files.data.Storage
import com.rr.numio.files.data.Trash
import com.rr.numio.files.data.TrashEntry
import com.rr.numio.files.data.ZipNode
import com.rr.numio.files.data.EmptyFolder
import com.rr.numio.files.data.Cleanup
import com.rr.numio.files.data.CancelledJob
import com.rr.numio.files.data.JobProgress
import com.rr.numio.files.data.Zips
import com.rr.numio.files.data.uniqueName
import android.net.Uri
import com.rr.numio.files.data.sortItems
import com.rr.numio.files.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Tab(val label: String) { HOME("Home"), BROWSE("Browse"), STARRED("Starred"), SETTINGS("Settings") }

/** A screen shown over the tabs. */
sealed interface Overlay {
    data class Cat(val category: Category) : Overlay
    data object Search : Overlay
    /** Looking inside a ZIP; [inner] is the folder in it ("" = top). */
    data class Zip(val path: String, val inner: String, val outside: Boolean) : Overlay
    data object Cleanup : Overlay
    /** A text file (TXT, Markdown, CSV, JSON, HTML…) in the built-in viewer. */
    data class Text(val path: String, val outside: Boolean) : Overlay
    /** An Apple Pages / Numbers / Keynote file, shown from the preview inside it. */
    data class Iwork(val path: String, val outside: Boolean) : Overlay
    /** A Word (.docx) or Excel (.xlsx) file in the built-in reader. */
    data class Office(val path: String, val outside: Boolean) : Overlay
}

enum class CleanTab(val label: String) { EMPTY("Empty folders"), DUPES("Duplicates"), LARGE("Large files"), APKS("APK files") }

/**
 * Files picked for Copy or Move, waiting for "Paste here".
 * With [zip] set, [paths] are entries of that ZIP to extract into the folder.
 */
data class Pending(val paths: List<String>, val move: Boolean, val zip: String? = null, val zipInner: String = "")

class FilesViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val trash = Trash(app)

    var appTheme by mutableStateOf(prefs.theme); private set
    /** With "lock hidden files" on, hidden files start hidden every time Files opens. */
    var showHidden by mutableStateOf(prefs.showHidden && !prefs.lockHidden); private set
    var openOnBrowse by mutableStateOf(prefs.openOnBrowse); private set
    var askBeforeTrash by mutableStateOf(prefs.askBeforeTrash); private set
    var trashDays by mutableStateOf(prefs.trashDays); private set
    var lockApp by mutableStateOf(prefs.lockApp); private set
    var lockHidden by mutableStateOf(prefs.lockHidden); private set

    /** True while the app lock is waiting for a fingerprint or PIN. */
    var locked by mutableStateOf(prefs.lockApp); private set
    private var leftAt = 0L

    fun unlocked() {
        locked = false
    }

    /** Called when Files goes to the background. */
    fun onLeave() {
        leftAt = System.currentTimeMillis()
    }

    /** Called when Files comes back: lock again after 30 seconds away. */
    fun onReturn() {
        if (lockApp && leftAt > 0 && System.currentTimeMillis() - leftAt > 30_000) locked = true
    }

    fun updateOpenOnBrowse(v: Boolean) { openOnBrowse = v; prefs.openOnBrowse = v }
    fun updateAskBeforeTrash(v: Boolean) { askBeforeTrash = v; prefs.askBeforeTrash = v }
    fun updateTrashDays(v: Int) { trashDays = v; prefs.trashDays = v }
    fun updateLockApp(v: Boolean) { lockApp = v; prefs.lockApp = v }
    fun updateLockHidden(v: Boolean) {
        lockHidden = v
        prefs.lockHidden = v
    }

    /** Pull down to refresh. */
    var refreshing by mutableStateOf(false); private set

    fun pullRefresh() {
        refreshing = true
        refreshAll()
        viewModelScope.launch {
            delay(600)
            refreshing = false
        }
    }
    var sort by mutableStateOf(prefs.sort); private set

    var hasAccess by mutableStateOf(false); private set
    var tab by mutableStateOf(if (prefs.openOnBrowse) Tab.BROWSE else Tab.HOME); private set
    var overlay by mutableStateOf<Overlay?>(null); private set

    var home by mutableStateOf<HomeStats?>(null); private set
    var dir by mutableStateOf(Storage.root); private set
    var dirItems by mutableStateOf<List<FileItem>>(emptyList()); private set
    var trashItems by mutableStateOf<List<TrashEntry>>(emptyList()); private set
    var starred by mutableStateOf(prefs.starred); private set
    var starredItems by mutableStateOf<List<FileItem>>(emptyList()); private set

    var query by mutableStateOf(""); private set
    var results by mutableStateOf<List<FileItem>>(emptyList()); private set
    var searching by mutableStateOf(false); private set
    private var searchJob: Job? = null

    /** Selected paths (trash files when in Trash). */
    val selected = mutableStateListOf<String>()
    var pending by mutableStateOf<Pending?>(null); private set
    /** A short note at the bottom ("3 items copied"). */
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false); private set

    /** Plugged-in storage: internal, SD cards, USB drives. */
    var volumes by mutableStateOf<List<com.rr.numio.files.data.Volume>>(emptyList()); private set

    /** The top of whichever storage the open folder is on. */
    val root: File get() = volumeOf(dir)?.dir ?: Storage.root

    fun volumeOf(f: File): com.rr.numio.files.data.Volume? =
        volumes.filter { f.absolutePath == it.dir.absolutePath || f.absolutePath.startsWith(it.dir.absolutePath + "/") }
            .maxByOrNull { it.dir.absolutePath.length }

    fun checkAccess() {
        val ctx = getApplication<Application>()
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
        val first = ok && !hasAccess
        hasAccess = ok
        if (ok) {
            if (first) refreshAll() else {
                // An SD card or USB drive may have been plugged in or out meanwhile
                volumes = runCatching { Storage.volumes(getApplication()) }.getOrDefault(volumes)
                if (volumeOf(dir) == null && dir.absolutePath != Storage.root.absolutePath && !dir.exists()) {
                    dir = Storage.root // the drive this folder was on is gone
                }
                reloadDir()
            }
        }
    }

    fun refreshAll() {
        volumes = runCatching { Storage.volumes(getApplication()) }.getOrDefault(emptyList())
        reloadDir()
        viewModelScope.launch {
            val stats = withContext(Dispatchers.IO) {
                trash.purgeOld(trashDays)
                val t = trash.entries()
                trashItems = t
                Storage.scan(getApplication(), trash.size())
            }
            home = stats
            reloadStarred()
        }
    }

    private fun reloadDir() {
        val d = dir
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { Storage.list(d, showHidden) }
            if (d == dir) dirItems = sortItems(items, sort)
        }
    }

    private fun reloadStarred() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                starred.map(::File).filter { it.exists() }.map { Storage.toItem(it, showHidden) }
            }
            starredItems = sortItems(items, sort)
        }
    }

    // Grid or list

    private var viewChoices by mutableStateOf(prefs.viewChoices)

    /**
     * Grid if the person chose it for this folder; otherwise grid when the folder
     * is mostly photos and videos (DCIM, Screenshots, WhatsApp Images…).
     */
    fun isGrid(key: String, items: List<FileItem>): Boolean {
        if ("g:$key" in viewChoices) return true
        if ("l:$key" in viewChoices) return false
        if (key == "cat:IMAGES" || key == "cat:VIDEOS") return true
        val files = items.filter { !it.isDir }
        if (files.size < 6) return false
        val media = files.count { com.rr.numio.files.data.categoryOf(it.name).let { c -> c == Category.IMAGES || c == Category.VIDEOS } }
        return media * 10 >= files.size * 7
    }

    fun toggleGrid(key: String, items: List<FileItem>) {
        val nowGrid = isGrid(key, items)
        val next = viewChoices.filter { it != "g:$key" && it != "l:$key" }.toSet() + (if (nowGrid) "l:$key" else "g:$key")
        viewChoices = next
        prefs.viewChoices = next
    }

    // Navigation

    fun openTab(t: Tab) {
        selected.clear()
        overlay = null
        overlayStack.clear()
        tab = t
        if (t == Tab.STARRED) reloadStarred()
    }

    fun openDir(f: File) {
        selected.clear()
        overlay = null
        overlayStack.clear()
        tab = Tab.BROWSE
        dir = f
        dirItems = emptyList()
        reloadDir()
    }

    /** One folder up; false at the top. */
    fun up(): Boolean {
        if (dir.absolutePath == root.absolutePath) return false
        openDir(dir.parentFile ?: root)
        return true
    }

    fun openCategory(c: Category) {
        selected.clear()
        when (c) {
            Category.DOWNLOADS -> openDir(Storage.downloads)
            Category.TRASH -> {
                overlayStack.clear()
                overlay = Overlay.Cat(c)
                viewModelScope.launch { trashItems = withContext(Dispatchers.IO) { trash.entries() } }
            }
            else -> {
                overlayStack.clear()
                overlay = Overlay.Cat(c)
            }
        }
    }

    fun categoryItems(c: Category): List<FileItem> =
        sortItems(home?.categoryFiles?.get(c).orEmpty(), sort)

    // ZIP files

    private var zipNodes: List<ZipNode> = emptyList()
    var zipItems by mutableStateOf<List<FileItem>>(emptyList()); private set
    var zipError by mutableStateOf<String?>(null); private set

    /** Opens a ZIP like a folder. [outside]: it came from another app (a copy in the cache). */
    fun openZip(path: String, outside: Boolean = false) {
        selected.clear()
        zipNodes = emptyList()
        zipItems = emptyList()
        zipError = null
        showViewer(Overlay.Zip(path, "", outside))
        viewModelScope.launch {
            val nodes = withContext(Dispatchers.IO) { runCatching { Zips.entries(File(path)) }.getOrNull() }
            if (nodes == null) zipError = "This ZIP can't be opened. It may be damaged or password-protected."
            else {
                zipNodes = nodes
                showZipFolder("")
            }
        }
    }

    /** A ZIP handed over by another app ("Open with Files"): copied into the cache first. */
    fun openZipFrom(uri: Uri, name: String) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val copy = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(ctx.cacheDir, "opened").apply { deleteRecursively(); mkdirs() }
                    val out = File(dir, name.ifBlank { "archive.zip" })
                    ctx.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                    out.takeIf { it.length() > 0 }
                }.getOrNull()
            }
            if (copy == null) message = "Couldn't open that ZIP" else openZip(copy.absolutePath, outside = true)
        }
    }

    private fun showZipFolder(inner: String) {
        val z = overlay as? Overlay.Zip ?: return
        selected.clear()
        overlay = z.copy(inner = inner)
        zipItems = sortItems(Zips.children(zipNodes, inner), sort)
    }

    fun zipInto(inner: String) = showZipFolder(inner)

    /** One folder up inside the ZIP; false at its top. */
    fun zipUp(): Boolean {
        val z = overlay as? Overlay.Zip ?: return false
        if (z.inner.isEmpty()) return false
        showZipFolder(z.inner.substringBeforeLast('/', ""))
        return true
    }

    /** Pulls one file out to the cache and hands it to [open]. */
    fun openZipEntry(path: String, open: (String) -> Unit) {
        val z = overlay as? Overlay.Zip ?: return
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val f = withContext(Dispatchers.IO) {
                runCatching { Zips.extractOne(File(z.path), path, File(ctx.cacheDir, "zipview").apply { deleteRecursively() }) }.getOrNull()
            }
            if (f == null) message = "Couldn't open that file" else open(f.absolutePath)
        }
    }

    /**
     * Extracts next to the ZIP into a new folder named after it
     * (into Download for a ZIP from another app). Empty selection = everything.
     */
    fun extractHere() {
        val z = overlay as? Overlay.Zip ?: return
        val chosen = selected.toList()
        selected.clear()
        val zip = File(z.path)
        val parent = if (z.outside) Storage.downloads else (zip.parentFile ?: Storage.root)
        runWithProgress("Extracting ${zip.name}") { prog ->
            val base = zip.name.substringBeforeLast('.').ifBlank { "Extracted" }
            val target = File(parent, uniqueName(base) { File(parent, it).exists() })
            val failed = try {
                Zips.extract(zip, chosen, z.inner, target, prog)
            } catch (e: CancelledJob) {
                target.deleteRecursively() // a new folder we just made: remove the partial extract
                throw e
            }
            FileOps.reindex(getApplication(), listOf(target.absolutePath))
            if (failed == 0) "Extracted to ${target.name}" else "Extracted to ${target.name}, $failed files couldn't be"
        }
    }

    /** Pick a folder in Browse, then "Paste here" extracts into it. */
    fun startExtractTo() {
        val z = overlay as? Overlay.Zip ?: return
        val chosen = selected.toList().ifEmpty { zipItems.map { it.path } }
        pending = Pending(chosen, move = false, zip = z.path, zipInner = z.inner)
        selected.clear()
        overlay = null
        overlayStack.clear()
        tab = Tab.BROWSE
        reloadDir()
    }

    /** Makes a ZIP of the selected things, in the folder of the first one. */
    fun makeZip(name: String) {
        val sources = selected.map(::File)
        selected.clear()
        if (sources.isEmpty()) return
        val parent = sources[0].parentFile ?: return
        val n = name.trim().let { if (it.endsWith(".zip", ignoreCase = true)) it else "$it.zip" }
        runWithProgress("Making $n") { prog ->
            val dest = File(parent, uniqueName(n) { File(parent, it).exists() })
            if (Zips.create(sources, dest, prog)) {
                FileOps.reindex(getApplication(), listOf(dest.absolutePath))
                "Made ${dest.name}"
            } else "Couldn't make the ZIP"
        }
    }

    // Apple Pages, Numbers, Keynote

    var iwork by mutableStateOf<com.rr.numio.files.data.IworkPreview?>(null); private set

    fun openIwork(path: String, outside: Boolean = false) {
        selected.clear()
        iwork = null
        showViewer(Overlay.Iwork(path, outside))
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            iwork = withContext(Dispatchers.IO) {
                runCatching {
                    com.rr.numio.files.data.PagesFile.extract(File(path), File(ctx.cacheDir, "iwork").apply { deleteRecursively() })
                }.getOrDefault(com.rr.numio.files.data.IworkPreview.Nothing)
            }
        }
    }

    /** Keeps the preview (PDF or picture) as a real file next to the original, or in Download. */
    fun saveIworkPreview() {
        val o = overlay as? Overlay.Iwork ?: return
        val preview = when (val p = iwork) {
            is com.rr.numio.files.data.IworkPreview.Pdf -> p.file
            is com.rr.numio.files.data.IworkPreview.Picture -> p.file
            else -> return
        }
        val parent = if (o.outside) Storage.downloads else (File(o.path).parentFile ?: Storage.downloads)
        run("Saving…") {
            val dest = File(parent, uniqueName(preview.name) { File(parent, it).exists() })
            preview.copyTo(dest)
            FileOps.reindex(getApplication(), listOf(dest.absolutePath))
            "Saved ${dest.name}" + if (o.outside) " in Download" else ""
        }
    }

    fun openOffice(path: String, outside: Boolean = false) {
        selected.clear()
        showViewer(Overlay.Office(path, outside))
    }

    // Text viewer

    fun openText(path: String, outside: Boolean = false) {
        selected.clear()
        showViewer(Overlay.Text(path, outside))
    }

    /** Opens a ZIP or text file another app handed over ("Open with Files"). */
    fun openFrom(uri: Uri, name: String) {
        if (com.rr.numio.files.data.PagesFile.isIwork(name) || com.rr.numio.files.data.OfficeFiles.isOffice(name)) {
            viewModelScope.launch {
                val ctx = getApplication<Application>()
                val copy = withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = File(ctx.cacheDir, "opened").apply { deleteRecursively(); mkdirs() }
                        val out = File(dir, name)
                        ctx.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                        out.takeIf { it.length() > 0 }
                    }.getOrNull()
                }
                if (copy == null) message = "Couldn't open that file"
                else if (com.rr.numio.files.data.OfficeFiles.isOffice(name)) openOffice(copy.absolutePath, outside = true)
                else openIwork(copy.absolutePath, outside = true)
            }
            return
        }
        if (com.rr.numio.files.data.extOf(name) == "zip") {
            openZipFrom(uri, name)
            return
        }
        if (!com.rr.numio.files.data.TextFiles.isText(name)) {
            message = "Files can't show this kind of file yet"
            return
        }
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val copy = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(ctx.cacheDir, "opened").apply { deleteRecursively(); mkdirs() }
                    val out = File(dir, name)
                    ctx.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                    out.takeIf { it.exists() }
                }.getOrNull()
            }
            if (copy == null) message = "Couldn't open that file" else openText(copy.absolutePath, outside = true)
        }
    }

    /** Saves edited text over the file, then calls [done] on success. */
    fun saveText(path: String, text: String, done: () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val f = File(path)
                    // Write to a side file first, so a failed save never empties the original
                    val tmp = File(f.parentFile, ".${f.name}.saving")
                    tmp.writeText(text)
                    tmp.setLastModified(System.currentTimeMillis())
                    if (!tmp.renameTo(f)) {
                        f.writeText(text)
                        tmp.delete()
                    }
                    true
                }.getOrDefault(false)
            }
            busy = false
            message = if (ok) "Saved" else "Couldn't save"
            if (ok) {
                FileOps.reindex(getApplication(), listOf(path))
                done()
            }
        }
    }

    /** Saves a PDF-compatible .ai file as a .pdf copy next to it. The .ai stays. */
    fun saveAsPdf(path: String) {
        selected.clear()
        val src = File(path)
        val parent = src.parentFile ?: return
        run("Saving as PDF…") {
            if (!com.rr.numio.files.data.Thumbs.isPdfInside(src)) return@run "This file has no PDF inside, so it can't be saved as PDF"
            val dest = File(parent, uniqueName(src.name.substringBeforeLast('.') + ".pdf") { File(parent, it).exists() })
            try {
                src.copyTo(dest, overwrite = false)
                FileOps.reindex(getApplication(), listOf(dest.absolutePath))
                "Saved ${dest.name}"
            } catch (e: Exception) {
                dest.delete()
                "Couldn't save as PDF"
            }
        }
    }

    /** Photoshop files: the flat picture inside, as a PNG another app can show. */
    fun openPsd(path: String, open: (String) -> Unit) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            busy = true
            val png = withContext(Dispatchers.IO) {
                runCatching {
                    val src = File(path)
                    val dir = File(ctx.cacheDir, "psd").apply { deleteRecursively(); mkdirs() }
                    val out = File(dir, src.name.substringBeforeLast('.') + ".png")
                    val bmp = com.rr.numio.files.data.Thumbs.psd(src, 2048)
                    out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    out
                }
            }
            busy = false
            png.onSuccess { open(it.absolutePath) }.onFailure {
                message = (it as? com.rr.numio.files.data.Psd.Unsupported)?.message
                    ?: "This PSD has no flat picture inside. Save it in Photoshop with \"Maximize compatibility\" on."
            }
        }
    }

    /** Saves the flat picture of a PSD as a PNG next to it. */
    fun savePsdPng(path: String) {
        selected.clear()
        val src = File(path)
        val parent = src.parentFile ?: return
        run("Saving PNG…") {
            val dest = File(parent, uniqueName(src.name.substringBeforeLast('.') + ".png") { File(parent, it).exists() })
            try {
                val bmp = com.rr.numio.files.data.Thumbs.psd(src, 4096)
                dest.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                FileOps.reindex(getApplication(), listOf(dest.absolutePath))
                "Saved ${dest.name}"
            } catch (e: com.rr.numio.files.data.Psd.Unsupported) {
                dest.delete()
                e.message
            } catch (e: Throwable) {
                dest.delete()
                "Couldn't read the picture in this PSD"
            }
        }
    }

    // Clean up

    var cleanTab by mutableStateOf(CleanTab.EMPTY); private set
    var cleanScanning by mutableStateOf(false); private set
    var emptyFolders by mutableStateOf<List<EmptyFolder>>(emptyList()); private set
    var largeFiles by mutableStateOf<List<FileItem>>(emptyList()); private set
    /** Same-content groups, oldest first in each. */
    var duplicates by mutableStateOf<List<List<FileItem>>>(emptyList()); private set

    fun openCleanup() {
        selected.clear()
        overlayStack.clear()
        overlay = Overlay.Cleanup
        scanCleanup()
    }

    fun pickCleanTab(t: CleanTab) {
        cleanTab = t
        selected.clear()
        // Empty folders are safe to pick all at once; for duplicates, every copy but the oldest
        if (t == CleanTab.EMPTY) selected.addAll(emptyFolders.map { it.path })
        if (t == CleanTab.DUPES) selected.addAll(duplicates.flatMap { it.drop(1) }.map { it.path })
    }

    private fun scanCleanup() {
        cleanScanning = true
        viewModelScope.launch {
            val (empty, large) = withContext(Dispatchers.IO) {
                Cleanup.emptyFolders(Storage.root) to Cleanup.largeFiles(Storage.root)
            }
            emptyFolders = empty
            largeFiles = large
            duplicates = withContext(Dispatchers.IO) { runCatching { Cleanup.duplicates(Storage.root) }.getOrDefault(emptyList()) }
            cleanScanning = false
            if (overlay == Overlay.Cleanup) pickCleanTab(cleanTab)
        }
    }

    fun apkFiles(): List<FileItem> = home?.categoryFiles?.get(Category.APKS).orEmpty().sortedByDescending { it.size }

    /** Deletes the picked empty folders. Anything that isn't empty any more is kept. */
    fun deleteEmptyFolders() {
        val paths = selected.toList()
        selected.clear()
        run("Deleting empty folders…") {
            var kept = 0
            for (p in paths) if (!Cleanup.deleteEmptyTree(File(p))) kept++
            FileOps.reindex(getApplication(), paths)
            val done = paths.size - kept
            val main = if (done == 1) "Deleted 1 empty folder" else "Deleted $done empty folders"
            if (kept == 0) main else "$main. $kept had something inside, so they were kept."
        }
        afterClean()
    }

    /** Large files and APKs go to Trash, so they can be restored. */
    fun trashPicked() {
        val paths = selected.toList()
        selected.clear()
        run("Moving to Trash…") {
            val (keys, failed) = trash.moveInKeys(paths.map(::File))
            FileOps.reindex(getApplication(), paths)
            undoKeys = keys
            if (failed == 0) "${items(paths.size)} moved to Trash" else "$failed couldn't be moved to Trash"
        }
        afterClean()
    }

    private fun afterClean() {
        viewModelScope.launch {
            // Wait for the delete job, then look again
            while (busy) delay(100)
            if (overlay == Overlay.Cleanup) scanCleanup()
        }
    }

    // Details

    /** Rows for the Details sheet; null when it's closed. */
    var details by mutableStateOf<List<Pair<String, String>>?>(null); private set
    var detailsPath by mutableStateOf<String?>(null); private set

    fun showDetails() {
        val paths = selected.toList()
        if (paths.isEmpty()) return
        detailsPath = paths.singleOrNull()
        details = listOf("" to "Reading…")
        viewModelScope.launch {
            details = withContext(Dispatchers.IO) {
                runCatching { com.rr.numio.files.data.Details.of(getApplication(), paths) }.getOrElse { listOf("" to "Couldn't read the details") }
            }
        }
    }

    fun closeDetails() {
        details = null
        detailsPath = null
    }

    fun openSearch() {
        selected.clear()
        overlayStack.clear()
        overlay = Overlay.Search
    }

    /** Screens a viewer was opened from, so Back returns there (Search › file › Back = Search). */
    private val overlayStack = ArrayList<Overlay>()

    private fun showViewer(o: Overlay) {
        overlay?.let { overlayStack.add(it) }
        overlay = o
    }

    fun closeOverlay() {
        selected.clear()
        if (overlayStack.isNotEmpty()) {
            overlay = overlayStack.removeAt(overlayStack.size - 1)
            return
        }
        overlay = null
        overlayStack.clear()
        searchJob?.cancel()
        query = ""
        results = emptyList()
        searching = false
    }

    var history by mutableStateOf(prefs.searchHistory); private set

    /** Remembers the search when a result is opened or the keyboard's Search key is pressed. */
    fun commitSearch() {
        val q = query.trim()
        if (q.length < 2) return
        history = (listOf(q) + history.filter { !it.equals(q, ignoreCase = true) }).take(8)
        prefs.searchHistory = history
    }

    fun clearHistory() {
        history = emptyList()
        prefs.searchHistory = emptyList()
    }

    fun search(q: String) {
        query = q
        searchJob?.cancel()
        if (q.isBlank()) {
            results = emptyList()
            searching = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(250) // wait for the typing to pause
            searching = true
            val found = withContext(Dispatchers.IO) { Storage.search(getApplication(), q, showHidden) }
            results = sortItems(found, sort)
            searching = false
        }
    }

    // Selection

    fun toggle(path: String) {
        if (path in selected) selected.remove(path) else selected.add(path)
    }

    fun selectAll(paths: List<String>) {
        selected.clear()
        selected.addAll(paths)
    }

    fun clearSelection() = selected.clear()

    // Actions

    fun startCopy(move: Boolean) {
        if (selected.isEmpty()) return
        pending = Pending(selected.toList(), move)
        selected.clear()
        overlay = null
        overlayStack.clear()
        if (tab != Tab.BROWSE) {
            tab = Tab.BROWSE
            reloadDir()
        }
    }

    fun cancelPending() {
        pending = null
    }

    fun paste() {
        val p = pending ?: return
        val target = dir
        pending = null
        val n = items(p.paths.size)
        val title = when {
            p.zip != null -> "Extracting $n"
            p.move -> "Moving $n"
            else -> "Copying $n"
        }
        runWithProgress(title) { prog ->
            if (p.zip != null) {
                val failed = try {
                    Zips.extract(File(p.zip), p.paths, p.zipInner, target, prog)
                } finally {
                    FileOps.reindex(getApplication(), listOf(target.absolutePath))
                }
                return@runWithProgress if (failed == 0) "Extracted here" else "Extracted, $failed files couldn't be"
            }
            val sources = p.paths.map(::File).filter { it.exists() }
            val r = if (p.move) FileOps.move(sources, target, prog) else FileOps.copy(sources, target, prog)
            if (p.move) starredPathsMoved(p.paths.filter { !File(it).exists() })
            FileOps.reindex(getApplication(), p.paths + target.absolutePath)
            val verb = if (p.move) "moved" else "copied"
            when {
                r.cancelled -> "Stopped. ${items(r.done)} $verb; the unfinished one was removed."
                r.failed == 0 -> "${items(r.done)} $verb"
                else -> "${items(r.done)} $verb, ${r.failed} couldn't be"
            }
        }
    }

    fun rename(path: String, newName: String) {
        run("Renaming…") {
            val f = File(path)
            val dest = FileOps.rename(f, newName)
            if (dest == null) "Couldn't rename. That name may be taken." else {
                if (path in starred) saveStarred(starred - path + dest.absolutePath)
                FileOps.reindex(getApplication(), listOf(path, dest.absolutePath))
                null
            }
        }
    }

    fun newFolder(name: String) {
        val parent = dir
        run("Creating…") {
            if (FileOps.newFolder(parent, name) == null) "Couldn't create that folder" else null
        }
    }

    fun deleteSelected() {
        val paths = selected.toList()
        selected.clear()
        run("Moving to Trash…") {
            val (keys, failed) = trash.moveInKeys(paths.map(::File))
            starredPathsMoved(paths)
            FileOps.reindex(getApplication(), paths)
            undoKeys = keys
            if (failed == 0) "${items(paths.size)} moved to Trash" else "$failed couldn't be moved to Trash"
        }
    }

    /** What the last delete moved to Trash, while its Undo button is showing. */
    var undoKeys by mutableStateOf<List<String>>(emptyList())

    fun undoDelete() {
        val keys = undoKeys
        undoKeys = emptyList()
        message = null
        if (keys.isEmpty()) return
        run("Putting back…") {
            val failed = trash.restoreKeys(keys)
            val back = keys.size - failed
            if (overlay == Overlay.Cleanup) viewModelScope.launch { scanCleanup() }
            if (failed == 0) "${items(back)} put back" else "$back put back, $failed couldn't be"
        }
    }

    fun restoreSelected() {
        val chosen = trashItems.filter { it.file.absolutePath in selected }
        selected.clear()
        run("Restoring…") {
            val failed = trash.restore(chosen)
            FileOps.reindex(getApplication(), chosen.map { it.originalPath })
            if (failed == 0) "${items(chosen.size)} restored" else "$failed couldn't be restored"
        }
    }

    fun deleteForeverSelected() {
        val chosen = trashItems.filter { it.file.absolutePath in selected }
        selected.clear()
        run("Deleting…") {
            trash.deleteForever(chosen)
            "${items(chosen.size)} deleted for good"
        }
    }

    fun emptyTrash() {
        val all = trashItems
        selected.clear()
        run("Emptying Trash…") {
            trash.deleteForever(all)
            "Trash emptied"
        }
    }

    fun toggleStarSelected() {
        val paths = selected.toList()
        val allStarred = paths.all { it in starred }
        saveStarred(if (allStarred) starred - paths.toSet() else starred + paths)
        selected.clear()
        reloadStarred()
        message = if (allStarred) "Removed from Starred" else "Added to Starred"
    }

    private fun starredPathsMoved(paths: List<String>) {
        val gone = paths.filter { it in starred }
        if (gone.isNotEmpty()) saveStarred(starred - gone.toSet())
    }

    private fun saveStarred(s: Set<String>) {
        starred = s
        prefs.starred = s
    }

    // Settings

    fun updateTheme(t: AppTheme) {
        appTheme = t
        prefs.theme = t
    }

    fun updateShowHidden(v: Boolean) {
        showHidden = v
        prefs.showHidden = v
        reloadDir()
        reloadStarred()
    }

    fun updateSort(s: SortOrder) {
        sort = s
        prefs.sort = s
        dirItems = sortItems(dirItems, s)
        zipItems = sortItems(zipItems, s)
        starredItems = sortItems(starredItems, s)
        results = sortItems(results, s)
    }

    /** Runs a file job off the main thread, then refreshes every list. */
    /** The long job running now, for the progress card (null when none). */
    var progress by mutableStateOf<JobProgress?>(null); private set

    /** Like [run], with a progress card and a Cancel button. */
    private fun runWithProgress(title: String, job: (JobProgress) -> String?) {
        if (busy) return
        val p = JobProgress(title)
        progress = p
        run(title) {
            try {
                job(p)
            } catch (e: CancelledJob) {
                "Stopped"
            } finally {
                progress = null
            }
        }
    }

    fun cancelJob() {
        progress?.cancel()
    }

    private fun run(label: String, job: () -> String?) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val note = try {
                withContext(Dispatchers.IO) { job() }
            } catch (e: Exception) {
                "Something went wrong: ${e.message ?: label}"
            }
            busy = false
            if (note != null) message = note
            refreshAll()
            if (overlay == Overlay.Search && query.isNotBlank()) search(query)
        }
    }

    private fun items(n: Int) = if (n == 1) "1 item" else "$n items"
}
