package com.housedash.app

import com.housedash.domain.case.CaseError
import com.housedash.domain.shared.ContactDetail
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreateCaseTest {
    private val cases = OneCaseRepository()

    private val createCase = CreateCase(cases, CountingCaseIdentifiers(), FIXED_CLOCK)

    @Test
    fun `a described case is stored with the photographs and the clock the use case was given`() {
        val stored = valueOf(createCase.handle(intake()))
        assertEquals("cs_1", stored.id.value)
        assertTrue(!stored.alreadyStored)
        val case = cases.storedById.getValue("cs_1")
        assertEquals(TAP_DESCRIPTION, case.description.text)
        assertEquals(listOf("ph_1", "ph_2"), case.photos.map { it.value })
        assertEquals(CREATED_AT, case.createdAt)
        assertEquals(CREATED_AT, case.describedAt)
    }

    @Test
    fun `a case with no photographs is legal because an intake may carry none`() {
        val stored = valueOf(createCase.handle(intake(photos = null)))
        assertEquals(emptyList(), cases.storedById.getValue(stored.id.value).photos)
    }

    @Test
    fun `the same intake key from the same nester stores one case and answers with the first identifier`() {
        val first = valueOf(createCase.handle(intake()))
        val second = valueOf(createCase.handle(intake()))
        assertEquals(first.id.value, second.id.value)
        assertTrue(second.alreadyStored)
        assertEquals(1, cases.storedById.size)
    }

    @Test
    fun `a different intake key from the same nester stores a second case`() {
        valueOf(createCase.handle(intake()))
        val second = valueOf(createCase.handle(intake(key = "intake-2")))
        assertTrue(!second.alreadyStored)
        assertEquals(2, cases.storedById.size)
    }

    @Test
    fun `the same intake key from a different nester stores a second case`() {
        valueOf(createCase.handle(intake()))
        val second = valueOf(createCase.handle(intake(owner = "ns_2")))
        assertTrue(!second.alreadyStored)
        assertEquals(2, cases.storedById.size)
    }

    @Test
    fun `a description carrying a phone number is refused and no case is stored`() {
        val failure = failureOf(createCase.handle(intake(description = PHONE_IN_A_DESCRIPTION)))
        assertEquals(
            CreateCaseFailure.Rejected(
                CaseError.ContactDetailsInDescription(setOf(ContactDetail.PhoneNumber)),
            ),
            failure,
            "I7 is enforced by the server, so the use case refuses the description the domain filter refuses. " +
                "The client filter is convenience and this is the boundary",
        )
        assertEquals(0, cases.storedById.size)
    }

    @Test
    fun `a description below the floor is refused as a typed error rather than thrown`() {
        val failure = failureOf(createCase.handle(intake(description = "tap drips")))
        assertIs<CreateCaseFailure.Rejected>(failure)
        assertIs<CaseError.DescriptionTooShort>(failure.error)
    }

    @Test
    fun `more photographs than the aggregate allows is refused`() {
        val failure = failureOf(createCase.handle(intake(photos = listOf("ph_1", "ph_2", "ph_3", "ph_4", "ph_5"))))
        assertIs<CreateCaseFailure.Rejected>(failure)
        assertIs<CaseError.TooManyPhotos>(failure.error)
    }

    @Test
    fun `a repeated photograph is refused`() {
        val failure = failureOf(createCase.handle(intake(photos = listOf("ph_1", "ph_1"))))
        assertIs<CreateCaseFailure.Rejected>(failure)
        assertIs<CaseError.DuplicatePhoto>(failure.error)
    }

    @Test
    fun `a malformed photograph identifier is refused`() {
        val failure = failureOf(createCase.handle(intake(photos = listOf("ph_1", "nope"))))
        assertIs<CreateCaseFailure.Rejected>(failure)
        assertIs<CaseError.MalformedPhotoId>(failure.error)
    }

    @Test
    fun `a malformed owner is refused`() {
        val failure = failureOf(createCase.handle(intake(owner = "nope")))
        assertIs<CreateCaseFailure.Rejected>(failure)
        assertIs<CaseError.MalformedNesterId>(failure.error)
    }

    @Test
    fun `each absent field is refused by its own name`() {
        assertEquals(CreateCaseFailure.IntakeAbsent, failureOf(createCase.handle(null)))
        assertEquals(CreateCaseFailure.OwnerAbsent, failureOf(createCase.handle(intake(owner = null))))
        assertEquals(
            CreateCaseFailure.DescriptionAbsent,
            failureOf(createCase.handle(intake(description = null))),
        )
        assertEquals(CreateCaseFailure.IntakeKeyAbsent, failureOf(createCase.handle(intake(key = null))))
    }

    @Test
    fun `an intake key outside the token shape is refused`() {
        assertEquals(CreateCaseFailure.MalformedIntakeKey, failureOf(createCase.handle(intake(key = ""))))
        assertEquals(
            CreateCaseFailure.MalformedIntakeKey,
            failureOf(createCase.handle(intake(key = "intake 1"))),
        )
        assertEquals(
            CreateCaseFailure.MalformedIntakeKey,
            failureOf(createCase.handle(intake(key = "k".repeat(IntakeKey.MOST_CHARACTERS + 1)))),
        )
    }

    @Test
    fun `an identifier source that cannot mint a case identifier is its own failure and not a rejection`() {
        val refusing = CreateCase(cases, RefusingCaseIdentifiers(), FIXED_CLOCK)
        val failure = failureOf(refusing.handle(intake()))
        assertIs<CreateCaseFailure.CaseIdRefused>(failure)
        assertIs<CaseError.MalformedCaseId>(failure.error)
        assertEquals(0, cases.storedById.size)
    }
}
