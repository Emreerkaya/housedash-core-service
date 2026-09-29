package com.housedash.adapters.inbound.http

import com.housedash.app.CreateCaseFailure
import com.housedash.domain.case.CaseError
import org.springframework.http.HttpStatus

fun statusFor(failure: CreateCaseFailure): HttpStatus =
    when (failure) {
        is CreateCaseFailure.Rejected -> statusFor(failure.error)
        is CreateCaseFailure.CaseIdRefused -> HttpStatus.INTERNAL_SERVER_ERROR
        CreateCaseFailure.OwnerAbsent,
        CreateCaseFailure.DescriptionAbsent,
        CreateCaseFailure.IntakeAbsent,
        CreateCaseFailure.IntakeKeyAbsent,
        CreateCaseFailure.MalformedIntakeKey,
        -> HttpStatus.BAD_REQUEST
    }

fun reasonFor(failure: CreateCaseFailure): String =
    if (failure is CreateCaseFailure.Rejected) {
        failure.error.javaClass.simpleName
    } else {
        failure.javaClass.simpleName
    }

private fun statusFor(error: CaseError): HttpStatus =
    when (error) {
        is CaseError.ContactDetailsInDescription -> HttpStatus.UNPROCESSABLE_CONTENT
        CaseError.NotOwner -> HttpStatus.FORBIDDEN
        is CaseError.DescriptionTooShort,
        is CaseError.DescriptionTooLong,
        CaseError.DescriptionNotPlainText,
        is CaseError.TooManyPhotos,
        is CaseError.DuplicatePhoto,
        is CaseError.MalformedCaseId,
        is CaseError.MalformedPhotoId,
        is CaseError.MalformedNesterId,
        -> HttpStatus.BAD_REQUEST
    }
