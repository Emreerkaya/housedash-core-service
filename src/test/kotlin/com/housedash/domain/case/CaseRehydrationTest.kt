package com.housedash.domain.case

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CaseRehydrationTest {
    private fun draftRow(
        id: String = "cs_1",
        owner: String = "ns_1",
        description: String? = null,
        describedAt: Instant? = null,
    ) = CaseRow(id, owner, CaseState.DRAFT.name, description, createdAt, describedAt)

    private fun describedRow(
        id: String = "cs_1",
        owner: String = "ns_1",
        description: String? = TAP_DESCRIPTION,
        describedAt: Instant? = com.housedash.domain.case.describedAt,
    ) = CaseRow(id, owner, CaseState.DESCRIBED.name, description, createdAt, describedAt)

    private fun faultOf(
        row: CaseRow,
        photos: List<String> = emptyList(),
    ): CaseFault = assertFailsWith<CorruptCase> { Case.rehydrate(row, photos) }.fault

    @Test
    fun `a draft row rebuilds into a draft case`() {
        val case = assertIs<DraftCase>(Case.rehydrate(draftRow(), emptyList()))
        assertEquals(caseId(), case.id)
        assertEquals(nesterId(), case.owner)
        assertEquals(createdAt, case.createdAt)
        assertEquals(CaseState.DRAFT, case.state)
    }

    @Test
    fun `a described row rebuilds into a described case with every field`() {
        val case = assertIs<DescribedCase>(Case.rehydrate(describedRow(), listOf("ph_1")))
        assertEquals(caseId(), case.id)
        assertEquals(nesterId(), case.owner)
        assertEquals(description(), case.description)
        assertEquals(listOf(photoId()), case.photos)
        assertEquals(describedAt, case.describedAt)
        assertEquals(CaseState.DESCRIBED, case.state)
    }

    @Test
    fun `a described row rebuilds with four photos in the stored order`() {
        val photos = (1..4).map { "ph_$it" }
        val case = assertIs<DescribedCase>(Case.rehydrate(describedRow(), photos))
        assertEquals(photos.map { photoId(it) }, case.photos)
    }

    @Test
    fun `a row with an unknown state is corrupt`() {
        val row = CaseRow("cs_1", "ns_1", "DIAGNOSED", null, createdAt, null)
        assertEquals(CaseFault.UNKNOWN_STATE, faultOf(row))
    }

    @Test
    fun `a row with a lowercase state is corrupt`() {
        val row = CaseRow("cs_1", "ns_1", "draft", null, createdAt, null)
        assertEquals(CaseFault.UNKNOWN_STATE, faultOf(row))
    }

    @Test
    fun `a row with a malformed case id is corrupt`() {
        assertEquals(CaseFault.MALFORMED_ID, faultOf(draftRow(id = "")))
        assertEquals(CaseFault.MALFORMED_ID, faultOf(draftRow(id = "../../etc/passwd")))
    }

    @Test
    fun `a row with a malformed owner is corrupt`() {
        assertEquals(CaseFault.MALFORMED_OWNER, faultOf(draftRow(owner = "cs_1")))
    }

    @Test
    fun `a described row with no description is corrupt`() {
        assertEquals(CaseFault.DESCRIPTION_ABSENT, faultOf(describedRow(description = null)))
    }

    @Test
    fun `a described row with no description time is corrupt`() {
        assertEquals(CaseFault.DESCRIBED_AT_ABSENT, faultOf(describedRow(describedAt = null)))
    }

    @Test
    fun `a described row whose description no longer passes its own rule is corrupt`() {
        assertEquals(CaseFault.DESCRIPTION_REJECTED, faultOf(describedRow(description = "tap")))
        assertEquals(
            CaseFault.DESCRIPTION_REJECTED,
            faultOf(describedRow(description = "call me on 917-555-0199 about it")),
        )
    }

    @Test
    fun `a described row with a malformed photo id is corrupt`() {
        assertEquals(CaseFault.MALFORMED_PHOTO_ID, faultOf(describedRow(), listOf("ph_1", "oops")))
    }

    @Test
    fun `a described row with more photos than the maximum is corrupt`() {
        assertEquals(CaseFault.TOO_MANY_PHOTOS, faultOf(describedRow(), (1..5).map { "ph_$it" }))
    }

    @Test
    fun `a described row with a repeated photo is corrupt`() {
        assertEquals(CaseFault.DUPLICATE_PHOTO, faultOf(describedRow(), listOf("ph_1", "ph_1")))
    }

    @Test
    fun `a described row described before it was created is corrupt`() {
        val row = describedRow(describedAt = Instant.parse("2026-09-24T09:00:00Z"))
        assertEquals(CaseFault.DESCRIBED_BEFORE_CREATED, faultOf(row))
    }

    @Test
    fun `a draft row carrying a description is corrupt`() {
        assertEquals(CaseFault.DESCRIPTION_PRESENT_ON_DRAFT, faultOf(draftRow(description = TAP_DESCRIPTION)))
    }

    @Test
    fun `a draft row carrying a description time is corrupt`() {
        assertEquals(CaseFault.DESCRIBED_AT_PRESENT_ON_DRAFT, faultOf(draftRow(describedAt = describedAt)))
    }

    @Test
    fun `a draft row carrying photos is corrupt`() {
        assertEquals(CaseFault.PHOTOS_PRESENT_ON_DRAFT, faultOf(draftRow(), listOf("ph_1")))
    }

    @Test
    fun `a corrupt case names its fault and nothing a user typed`() {
        val thrown = assertFailsWith<CorruptCase> { Case.rehydrate(describedRow(description = "tap"), emptyList()) }
        assertEquals(CaseFault.DESCRIPTION_REJECTED.name, thrown.message)
    }

    @Test
    fun `a rehydrated described case is the same shape a described case reaches by command`() {
        val byCommand = described()
        val byRow = assertIs<DescribedCase>(Case.rehydrate(describedRow(), listOf("ph_1")))
        assertEquals(byCommand.id, byRow.id)
        assertEquals(byCommand.owner, byRow.owner)
        assertEquals(byCommand.description, byRow.description)
        assertEquals(byCommand.photos, byRow.photos)
        assertEquals(byCommand.describedAt, byRow.describedAt)
    }
}
