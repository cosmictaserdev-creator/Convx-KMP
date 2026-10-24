package com.convx.music.client.ios

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosSecureSessionStoreTest {
    @Test
    fun sessionCanBeWrittenReadAndClearedWithoutTouchingTheAppAccount() = runTest {
        val account = "ci-roundtrip-${Random.nextLong()}"
        val store = IosSecureSessionStore(keychainAccount = account)
        val payload = "convx-keychain-roundtrip"

        try {
            store.write(payload)
            assertEquals(payload, store.read())
            store.clear()
            assertNull(store.read())
        } finally {
            store.clear()
        }
    }
}
