package com.housedash.adapters.outbound.persistence

import com.housedash.app.AccountRepository
import com.housedash.domain.identity.Account
import com.housedash.domain.identity.AccountId
import com.housedash.domain.identity.AccountRow
import com.housedash.domain.identity.LoginIdentifier
import com.housedash.domain.identity.StoredIdentifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp

@Repository
open class PostgresAccountRepository(
    private val jdbcTemplate: JdbcTemplate,
) : AccountRepository {
    @Transactional
    override fun store(account: Account): Account {
        jdbcTemplate.update(
            INSERT_ACCOUNT_SQL,
            account.id.value,
            account.identifier.kind.name,
            account.identifier.value,
            Timestamp.from(account.createdAt),
        )
        account.profileKinds.forEach { kind -> jdbcTemplate.update(INSERT_PROFILE_SQL, account.id.value, kind.name) }
        return account
    }

    override fun accountAt(id: AccountId): Account? = accountFrom(rowAt(id.value))

    override fun accountWithIdentifier(identifier: LoginIdentifier): Account? =
        accountFrom(
            jdbcTemplate
                .query(SELECT_BY_IDENTIFIER_SQL, AccountRowMapper, identifier.kind.name, identifier.value)
                .firstOrNull(),
        )

    private fun accountFrom(row: AccountRow?): Account? {
        val found = row ?: return null
        val profiles = jdbcTemplate.query(SELECT_PROFILES_SQL, { rs, _ -> rs.getString(PROFILE_KIND_COLUMN) }, found.id)
        return Account.rehydrate(found, profiles)
    }

    private fun rowAt(id: String) = jdbcTemplate.query(SELECT_BY_ID_SQL, AccountRowMapper, id).firstOrNull()

    private object AccountRowMapper : RowMapper<AccountRow> {
        override fun mapRow(
            rs: ResultSet,
            rowNum: Int,
        ): AccountRow =
            AccountRow(
                id = rs.getString("id"),
                identifier = StoredIdentifier(rs.getString("identifier_value"), rs.getString("identifier_kind")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
            )
    }

    private companion object {
        const val PROFILE_KIND_COLUMN = "profile_kind"

        const val INSERT_ACCOUNT_SQL =
            "INSERT INTO accounts (id, identifier_kind, identifier_value, created_at) VALUES (?, ?, ?, ?)"

        const val INSERT_PROFILE_SQL = "INSERT INTO account_profiles (account_id, profile_kind) VALUES (?, ?)"

        const val SELECT_BY_ID_SQL =
            "SELECT id, identifier_kind, identifier_value, created_at FROM accounts WHERE id = ?"

        const val SELECT_BY_IDENTIFIER_SQL =
            "SELECT id, identifier_kind, identifier_value, created_at FROM accounts " +
                "WHERE identifier_kind = ? AND identifier_value = ?"

        const val SELECT_PROFILES_SQL = "SELECT profile_kind FROM account_profiles WHERE account_id = ?"
    }
}
