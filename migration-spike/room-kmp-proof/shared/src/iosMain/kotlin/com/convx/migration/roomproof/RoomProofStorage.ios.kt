@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.convx.migration.roomproof

import platform.Foundation.NSTemporaryDirectory

actual fun roomProofDatabasePath(name: String): String = NSTemporaryDirectory() + name

actual fun deleteRoomProofDatabase(path: String) {
    listOf(path, "$path-wal", "$path-shm").forEach {
        platform.Foundation.NSFileManager.defaultManager.removeItemAtPath(it, error = null)
    }
}
