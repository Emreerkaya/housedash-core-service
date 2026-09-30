package com.housedash.app

import com.housedash.domain.notification.NotificationError
import com.housedash.domain.notification.ownerOf
import com.housedash.domain.shared.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun revokeRequest(
    nesterId: String? = "ns_1",
    deviceId: String? = "dv_1",
): RevokeDeviceTokenRequest {
    val owner = nesterId?.let { SubmittedNesterId(it) }
    val device = deviceId?.let { SubmittedDeviceId(it) }
    return RevokeDeviceTokenRequest(owner, device)
}

class RevokeDeviceTokenTest {
    private val registrations = InMemoryDeviceRegistrations()

    private val registerDeviceToken = RegisterDeviceToken(registrations, CountingDeviceTokenIdentifiers(), FIXED_CLOCK)

    private val revokeDeviceToken = RevokeDeviceToken(registrations, FIXED_CLOCK)

    @Test
    fun `revoking a registered device succeeds and the token no longer answers as active`() {
        unwrap(
            registerDeviceToken.handle(
                DeviceRegistrationRequest(SubmittedNesterId("ns_1"), SubmittedDeviceId("dv_1"), SubmittedToken("tok")),
            ),
        )
        val outcome = revokeDeviceToken.handle(revokeRequest())
        assertIs<Outcome.Ok<Unit>>(outcome)
        assertTrue(registrations.activeTokensFor(unwrap(ownerOf("ns_1"))).isEmpty())
    }

    @Test
    fun `revoking a device that was never registered is not found`() {
        val outcome = revokeDeviceToken.handle(revokeRequest())
        assertEquals(RevokeDeviceTokenFailure.NotFound, failureOf(outcome))
    }

    @Test
    fun `each absent field is refused by its own name`() {
        assertEquals(RevokeDeviceTokenFailure.RequestAbsent, failureOf(revokeDeviceToken.handle(null)))
        assertEquals(
            RevokeDeviceTokenFailure.NesterIdAbsent,
            failureOf(revokeDeviceToken.handle(revokeRequest(nesterId = null))),
        )
        assertEquals(
            RevokeDeviceTokenFailure.DeviceIdAbsent,
            failureOf(revokeDeviceToken.handle(revokeRequest(deviceId = null))),
        )
    }

    @Test
    fun `a malformed owner is refused as a typed error`() {
        val failure = failureOf(revokeDeviceToken.handle(revokeRequest(nesterId = "nope")))
        assertIs<RevokeDeviceTokenFailure.Rejected>(failure)
        assertIs<NotificationError.MalformedOwner>(failure.error)
    }

    private fun failureOf(outcome: Outcome<Unit, RevokeDeviceTokenFailure>): RevokeDeviceTokenFailure =
        when (outcome) {
            is Outcome.Ok -> error("expected a refusal and the revoke succeeded")
            is Outcome.Err -> outcome.error
        }
}
