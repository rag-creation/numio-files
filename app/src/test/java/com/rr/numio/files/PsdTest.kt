package com.rr.numio.files

import com.rr.numio.files.data.Psd
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class PsdTest {
    /** A tiny 4×2 RGB PSD: left half red, right half blue. */
    private fun psd(rle: Boolean): ByteArray {
        val w = 4
        val h = 2
        val planes = listOf(
            byteArrayOf(-1, -1, 0, 0), // R
            byteArrayOf(0, 0, 0, 0),   // G
            byteArrayOf(0, 0, -1, -1), // B
        )
        val bos = ByteArrayOutputStream()
        val d = DataOutputStream(bos)
        d.writeInt(0x38425053); d.writeShort(1); d.write(ByteArray(6))
        d.writeShort(3); d.writeInt(h); d.writeInt(w); d.writeShort(8); d.writeShort(3)
        d.writeInt(0); d.writeInt(0); d.writeInt(0)
        if (!rle) {
            d.writeShort(0)
            planes.forEach { p -> repeat(h) { d.write(p) } }
        } else {
            d.writeShort(1)
            // Each row as two runs: 2× first byte, 2× third byte (PackBits: -1 = repeat twice)
            val rows = planes.flatMap { p -> List(h) { byteArrayOf(-1, p[0], -1, p[2]) } }
            rows.forEach { d.writeShort(it.size) }
            rows.forEach { d.write(it) }
        }
        return bos.toByteArray()
    }

    @Test fun rawPixels() {
        val px = Psd.decode(ByteArrayInputStream(psd(rle = false)), maxSide = 100)
        assertEquals(4, px.width)
        assertEquals(0xFFFF0000.toInt(), px.argb[0])
        assertEquals(0xFF0000FF.toInt(), px.argb[3])
    }

    @Test fun rlePixels() {
        val px = Psd.decode(ByteArrayInputStream(psd(rle = true)), maxSide = 100)
        assertEquals(0xFFFF0000.toInt(), px.argb[4])
        assertEquals(0xFF0000FF.toInt(), px.argb[7])
    }

    @Test fun shrinksBigPictures() {
        val px = Psd.decode(ByteArrayInputStream(psd(rle = true)), maxSide = 2)
        assertEquals(2, px.width)
        assertEquals(1, px.height)
        assertEquals(0xFFFF0000.toInt(), px.argb[0])
        assertEquals(0xFF0000FF.toInt(), px.argb[1])
    }
}
