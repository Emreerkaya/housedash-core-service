package com.housedash.adapters.outbound.persistence

import com.housedash.app.ActiveDeviceToken
import com.housedash.domain.notification.DeviceId
import com.housedash.domain.notification.DeviceRegistration
import com.housedash.domain.notification.DeviceRegistrationRow
import com.housedash.domain.notification.DeviceTokenId
import com.housedash.domain.notification.QueuedNotification
import com.housedash.domain.shared.NesterId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

class PostgresDeviceRegistrationRepository(
    dataSource: DataSource,
) {
    private val jdbcTemplate = JdbcTemplate(dataSource)

    private val transactionTemplate = TransactionTemplate(DataSourceTransactionManager(dataSource))

    fun upsert(registration: DeviceRegistration): DeviceTokenId {
        insertOrReplace(registration)
        return registration.id
    }

    fun find(
        owner: NesterId,
        device: DeviceId,
    ): DeviceRegistration? =
        jdbcTemplate
            .query(SELECT_ONE_SQL, RegistrationRowMapper, owner.value, device.value)
            .firstOrNull()
            ?.let(DeviceRegistration::rehydrate)

    fun revoke(
        owner: NesterId,
        device: DeviceId,
        at: Instant,
    ): Boolean = jdbcTemplate.update(REVOKE_SQL, Timestamp.from(at), owner.value, device.value) > 0

    fun activeTokensFor(owner: NesterId): List<ActiveDeviceToken> =
        jdbcTemplate
            .query(SELECT_ACTIVE_SQL, RegistrationRowMapper, owner.value)
            .map(DeviceRegistration::rehydrate)
            .map { ActiveDeviceToken(it.device, it.token) }

    fun registerAndEnqueueWelcome(
        registration: DeviceRegistration,
        welcome: QueuedNotification,
    ): Unit =
        transactionTemplate.executeWithoutResult {
            insertOrReplace(registration)
            jdbcTemplate.update(
                INSERT_NOTIFICATION_SQL,
                welcome.id.toString(),
                welcome.owner.value,
                welcome.title,
                welcome.body,
                Timestamp.from(welcome.createdAt),
                welcome.status.name,
                welcome.attemptCount,
                Timestamp.from(welcome.nextAttemptAt),
            )
        }

    private fun insertOrReplace(registration: DeviceRegistration) {
        jdbcTemplate.update(
            UPSERT_SQL,
            registration.id.toString(),
            registration.owner.value,
            registration.device.value,
            registration.token,
            Timestamp.from(registration.registeredAt),
            registration.revokedAt?.let(Timestamp::from),
        )
    }

    private object RegistrationRowMapper : RowMapper<DeviceRegistrationRow> {
        override fun mapRow(
            rs: ResultSet,
            rowNum: Int,
        ): DeviceRegistrationRow =
            DeviceRegistrationRow(
                id = rs.getString("id"),
                owner = rs.getString("owner"),
                device = rs.getString("device_id"),
                token = rs.getString("token"),
                registeredAt = rs.getTimestamp("registered_at").toInstant(),
                revokedAt = rs.getTimestamp("revoked_at")?.toInstant(),
            )
    }

    private companion object {
        const val UPSERT_SQL =
            "INSERT INTO device_tokens (id, owner, device_id, token, registered_at, revoked_at) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (owner, device_id) DO UPDATE SET " +
                "id = EXCLUDED.id, token = EXCLUDED.token, registered_at = EXCLUDED.registered_at, " +
                "revoked_at = EXCLUDED.revoked_at"

        const val SELECT_ONE_SQL =
            "SELECT id, owner, device_id, token, registered_at, revoked_at FROM device_tokens " +
                "WHERE owner = ? AND device_id = ?"

        const val SELECT_ACTIVE_SQL =
            "SELECT id, owner, device_id, token, registered_at, revoked_at FROM device_tokens " +
                "WHERE owner = ? AND revoked_at IS NULL"

        const val REVOKE_SQL =
            "UPDATE device_tokens SET revoked_at = ? WHERE owner = ? AND device_id = ? AND revoked_at IS NULL"

        const val INSERT_NOTIFICATION_SQL =
            "INSERT INTO notification_outbox " +
                "(id, owner, title, body, created_at, status, attempt_count, next_attempt_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
    }
}
