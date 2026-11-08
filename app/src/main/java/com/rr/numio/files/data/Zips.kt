package com.rr.numio.files.data

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** One file or folder inside a ZIP, by its path in the ZIP ("Photos/Onam/1.jpg"). */
data class ZipNode(val path: String, val isDir: Boolean, val size: Long, val modified: Long) {
    val name: String get() = path.substringAfterLast('/')
}

/** Look inside, extract and make ZIP files with Android's own java.util.zip. */
object Zips {

    private fun clean(name: String): String =
        name.replace('\\', '/').trimStart('/').removePrefix("./").trimEnd('/')

    /** Every file and folder in the ZIP, including folders only implied by paths. */
    fun entries(zip: File): List<ZipNode> {
        val nodes = LinkedHashMap<String, ZipNode>()
        ZipFile(zip).use { zf ->
            for (e in zf.entries()) {
                val path = clean(e.name)
                if (path.isEmpty() || path.startsWith("__MACOSX")) continue
                // Parent folders, even if the ZIP has no entry for them
                var slash = path.indexOf('/')
                while (slash > 0) {
                    val dir = path.substring(0, slash)
                    if (dir !in nodes) nodes[dir] = ZipNode(dir, true, 0, e.time)
                    slash = path.indexOf('/', slash + 1)
                }
                nodes[path] = ZipNode(path, e.isDirectory, if (e.isDirectory) 0 else e.size.coerceAtLeast(0), e.time)
            }
        }
        return nodes.values.toList()
    }

    /** What sits directly in [inner] ("" is the top of the ZIP), as list rows. */
    fun children(all: List<ZipNode>, inner: String): List<FileItem> {
        val prefix = if (inner.isEmpty()) "" else "$inner/"
        return all.filter { it.path.startsWith(prefix) && it.path.length > prefix.length && '/' !in it.path.substring(prefix.length) }
            .map { n ->
                val kids = if (n.isDir) all.count { c -> c.path.startsWith(n.path + "/") && '/' !in c.path.substring(n.path.length + 1) } else -1
                FileItem(n.path, n.name, n.isDir, n.size, n.modified, kids)
            }
    }

    /**
     * Extracts [chosen] paths (folders include everything inside; empty = the whole ZIP)
     * into [target], dropping the [inner] folder part of each path. Returns how many files failed.
     */
    fun extract(zip: File, chosen: List<String>, inner: String, target: File, p: JobProgress? = null): Int {
        target.mkdirs()
        val root = target.canonicalPath + File.separator
        val prefix = if (inner.isEmpty()) "" else "$inner/"
        var failed = 0
        ZipFile(zip).use { zf ->
            fun wants(path: String) =
                if (chosen.isEmpty()) path.startsWith(prefix) else chosen.any { path == it || path.startsWith("$it/") }
            if (p != null) p.total = zf.entries().asSequence().filter { !it.isDirectory && wants(clean(it.name)) }.sumOf { it.size.coerceAtLeast(0) }
            for (e in zf.entries()) {
                val path = clean(e.name)
                if (path.isEmpty() || path.startsWith("__MACOSX")) continue
                val wanted = wants(path)
                if (!wanted) continue
                val rel = path.removePrefix(prefix)
                if (rel.isEmpty()) continue
                var out = File(target, rel)
                // Never write outside the target folder ("../" tricks in a ZIP)
                if (!out.canonicalPath.startsWith(root)) { failed++; continue }
                try {
                    if (e.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        if (out.exists()) {
                            val parent = out.parentFile ?: target
                            out = File(parent, uniqueName(out.name) { File(parent, it).exists() })
                        }
                        p?.start(out.name)
                        zf.getInputStream(e).use { input -> FileOutputStream(out).use { output -> copy(input, output, p) } }
                        if (e.time > 0) out.setLastModified(e.time)
                    }
                } catch (ex: CancelledJob) {
                    if (out.isFile) out.delete() // the half-written file
                    throw ex
                } catch (ex: Exception) {
                    failed++
                }
            }
        }
        return failed
    }

    /** Pulls one file out to [dir] so another app can open it. */
    fun extractOne(zip: File, path: String, dir: File): File? {
        dir.mkdirs()
        ZipFile(zip).use { zf ->
            val e = zf.entries().asSequence().firstOrNull { clean(it.name) == path && !it.isDirectory } ?: return null
            val out = File(dir, path.substringAfterLast('/'))
            zf.getInputStream(e).use { input -> FileOutputStream(out).use { input.copyTo(it) } }
            return out
        }
    }

    /** Makes [dest] from files and folders, each kept under its own name. */
    fun create(sources: List<File>, dest: File, p: JobProgress? = null): Boolean = try {
        p?.total = FileOps.sizeOf(sources)
        ZipOutputStream(FileOutputStream(dest)).use { zos ->
            for (src in sources) {
                val base = src.parentFile ?: continue
                src.walkTopDown().forEach { f ->
                    if (f.absolutePath == dest.absolutePath) return@forEach
                    val rel = f.relativeTo(base).invariantSeparatorsPath
                    if (f.isDirectory) {
                        zos.putNextEntry(ZipEntry("$rel/").apply { time = f.lastModified() })
                        zos.closeEntry()
                    } else {
                        p?.start(f.name)
                        zos.putNextEntry(ZipEntry(rel).apply { time = f.lastModified() })
                        f.inputStream().use { copy(it, zos, p) }
                        zos.closeEntry()
                    }
                }
            }
        }
        true
    } catch (e: Exception) {
        dest.delete() // also when cancelled: no half-made ZIP left behind
        if (e is CancelledJob) throw e
        false
    }

    private fun copy(input: java.io.InputStream, output: java.io.OutputStream, p: JobProgress?) {
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
