package com.housedash.adapters.inbound.http

import com.housedash.app.DeviceRegistrationRequest
import com.housedash.app.RegisterDeviceToken
import com.housedash.app.RegisterDeviceTokenFailure
import com.housedash.app.RegisteredDeviceToken
import com.housedash.app.RevokeDeviceToken
import com.housedash.app.RevokeDeviceTokenFailure
import com.housedash.app.RevokeDeviceTokenRequest
import com.housedash.app.SubmittedDeviceId
import com.housedash.app.SubmittedNesterId
import com.housedash.domain.shared.Outcome
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

const val DEVICE_TOKENS_PATH = "/device-tokens"

@RestController
class DeviceTokenController(
    private val registerDeviceToken: RegisterDeviceToken,
    private val revokeDeviceToken: RevokeDeviceToken,
) {
    @PostMapping(DEVICE_TOKENS_PATH)
    fun register(
        @RequestBody(required = false) request: RegisterDeviceTokenRequest?,
    ): ResponseEntity<DeviceTokensReply> =
        when (val outcome = registerDeviceToken.handle(intakeOf(request))) {
            is Outcome.Ok -> answerFor(outcome.value)
            is Outcome.Err -> refusalFor(outcome.error)
        }

    @DeleteMapping(DEVICE_TOKENS_PATH)
    fun revoke(
        @RequestParam(name = "nesterId") nesterId: SubmittedNesterId,
        @RequestParam(name = "deviceId") deviceId: SubmittedDeviceId,
    ): ResponseEntity<DeviceTokensReply> =
        when (val outcome = revokeDeviceToken.handle(RevokeDeviceTokenRequest(nesterId, deviceId))) {
            is Outcome.Ok -> ResponseEntity.noContent().build()
            is Outcome.Err -> refusalFor(outcome.error)
        }
}

private fun intakeOf(request: RegisterDeviceTokenRequest?): DeviceRegistrationRequest? =
    request?.let { DeviceRegistrationRequest(it.nesterId, it.deviceId, it.token) }

private fun answerFor(registered: RegisteredDeviceToken): ResponseEntity<DeviceTokensReply> =
    ResponseEntity
        .status(if (registered.replaced) HttpStatus.OK else HttpStatus.CREATED)
        .body(RegisterDeviceTokenResponse(registered.id.toString(), registered.replaced))

private fun refusalFor(failure: RegisterDeviceTokenFailure): ResponseEntity<DeviceTokensReply> =
    ResponseEntity.status(statusFor(failure)).body(DeviceTokenRefusal(reasonFor(failure)))

private fun refusalFor(failure: RevokeDeviceTokenFailure): ResponseEntity<DeviceTokensReply> =
    ResponseEntity.status(statusFor(failure)).body(DeviceTokenRefusal(reasonFor(failure)))
