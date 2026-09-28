package com.housedash.domain.shared

sealed interface ContactDetail {
    data object PhoneNumber : ContactDetail

    data object EmailAddress : ContactDetail

    data object PaymentLink : ContactDetail
}

private val PHONE_NUMBER =
    Regex("""(?<!\d)(?:\+?\d{1,3}[\s.\-]?)?(?:\(\d{3}\)|\d{3})[\s.\-]?\d{3}[\s.\-]?\d{4}(?!\d)""")

private val EMAIL_ADDRESS =
    Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9\-]+(?:\.[A-Za-z0-9\-]+)*\.[A-Za-z]{2,}""")

private val PAYMENT_SERVICE =
    Regex(
        """(?i)\b(?:cash\.?(?:app|me)|venmo|paypal|pay\.pal|zelle|wise\.com|revolut\.me|""" +
            """square\.link|monzo\.me|apple\s?pay|google\s?pay)\b""",
    )

private val CASH_TAG = Regex("""(?<![A-Za-z0-9])\$[A-Za-z][A-Za-z0-9_]+""")

fun contactDetailsIn(text: String): Set<ContactDetail> =
    buildSet {
        if (PHONE_NUMBER.containsMatchIn(text)) add(ContactDetail.PhoneNumber)
        if (EMAIL_ADDRESS.containsMatchIn(text)) add(ContactDetail.EmailAddress)
        if (PAYMENT_SERVICE.containsMatchIn(text) || CASH_TAG.containsMatchIn(text)) {
            add(ContactDetail.PaymentLink)
        }
    }

fun withoutContactDetails(text: String): Outcome<String, TextFlaw> {
    val kinds = contactDetailsIn(text)
    return if (kinds.isEmpty()) Outcome.Ok(text) else Outcome.Err(TextFlaw.ContactDetails(kinds))
}
