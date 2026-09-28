package com.housedash

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class IntegrationHarnessTest {
    @Test
    fun `the integration test source set runs as part of check`() {
        assertEquals(4, 2 + 2)
    }
}
