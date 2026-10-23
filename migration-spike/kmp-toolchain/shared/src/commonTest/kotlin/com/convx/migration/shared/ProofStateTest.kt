package com.convx.migration.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class ProofStateTest {
    @Test
    fun includesRequestedPlatformInState() {
        assertEquals(
            ProofState(title = "Convx shared screen", platform = "iOS"),
            proofState("iOS"),
        )
    }
}
