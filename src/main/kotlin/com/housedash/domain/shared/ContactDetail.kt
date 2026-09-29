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
    )

private val LINE_BREAKS_A_DESCRIPTION_BOX_CREATES =
    setOf('\n', '\r', '\t', '\u000B', '\u000C', '\u0085', '\u2028', '\u2029')

private val CATEGORIES_STRIPPED_BEFORE_MATCHING =
    setOf(
        CharCategory.FORMAT,
        CharCategory.NON_SPACING_MARK,
        CharCategory.COMBINING_SPACING_MARK,
        CharCategory.ENCLOSING_MARK,
    )

private const val SLASH_LIKE = """/\u2044\u2215\u29F8"""

private const val DOT_A_BRAND_MAY_BE_WRITTEN_WITH = """.\u00B7\u2022\u2027\u30FB"""

private const val DOT_IN_A_BRAND = """[$DOT_A_BRAND_MAY_BE_WRITTEN_WITH]"""

private const val BETWEEN_THE_WORDS_OF_A_BRAND =
    """[$DOT_A_BRAND_MAY_BE_WRITTEN_WITH\-_$SLASH_LIKE\p{Zs}]"""

private const val APOSTROPHE_OR_NONE = """['\u2019\u02BC\u055A]?+"""

private const val WHATSAPP =
    """what$BETWEEN_THE_WORDS_OF_A_BRAND{0,2}+$APOSTROPHE_OR_NONE""" +
        """s$BETWEEN_THE_WORDS_OF_A_BRAND{0,2}+app"""

private const val PUNCTUATION_A_SPACED_OUT_RUN_KEEPS = """[.@/\-_]"""

private const val BETWEEN_TWO_SPACED_OUT_CHARACTERS =
    """(?:\p{Zs}{1,2}+$PUNCTUATION_A_SPACED_OUT_RUN_KEEPS?+\p{Zs}{0,2}+|""" +
        """$PUNCTUATION_A_SPACED_OUT_RUN_KEEPS)"""

private const val FEWEST_FURTHER_CHARACTERS_IN_A_SPACED_OUT_RUN = "{2,}+"

private const val SINGLE_CHARACTER_TOKEN = """[A-Za-z0-9](?![A-Za-z0-9])"""

private val SPACED_OUT_RUN =
    Regex(
        """(?<![A-Za-z0-9])$SINGLE_CHARACTER_TOKEN""" +
            """(?:$BETWEEN_TWO_SPACED_OUT_CHARACTERS$SINGLE_CHARACTER_TOKEN)""" +
            FEWEST_FURTHER_CHARACTERS_IN_A_SPACED_OUT_RUN,
    )

private const val PHONE_SEPARATOR = """[\p{Zs}\t\p{Pd}\u2212\u00B7\u2022\u2027\u30FB._/\\()\[\]]"""

private const val LONGEST_SEPARATOR_BETWEEN_GROUPS = "{0,8}+"

private const val MOST_GROUPS_A_CANDIDATE_MAY_HOLD = "{0,31}+"

private val PHONE_CANDIDATE =
    Regex(
        """\+?+\p{Nd}(?:$PHONE_SEPARATOR$LONGEST_SEPARATOR_BETWEEN_GROUPS\p{Nd})""" +
            MOST_GROUPS_A_CANDIDATE_MAY_HOLD,
    )

private const val WHOLE_GROUP_OF_DIGITS = """(?<!\p{Nd})\p{Nd}{1,6}+(?!\p{Nd})"""

private const val THOUSANDS_PUNCTUATION = """[,;:]\p{Zs}{0,2}+"""

private const val MOST_PUNCTUATED_SEPARATORS = "{1,5}+"

private val PUNCTUATION_GROUPED_CANDIDATE =
    Regex(
        """\+?+$WHOLE_GROUP_OF_DIGITS(?:$THOUSANDS_PUNCTUATION$WHOLE_GROUP_OF_DIGITS)""" +
            MOST_PUNCTUATED_SEPARATORS,
    )

private val DIGIT_GROUP = Regex("""\p{Nd}++""")

private val SEPARATOR_A_SPREAD_OUT_NUMBER_USES = Regex("""[\p{Zs}\p{Pd}\u2212]""")

