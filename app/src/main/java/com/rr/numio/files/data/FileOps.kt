package com.rr.numio.files.data

import android.content.Context
import android.media.MediaScannerConnection
import java.io.File

/** Copy, move, rename and new folder. Everything here runs off the main thread. */
object FileOps {

    /** What a copy or move did. */
    data class Result(val done: Int, val failed: Int, val cancelled: Boolean)

    /** Total bytes in files and folders, for the progress bar. */
    fun sizeOf(files: List<File>): Long = files.sumOf { f ->
        if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()
    }

    /** Copies [sources] into [target]; taken names get " (1)". */
    fun copy(sources: List<File>, target: File, p: JobProgress? = null): Result {
        p?.total = sizeOf(sources)
        var done = 0
        var failed = 0
        for (src in sources) {
            if (isInside(target, src)) { failed++; continue } // a folder into itself
            val dest = File(target, uniqueName(src.name) { File(target, it).exists() })
            try {
                copyTree(src, dest, p)
                done++
            } catch (e: CancelledJob) {
                dest.deleteRecursively() // only the half-made copy, which we just created
                return Result(done, failed, cancelled = true)
            } catch (e: Exception) {
                failed++
            }
        }
        p?.finish()
        return Result(done, failed, cancelled = false)
    }

    /**
     * Moves [sources] into [target]: a quick rename on the same storage, else copy
     * then delete. The original is only deleted after its copy is complete.
     */
    fun move(sources: List<File>, target: File, p: JobProgress? = null): Result {
        p?.total = sizeOf(sources)
        var done = 0
        var failed = 0
        for (src in sources) {
            if (src.parentFile?.absolutePath == target.absolutePath) { done++; continue } // already there
            if (isInside(target, src)) { failed++; continue }
            val size = if (p != null) sizeOf(listOf(src)) else 0L
            val dest = File(target, uniqueName(src.name) { File(target, it).exists() })
            p?.let { if (it.isCancelled) return Result(done, failed, cancelled = true) }
            if (src.renameTo(dest)) {
                p?.add(size)
                done++
                continue
            }
            try {
                copyTree(src, dest, p)
                if (src.deleteRecursively()) done++ else failed++
            } catch (e: CancelledJob) {
                dest.deleteRecursively()
                return Result(done, failed, cancelled = true)
            } catch (e: Exception) {
                dest.deleteRecursively()
                failed++
            }
        }
        p?.finish()
        return Result(done, failed, cancelled = false)
    }

    private fun copyTree(src: File, dest: File, p: JobProgress?) {
        if (src.isDirectory) {
            if (!dest.mkdirs() && !dest.isDirectory) throw java.io.IOException("Can't create ${dest.name}")
            for (child in src.listFiles() ?: emptyArray()) copyTree(child, File(dest, child.name), p)
            dest.setLastModified(src.lastModified())
        } else {
            p?.start(src.name)
            src.inputStream().use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        p?.add(n.toLong())
                        p?.check()
                    }
                }
            }
            dest.setLastModified(src.lastModified())
        }
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

    /** True if [dir] is [folder] itself or somewhere inside it. */
    fun isInside(dir: File, folder: File): Boolean {
        if (!folder.isDirectory) return false
        val d = dir.absolutePath + "/"
        val f = folder.absolutePath + "/"
        return d.startsWith(f)
    }
}
