package com.housedash.domain.shared

internal const val PUNCTUATION_THE_THOUSANDS_ARM_OWNS = ",;:"

internal val waysOfAskingToBeRung =
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
        TablesTheGuardReads.whatsapp,
        "sms",
    )

internal val namesANumberAsSomethingElse =
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

private const val PHONE_SEPARATOR_OR_NONE = TablesTheGuardReads.SEPARATOR_OR_NONE_BETWEEN_TWO_DIGIT_GROUPS

private val PHONE_CUE =
    Regex("""(?i)\b(?:${waysOfAskingToBeRung.joinToString("|")})\b""")

private val NAMES_A_NUMBER_AS_SOMETHING_ELSE =
    "(?:" + namesANumberAsSomethingElse.joinToString("|") + ")"

private const val MOST_GROUPS_A_CANDIDATE_MAY_HOLD = "{0,31}+"

private val PHONE_CANDIDATE =
    Regex(
        """\+?+\p{Nd}(?:$PHONE_SEPARATOR_OR_NONE\p{Nd})""" +
            MOST_GROUPS_A_CANDIDATE_MAY_HOLD,
    )

private const val WHOLE_GROUP_OF_DIGITS = """(?<!\p{Nd})\p{Nd}{1,6}+(?!\p{Nd})"""

private val THOUSANDS_PUNCTUATION = """[${PUNCTUATION_THE_THOUSANDS_ARM_OWNS}]\p{Zs}{0,2}+"""

private const val MOST_PUNCTUATED_SEPARATORS = "{1,5}+"

private val PUNCTUATION_GROUPED_CANDIDATE =
    Regex(
        """\+?+$WHOLE_GROUP_OF_DIGITS(?:$THOUSANDS_PUNCTUATION$WHOLE_GROUP_OF_DIGITS)""" +
            MOST_PUNCTUATED_SEPARATORS,
    )

private val DIGIT_GROUP = Regex("""\p{Nd}++""")

private val THOUSANDS_GROUPED_SEPARATOR = Regex(THOUSANDS_PUNCTUATION)

private val ENDS_ONE_NUMBER_IN_A_LIST =
    Regex("""[${PUNCTUATION_THE_THOUSANDS_ARM_OWNS}]\p{Zs}{1,2}+""")

private val NAMES_THE_NUMBER_AS_SOMETHING_ELSE =
    Regex("""(?i)\b$NAMES_A_NUMBER_AS_SOMETHING_ELSE\b""")

private val UNQUALIFIED_NUMBER_CUE =
    Regex("""(?i)(?<!\b$NAMES_A_NUMBER_AS_SOMETHING_ELSE\p{Zs}{0,4})\bnumbers?\b""")

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

private const val ONE_DIGIT = 1

private const val ONE_GROUP = 1

private const val MOST_DIGITS_BEFORE_A_THOUSANDS_SEPARATOR = 3

private const val DIGITS_IN_A_THOUSANDS_GROUP = 3

private const val NO_MARK = 0

private const val ONE_CHUNK = 1

private val DIGITS_A_DIALABLE_NUMBER_REACHES = PHONE_DIGIT_COUNT.first

private const val PHONE_CUE_WINDOW = 24

internal fun holdsPhoneNumberIn(folded: FoldedForMatchingOnly): Boolean {
    val text = folded.text
    return PHONE_CANDIDATE.findAll(text).any { isAPhoneNumber(text, it) } ||
        PUNCTUATION_GROUPED_CANDIDATE.findAll(text).any { isAPhoneNumber(text, it) }
}

private data class WordsAroundTheRun(
    val cued: Boolean,
    val namedOtherwise: Boolean,
)

private class HowTheRunIsPunctuated(
    groups: List<Int>,
    separators: List<String>,
) {
    val groupedByTheThousandsMarks = isGroupedByTheThousandsMarks(separators)

    val readsAsAListOfNumbers = groupedByTheThousandsMarks && readsAsAListOfNumbers(groups, separators)
}

private fun wordsAround(
    folded: String,
    candidate: MatchResult,
): WordsAroundTheRun {
    val from = (candidate.range.first - PHONE_CUE_WINDOW).coerceAtLeast(0)
    val to = (candidate.range.last + 1 + PHONE_CUE_WINDOW).coerceAtMost(folded.length)
    val around = folded.substring(from, to)
    return WordsAroundTheRun(
        cued = PHONE_CUE.containsMatchIn(around) || UNQUALIFIED_NUMBER_CUE.containsMatchIn(around),
        namedOtherwise = NAMES_THE_NUMBER_AS_SOMETHING_ELSE.containsMatchIn(around),
    )
}

