package com.housedash.domain.money

class LedgerRow(
    val capturedCents: Long,
    val releasedCents: Long,
    val refundedCents: Long,
    val feePaidCents: Long,
)

class HoldRow(
    val id: String,
    val state: String,
    val acceptedTotalCents: Long,
    val ledger: LedgerRow,
    val bookingCompleted: Boolean?,
    val nesterConfirmed: Boolean?,
)
