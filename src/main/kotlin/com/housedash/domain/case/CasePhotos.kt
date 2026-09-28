package com.housedash.domain.case

import com.housedash.domain.shared.Outcome

class CasePhotos private constructor(
    photos: List<PhotoId>,
) {
    val ids: List<PhotoId> = photos.toList()

    init {
        photoFaultOf(ids)?.let { throw CorruptCase(it) }
    }

    internal companion object {
        fun of(photos: List<PhotoId>): Outcome<CasePhotos, CaseError> {
            if (photos.size > Case.MAX_PHOTOS) {
                return Outcome.Err(CaseError.TooManyPhotos(photos.size, Case.MAX_PHOTOS))
            }
            val seen = mutableSetOf<PhotoId>()
            photos.forEachIndexed { position, photo ->
                if (!seen.add(photo)) return Outcome.Err(CaseError.DuplicatePhoto(position))
            }
            return Outcome.Ok(CasePhotos(photos))
        }

        fun rehydrated(photos: List<PhotoId>): CasePhotos = CasePhotos(photos)
    }
}

private fun photoFaultOf(ids: List<*>): CaseFault? =
    when {
        ids.any { it !is PhotoId } -> CaseFault.MALFORMED_PHOTO_ID
        ids.size > Case.MAX_PHOTOS -> CaseFault.TOO_MANY_PHOTOS
        ids.distinct().size != ids.size -> CaseFault.DUPLICATE_PHOTO
        else -> null
    }
