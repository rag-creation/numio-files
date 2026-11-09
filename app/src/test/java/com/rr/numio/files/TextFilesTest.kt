package com.rr.numio.files

import com.rr.numio.files.data.TextFiles
import com.rr.numio.files.data.TextKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextFilesTest {
    @Test fun kinds() {
        assertEquals(TextKind.MARKDOWN, TextFiles.kindOf("README.md"))
        assertEquals(TextKind.CSV, TextFiles.kindOf("expenses.csv"))
        assertEquals(TextKind.JSON, TextFiles.kindOf("data.json"))
        assertEquals(TextKind.HTML, TextFiles.kindOf("index.html"))
        assertEquals(TextKind.PLAIN, TextFiles.kindOf("notes.txt"))
        assertEquals(TextKind.PLAIN, TextFiles.kindOf("LICENSE"))
        assertNull(TextFiles.kindOf("photo.jpg"))
    }

    @Test fun csvWithQuotesAndCommas() {
        val rows = TextFiles.csv("Name,Amount,Note\nRice,25,\"bag, 5 kg\"\n\"Say \"\"hi\"\"\",1,\n")
        assertEquals(listOf("Name", "Amount", "Note"), rows[0])
        assertEquals(listOf("Rice", "25", "bag, 5 kg"), rows[1])
        assertEquals("Say \"hi\"", rows[2][0])
        assertEquals(3, rows.size)
    }

    @Test fun semicolonCsv() {
        val rows = TextFiles.csv("a;b;c\n1;2;3")
        assertEquals(listOf("1", "2", "3"), rows[1])
    }
}
