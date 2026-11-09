package com.rr.numio.files.data

import java.util.Locale

/** One file or folder on the phone, as the lists show it. */
data class FileItem(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    /** Folders only: how many things are inside, or -1 if not counted. */
    val childCount: Int = -1,
) {
    val ext: String get() = extOf(name)
}

/** The tiles on the Home screen. */
enum class Category(val label: String) {
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCS("Docs"),
    DOWNLOADS("Downloads"),
    APKS("APKs"),
    ARCHIVES("Archives"),
    TRASH("Trash"),
}

enum class SortOrder(val label: String) { NAME("Name"), NEWEST("Newest first"), LARGEST("Largest first") }

fun extOf(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot <= 0 || dot == name.length - 1) "" else name.substring(dot + 1).lowercase(Locale.ROOT)
}

private val images = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "avif", "dng", "raw", "svg", "psd")
private val videos = setOf("mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v", "ts", "wmv")
private val audio = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav", "amr", "wma", "mid", "midi")
private val docs = setOf(
    "pdf", "doc", "docx", "odt", "rtf", "txt", "md", "xls", "xlsx", "ods", "csv", "ppt", "pptx", "odp",
    "epub", "ai", "pages", "numbers", "key", "json", "xml", "html", "htm", "numio", "vcf", "ics",
)
private val archives = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst")

/** Which Home tile a file belongs to by its name, or null for everything else. */
fun categoryOf(name: String): Category? = when (extOf(name)) {
    in images -> Category.IMAGES
    in videos -> Category.VIDEOS
    in audio -> Category.AUDIO
    in docs -> Category.DOCS
    "apk", "apks", "xapk" -> Category.APKS
    in archives -> Category.ARCHIVES
    else -> null
}

/** 1536 → "1.5 KB", the way phones show sizes (1 KB = 1024 bytes). */
fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    return if (value >= 100) String.format(Locale.US, "%.0f %s", value, units[unit])
    else String.format(Locale.US, "%.1f %s", value, units[unit])
}

/**
 * A name that isn't taken yet in the target folder:
 * "photo.jpg" → "photo (1).jpg" → "photo (2).jpg"…
 */
fun uniqueName(name: String, isTaken: (String) -> Boolean): String {
    if (!isTaken(name)) return name
    val dot = name.lastIndexOf('.')
    val (base, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
    var n = 1
    while (isTaken("$base ($n)$ext")) n++
    return "$base ($n)$ext"
}

/** Rejects names Android can't store: empty, "." or "..", or containing a slash. */
fun isValidName(name: String): Boolean {
    val n = name.trim()
    return n.isNotEmpty() && n != "." && n != ".." && !n.contains('/') && !n.contains('\u0000') && n.length <= 255
}

fun sortItems(items: List<FileItem>, order: SortOrder): List<FileItem> {
    val byOrder = when (order) {
        SortOrder.NAME -> compareBy<FileItem> { it.name.lowercase(Locale.ROOT) }
        SortOrder.NEWEST -> compareByDescending<FileItem> { it.modified }.thenBy { it.name.lowercase(Locale.ROOT) }
        SortOrder.LARGEST -> compareByDescending<FileItem> { it.size }.thenBy { it.name.lowercase(Locale.ROOT) }
    }
    // Folders always come first, like every file manager
    return items.sortedWith(compareByDescending<FileItem> { it.isDir }.then(byOrder))
}
