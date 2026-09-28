package com.housedash.domain.shared

private val FORBIDDEN_CATEGORIES =
    setOf(
        CharCategory.UNASSIGNED,
        CharCategory.CONTROL,
        CharCategory.FORMAT,
        CharCategory.PRIVATE_USE,
        CharCategory.SURROGATE,
        CharCategory.LINE_SEPARATOR,
        CharCategory.PARAGRAPH_SEPARATOR,
    )

private val PERMITTED_CONTROLS = setOf('\n'.code, '\t'.code)

fun isPlainText(text: String): Boolean {
    var index = 0
    while (index < text.length) {
        val first = text[index]
        val codePoint: Int
        if (first.isHighSurrogate()) {
            val second = text.getOrNull(index + 1)
            if (second == null || !second.isLowSurrogate()) return false
            codePoint = Character.toCodePoint(first, second)
            index += 2
        } else {
            if (first.isLowSurrogate()) return false
            codePoint = first.code
            index += 1
        }
        if (!isPermitted(codePoint)) return false
    }
    return true
}

fun characterCount(text: String): Int = text.codePointCount(0, text.length)

private fun isPermitted(codePoint: Int): Boolean =
    codePoint in PERMITTED_CONTROLS ||
        CharCategory.valueOf(Character.getType(codePoint)) !in FORBIDDEN_CATEGORIES
