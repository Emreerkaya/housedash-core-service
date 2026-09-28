package com.housedash.domain.case

sealed interface CaseError {
    data class DescriptionTooShort(
        val length: Int,
        val minimum: Int,
    ) : CaseError

    data class DescriptionTooLong(
        val length: Int,
        val maximum: Int,
    ) : CaseError

    data class TooManyPhotos(
        val count: Int,
        val maximum: Int,
    ) : CaseError

    data class DuplicatePhoto(
        val photoId: PhotoId,
    ) : CaseError

    data object AlreadyDescribed : CaseError
}
