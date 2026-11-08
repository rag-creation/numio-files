package com.rr.numio.files

import com.rr.numio.files.data.Cleanup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CleanupTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun phone(): File {
        val root = tmp.newFolder("phone")
        File(root, "Empty/inner/deeper").mkdirs()          // whole tree empty → one entry, 2 nested
        File(root, "Photos/Old").mkdirs()                  // Old is empty, Photos has a file
        File(root, "Photos/1.jpg").writeText("x")
        File(root, "Backup").mkdirs()
        File(root, "Backup/.backup").writeText("secret")   // hidden file: NOT empty
        File(root, "DCIM").mkdirs()                        // standard folder: kept even if empty
        File(root, "DCIM/Camera").mkdirs()                 // …but an empty folder inside it is listed
        File(root, "Android/data/com.app").mkdirs()        // never touched
        return root
    }

    @Test fun findsOnlyTrulyEmptyFolders() {
        val root = phone()
        val found = Cleanup.emptyFolders(root).associate { it.path.removePrefix(root.path + "/") to it.nested }
        assertEquals(mapOf("DCIM/Camera" to 0, "Empty" to 2, "Photos/Old" to 0), found)
    }

    @Test fun deletesEmptyTreesButNeverFiles() {
        val root = phone()
        assertTrue(Cleanup.deleteEmptyTree(File(root, "Empty")))
        assertFalse(File(root, "Empty").exists())
        // A folder with a hidden file is refused and the file stays
        assertFalse(Cleanup.deleteEmptyTree(File(root, "Backup")))
        assertTrue(File(root, "Backup/.backup").exists())
        // A file that appears after the scan stops the delete
        File(root, "Photos/Old/new.txt").writeText("hi")
        assertFalse(Cleanup.deleteEmptyTree(File(root, "Photos/Old")))
        assertTrue(File(root, "Photos/Old/new.txt").exists())
    }
}
