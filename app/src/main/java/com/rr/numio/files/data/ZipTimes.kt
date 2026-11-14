package com.rr.numio.files.data

import java.io.File
import java.io.RandomAccessFile
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The real time of each file in a ZIP.
 *
 * A ZIP's basic time is a plain clock reading ("07:20") with no time zone, and
 * java.util.zip reads it as the phone's own time zone. That is wrong for ZIPs made
 * in another zone. GitHub's artifact ZIPs, for one, write UTC, so in Dubai they
 * showed 4 hours early. So we read the central directory ourselves:
 *  1. An exact time (Info-ZIP "UT" or NTFS extra field) wins: that one is UTC by definition.
 *  2. Otherwise, a ZIP made on Unix with no exact time comes from tools that write UTC
 *     (GitHub artifacts, Node's archiver); real Unix zip always adds the "UT" field.
 *     If reading it as UTC would put it in the future, it was local after all.
 *  3. Otherwise (Windows, Java) the clock reading is local time, as before.
 */
object ZipTimes {

    private const val EOCD = 0x06054b50
    private const val ZIP64_LOCATOR = 0x07064b50
    private const val ZIP64_EOCD = 0x06064b50
    private const val CEN = 0x02014b50
    private const val HOST_UNIX = 3
    private const val MAX_CEN = 64L * 1024 * 1024

    /** Times in milliseconds by raw entry name. Empty if the ZIP can't be read this way. */
    fun read(zip: File, now: Long = System.currentTimeMillis()): Map<String, Long> =
        try { readOrThrow(zip, now) } catch (e: Exception) { emptyMap() }

    private fun readOrThrow(zip: File, now: Long): Map<String, Long> {
        val out = HashMap<String, Long>()
        RandomAccessFile(zip, "r").use { f ->
            val len = f.length()
            // End of central directory: last 22 bytes plus a comment of up to 64 KB
            val tailLen = minOf(len, 22L + 0xFFFF).toInt()
            val tail = ByteArray(tailLen)
            f.seek(len - tailLen)
            f.readFully(tail)
            var eocd = -1
            for (i in tailLen - 22 downTo 0) if (u32(tail, i) == EOCD.toLong()) { eocd = i; break }
            if (eocd < 0) return out
            var count = u16(tail, eocd + 10).toLong()
            var size = u32(tail, eocd + 12)
            var offset = u32(tail, eocd + 16)
            if (count == 0xFFFFL || size == 0xFFFFFFFFL || offset == 0xFFFFFFFFL) {
                // ZIP64: the locator sits just before the end record
                val loc = len - tailLen + eocd - 20
                if (loc < 0) return out
                val l = ByteArray(20)
                f.seek(loc); f.readFully(l)
                if (u32(l, 0) != ZIP64_LOCATOR.toLong()) return out
                val z = ByteArray(56)
                f.seek(u64(l, 8)); f.readFully(z)
                if (u32(z, 0) != ZIP64_EOCD.toLong()) return out
                count = u64(z, 32); size = u64(z, 40); offset = u64(z, 48)
            }
            if (size <= 0 || size > MAX_CEN || offset + size > len) return out
            val cen = ByteArray(size.toInt())
            f.seek(offset); f.readFully(cen)

            var p = 0
            var n = 0L
            while (n < count && p + 46 <= cen.size && u32(cen, p) == CEN.toLong()) {
                val host = cen[p + 5].toInt() and 0xFF
                val dosTime = u16(cen, p + 12)
                val dosDate = u16(cen, p + 14)
                val nameLen = u16(cen, p + 28)
                val extraLen = u16(cen, p + 30)
                val commentLen = u16(cen, p + 32)
                val name = String(cen, p + 46, nameLen, Charsets.UTF_8)
                val exact = exactTime(cen, p + 46 + nameLen, extraLen)
                val time = exact ?: dosTime(dosDate, dosTime, host == HOST_UNIX, now)
                if (time != null) out[name] = time
                p += 46 + nameLen + extraLen + commentLen
                n++
            }
        }
        return out
    }

    /** UTC time from the "UT" (0x5455) or NTFS (0x000a) extra field, if there is one. */
    private fun exactTime(b: ByteArray, start: Int, len: Int): Long? {
        var i = start
        val end = minOf(start + len, b.size)
        while (i + 4 <= end) {
            val id = u16(b, i)
            val size = u16(b, i + 2)
            val data = i + 4
            if (data + size > end) break
            if (id == 0x5455 && size >= 5 && (b[data].toInt() and 1) != 0) {
                val seconds = u32(b, data + 1).toInt().toLong() // signed 32-bit seconds
                return seconds * 1000
            }
            if (id == 0x000a && size >= 32) {
                var t = data + 4 // 4 reserved bytes, then tagged attributes
                while (t + 4 <= data + size) {
                    val tag = u16(b, t)
                    val tagSize = u16(b, t + 2)
                    if (tag == 1 && tagSize >= 24 && t + 12 <= data + size) {
                        val ft = u64(b, t + 4) // 100 ns steps since 1601
                        return ft / 10_000 - 11_644_473_600_000L
                    }
                    t += 4 + tagSize
                }
            }
            i = data + size
        }
        return null
    }

    private fun dosTime(date: Int, time: Int, utc: Boolean, now: Long): Long? = try {
        val clock = LocalDateTime.of(
            ((date shr 9) and 0x7F) + 1980, (date shr 5) and 0x0F, date and 0x1F,
            (time shr 11) and 0x1F, (time shr 5) and 0x3F, (time and 0x1F) * 2,
        )
        val local = clock.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (!utc) local else {
            val asUtc = clock.toInstant(ZoneOffset.UTC).toEpochMilli()
            if (asUtc > now + 60_000) local else asUtc
        }
    } catch (e: Exception) {
        null // an impossible date (0, month 13…): let the caller fall back
    }

    private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, i: Int) = u16(b, i).toLong() or (u16(b, i + 2).toLong() shl 16)
    private fun u64(b: ByteArray, i: Int) = u32(b, i) or (u32(b, i + 4) shl 32)
}
