package com.housedash.app

import com.housedash.domain.notification.NotificationError
import com.housedash.domain.shared.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private typealias HandleOutcome = Outcome<RegisteredDeviceToken, RegisterDeviceTokenFailure>

private fun request(
    owner: String? = "ns_1",
    device: String? = "dv_1",
    token: String? = "a1b2c3",
): DeviceRegistrationRequest =
    DeviceRegistrationRequest(
        owner?.let { SubmittedNesterId(it) },
        device?.let { SubmittedDeviceId(it) },
        token?.let { SubmittedToken(it) },
    )

class RegisterDeviceTokenTest {
    private val registrations = InMemoryDeviceRegistrations()

    private val registerDeviceToken = RegisterDeviceToken(registrations, CountingDeviceTokenIdentifiers(), FIXED_CLOCK)

    @Test
    fun `a first registration mints an id and is not a replacement`() {
        val registered = unwrap(registerDeviceToken.handle(request()))
        assertEquals("dvt_1", registered.id.toString())
        assertTrue(!registered.replaced)
        assertEquals(1, registrations.registrationsByKey.size)
    }

    @Test
    fun `re-registering the same device replaces rather than duplicates`() {
        val first = unwrap(registerDeviceToken.handle(request(token = "old-token")))
        val second = unwrap(registerDeviceToken.handle(request(token = "new-token")))
        assertEquals(first.id.toString(), second.id.toString())
        assertTrue(second.replaced)
        assertEquals(1, registrations.registrationsByKey.size)
        assertEquals("new-token", registrations.registrationsByKey.getValue("ns_1" to "dv_1").token)
    }

    @Test
    fun `a different device for the same owner is a second registration`() {
        unwrap(registerDeviceToken.handle(request(device = "dv_1")))
        val second = unwrap(registerDeviceToken.handle(request(device = "dv_2")))
        assertTrue(!second.replaced)
        assertEquals(2, registrations.registrationsByKey.size)
    }

    @Test
    fun `each absent field is refused by its own name`() {
        assertEquals(RegisterDeviceTokenFailure.RequestAbsent, failureOf(registerDeviceToken.handle(null)))
        val noOwner = registerDeviceToken.handle(request(owner = null))
        assertEquals(RegisterDeviceTokenFailure.NesterIdAbsent, failureOf(noOwner))
        val noDevice = registerDeviceToken.handle(request(device = null))
        assertEquals(RegisterDeviceTokenFailure.DeviceIdAbsent, failureOf(noDevice))
        val noToken = registerDeviceToken.handle(request(token = null))
        assertEquals(RegisterDeviceTokenFailure.TokenAbsent, failureOf(noToken))
    }

    @Test
    fun `a malformed owner is refused as a typed error`() {
        val failure = failureOf(registerDeviceToken.handle(request(owner = "nope")))
        assertIs<RegisterDeviceTokenFailure.Rejected>(failure)
        assertIs<NotificationError.MalformedOwner>(failure.error)
    }

    @Test
    fun `a malformed device id is refused as a typed error`() {
        val failure = failureOf(registerDeviceToken.handle(request(device = "nope")))
        assertIs<RegisterDeviceTokenFailure.Rejected>(failure)
        assertIs<NotificationError.MalformedDeviceId>(failure.error)
    }

    @Test
    fun `a blank token is refused as a typed error, not thrown`() {
        val failure = failureOf(registerDeviceToken.handle(request(token = "")))
        assertIs<RegisterDeviceTokenFailure.Rejected>(failure)
        assertIs<NotificationError.TooShort>(failure.error)
    }

    @Test
    fun `an identifier source that cannot mint an id is its own failure and nothing is stored`() {
        val refusing = RegisterDeviceToken(registrations, RefusingDeviceTokenIdentifiers(), FIXED_CLOCK)
        val failure = failureOf(refusing.handle(request()))
        assertIs<RegisterDeviceTokenFailure.DeviceTokenIdRefused>(failure)
        assertEquals(0, registrations.registrationsByKey.size)
    }

    private fun failureOf(outcome: HandleOutcome): RegisterDeviceTokenFailure =
        when (outcome) {
            is Outcome.Ok -> error("expected a refusal and got ${outcome.value.id}")
            is Outcome.Err -> outcome.error
        }
}
