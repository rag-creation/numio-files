package com.rr.numio.files.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import java.io.File

/**
 * Small previews for the lists, made on the phone and kept in memory only:
 * photos, video frames, an APK's own app icon, and album art.
 */
object Thumbs {
    private const val PX = 160
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    /** Files that have no preview, so we don't try again while scrolling. */
    private val none = HashSet<String>()

    /** True if the file starts like a PDF ("%PDF"): PDFs, and Illustrator .ai files saved PDF-compatible. */
    fun isPdfInside(f: File): Boolean = try {
        f.inputStream().use { s ->
            val head = ByteArray(1024)
            val n = s.read(head)
            n > 4 && String(head, 0, n, Charsets.ISO_8859_1).contains("%PDF-")
        }
    } catch (e: Exception) {
        false
    }

    fun hasPreview(name: String): Boolean = when (categoryOf(name)) {
        Category.IMAGES, Category.VIDEOS, Category.AUDIO -> true
        Category.DOCS -> extOf(name) == "pdf" || extOf(name) == "ai"
        Category.APKS -> extOf(name) == "apk"
        else -> false
    }

    fun cached(path: String, modified: Long): Bitmap? = cache.get("$path:$modified")

    fun known(path: String, modified: Long): Boolean = "$path:$modified" in none || cached(path, modified) != null

    /** Call off the main thread. */
    fun load(context: Context, path: String, modified: Long): Bitmap? {
        val key = "$path:$modified"
        cache.get(key)?.let { return it }
        if (key in none) return null
        val f = File(path)
        val bmp = try {
            when (categoryOf(f.name)) {
                Category.IMAGES -> image(f)
                Category.VIDEOS -> video(f)
                Category.AUDIO -> albumArt(f)
                Category.APKS -> apkIcon(context, f)
                else -> if (extOf(f.name) == "pdf" || extOf(f.name) == "ai") pdfPage(f) else null
            }
        } catch (e: Throwable) {
            null
        }
        if (bmp == null) synchronized(none) { none.add(key) } else cache.put(key, bmp)
        return bmp
    }

    private fun image(f: File): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ThumbnailUtils.createImageThumbnail(f, Size(PX, PX), null)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= PX && bounds.outHeight / (sample * 2) >= PX) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun video(f: File): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ThumbnailUtils.createVideoThumbnail(f, Size(PX, PX), null)
        } else {
            @Suppress("DEPRECATION")
            ThumbnailUtils.createVideoThumbnail(f.path, MediaStore.Images.Thumbnails.MINI_KIND)
        }

    private fun albumArt(f: File): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.path)
            val bytes = r.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= PX) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } finally {
            r.release()
        }
    }

    /** First page of a PDF (or PDF-compatible .ai), drawn by Android's own PDF renderer. */
    private fun pdfPage(f: File): Bitmap? {
        if (!isPdfInside(f)) return null
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                if (r.pageCount == 0) return null
                r.openPage(0).use { page ->
                    val scale = PX.toFloat() / maxOf(page.width, page.height)
                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE) // pages are drawn on white paper
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bmp
                }
            }
        }
    }

    private fun apkIcon(context: Context, f: File): Bitmap? {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(f.path, 0) ?: return null
        val app = info.applicationInfo ?: return null
        app.sourceDir = f.path
        app.publicSourceDir = f.path
        val d = app.loadIcon(pm) ?: return null
        val bmp = Bitmap.createBitmap(PX, PX, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, PX, PX)
        d.draw(Canvas(bmp))
        return bmp
    }
}
