package com.housedash.domain.shared

import java.text.Normalizer

const val MOST_CHARACTERS_THE_GUARD_READS = 2000

sealed interface ContactDetail {
    data object PhoneNumber : ContactDetail

    data object EmailAddress : ContactDetail

    data object PaymentLink : ContactDetail

    data object MessagingHandle : ContactDetail
}

internal object TablesTheGuardReads {
    val charactersACharacterClassEscapes = setOf('\\', '[', ']', '^', '&', '-')

    val confusablesFoldedToLatin =
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
            '\u0585' to 'o',
            '\u0578' to 'n',
            '\u057D' to 'u',
            '\u0570' to 'h',
            '\u056C' to 'l',
            '\u0566' to 'q',
        )

    val lineBreaksADescriptionBoxCreates =
        setOf('\n', '\r', '\t', '\u000B', '\u000C', '\u0085', '\u2028', '\u2029')

    val categoriesStrippedBeforeMatching =
        setOf(
            CharCategory.FORMAT,
            CharCategory.NON_SPACING_MARK,
            CharCategory.COMBINING_SPACING_MARK,
            CharCategory.ENCLOSING_MARK,
        )

    val slashesAHandleMayBeWrittenWith = listOf('/', '\u2044', '\u2215', '\u29F8')

    val dotsABrandMayBeWrittenWith = listOf('.', '\u00B7', '\u2022', '\u2027', '\u30FB')

    val apostrophesABrandMayCarry = listOf('\'', '\u2019', '\u02BC', '\u055A')

    val separatorsBetweenDigitGroups =
        listOf(
            '\t',
            '.',
            '_',
            '/',
            '\\',
            '(',
            ')',
            '[',
            ']',
            '\u2212',
            '\u00B7',
            '\u2022',
            '\u2027',
            '\u30FB',
        )

    val slashLike = slashesAHandleMayBeWrittenWith.joinToString("")

    val dotABrandMayBeWrittenWith = dotsABrandMayBeWrittenWith.joinToString("")

    val dotInABrand = "[$dotABrandMayBeWrittenWith]"

    val betweenTheWordsOfABrand = """[$dotABrandMayBeWrittenWith\-_$slashLike\p{Zs}]"""

    val apostropheOrNone = apostrophesABrandMayCarry.joinToString("", "[", "]?+")

    val whatsapp =
        """what$betweenTheWordsOfABrand{0,2}+$apostropheOrNone""" +
            """s$betweenTheWordsOfABrand{0,2}+app"""

    val phoneSeparator =
        separatorsBetweenDigitGroups.joinToString("", """[\p{Zs}\p{Pd}""", "]") {
            if (it in charactersACharacterClassEscapes) "\\" + it else it.toString()
        }

    val waysOfAskingToBeRung =
        listOf(
            "call",
            "calls",
            "called",
            "calling",
            "text",
            "texts",
            "texted",
            "ring",
            "rings",
            "dial",
            "dials",
            "phone",
            "phones",
            "telephone",
            "tel",
            "mobile",
            "cell",
            "cellphone",
            whatsapp,
            "sms",
        )

    val namesANumberAsSomethingElse =
        listOf(
            "serial",
            "serials",
            "model",
            "imei",
            "part",
            "parts",
            "sku",
            "meter",
            "reading",
            "invoice",
            "order",
            "ref",
            "reference",
            "barcode",
            "licence",
            "license",
            "policy",
            "warranty",
            "asset",
            "batch",
            "code",
            "account",
            "acct",
            "lot",
            "unit",
            "catalogue",
            "catalog",
            "job",
            "door",
            "flat",
            "buzzer",
            "apartment",
            "room",
            "version",
            "build",
        )

    val topLevelLabels =
        listOf(
            "com",
            "net",
            "org",
            "edu",
            "gov",
            "io",
            "co",
            "me",
            "uk",
            "us",
            "ca",
            "de",
            "fr",
            "nl",
            "es",
            "it",
            "ie",
            "au",
            "info",
            "mail",
            "email",
            "app",
            "dev",
        )

    val consumerMailHosts =
        listOf(
            "gmail",
            "googlemail",
            "hotmail",
            "outlook",
            "live",
            "msn",
            "yahoo",
            "ymail",
            "aol",
            "icloud",
            "proton",
            "protonmail",
            "gmx",
            "zoho",
            "yandex",
            "fastmail",
            "tutanota",
            "qq",
        )

    val paymentServices =
        listOf(
            """cash$betweenTheWordsOfABrand{0,2}+(?:app|me)""",
            "venmo",
            "revolut",
            "wero",
            "payid",
            """strike${dotInABrand}me""",
            """chime${dotInABrand}com""",
            """interac$betweenTheWordsOfABrand{0,2}+e$betweenTheWordsOfABrand{0,2}+transfer""",
            """pay$betweenTheWordsOfABrand{0,2}+pal""",
            "zelle",
            """wise${dotInABrand}com""",
            """square${dotInABrand}link""",
            """monzo${dotInABrand}me""",
            """apple$betweenTheWordsOfABrand{0,2}+pay""",
            """google$betweenTheWordsOfABrand{0,2}+pay""",
            """(?:buy$dotInABrand|checkout$dotInABrand)?+stripe${dotInABrand}com""",
            """ko-?+fi${dotInABrand}com""",
            """gofundme${dotInABrand}com""",
            """patreon${dotInABrand}com""",
            """western$betweenTheWordsOfABrand{0,2}+union""",
            "moneygram",
            "payoneer",
            "skrill",
            "bitcoin",
            "ethereum",
            "monero",
        )

    val messagingApps = listOf(whatsapp, "viber", "wechat", "kakaotalk")

    val messagingHosts =
        listOf(
            """t${dotInABrand}me""",
            """telegram${dotInABrand}me""",
            """wa${dotInABrand}me""",
            """m${dotInABrand}me""",
            """api${dotInABrand}whatsapp${dotInABrand}com""",
            """instagram${dotInABrand}com""",
            """ig${dotInABrand}me""",
            """facebook${dotInABrand}com""",
            """fb${dotInABrand}me""",
            """snapchat${dotInABrand}com""",
            """tiktok${dotInABrand}com""",
            """x${dotInABrand}com""",
            """twitter${dotInABrand}com""",
            """nextdoor${dotInABrand}com""",
            """signal${dotInABrand}me""",
            """discord${dotInABrand}gg""",
            """linkedin${dotInABrand}com/in""",
        )
}

