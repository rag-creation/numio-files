package com.rr.numio.files.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Something in Files' own Trash. */
data class TrashEntry(val file: File, val originalPath: String, val deletedAt: Long) {
    val name: String get() = originalPath.substringAfterLast('/')
}

/**
 * Deleting moves things into the app's own folder on the same storage, so it is
 * instant and can be undone. Kept 7, 30 or 90 days (Settings). Note: Android empties this folder if
 * Files is uninstalled.
 */
class Trash(private val context: Context) {
    private val dir: File? get() = context.getExternalFilesDir("trash")?.also { it.mkdirs() }
    private val indexFile get() = File(context.filesDir, "trash.json")

    private fun readIndex(): JSONObject = try {
        if (indexFile.exists()) JSONObject(indexFile.readText()) else JSONObject()
    } catch (e: Exception) {
        JSONObject()
    }

    private fun writeIndex(o: JSONObject) {
        indexFile.writeText(o.toString())
    }

    fun entries(): List<TrashEntry> {
        val d = dir ?: return emptyList()
        val index = readIndex()
        return (d.listFiles() ?: emptyArray()).map { f ->
            val info = index.optJSONObject(f.name)
            TrashEntry(
                file = f,
                originalPath = info?.optString("from")?.takeIf { it.isNotEmpty() } ?: f.name,
                deletedAt = info?.optLong("at") ?: f.lastModified(),
            )
        }.sortedByDescending { it.deletedAt }
    }

    fun size(): Long = entries().sumOf { if (it.file.isDirectory) it.file.walk().sumOf { f -> f.length() } else it.file.length() }

    /** Moves into Trash. Returns how many failed. */
    fun moveIn(files: List<File>): Int = moveInKeys(files).second

    /** Moves into Trash; returns the Trash names of what moved (for Undo) and how many failed. */
    fun moveInKeys(files: List<File>): Pair<List<String>, Int> {
        val d = dir ?: return emptyList<String>() to files.size
        val moved = ArrayList<String>()
        val index = readIndex()
        var failed = 0
        val now = System.currentTimeMillis()
        for (f in files) {
            val key = uniqueName("${now}_${f.name}") { File(d, it).exists() }
            val dest = File(d, key)
            val ok = f.renameTo(dest) || (runCatching { f.copyRecursively(dest) }.getOrDefault(false) && f.deleteRecursively())
            if (ok) {
                index.put(key, JSONObject().put("from", f.absolutePath).put("at", now))
                moved.add(key)
            } else failed++
        }
        writeIndex(index)
        return moved to failed
    }

    /** Undo: puts back exactly what one delete moved in. */
    fun restoreKeys(keys: List<String>): Int = restore(entries().filter { it.file.name in keys })

    /** Puts things back where they were; a taken name gets " (1)". */
    fun restore(entries: List<TrashEntry>): Int {
        val index = readIndex()
        var failed = 0
        for (e in entries) {
            val original = File(e.originalPath)
            val parent = original.parentFile ?: Storage.root
            parent.mkdirs()
            val dest = File(parent, uniqueName(original.name) { File(parent, it).exists() })
            val ok = e.file.renameTo(dest) || (runCatching { e.file.copyRecursively(dest) }.getOrDefault(false) && e.file.deleteRecursively())
            if (ok) index.remove(e.file.name) else failed++
        }
        writeIndex(index)
        return failed
    }

    fun deleteForever(entries: List<TrashEntry>) {
        val index = readIndex()
        for (e in entries) {
            if (e.file.deleteRecursively()) index.remove(e.file.name)
        }
        writeIndex(index)
    }

    /** Things older than [days] go for good. */
    fun purgeOld(days: Int = 30) {
        val cutoff = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
        deleteForever(entries().filter { it.deletedAt < cutoff })
    }
}
