package org.syalosovetskyi.onemoney.util

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** A failed write leaves the previous backup intact; staging stays in the same directory. */
internal fun writeAtomicBackup(target: File, write: (FileOutputStream) -> Unit) {
    val staging = File.createTempFile(".zerno-", ".tmp", target.parentFile)
    try {
        FileOutputStream(staging).use { output ->
            write(output)
            output.flush()
            output.fd.sync()
        }
        Files.move(staging.toPath(), target.toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
        if (staging.exists() && !staging.delete()) {
            android.util.Log.w("AtomicBackupFile", "Could not remove backup staging file")
        }
    }
}
