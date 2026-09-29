package com.housedash.app

import com.housedash.domain.identity.Account
import com.housedash.domain.identity.AccountId
import com.housedash.domain.identity.LoginIdentifier

interface AccountRepository {
    fun store(account: Account): Account

    fun accountAt(id: AccountId): Account?

    fun accountWithIdentifier(identifier: LoginIdentifier): Account?
}
