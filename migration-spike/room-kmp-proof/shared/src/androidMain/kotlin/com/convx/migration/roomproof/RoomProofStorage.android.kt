package com.convx.migration.roomproof

import java.io.File

actual fun roomProofDatabasePath(name: String): String =
    File(System.getProperty("java.io.tmpdir"), name).absolutePath

actual fun deleteRoomProofDatabase(path: String) {
    listOf(path, "$path-wal", "$path-shm").forEach { File(it).delete() }
}
