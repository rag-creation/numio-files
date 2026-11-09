package com.rr.numio.files.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import java.io.File

/** What the Home screen shows. */
data class HomeStats(
    val total: Long,
    val free: Long,
    val media: Long,
    val otherFiles: Long,
    val categorySizes: Map<Category, Long>,
    val categoryFiles: Map<Category, List<FileItem>>,
    val recent: List<FileItem>,
) {
    val used: Long get() = (total - free).coerceAtLeast(0)
    /** System, apps and anything the file index doesn't know about. */
    val system: Long get() = (used - media - otherFiles).coerceAtLeast(0)
}

/** A place files live: internal storage, an SD card or a USB drive. */
data class Volume(val label: String, val dir: File, val total: Long, val free: Long, val internal: Boolean)

object Storage {
    /** Internal storage first, then SD cards and USB drives that are plugged in. */
    fun volumes(context: Context): List<Volume> {
        val sm = context.getSystemService(android.os.storage.StorageManager::class.java) ?: return emptyList()
        return sm.storageVolumes.mapNotNull { v ->
            if (v.state != Environment.MEDIA_MOUNTED && v.state != Environment.MEDIA_MOUNTED_READ_ONLY) return@mapNotNull null
            val dir = if (android.os.Build.VERSION.SDK_INT >= 30) v.directory else runCatching {
                v.javaClass.getMethod("getPathFile").invoke(v) as File
            }.getOrNull()
            dir ?: return@mapNotNull null
            val stat = runCatching { StatFs(dir.path) }.getOrNull() ?: return@mapNotNull null
            val label = when {
                v.isPrimary -> "Internal storage"
                else -> v.getDescription(context)?.takeIf { it.isNotBlank() } ?: if (v.isRemovable) "SD card" else "Storage"
            }
            Volume(label, dir, stat.totalBytes, stat.availableBytes, v.isPrimary)
        }.sortedByDescending { it.internal }
    }

    val root: File get() = Environment.getExternalStorageDirectory()
    val downloads: File get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    /**
     * Reads Android's own file index (MediaStore) once, which is much faster
     * than walking every folder. With "All files access" it lists every file.
     */
    fun scan(context: Context, trashSize: Long): HomeStats {
        val stat = StatFs(root.path)
        val sizes = mutableMapOf<Category, Long>()
        val files = mutableMapOf<Category, MutableList<FileItem>>()
        val recent = ArrayList<FileItem>()
        var all = 0L
        val downloadsPath = downloads.absolutePath + "/"

        @Suppress("DEPRECATION")
        val dataCol = MediaStore.Files.FileColumns.DATA
        val projection = arrayOf(
            dataCol,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
        )
        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"), projection, null, null, null,
            )?.use { c ->
                val iPath = c.getColumnIndexOrThrow(dataCol)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val iDate = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val iMime = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                while (c.moveToNext()) {
                    val path = c.getString(iPath) ?: continue
                    // Folders have no MIME type in the index
                    if (c.isNull(iMime) && extOf(path).isEmpty()) continue
                    val name = path.substringAfterLast('/')
                    if (name.startsWith(".")) continue
                    val size = c.getLong(iSize)
                    val item = FileItem(path, name, false, size, c.getLong(iDate) * 1000)
                    all += size
                    categoryOf(name)?.let { cat ->
                        sizes[cat] = (sizes[cat] ?: 0L) + size
                        files.getOrPut(cat) { ArrayList() }.add(item)
                    }
                    if (path.startsWith(downloadsPath)) {
                        sizes[Category.DOWNLOADS] = (sizes[Category.DOWNLOADS] ?: 0L) + size
                    }
                    recent.add(item)
                }
            }
        } catch (e: Exception) {
            // Index unavailable: Home still shows the storage numbers
        }
        sizes[Category.TRASH] = trashSize
        val media = (sizes[Category.IMAGES] ?: 0) + (sizes[Category.VIDEOS] ?: 0) + (sizes[Category.AUDIO] ?: 0)
        val latest = recent.sortedByDescending { it.modified }.asSequence()
            .filter { File(it.path).isFile }
            .take(6).toList()
        return HomeStats(
            total = stat.totalBytes,
            free = stat.availableBytes,
            media = media,
            otherFiles = (all - media).coerceAtLeast(0),
            categorySizes = sizes,
            categoryFiles = files,
            recent = latest,
        )
    }

    fun list(dir: File, showHidden: Boolean): List<FileItem> =
        (dir.listFiles() ?: emptyArray())
            .filter { showHidden || !it.name.startsWith(".") }
            .map { toItem(it, showHidden) }

    fun toItem(f: File, showHidden: Boolean = false): FileItem = if (f.isDirectory) {
        val kids = f.list()?.count { showHidden || !it.startsWith(".") } ?: 0
        FileItem(f.absolutePath, f.name, true, 0, f.lastModified(), kids)
    } else {
        FileItem(f.absolutePath, f.name, false, f.length(), f.lastModified())
    }

    /**
     * Name search. Asks Android's file index first (near-instant, files and folders);
     * walks the folders only when hidden files are shown, since the index skips those.
     */
    fun search(context: Context, query: String, showHidden: Boolean, limit: Int = 300): List<FileItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        if (showHidden) return walkSearch(q, limit)
        val out = ArrayList<FileItem>()
        val seen = HashSet<String>()
        @Suppress("DEPRECATION")
        val dataCol = MediaStore.Files.FileColumns.DATA
        val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(dataCol),
                "$dataCol LIKE ? ESCAPE '\\'",
                arrayOf(like),
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val path = c.getString(0) ?: continue
                    val name = path.substringAfterLast('/')
                    // Match the name itself, not a folder somewhere above it
                    if (!name.lowercase().contains(q) || name.startsWith(".") || "/." in path) continue
                    if (path.startsWith(root.absolutePath + "/Android/")) continue
                    if (!seen.add(path)) continue
                    val f = File(path)
                    if (f.exists()) out.add(toItem(f))
                }
            }
        } catch (e: Exception) {
            return walkSearch(q, limit)
        }
        return out
    }

    private fun walkSearch(q: String, limit: Int): List<FileItem> {
        val out = ArrayList<FileItem>()
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (stack.isNotEmpty() && out.size < limit) {
            val dir = stack.removeLast()
            // Android/data and Android/obb belong to other apps
            if (dir.absolutePath == root.absolutePath + "/Android") continue
            for (f in dir.listFiles() ?: continue) {
                if (f.name.lowercase().contains(q)) out.add(toItem(f, showHidden = true))
                if (f.isDirectory) stack.add(f)
                if (out.size >= limit) break
            }
        }
        return out
    }
}
