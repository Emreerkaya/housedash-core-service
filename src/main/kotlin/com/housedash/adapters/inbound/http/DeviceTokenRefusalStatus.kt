package com.housedash.adapters.inbound.http

import com.housedash.app.RegisterDeviceTokenFailure
import com.housedash.app.RevokeDeviceTokenFailure
import com.housedash.domain.notification.NotificationError
import org.springframework.http.HttpStatus

fun statusFor(failure: RegisterDeviceTokenFailure): HttpStatus =
    when (failure) {
        is RegisterDeviceTokenFailure.Rejected -> statusFor(failure.error)
        is RegisterDeviceTokenFailure.DeviceTokenIdRefused -> HttpStatus.INTERNAL_SERVER_ERROR
        RegisterDeviceTokenFailure.RequestAbsent,
        RegisterDeviceTokenFailure.NesterIdAbsent,
        RegisterDeviceTokenFailure.DeviceIdAbsent,
        RegisterDeviceTokenFailure.TokenAbsent,
        -> HttpStatus.BAD_REQUEST
    }

fun reasonFor(failure: RegisterDeviceTokenFailure): String =
    if (failure is RegisterDeviceTokenFailure.Rejected) {
        failure.error.javaClass.simpleName
    } else {
        failure.javaClass.simpleName
    }

fun statusFor(failure: RevokeDeviceTokenFailure): HttpStatus =
    when (failure) {
        is RevokeDeviceTokenFailure.Rejected -> statusFor(failure.error)
        RevokeDeviceTokenFailure.NotFound -> HttpStatus.NOT_FOUND
        RevokeDeviceTokenFailure.RequestAbsent,
        RevokeDeviceTokenFailure.NesterIdAbsent,
        RevokeDeviceTokenFailure.DeviceIdAbsent,
        -> HttpStatus.BAD_REQUEST
    }

fun reasonFor(failure: RevokeDeviceTokenFailure): String =
    if (failure is RevokeDeviceTokenFailure.Rejected) {
        failure.error.javaClass.simpleName
    } else {
        failure.javaClass.simpleName
    }

private fun statusFor(error: NotificationError): HttpStatus =
    when (error) {
        is NotificationError.MalformedDeviceId,
        is NotificationError.MalformedDeviceTokenId,
        is NotificationError.MalformedNotificationId,
        is NotificationError.MalformedOwner,
        is NotificationError.NotPlainText,
        is NotificationError.TooShort,
        is NotificationError.TooLong,
        -> HttpStatus.BAD_REQUEST
    }
