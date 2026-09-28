package com.housedash.domain.shared

private val FORBIDDEN_CATEGORIES =
    setOf(
        CharCategory.CONTROL,
        CharCategory.FORMAT,
        CharCategory.PRIVATE_USE,
        CharCategory.SURROGATE,
        CharCategory.LINE_SEPARATOR,
        CharCategory.PARAGRAPH_SEPARATOR,
    )

private val CATEGORIES_A_JOINER_MAY_BIND =
    setOf(
        CharCategory.UPPERCASE_LETTER,
        CharCategory.LOWERCASE_LETTER,
        CharCategory.TITLECASE_LETTER,
        CharCategory.MODIFIER_LETTER,
        CharCategory.OTHER_LETTER,
        CharCategory.NON_SPACING_MARK,
        CharCategory.COMBINING_SPACING_MARK,
        CharCategory.ENCLOSING_MARK,
        CharCategory.MATH_SYMBOL,
        CharCategory.CURRENCY_SYMBOL,
        CharCategory.MODIFIER_SYMBOL,
        CharCategory.OTHER_SYMBOL,
        CharCategory.UNASSIGNED,
    )

private val PERMITTED_CONTROLS = setOf('\n'.code, '\t'.code)

private const val ZERO_WIDTH_JOINER = 0x200D

private const val LONE_SURROGATE = -1

private const val NOTHING_PRECEDES = -2

fun isPlainText(text: String): Boolean {
    var index = 0
    var preceding = NOTHING_PRECEDES
    var aJoinerAwaitsItsTarget = false
    while (index < text.length) {
        val codePoint = codePointOrLoneSurrogateAt(text, index)
        if (codePoint == LONE_SURROGATE) return false
        if (!isPermittedAfter(codePoint, preceding, aJoinerAwaitsItsTarget)) return false
        aJoinerAwaitsItsTarget = codePoint == ZERO_WIDTH_JOINER
        preceding = codePoint
        index += Character.charCount(codePoint)
    }
    return !aJoinerAwaitsItsTarget
}

fun characterCount(text: String): Int = text.codePointCount(0, text.length)

private fun codePointOrLoneSurrogateAt(
    text: String,
    index: Int,
): Int {
    val first = text[index]
    if (first.isHighSurrogate()) {
        val second = text.getOrNull(index + 1) ?: return LONE_SURROGATE
        return if (second.isLowSurrogate()) Character.toCodePoint(first, second) else LONE_SURROGATE
    }
    return if (first.isLowSurrogate()) LONE_SURROGATE else first.code
}

private fun isPermittedAfter(
    codePoint: Int,
    preceding: Int,
    aJoinerAwaitsItsTarget: Boolean,
): Boolean =
    if (codePoint == ZERO_WIDTH_JOINER) {
        mayBeBoundByAJoiner(preceding)
    } else {
        isPermitted(codePoint) && (!aJoinerAwaitsItsTarget || mayBeBoundByAJoiner(codePoint))
    }

private fun mayBeBoundByAJoiner(codePoint: Int): Boolean =
    codePoint >= 0 && CharCategory.valueOf(Character.getType(codePoint)) in CATEGORIES_A_JOINER_MAY_BIND

private fun isPermitted(codePoint: Int): Boolean =
    codePoint in PERMITTED_CONTROLS ||
        CharCategory.valueOf(Character.getType(codePoint)) !in FORBIDDEN_CATEGORIES
