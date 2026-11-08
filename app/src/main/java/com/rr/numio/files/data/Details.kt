package com.rr.numio.files.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.webkit.MimeTypeMap
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything worth knowing about a file, a folder or a group of them, as label → value rows. */
object Details {

    private fun date(millis: Long): String =
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))

    /** "Internal › Download › Telegram" instead of /storage/emulated/0/… */
    fun where(f: File): String {
        val root = Storage.root.absolutePath
        val parent = f.parentFile?.absolutePath ?: return ""
        return if (parent.startsWith(root)) ("Internal" + parent.removePrefix(root)).replace("/", " › ") else parent
    }

    private fun bytes(n: Long): String =
        if (n < 1024) "$n bytes" else "${formatSize(n)} (${String.format(Locale.US, "%,d", n)} bytes)"

    /** Call off the main thread: it reads the files. */
    fun of(context: Context, paths: List<String>): List<Pair<String, String>> {
        val files = paths.map(::File).filter { it.exists() }
        if (files.isEmpty()) return listOf("" to "This item is no longer there")
        if (files.size > 1) return group(files)
        val f = files[0]
        return if (f.isDirectory) folder(f) else file(context, f)
    }

    private fun group(files: List<File>): List<Pair<String, String>> {
        var count = 0
        var dirs = 0
        var size = 0L
        files.forEach { f ->
            if (f.isDirectory) {
                dirs++
                f.walkTopDown().drop(1).forEach { if (it.isFile) { count++; size += it.length() } else dirs++ }
            } else {
                count++
                size += f.length()
            }
        }
        return listOf(
            "Selected" to "${files.size} items",
            "Contains" to "$count files, $dirs folders",
            "Total size" to bytes(size),
        )
    }

    private fun folder(f: File): List<Pair<String, String>> {
        var files = 0
        var dirs = 0
        var size = 0L
        var hidden = 0
        f.walkTopDown().drop(1).forEach {
            if (it.name.startsWith(".")) hidden++
            if (it.isFile) { files++; size += it.length() } else dirs++
        }
        return buildList {
            add("Name" to f.name)
            add("Location" to where(f))
            add("Contains" to "$files files, $dirs folders" + if (hidden > 0) " ($hidden hidden)" else "")
            add("Size" to bytes(size))
            add("Modified" to date(f.lastModified()))
            add("Path" to f.absolutePath)
        }
    }

    private fun file(context: Context, f: File): List<Pair<String, String>> {
        val ext = extOf(f.name)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        val rows = mutableListOf(
            "Name" to f.name,
            "Location" to where(f),
            "Size" to bytes(f.length()),
            "Modified" to date(f.lastModified()),
            "Type" to (mime ?: ext.uppercase(Locale.ROOT).ifEmpty { "Unknown" }),
        )
        try {
            when (categoryOf(f.name)) {
                Category.IMAGES -> rows += image(f)
                Category.VIDEOS, Category.AUDIO -> rows += media(f)
                Category.APKS -> rows += apk(context, f)
                else -> Unit
            }
        } catch (e: Throwable) {
            // Extra details are a bonus; the basics above are enough
        }
        rows += "Path" to f.absolutePath
        return rows
    }

    private fun image(f: File): List<Pair<String, String>> = buildList {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth > 0) {
            val mp = o.outWidth.toLong() * o.outHeight / 1_000_000.0
            add("Resolution" to "${o.outWidth} × ${o.outHeight}" + if (mp >= 1) String.format(Locale.US, " (%.1f MP)", mp) else "")
        }
        val exif = ExifInterface(f.path)
        exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { add("Taken" to it.replaceFirst(':', '-').replaceFirst(':', '-')) }
        val camera = listOfNotNull(exif.getAttribute(ExifInterface.TAG_MAKE), exif.getAttribute(ExifInterface.TAG_MODEL))
            .joinToString(" ").trim()
        if (camera.isNotEmpty()) add("Camera" to camera)
        val shot = listOfNotNull(
            exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { "f/$it" },
            exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull()?.let {
                if (it < 1) "1/${(1 / it).toInt()} s" else "${it.toInt()} s"
            },
            exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { "ISO $it" },
        ).joinToString(" · ")
        if (shot.isNotEmpty()) add("Settings" to shot)
        if (exif.getLatLong(FloatArray(2))) add("Location data" to "Yes, this photo has GPS in it")
    }

    private fun media(f: File): List<Pair<String, String>> = buildList {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(f.path)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { ms ->
                val s = ms / 1000
                add("Length" to if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60))
            }
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (w != null && h != null) add("Resolution" to "$w × $h")
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { add("Title" to it) }
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { add("Artist" to it) }
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { add("Album" to it) }
        } finally {
            r.release()
        }
    }

    private fun apk(context: Context, f: File): List<Pair<String, String>> = buildList {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(f.path, 0) ?: return@buildList
        val app = info.applicationInfo
        if (app != null) {
            app.sourceDir = f.path
            app.publicSourceDir = f.path
            add("App" to app.loadLabel(pm).toString())
        }
        add("Package" to info.packageName)
        info.versionName?.let { add("Version" to it) }
    }
}
