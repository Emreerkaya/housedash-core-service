package com.housedash.adapters.inbound.http

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.housedash.app.SubmittedCode
import com.housedash.app.SubmittedIdentifier

class IssueOtpRequestBody
    @JsonCreator
    constructor(
        @JsonProperty("identifier") val identifier: SubmittedIdentifier?,
    )

class VerifyOtpRequestBody
    @JsonCreator
    constructor(
        @JsonProperty("identifier") val identifier: SubmittedIdentifier?,
        @JsonProperty("code") val code: SubmittedCode?,
    )

sealed interface OtpReply

object OtpAccepted : OtpReply

class OtpRefusal(
    val reason: String,
) : OtpReply
