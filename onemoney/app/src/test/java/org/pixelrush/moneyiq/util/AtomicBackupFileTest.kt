package org.syalosovetskyi.onemoney.util

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class AtomicBackupFileTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `interrupted write preserves existing backup and removes staging`() {
        val target = folder.newFile("backup.json").apply { writeText("previous") }
        try {
            writeAtomicBackup(target) { it.write("partial".toByteArray()); throw IOException("injected") }
            fail("Expected write failure")
        } catch (expected: IOException) {
            // Deliberately injected write error; previous bytes are asserted below.
        }
        assertEquals("previous", target.readText())
        assertEquals(listOf("backup.json"), folder.root.list()!!.toList())
    }

    @Test fun `successful write publishes complete replacement`() {
        val target = folder.newFile("backup.json").apply { writeText("previous") }
        writeAtomicBackup(target) { it.write("complete".toByteArray()) }
        assertEquals("complete", target.readText())
        assertEquals(listOf("backup.json"), folder.root.list()!!.toList())
    }
}
