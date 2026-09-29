package com.housedash.app

import java.security.SecureRandom

fun interface OtpCodeGenerator {
    fun next(): String
}

private const val CODE_DIGITS = 6

private const val DIGIT_BOUND = 10

class SecureRandomOtpCodes : OtpCodeGenerator {
    private val random = SecureRandom()

    override fun next(): String {
        val digit = { random.nextInt(DIGIT_BOUND) }
        return buildString(CODE_DIGITS) { repeat(CODE_DIGITS) { append(digit()) } }
    }
}
