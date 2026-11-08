package com.rr.numio.files

import com.rr.numio.files.data.Category
import com.rr.numio.files.data.FileItem
import com.rr.numio.files.data.SortOrder
import com.rr.numio.files.data.categoryOf
import com.rr.numio.files.data.extOf
import com.rr.numio.files.data.formatSize
import com.rr.numio.files.data.isValidName
import com.rr.numio.files.data.sortItems
import com.rr.numio.files.data.uniqueName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileItemTest {
    @Test fun sizes() {
        assertEquals("512 B", formatSize(512))
        assertEquals("1.5 KB", formatSize(1536))
        assertEquals("4.2 MB", formatSize((4.2 * 1024 * 1024).toLong()))
        assertEquals("128 GB", formatSize(128L * 1024 * 1024 * 1024))
    }

    @Test fun extensions() {
        assertEquals("jpg", extOf("IMG_1.JPG"))
        assertEquals("", extOf(".nomedia"))
        assertEquals("", extOf("README"))
        assertEquals("gz", extOf("backup.tar.gz"))
    }

    @Test fun categories() {
        assertEquals(Category.IMAGES, categoryOf("a.heic"))
        assertEquals(Category.APKS, categoryOf("Ezhuthola-v0.5.0.apk"))
        assertEquals(Category.DOCS, categoryOf("notes-backup.numio"))
        assertEquals(Category.ARCHIVES, categoryOf("stickers.zip"))
        assertNull(categoryOf("thing.bin"))
    }

    @Test fun takenNamesGetANumber() {
        val taken = setOf("photo.jpg", "photo (1).jpg", "Folder")
        assertEquals("photo (2).jpg", uniqueName("photo.jpg") { it in taken })
        assertEquals("Folder (1)", uniqueName("Folder") { it in taken })
        assertEquals("new.txt", uniqueName("new.txt") { it in taken })
    }

    @Test fun names() {
        assertTrue(isValidName("Kerala trip"))
        assertTrue(isValidName("മലയാളം.txt"))
        assertFalse(isValidName("a/b"))
        assertFalse(isValidName(".."))
        assertFalse(isValidName("   "))
    }

    @Test fun foldersComeFirst() {
        val items = listOf(
            FileItem("/a/z.txt", "z.txt", false, 10, 3),
            FileItem("/a/b", "b", true, 0, 1),
            FileItem("/a/a.txt", "a.txt", false, 99, 2),
        )
        assertEquals(listOf("b", "a.txt", "z.txt"), sortItems(items, SortOrder.NAME).map { it.name })
        assertEquals(listOf("b", "z.txt", "a.txt"), sortItems(items, SortOrder.NEWEST).map { it.name })
        assertEquals(listOf("b", "a.txt", "z.txt"), sortItems(items, SortOrder.LARGEST).map { it.name })
    }
}
