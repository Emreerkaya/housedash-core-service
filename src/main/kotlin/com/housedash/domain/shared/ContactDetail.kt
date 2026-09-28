package com.housedash.domain.shared

import java.text.Normalizer

sealed interface ContactDetail {
    data object PhoneNumber : ContactDetail

    data object EmailAddress : ContactDetail

    data object PaymentLink : ContactDetail

    data object MessagingHandle : ContactDetail
}

private val CONFUSABLES_FOLDED_TO_LATIN =
    mapOf(
        '\u0430' to 'a',
        '\u0432' to 'b',
        '\u0435' to 'e',
        '\u043A' to 'k',
        '\u043C' to 'm',
        '\u043D' to 'n',
        '\u043E' to 'o',
        '\u0440' to 'p',
        '\u0441' to 'c',
        '\u0442' to 't',
        '\u0443' to 'y',
        '\u0445' to 'x',
        '\u0455' to 's',
        '\u0456' to 'i',
        '\u0458' to 'j',
        '\u04BB' to 'h',
        '\u04CF' to 'l',
        '\u0501' to 'd',
        '\u051B' to 'q',
        '\u051D' to 'w',
        '\u0410' to 'A',
        '\u0412' to 'B',
        '\u0415' to 'E',
        '\u041A' to 'K',
        '\u041C' to 'M',
        '\u041D' to 'H',
        '\u041E' to 'O',
        '\u0420' to 'P',
        '\u0421' to 'C',
        '\u0422' to 'T',
        '\u0425' to 'X',
        '\u0405' to 'S',
        '\u0406' to 'I',
        '\u0408' to 'J',
        '\u03B1' to 'a',
        '\u03B3' to 'y',
        '\u03B5' to 'e',
        '\u03B9' to 'i',
        '\u03BA' to 'k',
        '\u03BC' to 'u',
        '\u03BD' to 'v',
        '\u03BF' to 'o',
        '\u03C1' to 'p',
        '\u03C4' to 't',
        '\u03C5' to 'u',
        '\u03C7' to 'x',
        '\u0391' to 'A',
        '\u0392' to 'B',
        '\u0395' to 'E',
        '\u0397' to 'H',
        '\u0399' to 'I',
        '\u039A' to 'K',
        '\u039C' to 'M',
        '\u039D' to 'N',
        '\u039F' to 'O',
        '\u03A1' to 'P',
        '\u03A4' to 'T',
        '\u03A5' to 'Y',
        '\u03A7' to 'X',
        '\u0131' to 'i',
        '\u0251' to 'a',
        '\u2044' to '/',
        '\u2215' to '/',
        '\u29F8' to '/',
    )

private const val PHONE_SEPARATOR = """[\p{Zs}\t\p{Pd}\u2212\u00B7\u2022\u2027\u30FB._/\\()\[\]]"""

private const val LONGEST_SEPARATOR_BETWEEN_GROUPS = "{0,8}+"

private const val MOST_GROUPS_A_CANDIDATE_MAY_HOLD = "{0,31}+"

private val PHONE_CANDIDATE =
    Regex(
        """\+?+\p{Nd}(?:$PHONE_SEPARATOR$LONGEST_SEPARATOR_BETWEEN_GROUPS\p{Nd})""" +
            MOST_GROUPS_A_CANDIDATE_MAY_HOLD,
    )

private val DIGIT_GROUP = Regex("""\p{Nd}++""")

private val PHONE_CUE =
    Regex(
        """(?i)\b(?:call|calls|called|calling|text|texts|texted|ring|rings|dial|dials|""" +
            """phone|phones|telephone|tel|mobile|cell|cellphone|whatsapp|sms|number|numbers)\b""",
    )

private val NOT_A_PHONE_CUE =
    Regex(
        """(?i)\b(?:serial|serials|model|imei|part|parts|sku|meter|reading|invoice|order|""" +
            """ref|reference|barcode|licence|license|policy|warranty|asset|batch|code)\b""",
    )

private const val COMMON_TLD =
    """(?:com|net|org|edu|gov|io|co|me|uk|us|ca|de|fr|nl|es|it|ie|au|info|mail|email|app|dev)"""

private val EMAIL_CANDIDATE =
    Regex("""[A-Za-z0-9._%+\-]{1,64}+(?:@|\(at\)|\[at\]|\{at\})([A-Za-z0-9.\-]{1,255}+)""")

