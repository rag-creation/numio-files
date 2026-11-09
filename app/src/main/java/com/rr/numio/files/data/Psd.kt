package com.rr.numio.files.data

import java.io.DataInputStream
import java.io.File
import java.io.InputStream

/** A decoded picture as ARGB pixels (no Android classes, so it can be tested). */
class Pixels(val width: Int, val height: Int, val argb: IntArray)

/**
 * Reads the flat "whole picture" that Photoshop saves at the end of a .psd
 * (with the default "Maximize compatibility" setting). Layers are not read.
 * Supports 8-bit RGB and grayscale, raw or RLE-packed. Pure Kotlin, offline.
 */
object Psd {
    private const val RGB = 3
    private const val GRAY = 1

    class Unsupported(msg: String) : Exception(msg)

    /** Decodes with every [sample]-th pixel kept, so big files stay light on memory. */
    fun decode(file: File, maxSide: Int): Pixels =
        file.inputStream().buffered(1 shl 16).use { decode(it, maxSide) }

    fun decode(input: InputStream, maxSide: Int): Pixels {
        val d = DataInputStream(input)
        if (d.readInt() != 0x38425053) throw Unsupported("Not a PSD") // "8BPS"
        val version = d.readUnsignedShort()
        if (version != 1) throw Unsupported("Big PSB files aren't supported")
        d.skipFully(6)
        val channels = d.readUnsignedShort()
        val height = d.readInt()
        val width = d.readInt()
        val depth = d.readUnsignedShort()
        val mode = d.readUnsignedShort()
        if (depth != 8) throw Unsupported("Only 8-bit PSDs are supported")
        if (mode != RGB && mode != GRAY) throw Unsupported("Only RGB and grayscale PSDs are supported")
        // Colour mode data, image resources, layers: skip all three
        repeat(3) { d.skipFully(d.readInt().toLong() and 0xFFFFFFFFL) }

        val compression = d.readUnsignedShort()
        val sample = maxOf(1, (maxOf(width, height) + maxSide - 1) / maxSide)
        val outW = (width + sample - 1) / sample
        val outH = (height + sample - 1) / sample
        val used = if (mode == RGB) minOf(channels, 4) else minOf(channels, 2)
        val planes = Array(used) { ByteArray(outW * outH) }

        val row = ByteArray(width)
        when (compression) {
            0 -> for (c in 0 until channels) {
                for (y in 0 until height) {
                    d.readFully(row)
                    if (c < used && y % sample == 0) keep(row, planes[c], y / sample, outW, sample)
                }
            }
            1 -> {
                val counts = IntArray(channels * height) { d.readUnsignedShort() }
                var i = 0
                for (c in 0 until channels) {
                    for (y in 0 until height) {
                        val n = counts[i++]
                        if (c < used && y % sample == 0) {
                            unpack(d, n, row)
                            keep(row, planes[c], y / sample, outW, sample)
                        } else d.skipFully(n.toLong())
                    }
                }
            }
            else -> throw Unsupported("This PSD uses a packing Files can't read")
        }

        val px = IntArray(outW * outH)
        for (i in px.indices) {
            px[i] = if (mode == RGB) {
                val r = planes[0][i].toInt() and 0xFF
                val g = planes.getOrNull(1)?.get(i)?.toInt()?.and(0xFF) ?: r
                val b = planes.getOrNull(2)?.get(i)?.toInt()?.and(0xFF) ?: r
                val a = planes.getOrNull(3)?.get(i)?.toInt()?.and(0xFF) ?: 255
                (a shl 24) or (r shl 16) or (g shl 8) or b
            } else {
                val v = planes[0][i].toInt() and 0xFF
                val a = planes.getOrNull(1)?.get(i)?.toInt()?.and(0xFF) ?: 255
                (a shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        return Pixels(outW, outH, px)
    }

    private fun keep(row: ByteArray, plane: ByteArray, outY: Int, outW: Int, sample: Int) {
        val base = outY * outW
        var x = 0
        var o = 0
        while (o < outW) {
            plane[base + o] = row[x]
            x += sample
            o++
        }
    }

    /** PackBits, as Photoshop writes it: n ≥ 0 copies n+1 bytes; n < 0 repeats the next byte 1−n times. */
    private fun unpack(d: DataInputStream, packed: Int, out: ByteArray) {
        var read = 0
        var o = 0
        while (read < packed && o < out.size) {
            val n = d.readByte().toInt()
            read++
            if (n >= 0) {
                val len = minOf(n + 1, out.size - o)
                d.readFully(out, o, len)
                if (n + 1 > len) d.skipFully((n + 1 - len).toLong())
                o += len
                read += n + 1
            } else if (n != -128) {
                val b = d.readByte()
                read++
                val len = minOf(1 - n, out.size - o)
                java.util.Arrays.fill(out, o, o + len, b)
                o += len
            }
        }
        if (read < packed) d.skipFully((packed - read).toLong())
    }

    private fun DataInputStream.skipFully(n: Long) {
        var left = n
        while (left > 0) {
            val s = skip(left)
            if (s <= 0) {
                if (read() < 0) throw java.io.EOFException()
                left--
            } else left -= s
        }
    }
}