private val PHONE_CUE =
    Regex(
        """(?i)\b(?:call|calls|called|calling|text|texts|texted|ring|rings|dial|dials|""" +
            """phone|phones|telephone|tel|mobile|cell|cellphone|$WHATSAPP|sms)\b""",
    )

private const val NAMES_A_NUMBER_AS_SOMETHING_ELSE =
    """(?:serial|serials|model|imei|part|parts|sku|meter|reading|invoice|order|ref|reference|""" +
        """barcode|licence|license|policy|warranty|asset|batch|code|account|acct|lot|unit|""" +
        """catalogue|catalog|job|door|flat|buzzer|apartment|room|version|build)"""

private val UNQUALIFIED_NUMBER_CUE =
    Regex("""(?i)(?<!\b$NAMES_A_NUMBER_AS_SOMETHING_ELSE\p{Zs}{0,4})\bnumbers?\b""")

private const val COMMON_TLD =
    """(?:com|net|org|edu|gov|io|co|me|uk|us|ca|de|fr|nl|es|it|ie|au|info|mail|email|app|dev)"""

private const val BRACKET_OPENING_A_WORDED_SEPARATOR = """[(\[{<]"""

private const val BRACKET_CLOSING_A_WORDED_SEPARATOR = """[)\]}>]"""

private const val BRACKETED_AT =
    """$BRACKET_OPENING_A_WORDED_SEPARATOR""" +
        """at$BRACKET_CLOSING_A_WORDED_SEPARATOR"""

private const val DOT_A_DOMAIN_LABEL_MAY_BE_SEPARATED_BY =
    """[$DOT_A_BRAND_MAY_BE_WRITTEN_WITH\u3002]"""

private const val BRACKETED_DOT =
    """\p{Zs}{0,3}+$BRACKET_OPENING_A_WORDED_SEPARATOR""" +
        """dot$BRACKET_CLOSING_A_WORDED_SEPARATOR\p{Zs}{0,3}+"""

private const val WORDED_DOT_BETWEEN_DOMAIN_LABELS = """\p{Zs}{1,4}+dot\p{Zs}{1,4}+"""

private const val DOMAIN_LABEL_SEPARATOR =
    """(?:$BRACKETED_DOT|$WORDED_DOT_BETWEEN_DOMAIN_LABELS|""" +
        """\p{Zs}{0,3}+$DOT_A_DOMAIN_LABEL_MAY_BE_SEPARATED_BY\p{Zs}{0,3}+)"""

private const val DOMAIN_LABEL = """[A-Za-z0-9\-]{1,63}+"""

private const val MOST_SPACES_AROUND_AN_AT_SIGN = "{0,3}+"

private const val COMMA_BEFORE_A_TOP_LEVEL_LABEL = """,\p{Zs}{0,3}+$COMMON_TLD(?![A-Za-z0-9])"""

private val EMAIL_CANDIDATE =
    Regex(
        """(?i)[A-Za-z0-9._%+\-]{1,64}+\p{Zs}$MOST_SPACES_AROUND_AN_AT_SIGN(?:@|$BRACKETED_AT)""" +
            """\p{Zs}$MOST_SPACES_AROUND_AN_AT_SIGN""" +
            """($DOMAIN_LABEL(?:$DOMAIN_LABEL_SEPARATOR$DOMAIN_LABEL)*+""" +
            """(?:(?<=[A-Za-z])$COMMA_BEFORE_A_TOP_LEVEL_LABEL)?+)""",
    )

private val LABEL_SEPARATOR_IN_A_DOMAIN =
    Regex(
        """(?i)(?:$WORDED_DOT_BETWEEN_DOMAIN_LABELS|\p{Zs}{0,3}+(?:""" +
            """$BRACKET_OPENING_A_WORDED_SEPARATOR""" +
            """dot$BRACKET_CLOSING_A_WORDED_SEPARATOR|""" +
            """$DOT_A_DOMAIN_LABEL_MAY_BE_SEPARATED_BY|,)\p{Zs}{0,3}+)""",
    )

private val DOMAIN_SPLIT_ONE_LETTER_TO_A_LABEL =
    Regex("""(?i)(?<=[A-Za-z0-9\-]{2})$COMMON_TLD$""")

