package com.housedash.domain.case

import java.time.Instant

class CaseRow(
    val id: String,
    val owner: String,
    val state: String,
    val description: String?,
    val createdAt: Instant,
    val describedAt: Instant?,
)
