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
}

enum class CleanTab(val label: String) { EMPTY("Empty folders"), LARGE("Large files"), APKS("APK files") }

/**
 * Files picked for Copy or Move, waiting for "Paste here".
 * With [zip] set, [paths] are entries of that ZIP to extract into the folder.
 */
data class Pending(val paths: List<String>, val move: Boolean, val zip: String? = null, val zipInner: String = "")

class FilesViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val trash = Trash(app)

    var appTheme by mutableStateOf(prefs.theme); private set
    var showHidden by mutableStateOf(prefs.showHidden); private set
    var sort by mutableStateOf(prefs.sort); private set

    var hasAccess by mutableStateOf(false); private set
    var tab by mutableStateOf(Tab.HOME); private set
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

    val root: File get() = Storage.root

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
            if (first) refreshAll() else reloadDir()
        }
    }

    fun refreshAll() {
        reloadDir()
        viewModelScope.launch {
            val stats = withContext(Dispatchers.IO) {
                trash.purgeOld()
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

    // Navigation

    fun openTab(t: Tab) {
        selected.clear()
        overlay = null
        tab = t
        if (t == Tab.STARRED) reloadStarred()
    }

    fun openDir(f: File) {
        selected.clear()
        overlay = null
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
                overlay = Overlay.Cat(c)
                viewModelScope.launch { trashItems = withContext(Dispatchers.IO) { trash.entries() } }
            }
            else -> overlay = Overlay.Cat(c)
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
        overlay = Overlay.Zip(path, "", outside)
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

    // Clean up

    var cleanTab by mutableStateOf(CleanTab.EMPTY); private set
    var cleanScanning by mutableStateOf(false); private set
    var emptyFolders by mutableStateOf<List<EmptyFolder>>(emptyList()); private set
    var largeFiles by mutableStateOf<List<FileItem>>(emptyList()); private set

    fun openCleanup() {
        selected.clear()
        overlay = Overlay.Cleanup
        scanCleanup()
    }

    fun pickCleanTab(t: CleanTab) {
        cleanTab = t
        selected.clear()
        // Empty folders are safe to pick all at once; files are picked one by one
        if (t == CleanTab.EMPTY) selected.addAll(emptyFolders.map { it.path })
    }

    private fun scanCleanup() {
        cleanScanning = true
        viewModelScope.launch {
            val (empty, large) = withContext(Dispatchers.IO) {
                Cleanup.emptyFolders(Storage.root) to Cleanup.largeFiles(Storage.root)
            }
            emptyFolders = empty
            largeFiles = large
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
            val failed = trash.moveIn(paths.map(::File))
            FileOps.reindex(getApplication(), paths)
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
        overlay = Overlay.Search
    }

    fun closeOverlay() {
        selected.clear()
        overlay = null
        searchJob?.cancel()
        query = ""
        results = emptyList()
        searching = false
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
            val found = withContext(Dispatchers.IO) { Storage.search(q, showHidden) }
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
            val failed = trash.moveIn(paths.map(::File))
            starredPathsMoved(paths)
            FileOps.reindex(getApplication(), paths)
            if (failed == 0) "${items(paths.size)} moved to Trash" else "$failed couldn't be moved to Trash"
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
