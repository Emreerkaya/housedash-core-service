package com.housedash.domain.identity

import com.housedash.domain.shared.Outcome
import java.time.Instant

class Account private constructor(
    val id: AccountId,
    val identifier: LoginIdentifier,
    private val profiles: AccountProfiles,
    val createdAt: Instant,
) {
    val profileKinds: Set<ProfileKind> get() = profiles.kinds

    fun addProfile(kind: ProfileKind): Outcome<Account, IdentityError> =
        profiles.adding(kind).map { Account(id, identifier, it, createdAt) }

    companion object {
        fun opened(
            id: AccountId,
            identifier: LoginIdentifier,
            role: ProfileKind,
            at: Instant,
        ): Account = Account(id, identifier, AccountProfiles.of(role), at)

        fun rehydrate(
            row: AccountRow,
            profileKinds: List<String>,
        ): Account {
            val id = accountIdOrThrow(row.id)
            val identifier = identifierOrThrow(row)
            val profiles = AccountProfiles.rehydrated(profileKinds.map(::profileKindOrThrow))
            return Account(id, identifier, profiles, row.createdAt)
        }
    }
}

private fun accountIdOrThrow(raw: String): AccountId =
    when (val parsed = AccountId.of(raw)) {
        is Outcome.Ok -> parsed.value
        is Outcome.Err -> throw CorruptAccount(AccountFault.MALFORMED_ID)
    }

private fun identifierOrThrow(row: AccountRow): LoginIdentifier {
    val kind =
        IdentifierKind.entries.firstOrNull { it.name == row.identifier.kind }
            ?: throw CorruptAccount(AccountFault.UNKNOWN_IDENTIFIER_KIND)
    return LoginIdentifier.rehydrated(row.identifier.value, kind)
        ?: throw CorruptAccount(AccountFault.MALFORMED_IDENTIFIER)
}

private fun profileKindOrThrow(raw: String): ProfileKind =
    ProfileKind.entries.firstOrNull { it.name == raw } ?: throw CorruptAccount(AccountFault.UNKNOWN_PROFILE_KIND)
