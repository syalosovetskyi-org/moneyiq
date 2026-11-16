package org.syalosovetskyi.onemoney.workers

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ротація бекапів (pruneOldBackups) видаляє все, що повернув listBackupFiles, крім 30
 * найсвіжіших. Тому список мусить містити ЛИШЕ власні бекапи: якщо людина вибрала для
 * бекапу спільну папку (Download, корінь Drive), чужі JSON там не наші (serg/tasks#3045).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DriveBackupRetentionTest {

    private val treeUri: Uri = DocumentsContract.buildTreeDocumentUri("test.documents", "root")

    private fun folderWith(vararg names: String): Context {
        val cursor = MatrixCursor(arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_SIZE, Document.COLUMN_MIME_TYPE))
        names.forEachIndexed { i, name ->
            cursor.addRow(arrayOf<Any>("doc$i", name, 1_000L + i, 10L, "application/json"))
        }
        val resolver = mockk<ContentResolver>()
        every {
            resolver.query(any<Uri>(), any<Array<String>>(), isNull<String>(), isNull<Array<String>>(), isNull<String>())
        } returns cursor
        val context = mockk<Context>()
        every { context.contentResolver } returns resolver
        return context
    }

    @Test fun foreign_json_in_shared_folder_is_not_a_backup_and_never_pruned() {
        val context = folderWith(
            "zerno_2026-11-01_020000_000.json",
            "onemoney_2025-01-01_020000_000.json",       // старий префікс бекапів
            "zerno_2026-11-02_020000_000.json.0b1c.tmp", // недописаний staging
            "bank-export.json",
            "settings.json",
        )
        val names = DriveBackupWorker.listBackupFiles(context, treeUri).map { it.name }.toSet()
        assertEquals(setOf("zerno_2026-11-01_020000_000.json", "onemoney_2025-01-01_020000_000.json"), names)
    }
}
