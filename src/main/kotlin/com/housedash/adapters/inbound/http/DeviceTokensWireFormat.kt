package com.housedash.adapters.inbound.http

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.housedash.app.SubmittedDeviceId
import com.housedash.app.SubmittedNesterId
import com.housedash.app.SubmittedToken

class RegisterDeviceTokenRequest
    @JsonCreator
    constructor(
        @JsonProperty("nesterId") val nesterId: SubmittedNesterId?,
        @JsonProperty("deviceId") val deviceId: SubmittedDeviceId?,
        @JsonProperty("token") val token: SubmittedToken?,
    )

sealed interface DeviceTokensReply

class RegisterDeviceTokenResponse(
    val deviceTokenId: String,
    val replaced: Boolean,
) : DeviceTokensReply

class DeviceTokenRefusal(
    val reason: String,
) : DeviceTokensReply
