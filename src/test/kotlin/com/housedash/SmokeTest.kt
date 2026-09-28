package com.housedash

import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun `the build runs tests on the pinned toolchain`() {
        assertEquals(21, Runtime.version().feature())
    }
}
