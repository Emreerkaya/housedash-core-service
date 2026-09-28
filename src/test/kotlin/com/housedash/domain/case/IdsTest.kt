package com.housedash.domain.case

import com.housedash.domain.shared.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class IdsTest {
    private val malformed =
        listOf(
            "",
            " ",
            "cs_",
            "nope",
            "CS_1",
            " cs_1",
            "cs_1 ",
            "cs_1\n",
            "cs_a b",
            "cs_'; drop table cases; --",
            "cs_../../etc/passwd",
            "../../etc/passwd",
            "cs_" + "x".repeat(65),
            "x".repeat(1_000_000),
        )

    @Test
    fun `a case id accepts its own prefix and body`() {
        assertEquals("cs_1", caseId("cs_1").value)
        assertEquals("cs_" + "a".repeat(64), caseId("cs_" + "a".repeat(64)).value)
        assertEquals("cs_A-z_0", caseId("cs_A-z_0").value)
    }

    @Test
    fun `a case id rejects everything that is not its shape`() {
        malformed.forEach { raw ->
            assertEquals(CaseError.MalformedCaseId, assertIs<Outcome.Err<CaseError>>(CaseId.of(raw)).error, raw)
        }
    }

    @Test
    fun `a case id rejects a body one character past the bound`() {
        assertIs<Outcome.Ok<CaseId>>(CaseId.of("cs_" + "a".repeat(64)))
        assertIs<Outcome.Err<CaseError>>(CaseId.of("cs_" + "a".repeat(65)))
    }

    @Test
    fun `a photo id accepts its own prefix and rejects another type's`() {
        assertEquals("ph_1", photoId("ph_1").value)
        assertEquals(CaseError.MalformedPhotoId, assertIs<Outcome.Err<CaseError>>(PhotoId.of("cs_1")).error)
        assertEquals(CaseError.MalformedPhotoId, assertIs<Outcome.Err<CaseError>>(PhotoId.of("")).error)
    }

    @Test
    fun `a nester id accepts its own prefix and rejects another type's`() {
        assertEquals("ns_1", nesterId("ns_1").value)
        assertEquals(CaseError.MalformedNesterId, assertIs<Outcome.Err<CaseError>>(NesterId.of("ph_1")).error)
        assertEquals(CaseError.MalformedNesterId, assertIs<Outcome.Err<CaseError>>(NesterId.of("")).error)
    }

    @Test
    fun `identifiers of the same type and value are equal and hash alike`() {
        assertEquals(caseId("cs_1"), caseId("cs_1"))
        assertEquals(caseId("cs_1").hashCode(), caseId("cs_1").hashCode())
        assertNotEquals(caseId("cs_1"), caseId("cs_2"))
    }

    @Test
    fun `identifiers of different types never compare equal`() {
        assertNotEquals<Any>(caseId("cs_1"), photoId("ph_1"))
        assertNotEquals<Any>(photoId("ph_1"), nesterId("ns_1"))
        assertNotEquals<Any>(caseId("cs_1"), "cs_1")
    }

    @Test
    fun `the published body bound is the one enforced`() {
        assertEquals(64, CaseId.MAX_BODY_LENGTH)
        assertEquals(64, PhotoId.MAX_BODY_LENGTH)
        assertEquals(64, NesterId.MAX_BODY_LENGTH)
    }
}