private val WORDED_EMAIL =
    Regex(
        """(?i)\b[A-Za-z0-9._%+\-]{1,64}+\p{Zs}{1,4}+(?:at|\(at\)|\[at\])\p{Zs}{1,4}+""" +
            """[A-Za-z0-9\-]{1,63}+\p{Zs}{1,4}+(?:dot|\(dot\)|\[dot\])\p{Zs}{1,4}+$COMMON_TLD\b""",
    )

private const val CONSUMER_MAIL_HOST =
    """(?:gmail|googlemail|hotmail|outlook|live|msn|yahoo|ymail|aol|icloud|proton|protonmail|""" +
        """gmx|zoho|yandex|fastmail|tutanota|qq)"""

private val WORDED_AT_BEFORE_A_MAIL_HOST =
    Regex(
        """(?i)\b[A-Za-z0-9._%+\-]{1,64}+\p{Zs}{1,4}+at\p{Zs}{1,4}+$CONSUMER_MAIL_HOST""" +
            """(?:\.|$BRACKETED_DOT)$COMMON_TLD\b""",
    )

private val EMAIL_TOP_LEVEL_LABEL = Regex("""[A-Za-z]{2,24}""")

private val PAYMENT_SERVICE =
    Regex(
        """(?i)\b(?:cash$BETWEEN_THE_WORDS_OF_A_BRAND{0,2}+(?:app|me)|venmo|""" +
            """pay$BETWEEN_THE_WORDS_OF_A_BRAND{0,2}+pal|zelle|wise${DOT_IN_A_BRAND}com|""" +
            """revolut${DOT_IN_A_BRAND}me|square${DOT_IN_A_BRAND}link|monzo${DOT_IN_A_BRAND}me|""" +
            """apple$BETWEEN_THE_WORDS_OF_A_BRAND{0,2}+pay|google$BETWEEN_THE_WORDS_OF_A_BRAND{0,2}+pay|""" +
            """(?:buy$DOT_IN_A_BRAND|checkout$DOT_IN_A_BRAND)?+stripe${DOT_IN_A_BRAND}com|""" +
            """ko-?+fi${DOT_IN_A_BRAND}com|gofundme${DOT_IN_A_BRAND}com|patreon${DOT_IN_A_BRAND}com|""" +
            """western$BETWEEN_THE_WORDS_OF_A_BRAND{0,2}+union|moneygram|payoneer|skrill|""" +
            """bitcoin|ethereum|monero)\b""",
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
        """(?i)(?:\b(?:$WHATSAPP|viber|wechat|kakaotalk)\b|""" +
            """\b(?:t${DOT_IN_A_BRAND}me|telegram${DOT_IN_A_BRAND}me|wa${DOT_IN_A_BRAND}me|""" +
            """m${DOT_IN_A_BRAND}me|api${DOT_IN_A_BRAND}whatsapp${DOT_IN_A_BRAND}com|""" +
            """instagram${DOT_IN_A_BRAND}com|ig${DOT_IN_A_BRAND}me|facebook${DOT_IN_A_BRAND}com|""" +
            """fb${DOT_IN_A_BRAND}me|snapchat${DOT_IN_A_BRAND}com|tiktok${DOT_IN_A_BRAND}com|""" +
            """x${DOT_IN_A_BRAND}com|twitter${DOT_IN_A_BRAND}com|nextdoor${DOT_IN_A_BRAND}com|""" +
            """signal${DOT_IN_A_BRAND}me|discord${DOT_IN_A_BRAND}gg|linkedin${DOT_IN_A_BRAND}com/in)""" +
            """\p{Zs}{0,2}+[$SLASH_LIKE]\p{Zs}{0,2}+[A-Za-z0-9._~%+\-]{2,40}+)""",
    )

private val PAYMENT_PATTERNS = listOf(PAYMENT_SERVICE, CASH_TAG, SORT_CODE, BANK_ACCOUNT, CRYPTO_ADDRESS)

private const val INTERNATIONAL_PREFIX = "+"

private val PHONE_DIGIT_COUNT = 9..15

private const val MOST_PHONE_GROUPS = 6

private const val FEWEST_GROUPS_NO_OTHER_NUMBER_USES = 3

