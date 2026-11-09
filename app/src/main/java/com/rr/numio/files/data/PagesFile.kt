package com.rr.numio.files.data

import java.io.File
import java.util.zip.ZipFile

/** What Files found inside an Apple Pages / Numbers / Keynote file. */
sealed interface IworkPreview {
    /** A full PDF of every page (older iWork files). */
    data class Pdf(val file: File) : IworkPreview
    /** A picture of the first page (newer iWork files). */
    data class Picture(val file: File) : IworkPreview
    data object Nothing : IworkPreview
}

/**
 * Apple's .pages, .numbers and .key files are ZIPs. Apple puts a ready-made
 * preview inside: older files a QuickLook PDF of every page, newer ones a JPEG
 * of the first page. Files shows that preview, offline, with no extra library.
 */
object PagesFile {
    val exts = setOf("pages", "numbers", "key")

    fun isIwork(name: String) = extOf(name) in exts

    private val pdfNames = listOf("QuickLook/Preview.pdf", "Preview.pdf", "preview.pdf")
    private val picNames = listOf("preview.jpg", "QuickLook/Thumbnail.jpg", "preview-web.jpg", "QuickLook/Preview.jpg", "preview-micro.jpg")

    /** Pulls the best preview out into [outDir]. */
    fun extract(file: File, outDir: File): IworkPreview {
        outDir.mkdirs()
        ZipFile(file).use { zf ->
            val names = zf.entries().asSequence().associateBy { it.name.trimStart('/') }
            fun pull(entryName: String, outName: String): File? {
                val e = names.entries.firstOrNull { it.key.equals(entryName, ignoreCase = true) }?.value ?: return null
                val out = File(outDir, outName)
                zf.getInputStream(e).use { input -> out.outputStream().use { input.copyTo(it) } }
                return out.takeIf { it.length() > 0 }
            }
            val base = file.name.substringBeforeLast('.')
            pdfNames.forEach { n -> pull(n, "$base.pdf")?.let { return IworkPreview.Pdf(it) } }
            picNames.forEach { n -> pull(n, "$base.jpg")?.let { return IworkPreview.Picture(it) } }
        }
        return IworkPreview.Nothing
    }

    /** Smallest preview picture, for list thumbnails. */
    fun thumbBytes(file: File): ByteArray? = try {
        ZipFile(file).use { zf ->
            val order = listOf("preview-micro.jpg", "QuickLook/Thumbnail.jpg", "preview-web.jpg", "preview.jpg")
            val entries = zf.entries().asSequence().toList()
            order.firstNotNullOfOrNull { n -> entries.firstOrNull { it.name.trimStart('/').equals(n, true) } }
                ?.let { e -> zf.getInputStream(e).use { it.readBytes() } }
        }
    } catch (e: Exception) {
        null
    }
}
