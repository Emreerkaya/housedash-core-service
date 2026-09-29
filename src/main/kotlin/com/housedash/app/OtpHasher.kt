package com.housedash.app

import java.security.MessageDigest
import java.util.HexFormat

fun interface OtpHasher {
    fun hash(code: String): String
}

private const val HASH_ALGORITHM = "SHA-256"

class Sha256OtpHasher(
    private val pepper: String,
) : OtpHasher {
    override fun hash(code: String): String {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        digest.update(pepper.toByteArray(Charsets.UTF_8))
        digest.update(code.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest.digest())
    }
}
