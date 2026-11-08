package com.rr.numio.files

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
}
