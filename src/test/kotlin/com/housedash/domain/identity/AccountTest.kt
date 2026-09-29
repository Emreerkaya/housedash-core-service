package com.housedash.domain.identity

import com.housedash.domain.shared.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun everyProfileState(): List<Set<ProfileKind>> =
    listOf(setOf(ProfileKind.NESTER), setOf(ProfileKind.TASKER), setOf(ProfileKind.NESTER, ProfileKind.TASKER))

private fun accountWith(kinds: Set<ProfileKind>): Account {
    var account = Account.opened(accountId(), emailIdentifier(), kinds.first(), createdAt)
    kinds.drop(1).forEach { extra -> account = assertIs<Outcome.Ok<Account>>(account.addProfile(extra)).value }
    return account
}

class AccountTest {
    @Test
    fun `opening an account holds exactly the one role it was opened with`() {
        val account = Account.opened(accountId(), emailIdentifier(), ProfileKind.NESTER, createdAt)
        assertEquals(setOf(ProfileKind.NESTER), account.profileKinds)
        assertEquals(accountId(), account.id)
        assertEquals(emailIdentifier(), account.identifier)
        assertEquals(createdAt, account.createdAt)
    }

    @Test
    fun `every command in every state lands where the domain model draws it and nowhere else`() {
        var applications = 0
        everyProfileState().forEach { state ->
            ProfileKind.entries.forEach { command ->
                applications += 1
                val account = accountWith(state)
                val outcome = account.addProfile(command)
                if (command in state) {
                    assertEquals(
                        IdentityError.AlreadyHasProfile(command),
                        assertIs<Outcome.Err<IdentityError>>(outcome).error,
                        "$state + $command",
                    )
                } else {
                    val next = assertIs<Outcome.Ok<Account>>(outcome).value
                    assertEquals(state + command, next.profileKinds, "$state + $command")
                }
            }
        }
        assertEquals(everyProfileState().size * ProfileKind.entries.size, applications)
    }

    @Test
    fun `a refused command leaves the account it was applied to unchanged`() {
        val account = accountWith(setOf(ProfileKind.NESTER))
        assertTrue(account.addProfile(ProfileKind.NESTER) is Outcome.Err)
        assertEquals(setOf(ProfileKind.NESTER), account.profileKinds)
    }

    @Test
    fun `a second, different profile can always be added, in either order`() {
        val nesterFirst = accountWith(setOf(ProfileKind.NESTER, ProfileKind.TASKER))
        val taskerFirst = accountWith(setOf(ProfileKind.TASKER, ProfileKind.NESTER))
        assertEquals(setOf(ProfileKind.NESTER, ProfileKind.TASKER), nesterFirst.profileKinds)
        assertEquals(setOf(ProfileKind.NESTER, ProfileKind.TASKER), taskerFirst.profileKinds)
    }
}
