package com.housedash.domain.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private fun accountRow(
    id: String = "ac_1",
    identifier: StoredIdentifier = StoredIdentifier(AN_EMAIL, "EMAIL"),
): AccountRow = AccountRow(id, identifier, createdAt)

private fun corruptOnLoad(
    row: AccountRow,
    profiles: List<String> = listOf("NESTER"),
): AccountFault = assertFailsWith<CorruptAccount> { Account.rehydrate(row, profiles) }.fault

class AccountRehydrationTest {
    @Test
    fun `an account with one profile round trips with the same id, identifier and creation time`() {
        val back = Account.rehydrate(accountRow(), listOf("NESTER"))
        assertEquals(accountId(), back.id)
        assertEquals(emailIdentifier(), back.identifier)
        assertEquals(createdAt, back.createdAt)
        assertEquals(setOf(ProfileKind.NESTER), back.profileKinds)
    }

    @Test
    fun `an account with both profiles round trips holding both`() {
        val back = Account.rehydrate(accountRow(), listOf("NESTER", "TASKER"))
        assertEquals(setOf(ProfileKind.NESTER, ProfileKind.TASKER), back.profileKinds)
    }

    @Test
    fun `a phone identifier round trips as a phone identifier`() {
        val row = accountRow(identifier = StoredIdentifier(A_PHONE, "PHONE"))
        assertEquals(phoneIdentifier(), Account.rehydrate(row, listOf("TASKER")).identifier)
    }

    @Test
    fun `a malformed account id is refused, constructed directly and never merely believed`() {
        assertEquals(AccountFault.MALFORMED_ID, corruptOnLoad(accountRow(id = "cs_1")))
        assertEquals(AccountFault.MALFORMED_ID, corruptOnLoad(accountRow(id = "ac_")))
    }

    @Test
    fun `an unknown identifier kind is refused`() {
        val fax = accountRow(identifier = StoredIdentifier(AN_EMAIL, "FAX"))
        assertEquals(AccountFault.UNKNOWN_IDENTIFIER_KIND, corruptOnLoad(fax))
        val lowerCase = accountRow(identifier = StoredIdentifier(AN_EMAIL, "email"))
        assertEquals(AccountFault.UNKNOWN_IDENTIFIER_KIND, corruptOnLoad(lowerCase))
    }

    @Test
    fun `an identifier value that no longer matches its stored kind is refused, not silently reinterpreted`() {
        val row = accountRow(identifier = StoredIdentifier("not an email", "EMAIL"))
        assertEquals(AccountFault.MALFORMED_IDENTIFIER, corruptOnLoad(row))
        val phoneShaped = accountRow(identifier = StoredIdentifier(AN_EMAIL, "PHONE"))
        assertEquals(AccountFault.MALFORMED_IDENTIFIER, corruptOnLoad(phoneShaped))
    }

    @Test
    fun `a row with no stored profile is refused, an account is never rootless`() {
        assertEquals(AccountFault.NO_PROFILES, corruptOnLoad(accountRow(), emptyList()))
    }

    @Test
    fun `a row with the same profile stored twice is refused, not deduplicated`() {
        assertEquals(AccountFault.DUPLICATE_PROFILE, corruptOnLoad(accountRow(), listOf("NESTER", "NESTER")))
    }

    @Test
    fun `a row naming an unknown profile kind is refused`() {
        assertEquals(AccountFault.UNKNOWN_PROFILE_KIND, corruptOnLoad(accountRow(), listOf("LANDLORD")))
        assertEquals(AccountFault.UNKNOWN_PROFILE_KIND, corruptOnLoad(accountRow(), listOf("nester")))
    }
}
