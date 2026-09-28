package com.housedash.domain.case

import com.housedash.domain.shared.ContactDetail

sealed interface CaseError {
    data class DescriptionTooShort(
        val length: Int,
        val minimum: Int,
    ) : CaseError

    data class DescriptionTooLong(
        val length: Int,
        val maximum: Int,
    ) : CaseError

    data object DescriptionNotPlainText : CaseError

    data class ContactDetailsInDescription(
        val kinds: Set<ContactDetail>,
    ) : CaseError

    data class TooManyPhotos(
        val count: Int,
        val maximum: Int,
    ) : CaseError

    data class DuplicatePhoto(
        val position: Int,
    ) : CaseError

    data object MalformedCaseId : CaseError

    data object MalformedPhotoId : CaseError

    data object MalformedNesterId : CaseError

    data object NotOwner : CaseError
}
