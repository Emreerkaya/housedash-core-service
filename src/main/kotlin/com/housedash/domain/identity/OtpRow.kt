package com.housedash.domain.identity

import java.time.Instant

class OtpRow(
    val id: String,
    val identifier: StoredIdentifier,
    val codeHash: String,
    val issuedAt: Instant,
    val attempts: Int,
    val consumed: Boolean,
)
