package com.housedash.domain.identity

import java.time.Instant

class AccountRow(
    val id: String,
    val identifier: StoredIdentifier,
    val createdAt: Instant,
)