private val CONFUSABLES_FOLDED_TO_LATIN = TablesTheGuardReads.confusablesFoldedToLatin

private val LINE_BREAKS_A_DESCRIPTION_BOX_CREATES = TablesTheGuardReads.lineBreaksADescriptionBoxCreates

private val CATEGORIES_STRIPPED_BEFORE_MATCHING = TablesTheGuardReads.categoriesStrippedBeforeMatching

private val DOT_A_BRAND_MAY_BE_WRITTEN_WITH = TablesTheGuardReads.dotABrandMayBeWrittenWith

private val COMMON_TLD = "(?:" + TablesTheGuardReads.topLevelLabels.joinToString("|") + ")"

private val CONSUMER_MAIL_HOST = "(?:" + TablesTheGuardReads.consumerMailHosts.joinToString("|") + ")"

private val PAYMENT_SERVICE =
    Regex("""(?i)\b(?:${TablesTheGuardReads.paymentServices.joinToString("|")})\b""")

private val MESSAGING_HANDLE =
    Regex(
        """(?i)(?:\b(?:${TablesTheGuardReads.messagingApps.joinToString("|")})\b|""" +
            """\b(?:${TablesTheGuardReads.messagingHosts.joinToString("|")})""" +
            """\p{Zs}{0,2}+[${TablesTheGuardReads.slashLike}]\p{Zs}{0,2}+[A-Za-z0-9._~%+\-]{2,40}+)""",
    )

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

private const val BRACKET_OPENING_A_WORDED_SEPARATOR = """[(\[{<]"""

private const val BRACKET_CLOSING_A_WORDED_SEPARATOR = """[)\]}>]"""

private const val BRACKETED_AT =
    """$BRACKET_OPENING_A_WORDED_SEPARATOR""" +
        """at$BRACKET_CLOSING_A_WORDED_SEPARATOR"""

private val DOT_A_DOMAIN_LABEL_MAY_BE_SEPARATED_BY =
    """[$DOT_A_BRAND_MAY_BE_WRITTEN_WITH\u3002]"""

private const val BRACKETED_DOT =
    """\p{Zs}{0,3}+$BRACKET_OPENING_A_WORDED_SEPARATOR""" +
        """dot$BRACKET_CLOSING_A_WORDED_SEPARATOR\p{Zs}{0,3}+"""

private const val WORDED_DOT_BETWEEN_DOMAIN_LABELS = """\p{Zs}{1,4}+dot\p{Zs}{1,4}+"""

private val DOMAIN_LABEL_SEPARATOR =
    """(?:$BRACKETED_DOT|$WORDED_DOT_BETWEEN_DOMAIN_LABELS|""" +
        """\p{Zs}{0,3}+$DOT_A_DOMAIN_LABEL_MAY_BE_SEPARATED_BY\p{Zs}{0,3}+)"""

private const val DOMAIN_LABEL = """[A-Za-z0-9\-]{1,63}+"""

private const val MOST_SPACES_AROUND_AN_AT_SIGN = "{0,3}+"

private val COMMA_BEFORE_A_TOP_LEVEL_LABEL = """,\p{Zs}{0,3}+$COMMON_TLD(?![A-Za-z0-9])"""

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

private val WORDED_AT_BEFORE_A_MAIL_HOST =
    Regex(
        """(?i)\b[A-Za-z0-9._%+\-]{1,64}+\p{Zs}{1,4}+at\p{Zs}{1,4}+$CONSUMER_MAIL_HOST""" +
            """(?:\.|$BRACKETED_DOT)$COMMON_TLD\b""",
    )

private val EMAIL_TOP_LEVEL_LABEL = Regex("""[A-Za-z]{2,24}""")

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

private val PAYMENT_PATTERNS = listOf(PAYMENT_SERVICE, CASH_TAG, SORT_CODE, BANK_ACCOUNT, CRYPTO_ADDRESS)

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
        if (folds.any { holdsPhoneNumberInAlreadyFoldedText(it) }) add(ContactDetail.PhoneNumber)
        if (folds.any { holdsEmailAddress(it) }) add(ContactDetail.EmailAddress)
        if (folds.any { holdsPaymentDetail(it) }) add(ContactDetail.PaymentLink)
        if (folds.any { MESSAGING_HANDLE.containsMatchIn(it) }) add(ContactDetail.MessagingHandle)
    }
}

fun withoutContactDetails(text: String): Outcome<String, TextFlaw> {
    val characters = characterCount(text)
    if (characters > MOST_CHARACTERS_THE_GUARD_READS) {
        return Outcome.Err(TextFlaw.TooLong(characters, MOST_CHARACTERS_THE_GUARD_READS))
    }
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
