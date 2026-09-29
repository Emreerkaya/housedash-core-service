package com.housedash.app

import com.housedash.domain.case.CaseError
import com.housedash.domain.case.Description
import com.housedash.domain.case.DraftCase
import com.housedash.domain.case.PhotoId
import com.housedash.domain.shared.NesterId
import com.housedash.domain.shared.Outcome
import java.time.Clock

class CaseIntake(
    val owner: SubmittedId?,
    val description: SubmittedText?,
    val photos: List<SubmittedId>?,
    val key: SubmittedKey?,
)

private class PresentIntake(
    val owner: SubmittedId,
    val description: SubmittedText,
    val photos: List<SubmittedId>,
    val key: SubmittedKey,
)

private class ReadIntake(
    val owner: NesterId,
    val description: Description,
    val photos: List<PhotoId>,
    val key: IntakeKey,
)

class CreateCase(
    private val cases: CaseRepository,
    private val identifiers: CaseIdentifiers,
    private val clock: Clock,
) {
    fun handle(intake: CaseIntake?): Outcome<StoredCase, CreateCaseFailure> =
        presentFieldsOf(intake).flatMap(::readFieldsOf).flatMap(::storedCaseFor)

    private fun storedCaseFor(read: ReadIntake): Outcome<StoredCase, CreateCaseFailure> {
        val at = clock.instant()
        val id = identifiers.next().mapError(CreateCaseFailure::CaseIdRefused).valueOr { return it }
        val case =
            DraftCase
                .of(id, read.owner, at)
                .describe(read.owner, read.description, read.photos, at)
                .asFailure()
                .valueOr { return it }
        return Outcome.Ok(cases.storeUnlessAlreadyStored(IntakeAttempt(read.owner, read.key), case))
    }
}

private fun presentFieldsOf(intake: CaseIntake?): Outcome<PresentIntake, CreateCaseFailure> {
    if (intake == null) return Outcome.Err(CreateCaseFailure.IntakeAbsent)
    val owner = intake.owner ?: return Outcome.Err(CreateCaseFailure.OwnerAbsent)
    val description = intake.description ?: return Outcome.Err(CreateCaseFailure.DescriptionAbsent)
    val key = intake.key ?: return Outcome.Err(CreateCaseFailure.IntakeKeyAbsent)
    return Outcome.Ok(PresentIntake(owner, description, intake.photos.orEmpty(), key))
}

private fun readFieldsOf(intake: PresentIntake): Outcome<ReadIntake, CreateCaseFailure> {
    val key = intake.key.asIntakeKey().valueOr { return it }
    val owner =
        intake.owner
            .asOwner()
            .asFailure()
            .valueOr { return it }
    val description =
        intake.description
            .asDescription()
            .asFailure()
            .valueOr { return it }
    val photos = photosOf(intake.photos).valueOr { return it }
    return Outcome.Ok(ReadIntake(owner, description, photos, key))
}

private fun photosOf(submitted: List<SubmittedId>): Outcome<List<PhotoId>, CreateCaseFailure> {
    val read = mutableListOf<PhotoId>()
    submitted.forEach { one ->
        read.add(one.asPhotoId().asFailure().valueOr { return it })
    }
    return Outcome.Ok(read)
}

private fun <T> Outcome<T, CaseError>.asFailure(): Outcome<T, CreateCaseFailure> = mapError(CreateCaseFailure::Rejected)

private inline fun <T> Outcome<T, CreateCaseFailure>.valueOr(bail: (Outcome.Err<CreateCaseFailure>) -> Nothing): T =
    when (this) {
        is Outcome.Ok -> value
        is Outcome.Err -> bail(this)
    }
