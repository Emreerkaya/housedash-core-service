package com.housedash.domain.case

import com.housedash.domain.shared.Outcome
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CaseTest {
    @Test
    fun `a draft carries its id, its owner and its creation time and nothing else`() {
        val case = draft()
        assertEquals(caseId(), case.id)
        assertEquals(nesterId(), case.owner)
        assertEquals(createdAt, case.createdAt)
        assertEquals(CaseState.DRAFT, case.state)
    }

    @Test
    fun `describing a draft produces a described case that stamps the time`() {
        val case = described()
        assertEquals(CaseState.DESCRIBED, case.state)
        assertEquals(createdAt, case.createdAt)
        assertEquals(describedAt, case.describedAt)
        assertEquals(description(), case.description)
        assertEquals(listOf(photoId()), case.photos)
    }

    @Test
    fun `describing carries the id and the owner across the transition`() {
        val case = described()
        assertEquals(caseId(), case.id)
        assertEquals(nesterId(), case.owner)
    }

    @Test
    fun `only the owner may describe their case`() {
        val outcome = draft().describe(nesterId("ns_intruder"), description(), emptyList(), describedAt)
        assertEquals(CaseError.NotOwner, assertIs<Outcome.Err<CaseError>>(outcome).error)
    }

    @Test
    fun `the owner check runs before the photo checks`() {
        val photos = (1..5).map { photoId("ph_$it") }
        val outcome = draft().describe(nesterId("ns_intruder"), description(), photos, describedAt)
        assertEquals(CaseError.NotOwner, assertIs<Outcome.Err<CaseError>>(outcome).error)
    }

    @Test
    fun `zero photos is accepted`() {
        val case = described(photos = emptyList())
        assertEquals(emptyList(), case.photos)
    }

    @Test
    fun `exactly four photos is accepted`() {
        val photos = (1..4).map { photoId("ph_$it") }
        assertEquals(photos, described(photos = photos).photos)
    }

    @Test
    fun `five photos is rejected with the count and the maximum`() {
        val photos = (1..5).map { photoId("ph_$it") }
        val outcome = draft().describe(nesterId(), description(), photos, describedAt)
        assertEquals(CaseError.TooManyPhotos(5, 4), assertIs<Outcome.Err<CaseError>>(outcome).error)
    }

    @Test
    fun `a repeated photo is rejected by position and not by identifier`() {
        val photos = listOf(photoId("ph_a"), photoId("ph_b"), photoId("ph_a"))
        val outcome = draft().describe(nesterId(), description(), photos, describedAt)
        assertEquals(CaseError.DuplicatePhoto(2), assertIs<Outcome.Err<CaseError>>(outcome).error)
    }

    @Test
    fun `a photo repeated immediately is reported at the second position`() {
        val photos = listOf(photoId("ph_a"), photoId("ph_a"))
        val outcome = draft().describe(nesterId(), description(), photos, describedAt)
        assertEquals(CaseError.DuplicatePhoto(1), assertIs<Outcome.Err<CaseError>>(outcome).error)
    }

    @Test
    fun `photo order is preserved`() {
        val photos = listOf(photoId("ph_c"), photoId("ph_a"), photoId("ph_b"))
        assertEquals(photos, described(photos = photos).photos)
    }

    @Test
    fun `the photo list is defensively copied`() {
        val mutable = mutableListOf(photoId("ph_1"))
        val case = described(photos = mutable)
        mutable.add(photoId("ph_2"))
        assertEquals(1, case.photos.size)
    }

    @Test
    fun `a failed describe leaves the draft untouched and returns no case`() {
        val original = draft()
        val outcome = original.describe(nesterId("ns_intruder"), description(), emptyList(), describedAt)
        assertIs<Outcome.Err<CaseError>>(outcome)
        assertEquals(CaseState.DRAFT, original.state)
        assertIs<Outcome.Ok<DescribedCase>>(
            original.describe(nesterId(), description(), emptyList(), describedAt),
        )
    }

    @Test
    fun `describing at the instant of creation is accepted`() {
        val case = described(at = createdAt)
        assertEquals(createdAt, case.describedAt)
    }

    @Test
    fun `describing before the case was created is a corrupt case and not a returned error`() {
        val before = Instant.parse("2026-09-24T09:00:00Z")
        val thrown =
            assertFailsWith<CorruptCase> {
                draft().describe(nesterId(), description(), emptyList(), before)
            }
        assertEquals(CaseFault.DESCRIBED_BEFORE_CREATED, thrown.fault)
    }

    @Test
    fun `the described case constructor rejects more photos than the maximum`() {
        val photos = (1..5).map { photoId("ph_$it") }
        val thrown =
            assertFailsWith<CorruptCase> {
                DescribedCase(caseId(), nesterId(), createdAt, description(), photos, describedAt)
            }
        assertEquals(CaseFault.TOO_MANY_PHOTOS, thrown.fault)
    }

    @Test
    fun `the described case constructor rejects a repeated photo`() {
        val photos = listOf(photoId("ph_a"), photoId("ph_a"))
        val thrown =
            assertFailsWith<CorruptCase> {
                DescribedCase(caseId(), nesterId(), createdAt, description(), photos, describedAt)
            }
        assertEquals(CaseFault.DUPLICATE_PHOTO, thrown.fault)
    }

    @Test
    fun `the described case constructor rejects a description time before creation`() {
        val thrown =
            assertFailsWith<CorruptCase> {
                DescribedCase(caseId(), nesterId(), describedAt, description(), emptyList(), createdAt)
            }
        assertEquals(CaseFault.DESCRIBED_BEFORE_CREATED, thrown.fault)
    }

    @Test
    fun `the maximum photo count is four`() {
        assertEquals(4, Case.MAX_PHOTOS)
    }
}
