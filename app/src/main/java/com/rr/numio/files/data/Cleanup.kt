package com.rr.numio.files.data

import java.io.File

/** A folder with nothing at all inside, except maybe more empty folders. */
data class EmptyFolder(val path: String, val nested: Int)

/**
 * Finds things to tidy. Built to never lose a file:
 * - "Empty" means nothing inside at all, hidden files included.
 * - A folder Files can't look into counts as NOT empty and is left alone.
 * - Android/ (other apps' private data, which can look empty from outside)
 *   is never scanned, and the standard folders like DCIM are kept.
 * - Deleting uses the plain folder delete, which Android refuses for
 *   any folder that has something in it.
 */
object Cleanup {
    /** Top-level folders apps expect to exist; kept even when empty. */
    val keepTop = setOf(
        "Android", "DCIM", "Download", "Pictures", "Music", "Movies", "Documents", "Alarms",
        "Notifications", "Ringtones", "Podcasts", "Audiobooks", "Recordings",
    )

    fun emptyFolders(root: File): List<EmptyFolder> {
        val found = ArrayList<EmptyFolder>()
        val kids = root.listFiles() ?: return found
        for (child in kids) {
            if (!child.isDirectory || child.name == "Android") continue
            val n = check(child, keep = child.name in keepTop, found = found)
            if (n > 0) found.add(EmptyFolder(child.absolutePath, n - 1))
        }
        return found.sortedBy { it.path.lowercase() }
    }

    /** Returns how many folders are in this empty tree (itself included), or -1 if it's not empty. */
    private fun check(dir: File, keep: Boolean, found: MutableList<EmptyFolder>): Int {
        if (isLink(dir)) return -1
        val kids = dir.listFiles() ?: return -1 // can't look inside: treat as not empty
        var allEmpty = true
        val emptyKids = ArrayList<Pair<File, Int>>()
        for (k in kids) {
            if (!k.isDirectory) {
                allEmpty = false
                continue
            }
            val n = check(k, keep = false, found = found)
            if (n < 0) allEmpty = false else emptyKids.add(k to n)
        }
        if (allEmpty && !keep) return 1 + emptyKids.sumOf { it.second }
        // This folder stays: list its empty sub-folders as their own entries
        for ((k, n) in emptyKids) found.add(EmptyFolder(k.absolutePath, n - 1))
        return -1
    }

    private fun isLink(f: File): Boolean = try {
        f.canonicalPath != f.absolutePath
    } catch (e: Exception) {
        true
    }

    /**
     * Deletes an empty folder tree from the bottom up. Stops at anything that
     * isn't an empty folder. Returns true only if the whole tree is gone.
     */
    fun deleteEmptyTree(dir: File): Boolean {
        if (!dir.isDirectory || isLink(dir)) return false
        val kids = dir.listFiles() ?: return false
        var ok = true
        for (k in kids) {
            if (!k.isDirectory) return false // a file showed up: keep everything
            if (!deleteEmptyTree(k)) ok = false
        }
        // File.delete() on a folder only works when it's empty
        return ok && dir.delete()
    }

    /** Files of at least [minBytes], biggest first; Android/ is skipped. */
    fun largeFiles(root: File, minBytes: Long = 100L * 1024 * 1024, limit: Int = 100): List<FileItem> {
        val out = ArrayList<FileItem>()
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            for (f in dir.listFiles() ?: continue) {
                if (dir == root && f.name == "Android") continue
                if (f.isDirectory) {
                    if (!isLink(f)) stack.add(f)
                } else if (f.length() >= minBytes) {
                    out.add(FileItem(f.absolutePath, f.name, false, f.length(), f.lastModified()))
                }
            }
        }
        return out.sortedByDescending { it.size }.take(limit)
    }
}
