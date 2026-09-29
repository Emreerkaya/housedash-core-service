package com.housedash.adapters.outbound.persistence

import com.housedash.app.OtpRepository
import com.housedash.domain.identity.IssuedOtp
import com.housedash.domain.identity.LoginIdentifier
import com.housedash.domain.identity.OtpRow
import com.housedash.domain.identity.StoredIdentifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

@Repository
open class PostgresOtpRepository(
    private val jdbcTemplate: JdbcTemplate,
) : OtpRepository {
    override fun latestFor(identifier: LoginIdentifier): IssuedOtp? =
        jdbcTemplate
            .query(SELECT_LATEST_SQL, OtpRowMapper, identifier.kind.name, identifier.value)
            .firstOrNull()
            ?.let(IssuedOtp::rehydrate)

    override fun issuedCountSince(
        identifier: LoginIdentifier,
        since: Instant,
    ): Int =
        jdbcTemplate.queryForObject(
            COUNT_BY_IDENTIFIER_SQL,
            Int::class.java,
            identifier.kind.name,
            identifier.value,
            Timestamp.from(since),
        ) ?: NO_ROWS

    override fun issuedCountFromIpSince(
        ip: String,
        since: Instant,
    ): Int = jdbcTemplate.queryForObject(COUNT_BY_IP_SQL, Int::class.java, ip, Timestamp.from(since)) ?: NO_ROWS

    @Transactional
    override fun insert(
        otp: IssuedOtp,
        requesterIp: String,
    ) {
        jdbcTemplate.update(
            INSERT_SQL,
            otp.id.value,
            otp.identifier.kind.name,
            otp.identifier.value,
            otp.codeHash,
            Timestamp.from(otp.issuedAt),
            otp.attempts,
            otp.consumed,
            requesterIp,
        )
    }

    @Transactional
    override fun update(otp: IssuedOtp) {
        jdbcTemplate.update(UPDATE_SQL, otp.attempts, otp.consumed, otp.id.value)
    }

    private object OtpRowMapper : RowMapper<OtpRow> {
        override fun mapRow(
            rs: ResultSet,
            rowNum: Int,
        ): OtpRow =
            OtpRow(
                id = rs.getString("id"),
                identifier = StoredIdentifier(rs.getString("identifier_value"), rs.getString("identifier_kind")),
                codeHash = rs.getString("code_hash"),
                issuedAt = rs.getTimestamp("issued_at").toInstant(),
                attempts = rs.getInt("attempts"),
                consumed = rs.getBoolean("consumed"),
            )
    }

    private companion object {
        const val NO_ROWS = 0

        const val SELECT_LATEST_SQL =
            "SELECT id, identifier_kind, identifier_value, code_hash, issued_at, attempts, consumed " +
                "FROM otp_codes WHERE identifier_kind = ? AND identifier_value = ? ORDER BY issued_at DESC LIMIT 1"

        const val COUNT_BY_IDENTIFIER_SQL =
            "SELECT COUNT(*) FROM otp_codes WHERE identifier_kind = ? AND identifier_value = ? AND issued_at >= ?"

        const val COUNT_BY_IP_SQL = "SELECT COUNT(*) FROM otp_codes WHERE requester_ip = ? AND issued_at >= ?"

        const val INSERT_SQL =
            "INSERT INTO otp_codes " +
                "(id, identifier_kind, identifier_value, code_hash, issued_at, attempts, consumed, requester_ip) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"

        const val UPDATE_SQL = "UPDATE otp_codes SET attempts = ?, consumed = ? WHERE id = ?"
    }
}