private const val MOST_DIGITS_IN_A_PHONE_GROUP = 6

private const val FEWEST_DIGITS_IN_THE_LAST_PHONE_GROUP = 3

private const val DIGITS_IN_AN_EXCHANGE_GROUP = 3

private const val DIGITS_IN_A_DIALABLE_NUMBER = 10

private const val DIGITS_IN_THE_LINE_GROUP = 4

private const val GROUPS_FROM_THE_END_TO_THE_LINES_OWN_GROUP = 2

private val DIGITS_IN_THE_GROUP_BEFORE_THE_LINE = 3..4

private const val FEWEST_SINGLE_DIGIT_GROUPS = 9

private const val ONE_DIGIT = 1

private const val ONE_GROUP = 1

private const val MOST_DIGITS_BEFORE_A_THOUSANDS_SEPARATOR = 3

private const val DIGITS_IN_A_THOUSANDS_GROUP = 3

private const val PHONE_CUE_WINDOW = 24

private const val EMAIL_DOMAIN_GROUP = 1

private const val FEWEST_DOMAIN_LABELS = 2

private const val ONE_CHARACTER = 1

private val IBAN_LENGTH = 15..34

private const val IBAN_COUNTRY_AND_CHECK_DIGITS = 4

private const val IBAN_MODULUS = 97

private const val IBAN_REMAINDER_OF_A_VALID_NUMBER = 1

private const val DECIMAL_SHIFT = 10

private const val LETTER_SHIFT = 100

private const val FIRST_LETTER_VALUE = 10

fun contactDetailsIn(text: String): Set<ContactDetail> {
    val folds = foldsForMatchingOnly(text)
    return buildSet {
        if (folds.any { holdsPhoneNumber(it) }) add(ContactDetail.PhoneNumber)
        if (folds.any { holdsEmailAddress(it) }) add(ContactDetail.EmailAddress)
        if (folds.any { holdsPaymentDetail(it) }) add(ContactDetail.PaymentLink)
        if (folds.any { MESSAGING_HANDLE.containsMatchIn(it) }) add(ContactDetail.MessagingHandle)
    }
}

fun withoutContactDetails(text: String): Outcome<String, TextFlaw> {
    val kinds = contactDetailsIn(text)
    return if (kinds.isEmpty()) Outcome.Ok(text) else Outcome.Err(TextFlaw.ContactDetails(kinds))
}

private fun foldsForMatchingOnly(text: String): List<String> {
    val compatibility = Normalizer.normalize(text, Normalizer.Form.NFKD)
    val folded =
        buildString(compatibility.length) {
            for (character in compatibility) {
                if (character.category in CATEGORIES_STRIPPED_BEFORE_MATCHING) continue
                if (character in LINE_BREAKS_A_DESCRIPTION_BOX_CREATES) {
                    append(' ')
                } else {
                    append(CONFUSABLES_FOLDED_TO_LATIN[character] ?: character)
                }
            }
        }
    val spacingClosed =
        SPACED_OUT_RUN.replace(folded) { run ->
            run.value.filter { it.category != CharCategory.SPACE_SEPARATOR }
        }
    return if (spacingClosed == folded) listOf(folded) else listOf(folded, spacingClosed)
}

private fun holdsPhoneNumber(folded: String): Boolean =
    PHONE_CANDIDATE.findAll(folded).any { isAPhoneNumber(folded, it, punctuationGrouped = false) } ||
        PUNCTUATION_GROUPED_CANDIDATE.findAll(folded).any {
            isAPhoneNumber(folded, it, punctuationGrouped = true)
        }

