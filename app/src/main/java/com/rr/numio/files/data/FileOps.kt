package com.rr.numio.files.data

import android.content.Context
import android.media.MediaScannerConnection
import java.io.File

/** Copy, move, rename and new folder. Everything here runs off the main thread. */
object FileOps {

    /** Copies [sources] into [target]; taken names get " (1)". Returns how many failed. */
    fun copy(sources: List<File>, target: File): Int {
        var failed = 0
        for (src in sources) {
            if (isInside(target, src)) { failed++; continue } // a folder into itself
            val dest = File(target, uniqueName(src.name) { File(target, it).exists() })
            if (!copyOne(src, dest)) failed++
        }
        return failed
    }

    /** Moves [sources] into [target]: a quick rename on the same storage, else copy then delete. */
    fun move(sources: List<File>, target: File): Int {
        var failed = 0
        for (src in sources) {
            if (src.parentFile?.absolutePath == target.absolutePath) continue // already there
            if (isInside(target, src)) { failed++; continue }
            val dest = File(target, uniqueName(src.name) { File(target, it).exists() })
            if (src.renameTo(dest)) continue
            if (copyOne(src, dest) && src.deleteRecursively()) continue
            failed++
        }
        return failed
    }

    fun rename(file: File, newName: String): File? {
        val name = newName.trim()
        if (!isValidName(name)) return null
        val dest = File(file.parentFile, name)
        if (dest.exists() && !dest.absolutePath.equals(file.absolutePath, ignoreCase = true)) return null
        return if (file.renameTo(dest)) dest else null
    }

    fun newFolder(parent: File, name: String): File? {
        val n = name.trim()
        if (!isValidName(n)) return null
        val dir = File(parent, n)
        if (dir.exists()) return null
        return if (dir.mkdirs()) dir else null
    }

    /** Tells Android's file index about changed paths, so Home's tiles stay right. */
    fun reindex(context: Context, paths: Collection<String>) {
        if (paths.isEmpty()) return
        MediaScannerConnection.scanFile(context.applicationContext, paths.toTypedArray(), null, null)
    }

    private fun copyOne(src: File, dest: File): Boolean = try {
        if (src.isDirectory) src.copyRecursively(dest, overwrite = false) else {
            src.copyTo(dest, overwrite = false)
            dest.setLastModified(src.lastModified())
            true
        }
    } catch (e: Exception) {
        false
    }

    /** True if [dir] is [folder] itself or somewhere inside it. */
    fun isInside(dir: File, folder: File): Boolean {
        if (!folder.isDirectory) return false
        val d = dir.absolutePath + "/"
        val f = folder.absolutePath + "/"
        return d.startsWith(f)
    }
}
