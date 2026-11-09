package com.rr.numio.files

import com.rr.numio.files.data.OfficeFiles
import org.junit.Assert.assertEquals
import org.junit.Test

class OfficeFilesTest {
    @Test fun excelColumns() {
        assertEquals(0, OfficeFiles.columnOf("A1"))
        assertEquals(25, OfficeFiles.columnOf("Z9"))
        assertEquals(26, OfficeFiles.columnOf("AA3"))
        assertEquals(27, OfficeFiles.columnOf("AB12"))
    }
}