private val WORDED_EMAIL =
    Regex(
        """(?i)\b[A-Za-z0-9._%+\-]{1,64}+\p{Zs}{1,4}+(?:at|\(at\)|\[at\])\p{Zs}{1,4}+""" +
            """[A-Za-z0-9\-]{1,63}+\p{Zs}{1,4}+(?:dot|\(dot\)|\[dot\])\p{Zs}{1,4}+$COMMON_TLD\b""",
    )

private val EMAIL_TOP_LEVEL_LABEL = Regex("""[A-Za-z]{2,24}""")

private val PAYMENT_SERVICE =
    Regex(
        """(?i)\b(?:cash[.\-/\p{Zs}]?+(?:app|me)|venmo|paypal|pay[.\-]?+pal|zelle|""" +
            """wise\.com|revolut\.me|square\.link|monzo\.me|apple\p{Zs}?+pay|google\p{Zs}?+pay|""" +
            """(?:buy\.|checkout\.)?+stripe\.com|ko-?+fi\.com|gofundme\.com|patreon\.com|""" +
            """western\p{Zs}?+union|moneygram|payoneer|skrill|bitcoin|ethereum|monero)\b""",
    )

private val CASH_TAG = Regex("""(?<![A-Za-z0-9])\$[A-Za-z][A-Za-z0-9_]{1,30}+""")

private val SORT_CODE =
    Regex(
        """(?i)\bsort\p{Zs}?+(?:code)?+\p{Zs}?+:?+\p{Zs}?+""" +
            """\p{Nd}{2}[\p{Pd}\p{Zs}.]?+\p{Nd}{2}[\p{Pd}\p{Zs}.]?+\p{Nd}{2}(?!\p{Nd})""",
    )

private val BANK_ACCOUNT =
    Regex("""(?i)\b(?:acct|a/c|account)\p{Zs}?+(?:number|no|nr)?+\p{Zs}?+:?+\p{Zs}?+\p{Nd}{6,12}(?!\p{Nd})""")

private val CRYPTO_ADDRESS =
    Regex("""\b(?:bc1[ac-hj-np-z02-9]{11,71}+|[13][1-9A-HJ-NP-Za-km-z]{25,34}+|0x[0-9A-Fa-f]{40}+)\b""")

private val IBAN_CANDIDATE =
    Regex(
        """\b[A-Za-z]{2}[0-9]{2}(?:[A-Za-z0-9]{11,30}+|""" +
            """(?:[\p{Zs}\p{Pd}][A-Za-z0-9]{4}){2,7}+(?:[\p{Zs}\p{Pd}][A-Za-z0-9]{1,3})?+)""",
    )

private val MESSAGING_HANDLE =
    Regex(
        """(?i)(?:\b(?:whatsapp|viber|wechat|kakaotalk)\b|""" +
            """\b(?:t\.me|telegram\.me|wa\.me|m\.me|api\.whatsapp\.com|instagram\.com|ig\.me|""" +
            """facebook\.com|fb\.me|snapchat\.com|tiktok\.com|x\.com|twitter\.com|nextdoor\.com|""" +
            """signal\.me|discord\.gg|linkedin\.com/in)/[A-Za-z0-9._~%+\-]{2,40}+)""",
    )

private val PAYMENT_PATTERNS = listOf(PAYMENT_SERVICE, CASH_TAG, SORT_CODE, BANK_ACCOUNT, CRYPTO_ADDRESS)

private const val INTERNATIONAL_PREFIX = "+"

private val PHONE_DIGIT_COUNT = 9..15

private val PHONE_GROUP_COUNT = 2..6

private const val MOST_DIGITS_IN_A_PHONE_GROUP = 6

private const val FEWEST_DIGITS_IN_A_PHONE_GROUP = 3

private const val FEWEST_SINGLE_DIGIT_GROUPS = 9

private const val ONE_DIGIT = 1

private const val ONE_GROUP = 1

private const val PHONE_CUE_WINDOW = 24

private const val EMAIL_DOMAIN_GROUP = 1

private const val FEWEST_DOMAIN_LABELS = 2

private val IBAN_LENGTH = 15..34

private const val IBAN_COUNTRY_AND_CHECK_DIGITS = 4

private const val IBAN_MODULUS = 97

private const val IBAN_REMAINDER_OF_A_VALID_NUMBER = 1

private const val DECIMAL_SHIFT = 10

private const val LETTER_SHIFT = 100

private const val FIRST_LETTER_VALUE = 10

fun contactDetailsIn(text: String): Set<ContactDetail> {
    val folded = foldedForMatchingOnly(text)
    return buildSet {
        if (holdsPhoneNumber(folded)) add(ContactDetail.PhoneNumber)
        if (holdsEmailAddress(folded)) add(ContactDetail.EmailAddress)
        if (holdsPaymentDetail(folded)) add(ContactDetail.PaymentLink)
        if (MESSAGING_HANDLE.containsMatchIn(folded)) add(ContactDetail.MessagingHandle)
    }
}

