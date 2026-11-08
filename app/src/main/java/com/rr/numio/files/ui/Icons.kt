package com.rr.numio.files.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.rr.numio.files.data.Category

/** Line icons on a 24×24 grid with round ends, tinted by Icon(tint = …). */
object FIcons {
    private fun stroke(name: String, vararg paths: String, width: Float = 1.8f) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun fill(name: String, vararg paths: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { addPath(addPathNodes(it), fill = SolidColor(Color.Black)) }
        }.build()

    val Back = stroke("Back", "M15 5l-7 7l7 7", width = 2f)
    val Close = stroke("Close", "M6 6l12 12M18 6L6 18")
    val Search = stroke("Search", "M11 4a7 7 0 1 1 0 14a7 7 0 1 1 0 -14z", "M20 20l-3.5-3.5")
    val Chevron = stroke("Chevron", "M9 6l6 6l-6 6", width = 2f)
    val Check = stroke("Check", "M5 12l5 5l9-10", width = 3f)
    val Plus = stroke("Plus", "M12 5v14M5 12h14", width = 2f)
    val FolderPlus = stroke("FolderPlus", "M3 6h7l2 2h9v11H3z", "M12 11v5M9.5 13.5h5")
    val Sort = stroke("Sort", "M7 4v16M3 16l4 4l4-4", "M14 6h7M14 12h5M14 18h3")
    val SelectAll = stroke("SelectAll", "M4 7V4h3M17 4h3v3M20 17v3h-3M7 20H4v-3", "M8 12l3 3l5-6")
    val More = fill(
        "More",
        "M12 4.5a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0 -3.2z",
        "M12 10.4a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0 -3.2z",
        "M12 16.3a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0 -3.2z",
    )

    val Home = stroke("Home", "M4 11l8-7l8 7v9H4z", "M10 20v-6h4v6")
    val Folder = stroke("Folder", "M3 6h7l2 2h9v11H3z")
    val Star = stroke("Star", "M12 3.5l2.6 5.4l5.9.8l-4.3 4.1l1 5.8L12 16.8l-5.2 2.8l1-5.8l-4.3-4.1l5.9-.8z", width = 1.6f)
    val StarFilled = fill("StarFilled", "M12 3.5l2.6 5.4l5.9.8l-4.3 4.1l1 5.8L12 16.8l-5.2 2.8l1-5.8l-4.3-4.1l5.9-.8z")
    val Gear = stroke(
        "Gear",
        "M12 9a3 3 0 1 0 0 6a3 3 0 0 0 0 -6z",
        "M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1",
    )

    val Image = stroke("Image", "M4 5h16v14H4z", "M4 16l5-5l4 4l3-3l4 4", "M15.5 8.5h.01")
    val Video = stroke("Video", "M3 6h13v12H3z", "M16 10l5-3v10l-5-3")
    val Audio = stroke("Audio", "M9 18V5l11-2v13", "M6 15a3 3 0 1 1 0 6a3 3 0 1 1 0 -6z", "M17 13a3 3 0 1 1 0 6a3 3 0 1 1 0 -6z")
    val Doc = stroke("Doc", "M6 3h8l4 4v14H6z", "M14 3v4h4", "M9 12h6M9 16h6")
    val Download = stroke("Download", "M12 4v11", "M7 10l5 5l5-5", "M5 20h14")
    val Apk = stroke("Apk", "M7 3h10v18H7z", "M11 18h2")
    val Archive = stroke("Archive", "M5 4h14v16H5z", "M12 4v2M12 8v2M12 12v2", "M10.5 15h3v3h-3z")
    val Trash = stroke("Trash", "M4 7h16", "M9 7V4h6v3", "M6 7l1 13h10l1-13")

    val Copy = stroke("Copy", "M8 8h12v12H8z", "M4 16V4h12")
    val Move = stroke("Move", "M3 6h7l2 2h9v11H3z", "M10 13h6M13 10l3 3l-3 3")
    val Share = stroke("Share", "M12 15V4M8 8l4-4l4 4", "M5 13v6h14v-6")
    val Rename = stroke("Rename", "M4 20h4l11-11l-4-4L4 16z", "M13 7l4 4")
    val Restore = stroke("Restore", "M4 12a8 8 0 1 0 2.3-5.6", "M4 4v4h4")
    val OpenWith = stroke("OpenWith", "M14 4h6v6", "M20 4l-9 9", "M18 14v5a1 1 0 0 1 -1 1H5a1 1 0 0 1 -1 -1V7a1 1 0 0 1 1 -1h5")
    val Info = stroke("Info", "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0 -18z", "M12 11v6", "M12 7.5h.01", width = 1.8f)
    val Play = fill("Play", "M8 5v14l11-7z")
    val Paste = stroke("Paste", "M8 4h8v3H8z", "M8 5.5H5v15h14v-15h-3")

    fun forCategory(c: Category): ImageVector = when (c) {
        Category.IMAGES -> Image
        Category.VIDEOS -> Video
        Category.AUDIO -> Audio
        Category.DOCS -> Doc
        Category.DOWNLOADS -> Download
        Category.APKS -> Apk
        Category.ARCHIVES -> Archive
        Category.TRASH -> Trash
    }
}