private fun isAPhoneNumber(
    folded: String,
    candidate: MatchResult,
): Boolean {
    val groups = DIGIT_GROUP.findAll(candidate.value).map { characterCount(it.value) }.toList()
    val words = wordsAround(folded, candidate)
    val prefixed = candidate.value.startsWith(INTERNATIONAL_PREFIX)
    val separators = DIGIT_GROUP.split(candidate.value).drop(1).dropLast(1)
    val punctuation = HowTheRunIsPunctuated(groups, separators)
    val digits = groups.sum()
    if (digits in PHONE_DIGIT_COUNT &&
        (
            isSpreadOneDigitToASeparator(groups, separators) ||
                hasPhoneShape(groups, digits, prefixed, words, punctuation)
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
            words,
            punctuation,
        )
}

private fun isGroupedByTheThousandsMarks(separators: List<String>): Boolean =
    separators.isNotEmpty() && separators.all { THOUSANDS_GROUPED_SEPARATOR.matches(it) }

private fun hasPhoneShape(
    groups: List<Int>,
    digitsBeforeTrimming: Int,
    internationallyPrefixed: Boolean,
    words: WordsAroundTheRun,
    punctuation: HowTheRunIsPunctuated,
): Boolean =
    when {
        punctuation.groupedByTheThousandsMarks && isGroupedLikeThousands(groups) -> false
        punctuation.readsAsAListOfNumbers -> false
        internationallyPrefixed -> true
        groups.size == ONE_GROUP -> words.cued
        !isGroupedWithinAPhoneNumbersLimits(groups) -> false
        words.cued -> digitsBeforeTrimming >= DIGITS_IN_A_DIALABLE_NUMBER
        else -> isDialableWithoutACue(groups, digitsBeforeTrimming, words.namedOtherwise)
    }

private fun isGroupedLikeThousands(groups: List<Int>): Boolean =
    groups.first() <= MOST_DIGITS_BEFORE_A_THOUSANDS_SEPARATOR &&
        groups.drop(1).all { it == DIGITS_IN_A_THOUSANDS_GROUP }

private fun isGroupedWithinAPhoneNumbersLimits(groups: List<Int>): Boolean =
    groups.size <= MOST_PHONE_GROUPS &&
        groups.all { it <= MOST_DIGITS_IN_A_PHONE_GROUP } &&
        groups.last() >= FEWEST_DIGITS_IN_THE_LAST_PHONE_GROUP

private fun isDialableWithoutACue(
    groups: List<Int>,
    digitsBeforeTrimming: Int,
    namedOtherwise: Boolean,
): Boolean {
    if (groups.size < FEWEST_GROUPS_NO_OTHER_NUMBER_USES) return false
    val digits = groups.sum()
    val strayDigitsBrokeUpADialableRun =
        !namedOtherwise &&
            digitsBeforeTrimming == DIGITS_IN_A_DIALABLE_NUMBER &&
            digitsBeforeTrimming > digits &&
            groups.any { it == DIGITS_IN_AN_EXCHANGE_GROUP }
    return endsLikeAnExchangeAndALine(groups) || strayDigitsBrokeUpADialableRun
}

private fun endsLikeAnExchangeAndALine(groups: List<Int>): Boolean =
    groups.size >= GROUPS_FROM_THE_END_TO_THE_LINES_OWN_GROUP &&
        groups.sum() == DIGITS_IN_A_DIALABLE_NUMBER &&
        groups.last() == DIGITS_IN_THE_LINE_GROUP &&
        groups[groups.size - GROUPS_FROM_THE_END_TO_THE_LINES_OWN_GROUP] in DIGITS_IN_THE_GROUP_BEFORE_THE_LINE

private fun marksIn(separator: String): Int = separator.count { it.category != CharCategory.SPACE_SEPARATOR }

private fun digitsInEachChunk(
    groups: List<Int>,
    separators: List<String>,
    endsAChunk: (String) -> Boolean,
): List<Int> {
    val chunks = mutableListOf(groups.first())
    groups.drop(1).zip(separators).forEach { (group, separator) ->
        if (endsAChunk(separator)) chunks.add(group) else chunks[chunks.lastIndex] += group
    }
    return chunks
}

private fun theSpacesGroupTheRunAsANumberIsGrouped(
    groups: List<Int>,
    separators: List<String>,
): Boolean {
    val chunks = digitsInEachChunk(groups, separators) { marksIn(it) == NO_MARK }
    return chunks.size == ONE_CHUNK ||
        chunks.all { it == ONE_DIGIT } ||
        isGroupedWithinAPhoneNumbersLimits(chunks)
}

private fun readsAsAListOfNumbers(
    groups: List<Int>,
    separators: List<String>,
): Boolean {
    val numbers = digitsInEachChunk(groups, separators) { ENDS_ONE_NUMBER_IN_A_LIST.matches(it) }
    return numbers.size > ONE_CHUNK &&
        numbers.none { it >= DIGITS_A_DIALABLE_NUMBER_REACHES } &&
        !endsLikeAnExchangeAndALine(numbers)
}

private fun isSpreadOneDigitToASeparator(
    groups: List<Int>,
    separators: List<String>,
): Boolean =
    groups.size >= DIGITS_IN_A_DIALABLE_NUMBER &&
        groups.all { it == ONE_DIGIT } &&
        theSpacesGroupTheRunAsANumberIsGrouped(groups, separators)