fun withoutContactDetails(text: String): Outcome<String, TextFlaw> {
    val kinds = contactDetailsIn(text)
    return if (kinds.isEmpty()) Outcome.Ok(text) else Outcome.Err(TextFlaw.ContactDetails(kinds))
}

private fun foldedForMatchingOnly(text: String): String {
    val compatibility = Normalizer.normalize(text, Normalizer.Form.NFKC)
    return buildString(compatibility.length) {
        for (character in compatibility) {
            append(CONFUSABLES_FOLDED_TO_LATIN[character] ?: character)
        }
    }
}

private fun holdsPhoneNumber(folded: String): Boolean =
    PHONE_CANDIDATE.findAll(folded).any { candidate ->
        val window = windowAround(folded, candidate.range)
        hasPhoneShape(
            digitGroupSizes(candidate.value),
            candidate.value.startsWith(INTERNATIONAL_PREFIX),
            PHONE_CUE.containsMatchIn(window),
            NOT_A_PHONE_CUE.containsMatchIn(window),
        )
    }

private fun hasPhoneShape(
    groups: List<Int>,
    internationallyPrefixed: Boolean,
    cued: Boolean,
    namedAsSomethingElse: Boolean,
): Boolean =
    when {
        groups.sum() !in PHONE_DIGIT_COUNT -> false
        internationallyPrefixed -> true
        namedAsSomethingElse -> false
        isGroupedLikeAPhoneNumber(groups) -> true
        isOneDigitPerSeparator(groups) -> true
        else -> groups.size == ONE_GROUP && cued
    }

private fun isGroupedLikeAPhoneNumber(groups: List<Int>): Boolean =
    groups.size in PHONE_GROUP_COUNT &&
        groups.all { it <= MOST_DIGITS_IN_A_PHONE_GROUP } &&
        groups.any { it >= FEWEST_DIGITS_IN_A_PHONE_GROUP }

private fun isOneDigitPerSeparator(groups: List<Int>): Boolean =
    groups.size >= FEWEST_SINGLE_DIGIT_GROUPS &&
        groups.all { it == ONE_DIGIT }

private fun digitGroupSizes(candidate: String): List<Int> =
    DIGIT_GROUP
        .findAll(candidate)
        .map { characterCount(it.value) }
        .toList()

private fun windowAround(
    folded: String,
    range: IntRange,
): String {
    val from = (range.first - PHONE_CUE_WINDOW).coerceAtLeast(0)
    val to = (range.last + 1 + PHONE_CUE_WINDOW).coerceAtMost(folded.length)
    return folded.substring(from, to)
}

private fun holdsEmailAddress(folded: String): Boolean =
    WORDED_EMAIL.containsMatchIn(folded) ||
        EMAIL_CANDIDATE.findAll(folded).any { hasDomainShape(it.groupValues[EMAIL_DOMAIN_GROUP]) }

private fun hasDomainShape(domain: String): Boolean {
    val labels = domain.trim('.', '-').split('.')
    return labels.size >= FEWEST_DOMAIN_LABELS &&
        labels.none { it.isEmpty() } &&
        EMAIL_TOP_LEVEL_LABEL.matches(labels.last())
}

private fun holdsPaymentDetail(folded: String): Boolean =
    PAYMENT_PATTERNS.any { it.containsMatchIn(folded) } ||
        holdsIban(folded)

private fun holdsIban(folded: String): Boolean =
    IBAN_CANDIDATE.findAll(folded).any { candidate ->
        val compact = candidate.value.filter { it.isLetterOrDigit() }.uppercase()
        compact.length in IBAN_LENGTH && ibanRemainder(compact) == IBAN_REMAINDER_OF_A_VALID_NUMBER
    }

private fun ibanRemainder(compact: String): Int {
    val rearranged =
        compact.substring(IBAN_COUNTRY_AND_CHECK_DIGITS) + compact.take(IBAN_COUNTRY_AND_CHECK_DIGITS)
    var remainder = 0
    for (character in rearranged) {
        remainder =
            if (character.isDigit()) {
                (remainder * DECIMAL_SHIFT + (character - '0')) % IBAN_MODULUS
            } else {
                (remainder * LETTER_SHIFT + (character - 'A' + FIRST_LETTER_VALUE)) % IBAN_MODULUS
            }
    }
    return remainder
}
