package com.housedash.adapters.outbound.persistence

import com.housedash.app.NotificationOutbox
import com.housedash.domain.notification.NotificationContent
import com.housedash.domain.notification.NotificationRow
import com.housedash.domain.notification.QueuedNotification
import com.housedash.domain.notification.RawDeliveryProgress
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

class PostgresNotificationOutboxRepository(
    dataSource: DataSource,
) : NotificationOutbox {
    private val jdbcTemplate = JdbcTemplate(dataSource)

    private val transactionTemplate = TransactionTemplate(DataSourceTransactionManager(dataSource))

    override fun enqueue(notification: QueuedNotification) {
        jdbcTemplate.update(
            INSERT_SQL,
            notification.id.toString(),
            notification.owner.value,
            notification.title,
            notification.body,
            Timestamp.from(notification.createdAt),
            notification.status.name,
            notification.attemptCount,
            Timestamp.from(notification.nextAttemptAt),
        )
    }

    override fun drainDue(
        now: Instant,
        limit: Int,
        attempt: (QueuedNotification) -> QueuedNotification,
    ): Int =
        transactionTemplate.execute {
            val claimed = jdbcTemplate.query(CLAIM_SQL, RowMapper, Timestamp.from(now), limit)
            claimed.forEach { row -> applyOutcome(attempt(QueuedNotification.rehydrate(row))) }
            claimed.size
        }

    private fun applyOutcome(notification: QueuedNotification) {
        jdbcTemplate.update(
            UPDATE_SQL,
            notification.status.name,
            notification.attemptCount,
            Timestamp.from(notification.nextAttemptAt),
            notification.id.toString(),
        )
    }

    private object RowMapper : org.springframework.jdbc.core.RowMapper<NotificationRow> {
        override fun mapRow(
            rs: ResultSet,
            rowNum: Int,
        ): NotificationRow =
            NotificationRow(
                id = rs.getString("id"),
                owner = rs.getString("owner"),
                content = NotificationContent(rs.getString("title"), rs.getString("body")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                progress =
                    RawDeliveryProgress(
                        status = rs.getString("status"),
                        attemptCount = rs.getInt("attempt_count"),
                        nextAttemptAt = rs.getTimestamp("next_attempt_at").toInstant(),
                    ),
            )
    }

    private companion object {
        const val INSERT_SQL =
            "INSERT INTO notification_outbox " +
                "(id, owner, title, body, created_at, status, attempt_count, next_attempt_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"

        const val CLAIM_SQL =
            "SELECT id, owner, title, body, created_at, status, attempt_count, next_attempt_at " +
                "FROM notification_outbox WHERE status = 'PENDING' AND next_attempt_at <= ? " +
                "ORDER BY next_attempt_at, id FOR UPDATE SKIP LOCKED LIMIT ?"

        const val UPDATE_SQL =
            "UPDATE notification_outbox SET status = ?, attempt_count = ?, next_attempt_at = ? WHERE id = ?"
    }
}
