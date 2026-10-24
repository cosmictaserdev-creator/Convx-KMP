package com.convx.music.client.ios

import com.convx.music.account.SecureSessionStore
import com.convx.music.client.ios.keychaincompat.convx_keychain_clear
import com.convx.music.client.ios.keychaincompat.convx_keychain_read
import com.convx.music.client.ios.keychaincompat.convx_keychain_write
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.getPointer
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.cinterop.size_tVar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stores the InnerTube session as a device-only Keychain generic password. */
internal class IosSecureSessionStore : SecureSessionStore {
    override suspend fun read(): String? = withContext(Dispatchers.Default) {
        memScoped {
            val buffer = allocArray<ByteVar>(MAX_SESSION_BYTES)
            val length = alloc<size_tVar>()
            val status = convx_keychain_read(
                KEYCHAIN_SERVICE.cstr.getPointer(this),
                KEYCHAIN_ACCOUNT.cstr.getPointer(this),
                buffer,
                MAX_SESSION_BYTES.convert(),
                length.ptr,
            )
            when (status) {
                ITEM_NOT_FOUND -> null
                SUCCESS -> buffer.readBytes(length.value.convert()).decodeToString()
                else -> error("iOS Keychain read failed with status $status.")
            }
        }
    }

    override suspend fun write(value: String) = withContext(Dispatchers.Default) {
        val bytes = value.encodeToByteArray()
        require(bytes.size <= MAX_SESSION_BYTES) { "The session is too large for secure storage." }
        memScoped {
            val service = KEYCHAIN_SERVICE.cstr.getPointer(this)
            val account = KEYCHAIN_ACCOUNT.cstr.getPointer(this)
            bytes.usePinned { pinned ->
                val status = convx_keychain_write(
                    service,
                    account,
                    pinned.addressOf(0),
                    bytes.size.convert(),
                )
                check(status == SUCCESS) { "iOS Keychain write failed with status $status." }
            }
        }
    }

    override suspend fun clear() = withContext(Dispatchers.Default) {
        memScoped {
            val status = convx_keychain_clear(
                KEYCHAIN_SERVICE.cstr.getPointer(this),
                KEYCHAIN_ACCOUNT.cstr.getPointer(this),
            )
            check(status == SUCCESS || status == ITEM_NOT_FOUND) {
                "iOS Keychain clear failed with status $status."
            }
        }
    }

    private companion object {
        const val KEYCHAIN_SERVICE = "com.convx.music.youtube"
        const val KEYCHAIN_ACCOUNT = "session"
        const val MAX_SESSION_BYTES = 65_536
        const val SUCCESS = 0
        const val ITEM_NOT_FOUND = -25_300
    }
}
