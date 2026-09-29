package com.housedash.domain.identity

import com.housedash.domain.shared.Outcome

class AccountProfiles private constructor(
    kinds: Set<ProfileKind>,
) {
    val kinds: Set<ProfileKind> = kinds.toSet()

    init {
        if (this.kinds.isEmpty()) throw CorruptAccount(AccountFault.NO_PROFILES)
    }

    fun holds(kind: ProfileKind): Boolean = kind in kinds

    fun adding(kind: ProfileKind): Outcome<AccountProfiles, IdentityError> {
        if (holds(kind)) return Outcome.Err(IdentityError.AlreadyHasProfile(kind))
        return Outcome.Ok(AccountProfiles(kinds + kind))
    }

    internal companion object {
        fun of(kind: ProfileKind): AccountProfiles = AccountProfiles(setOf(kind))

        fun rehydrated(kinds: List<ProfileKind>): AccountProfiles {
            if (kinds.size != kinds.toSet().size) throw CorruptAccount(AccountFault.DUPLICATE_PROFILE)
            return AccountProfiles(kinds.toSet())
        }
    }
}
