package com.housedash.domain.case

import com.housedash.domain.shared.Outcome
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CaseTest {
    private val t0 = Instant.parse("2026-09-24T10:00:00Z")
    private val t1 = Instant.parse("2026-09-24T10:05:00Z")
    private val id = CaseId("cs_test")

    private fun description(): Description {
        val outcome = Description.of("kitchen tap drips from the base")
        return assertIs<Outcome.Ok<Description>>(outcome).value
    }

    @Test
    fun `a draft carries no description and no photos`() {
        val case = Case.draft(id, t0)
        assertEquals(CaseState.DRAFT, case.state)
        assertNull(case.description)
        assertEquals(emptyList(), case.photos)
        assertNull(case.describedAt)
    }

    @Test
    fun `describing a draft moves it to DESCRIBED and stamps the time`() {
        val case =
            assertIs<Outcome.Ok<Case>>(
                Case.draft(id, t0).describe(description(), listOf(PhotoId("ph_1")), t1),
            ).value
        assertEquals(CaseState.DESCRIBED, case.state)
        assertEquals(t1, case.describedAt)
        assertEquals(t0, case.createdAt)
    }

    @Test
    fun `describing an already described case is rejected`() {
        val once =
            assertIs<Outcome.Ok<Case>>(
                Case.draft(id, t0).describe(description(), emptyList(), t1),
            ).value
        val twice = once.describe(description(), emptyList(), t1)
        assertEquals(CaseError.AlreadyDescribed, assertIs<Outcome.Err<CaseError>>(twice).error)
    }

    @Test
    fun `exactly four photos is accepted and five is not`() {
        val four = (1..4).map { PhotoId("ph_$it") }
        assertIs<Outcome.Ok<Case>>(Case.draft(id, t0).describe(description(), four, t1))
        val five = (1..5).map { PhotoId("ph_$it") }
        val err =
            assertIs<Outcome.Err<CaseError>>(
                Case.draft(id, t0).describe(description(), five, t1),
            )
        assertEquals(CaseError.TooManyPhotos(5, 4), err.error)
    }

    @Test
    fun `a repeated photo is rejected`() {
        val err =
            assertIs<Outcome.Err<CaseError>>(
                Case.draft(id, t0).describe(description(), listOf(PhotoId("ph_1"), PhotoId("ph_1")), t1),
            )
        assertEquals(CaseError.DuplicatePhoto(PhotoId("ph_1")), err.error)
    }

    @Test
    fun `photo order is preserved`() {
        val given = listOf(PhotoId("ph_c"), PhotoId("ph_a"), PhotoId("ph_b"))
        val case =
            assertIs<Outcome.Ok<Case>>(
                Case.draft(id, t0).describe(description(), given, t1),
            ).value
        assertEquals(given, case.photos)
    }

    @Test
    fun `the photo list is defensively copied`() {
        val mutable = mutableListOf(PhotoId("ph_1"))
        val case =
            assertIs<Outcome.Ok<Case>>(
                Case.draft(id, t0).describe(description(), mutable, t1),
            ).value
        mutable.add(PhotoId("ph_2"))
        assertEquals(1, case.photos.size)
    }
}
