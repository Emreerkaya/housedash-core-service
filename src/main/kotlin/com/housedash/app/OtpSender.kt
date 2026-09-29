package com.housedash.app

import com.housedash.domain.identity.LoginIdentifier

fun interface OtpSender {
    fun send(
        identifier: LoginIdentifier,
        code: String,
    )
}
