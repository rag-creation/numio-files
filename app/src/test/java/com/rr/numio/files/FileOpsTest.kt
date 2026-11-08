package com.rr.numio.files

import com.rr.numio.files.data.FileOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileOpsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun copyKeepsTheOriginalAndNumbersTakenNames() {
        val src = tmp.newFolder("Trip")
        File(src, "a.jpg").writeText("a")
        val target = tmp.newFolder("Backup")
        File(target, "Trip").mkdirs()
        val r = FileOps.copy(listOf(src), target)
        assertEquals(1, r.done)
        assertEquals("a", File(target, "Trip (1)/a.jpg").readText())
        assertTrue(File(src, "a.jpg").exists())
    }

    @Test fun moveRemovesTheOriginal() {
        val f = tmp.newFile("note.txt").apply { writeText("hi") }
        val target = tmp.newFolder("Docs")
        val r = FileOps.move(listOf(f), target)
        assertEquals(1, r.done)
        assertFalse(f.exists())
        assertEquals("hi", File(target, "note.txt").readText())
    }

    @Test fun neverCopiesAFolderIntoItself() {
        val src = tmp.newFolder("Box")
        val inside = File(src, "inner").apply { mkdirs() }
        val r = FileOps.copy(listOf(src), inside)
        assertEquals(1, r.failed)
    }
}
