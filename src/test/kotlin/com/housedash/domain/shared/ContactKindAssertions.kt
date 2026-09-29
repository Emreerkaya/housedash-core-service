package com.housedash.domain.shared

import kotlin.test.assertEquals

internal fun assertKinds(
    expected: Set<ContactDetail>,
    text: String,
) = assertEquals(
    expected,
    contactDetailsIn(text),
    "the guard was expected to report ${expected.ifEmpty { "nothing" }} on ${'"'}$text${'"'}. This fixture " +
        "names the kinds because a fixture whose whole vocabulary is one kind or nothing cannot express a row " +
        "rejected for the wrong reason, and the kind is what the caller is shown",
)

internal fun assertPhoneNumber(text: String) = assertKinds(setOf(ContactDetail.PhoneNumber), text)

internal fun assertEmailAddress(text: String) = assertKinds(setOf(ContactDetail.EmailAddress), text)

internal fun assertPaymentLink(text: String) = assertKinds(setOf(ContactDetail.PaymentLink), text)

internal fun assertMessagingHandle(text: String) = assertKinds(setOf(ContactDetail.MessagingHandle), text)

internal fun assertNothingFound(text: String) = assertKinds(emptySet(), text)
