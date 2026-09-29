package com.housedash.app

import com.housedash.domain.identity.AccountId
import com.housedash.domain.identity.IdentityError
import com.housedash.domain.shared.Outcome
import java.util.UUID

fun interface AccountIdentifiers {
    fun next(): Outcome<AccountId, IdentityError>
}

private const val ACCOUNT_ID_PREFIX = "ac_"

private const val MARK_UUID_GROUPS_ARE_SPLIT_BY = "-"

class RandomAccountIdentifiers : AccountIdentifiers {
    override fun next(): Outcome<AccountId, IdentityError> =
        AccountId.of(ACCOUNT_ID_PREFIX + UUID.randomUUID().toString().replace(MARK_UUID_GROUPS_ARE_SPLIT_BY, ""))
}
