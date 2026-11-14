package com.rr.numio.files

import com.rr.numio.files.data.ZipTimes
import com.rr.numio.files.data.Zips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.TimeZone
import java.util.zip.ZipOutputStream

class ZipsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun sample(): File {
        val src = tmp.newFolder("Onam")
        File(src, "photos").mkdirs()
        File(src, "photos/1.jpg").writeText("one")
        File(src, "list.txt").writeText("sadhya")
        val zip = File(tmp.root, "Onam.zip")
        assertTrue(Zips.create(listOf(src), zip))
        return zip
    }

    @Test fun makeAndLookInside() {
        val all = Zips.entries(sample())
        val top = Zips.children(all, "").map { it.name }
        assertEquals(listOf("Onam"), top)
        val inside = Zips.children(all, "Onam").map { it.name }.sorted()
        assertEquals(listOf("list.txt", "photos"), inside)
    }

    @Test fun extractEverything() {
        val out = tmp.newFolder("out")
        assertEquals(0, Zips.extract(sample(), emptyList(), "", out))
        assertEquals("one", File(out, "Onam/photos/1.jpg").readText())
    }

    @Test fun extractChosenDropsTheInnerFolder() {
        val out = tmp.newFolder("out2")
        assertEquals(0, Zips.extract(sample(), listOf("Onam/photos"), "Onam", out))
        assertEquals("one", File(out, "photos/1.jpg").readText())
        assertFalse(File(out, "list.txt").exists())
    }

    @Test fun neverWritesOutsideTheTarget() {
        val evil = File(tmp.root, "evil.zip")
        ZipOutputStream(FileOutputStream(evil)).use {
            it.putNextEntry(ZipEntry("../escaped.txt"))
            it.write("x".toByteArray())
            it.closeEntry()
        }
        val out = tmp.newFolder("out3")
        assertEquals(1, Zips.extract(evil, emptyList(), "", out))
        assertFalse(File(tmp.root, "escaped.txt").exists())
    }

    // ---- Times inside a ZIP (a ZIP's basic time has no time zone) ----

    private val t = 1_763_104_800_000L // 14 Nov 2025, 07:20 UTC

    private fun inZone(id: String, block: () -> Unit) {
        val old = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        try { block() } finally { TimeZone.setDefault(old) }
    }

    /** A ZIP with only the basic clock time, like Windows tools and GitHub make. */
    private fun basicZip(madeOnUnix: Boolean): File {
        val zip = File(tmp.root, "basic-$madeOnUnix.zip")
        ZipOutputStream(FileOutputStream(zip)).use {
            it.putNextEntry(ZipEntry("app-debug.apk").apply { time = t })
            it.write("apk".toByteArray())
            it.closeEntry()
        }
        if (madeOnUnix) {
            val b = zip.readBytes()
            for (i in 0 until b.size - 6) {
                if (b[i] == 0x50.toByte() && b[i + 1] == 0x4b.toByte() && b[i + 2] == 1.toByte() && b[i + 3] == 2.toByte()) b[i + 5] = 3
            }
            zip.writeBytes(b)
        }
        return zip
    }

    @Test fun githubZipShowsTheRealTime() {
        // GitHub writes the clock in UTC and says "made on Unix"; the phone is in Dubai
        lateinit var zip: File
        inZone("UTC") { zip = basicZip(madeOnUnix = true) }
        inZone("Asia/Dubai") {
            assertEquals(t, ZipTimes.read(zip, now = t + 3_600_000)["app-debug.apk"])
            assertEquals(t, Zips.entries(zip).single().modified)
        }
    }

    @Test fun windowsZipStaysLocalTime() {
        inZone("Asia/Dubai") {
            val zip = basicZip(madeOnUnix = false)
            assertEquals(t, ZipTimes.read(zip, now = t + 3_600_000)["app-debug.apk"])
        }
    }

    @Test fun unixZipInTheFutureAsUtcIsLocal() {
        inZone("Asia/Dubai") {
            val zip = basicZip(madeOnUnix = true) // clock written in Dubai time
            assertEquals(t, ZipTimes.read(zip, now = t + 60_000)["app-debug.apk"])
        }
    }

    @Test fun zipsWeMakeKeepTheExactTime() {
        val src = tmp.newFolder("Times")
        val f = File(src, "a.txt").apply { writeText("a"); setLastModified(t) }
        val zip = File(tmp.root, "Times.zip")
        assertTrue(Zips.create(listOf(f), zip))
        inZone("Asia/Kolkata") {
            assertEquals(t, ZipTimes.read(zip)["a.txt"])
        }
    }
}