private fun isAPhoneNumber(
    folded: String,
    candidate: MatchResult,
    punctuationGrouped: Boolean,
): Boolean {
    val groups = DIGIT_GROUP.findAll(candidate.value).map { characterCount(it.value) }.toList()
    val from = (candidate.range.first - PHONE_CUE_WINDOW).coerceAtLeast(0)
    val to = (candidate.range.last + 1 + PHONE_CUE_WINDOW).coerceAtMost(folded.length)
    val around = folded.substring(from, to)
    val cued = PHONE_CUE.containsMatchIn(around) || UNQUALIFIED_NUMBER_CUE.containsMatchIn(around)
    val prefixed = candidate.value.startsWith(INTERNATIONAL_PREFIX)
    val separators = DIGIT_GROUP.split(candidate.value).drop(1).dropLast(1)
    val digits = groups.sum()
    if (digits in PHONE_DIGIT_COUNT &&
        (
            isSpreadOneDigitToASeparator(groups, separators) ||
                hasPhoneShape(groups, digits, prefixed, cued, punctuationGrouped)
        )
    ) {
        return true
    }
    val unpadded = groups.dropWhile { it == ONE_DIGIT }.dropLastWhile { it == ONE_DIGIT }
    return unpadded.size != groups.size &&
        unpadded.sum() in PHONE_DIGIT_COUNT &&
        hasPhoneShape(
            unpadded,
            digits,
            prefixed && groups.first() != ONE_DIGIT,
            cued,
            punctuationGrouped,
        )
}

private fun hasPhoneShape(
    groups: List<Int>,
    digitsBeforeTrimming: Int,
    internationallyPrefixed: Boolean,
    cued: Boolean,
    punctuationGrouped: Boolean,
): Boolean =
    when {
        punctuationGrouped &&
            groups.first() <= MOST_DIGITS_BEFORE_A_THOUSANDS_SEPARATOR &&
            groups.drop(1).all { it == DIGITS_IN_A_THOUSANDS_GROUP } -> false
        internationallyPrefixed -> true
        groups.size == ONE_GROUP -> cued
        !isGroupedWithinAPhoneNumbersLimits(groups) -> false
        cued -> true
        else -> isDialableWithoutACue(groups, digitsBeforeTrimming)
    }

private fun isGroupedWithinAPhoneNumbersLimits(groups: List<Int>): Boolean =
    groups.size <= MOST_PHONE_GROUPS &&
        groups.all { it <= MOST_DIGITS_IN_A_PHONE_GROUP } &&
        groups.last() >= FEWEST_DIGITS_IN_THE_LAST_PHONE_GROUP

private fun isDialableWithoutACue(
    groups: List<Int>,
    digitsBeforeTrimming: Int,
): Boolean {
    if (groups.size < FEWEST_GROUPS_NO_OTHER_NUMBER_USES) return false
    val digits = groups.sum()
    val endsLikeAnExchangeAndALine =
        digits == DIGITS_IN_A_DIALABLE_NUMBER &&
            groups.last() == DIGITS_IN_THE_LINE_GROUP &&
            groups[groups.size - GROUPS_FROM_THE_END_TO_THE_LINES_OWN_GROUP] in
            DIGITS_IN_THE_GROUP_BEFORE_THE_LINE
    val strayDigitsBrokeUpADialableRun =
        digitsBeforeTrimming == DIGITS_IN_A_DIALABLE_NUMBER &&
            digitsBeforeTrimming > digits &&
            groups.any { it == DIGITS_IN_AN_EXCHANGE_GROUP }
    return endsLikeAnExchangeAndALine || strayDigitsBrokeUpADialableRun
}

private fun isSpreadOneDigitToASeparator(
    groups: List<Int>,
    separators: List<String>,
): Boolean =
    groups.size >= FEWEST_SINGLE_DIGIT_GROUPS &&
        groups.all { it == ONE_DIGIT } &&
        separators.all { SEPARATOR_A_SPREAD_OUT_NUMBER_USES.matches(it) }

private fun holdsEmailAddress(folded: String): Boolean =
    WORDED_EMAIL.containsMatchIn(folded) ||
        WORDED_AT_BEFORE_A_MAIL_HOST.containsMatchIn(folded) ||
        EMAIL_CANDIDATE.findAll(folded).any { hasDomainShape(it.groupValues[EMAIL_DOMAIN_GROUP]) }

private fun hasDomainShape(domain: String): Boolean {
    val labels = LABEL_SEPARATOR_IN_A_DOMAIN.replace(domain, ".").split('.')
    return if (labels.all { it.length == ONE_CHARACTER }) {
        DOMAIN_SPLIT_ONE_LETTER_TO_A_LABEL.containsMatchIn(labels.joinToString(""))
    } else {
        labels.size >= FEWEST_DOMAIN_LABELS && EMAIL_TOP_LEVEL_LABEL.matches(labels.last())
    }
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
