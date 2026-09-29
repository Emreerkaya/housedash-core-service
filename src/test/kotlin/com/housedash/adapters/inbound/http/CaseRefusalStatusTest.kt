package com.housedash.adapters.inbound.http

import com.housedash.app.CreateCaseFailure
import com.housedash.domain.case.CaseError
import com.housedash.domain.shared.ContactDetail
import com.housedash.domain.shared.IdentifierFlaw
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val A_FLAW = IdentifierFlaw.WrongPrefix

private const val A_LIMIT = 20

private const val A_LENGTH = 4

private val EVERY_CASE_ERROR: List<CaseError> =
    listOf(
        CaseError.DescriptionTooShort(A_LENGTH, A_LIMIT),
        CaseError.DescriptionTooLong(A_LIMIT, A_LENGTH),
        CaseError.DescriptionNotPlainText,
        CaseError.ContactDetailsInDescription(setOf(ContactDetail.PhoneNumber)),
        CaseError.TooManyPhotos(A_LENGTH, A_LIMIT),
        CaseError.DuplicatePhoto(A_LENGTH),
        CaseError.MalformedCaseId(A_FLAW),
        CaseError.MalformedPhotoId(A_FLAW),
        CaseError.MalformedNesterId(A_FLAW),
        CaseError.NotOwner,
    )

private val EVERY_FAILURE: List<CreateCaseFailure> =
    EVERY_CASE_ERROR.map(CreateCaseFailure::Rejected) +
        listOf(
            CreateCaseFailure.CaseIdRefused(CaseError.MalformedCaseId(A_FLAW)),
            CreateCaseFailure.OwnerAbsent,
            CreateCaseFailure.DescriptionAbsent,
            CreateCaseFailure.IntakeAbsent,
            CreateCaseFailure.IntakeKeyAbsent,
            CreateCaseFailure.MalformedIntakeKey,
        )

class CaseRefusalStatusTest {
    private val production =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("com.housedash")

    private fun variantsOf(name: String): Set<String> =
        production
            .single { it.simpleName == name }
            .subclasses
            .map { it.simpleName }
            .toSortedSet()

    @Test
    fun `this test names every variant of both error types, read off the types rather than remembered`() {
        assertEquals(
            variantsOf("CaseError"),
            EVERY_CASE_ERROR.map { it.javaClass.simpleName }.toSortedSet(),
            "a variant added to CaseError that nobody mapped would otherwise be invisible here, because a " +
                "loop over the list this test holds simply stops visiting it",
        )
        assertEquals(
            variantsOf("CreateCaseFailure"),
            EVERY_FAILURE.map { it.javaClass.simpleName }.toSortedSet(),
        )
    }

    @Test
    fun `a refused description is not a server error, and no shape the domain refuses is one`() {
        val serverErrors =
            EVERY_CASE_ERROR
                .filter { statusFor(CreateCaseFailure.Rejected(it)).is5xxServerError }
                .map { it.javaClass.simpleName }
        assertEquals(
            emptyList(),
            serverErrors,
            "the whole point of a typed error across the boundary is that the client hears what it did. A " +
                "domain refusal answered with 500 is an exception that escaped in a different costume: " +
                serverErrors,
        )
    }

    @Test
    fun `each shape the domain refuses maps to the status and the reason this test names`() {
        assertEquals(
            listOf(
                "ContactDetailsInDescription 422 ContactDetailsInDescription",
                "DescriptionNotPlainText 400 DescriptionNotPlainText",
                "DescriptionTooLong 400 DescriptionTooLong",
                "DescriptionTooShort 400 DescriptionTooShort",
                "DuplicatePhoto 400 DuplicatePhoto",
                "MalformedCaseId 400 MalformedCaseId",
                "MalformedNesterId 400 MalformedNesterId",
                "MalformedPhotoId 400 MalformedPhotoId",
                "NotOwner 403 NotOwner",
                "TooManyPhotos 400 TooManyPhotos",
            ),
            EVERY_CASE_ERROR
                .map { CreateCaseFailure.Rejected(it) }
                .map { "${it.error.javaClass.simpleName} ${statusFor(it).value()} ${reasonFor(it)}" }
                .sorted(),
            "a contact detail in a description is understood and refused rather than misunderstood, which is " +
                "422; a case belonging to somebody else is 403; every other shape is a malformed request. The " +
                "reason a client branches on is the variant's own name, so a rename is an API change and this " +
                "list is where it announces itself",
        )
    }

    @Test
    fun `each failure of the use case itself maps to the status and the reason this test names`() {
        assertEquals(
            listOf(
                "CaseIdRefused 500 CaseIdRefused",
                "DescriptionAbsent 400 DescriptionAbsent",
                "IntakeAbsent 400 IntakeAbsent",
                "IntakeKeyAbsent 400 IntakeKeyAbsent",
                "MalformedIntakeKey 400 MalformedIntakeKey",
                "OwnerAbsent 400 OwnerAbsent",
            ),
            EVERY_FAILURE
                .filterNot { it is CreateCaseFailure.Rejected }
                .map { "${it.javaClass.simpleName} ${statusFor(it).value()} ${reasonFor(it)}" }
                .sorted(),
            "the one 500 on this endpoint is the service failing to mint its own case identifier, which is " +
                "the server's fault and not the client's",
        )
    }

    @Test
    fun `every status this endpoint can answer with is one this test names`() {
        assertEquals(
            setOf(
                HttpStatus.BAD_REQUEST,
                HttpStatus.FORBIDDEN,
                HttpStatus.UNPROCESSABLE_CONTENT,
                HttpStatus.INTERNAL_SERVER_ERROR,
            ),
            EVERY_FAILURE.map(::statusFor).toSet(),
        )
        assertTrue(
            EVERY_FAILURE.isNotEmpty(),
            "every assertion above iterates this list, so an empty list reports all of them as satisfied",
        )
    }
}
