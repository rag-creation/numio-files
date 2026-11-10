package com.rr.numio.files.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.caverock.androidsvg.SVG
import java.io.File

/** Draws .svg files as pictures with AndroidSVG. Nothing is fetched from the internet. */
object SvgFiles {
    /** The drawing scaled to fit [maxSide] pixels, on a transparent background. */
    fun render(f: File, maxSide: Int): Bitmap? {
        val svg = f.inputStream().use { SVG.getFromInputStream(it) }
        val box = svg.documentViewBox
        var w = if (svg.documentWidth > 0) svg.documentWidth else box?.width() ?: 0f
        var h = if (svg.documentHeight > 0) svg.documentHeight else box?.height() ?: 0f
        if (w <= 0f || h <= 0f) { w = 512f; h = 512f }
        if (box == null) svg.setDocumentViewBox(0f, 0f, w, h)
        val scale = maxSide / maxOf(w, h)
        val bw = (w * scale).toInt().coerceAtLeast(1)
        val bh = (h * scale).toInt().coerceAtLeast(1)
        svg.setDocumentWidth("100%")
        svg.setDocumentHeight("100%")
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        svg.renderToCanvas(Canvas(bmp), RectF(0f, 0f, bw.toFloat(), bh.toFloat()))
        return bmp
    }
}
