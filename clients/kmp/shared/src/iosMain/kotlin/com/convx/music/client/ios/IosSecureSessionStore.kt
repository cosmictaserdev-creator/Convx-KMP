package com.convx.music.client.ios

import com.convx.music.account.SecureSessionStore
import com.convx.music.client.ios.keychaincompat.convx_keychain_clear
import com.convx.music.client.ios.keychaincompat.convx_keychain_read
import com.convx.music.client.ios.keychaincompat.convx_keychain_write
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stores the InnerTube session as a device-only Keychain generic password. */
@OptIn(ExperimentalForeignApi::class)
internal class IosSecureSessionStore : SecureSessionStore {
    override suspend fun read(): String? = withContext(Dispatchers.Default) {
        memScoped {
            val buffer = allocArray<ByteVar>(MAX_SESSION_BYTES)
            val status = convx_keychain_read(
                buffer,
                MAX_SESSION_BYTES.toULong(),
            )
            when {
                status == ITEM_NOT_FOUND -> null
                status >= 0 -> buffer.readBytes(status).decodeToString()
                else -> error("iOS Keychain read failed with status $status.")
            }
        }
    }

    override suspend fun write(value: String) = withContext(Dispatchers.Default) {
        val bytes = value.encodeToByteArray()
        require(bytes.isNotEmpty()) { "The session is empty." }
        require(bytes.size <= MAX_SESSION_BYTES) { "The session is too large for secure storage." }
        memScoped {
            bytes.usePinned { pinned ->
                val status = convx_keychain_write(
                    pinned.addressOf(0),
                    bytes.size.toULong(),
                )
                check(status == SUCCESS) { "iOS Keychain write failed with status $status." }
            }
        }
    }

    override suspend fun clear() = withContext(Dispatchers.Default) {
        val status = convx_keychain_clear()
        check(status == SUCCESS || status == ITEM_NOT_FOUND) {
            "iOS Keychain clear failed with status $status."
        }
    }

    private companion object {
        const val MAX_SESSION_BYTES = 65_536
        const val SUCCESS = 0
        const val ITEM_NOT_FOUND = -25_300
    }
}
