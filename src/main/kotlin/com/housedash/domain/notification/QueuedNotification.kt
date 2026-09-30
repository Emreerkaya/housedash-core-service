package com.housedash.domain.notification

import com.housedash.domain.shared.FreeTextRule
import com.housedash.domain.shared.NesterId
import com.housedash.domain.shared.Outcome
import com.housedash.domain.shared.TextFlaw
import java.time.Duration
import java.time.Instant

enum class DeliveryStatus { PENDING, SENT, DEAD }

enum class NotificationFault {
    MALFORMED_ID,
    MALFORMED_OWNER,
    UNKNOWN_STATUS,
    NEGATIVE_ATTEMPT_COUNT,
    NEXT_ATTEMPT_BEFORE_CREATED,
}

class CorruptNotification internal constructor(
    val fault: NotificationFault,
) : IllegalStateException(fault.name)

class NotificationContent(
    val title: String,
    val body: String,
)

class DeliveryProgress(
    val status: DeliveryStatus,
    val attemptCount: Int,
    val nextAttemptAt: Instant,
)

class RawDeliveryProgress(
    val status: String,
    val attemptCount: Int,
    val nextAttemptAt: Instant,
)

class NotificationRow(
    val id: String,
    val owner: String,
    val content: NotificationContent,
    val createdAt: Instant,
    val progress: RawDeliveryProgress,
)

class QueuedNotification private constructor(
    val id: NotificationId,
    val owner: NesterId,
    val content: NotificationContent,
    val createdAt: Instant,
    val progress: DeliveryProgress,
) {
    init {
        if (progress.attemptCount < 0) throw CorruptNotification(NotificationFault.NEGATIVE_ATTEMPT_COUNT)
        if (progress.nextAttemptAt.isBefore(createdAt)) {
            throw CorruptNotification(NotificationFault.NEXT_ATTEMPT_BEFORE_CREATED)
        }
    }

    val title: String get() = content.title

    val body: String get() = content.body

    val status: DeliveryStatus get() = progress.status

    val attemptCount: Int get() = progress.attemptCount

    val nextAttemptAt: Instant get() = progress.nextAttemptAt

    val idempotencyKey: String get() = id.toString()

    override fun toString(): String {
        val description = "QueuedNotification(id=$id, owner=$owner, status=$status, attemptCount=$attemptCount)"
        return description
    }

    fun sent(at: Instant): QueuedNotification =
        QueuedNotification(id, owner, content, createdAt, DeliveryProgress(DeliveryStatus.SENT, attemptCount, at))

    fun failed(at: Instant): QueuedNotification {
        val attempts = attemptCount + 1
        val next =
            if (attempts >= MAX_ATTEMPTS) {
                DeliveryProgress(DeliveryStatus.DEAD, attempts, at)
            } else {
                DeliveryProgress(DeliveryStatus.PENDING, attempts, at.plus(backoffFor(attempts)))
            }
        return QueuedNotification(id, owner, content, createdAt, next)
    }

    companion object {
        const val MAX_ATTEMPTS = 5

        const val TITLE_MIN = 1

        const val TITLE_MAX = 128

        const val BODY_MIN = 1

        const val BODY_MAX = 500

        private val TITLE_RULE = FreeTextRule(TITLE_MIN, TITLE_MAX)

        private val BODY_RULE = FreeTextRule(BODY_MIN, BODY_MAX)

        private val BASE_BACKOFF: Duration = Duration.ofMinutes(1)

        fun queue(
            id: NotificationId,
            owner: NesterId,
            title: String,
            body: String,
            at: Instant,
        ): Outcome<QueuedNotification, NotificationError> =
            TITLE_RULE
                .check(title)
                .mapError { asError(NotificationTextField.TITLE, it) }
                .flatMap { validTitle -> checkedBody(body).map { validBody -> validTitle to validBody } }
                .map { (validTitle, validBody) ->
                    val content = NotificationContent(validTitle, validBody)
                    QueuedNotification(id, owner, content, at, DeliveryProgress(DeliveryStatus.PENDING, 0, at))
                }

        fun rehydrate(row: NotificationRow): QueuedNotification {
            val id = idOrThrow(row.id)
            val owner = ownerOrThrow(row.owner)
            val status = statusOrThrow(row.progress.status)
            val progress = DeliveryProgress(status, row.progress.attemptCount, row.progress.nextAttemptAt)
            return QueuedNotification(id, owner, row.content, row.createdAt, progress)
        }

        private fun checkedBody(body: String): Outcome<String, NotificationError> =
            BODY_RULE.check(body).mapError { asError(NotificationTextField.BODY, it) }

        private fun backoffFor(attempts: Int): Duration = BASE_BACKOFF.multipliedBy(1L shl (attempts - 1))

        private fun idOrThrow(raw: String): NotificationId =
            when (val parsed = NotificationId.of(raw)) {
                is Outcome.Ok -> parsed.value
                is Outcome.Err -> throw CorruptNotification(NotificationFault.MALFORMED_ID)
            }

        private fun ownerOrThrow(raw: String): NesterId =
            when (val parsed = ownerOf(raw)) {
                is Outcome.Ok -> parsed.value
                is Outcome.Err -> throw CorruptNotification(NotificationFault.MALFORMED_OWNER)
            }

        private fun statusOrThrow(raw: String): DeliveryStatus =
            DeliveryStatus.entries.firstOrNull { it.name == raw }
                ?: throw CorruptNotification(NotificationFault.UNKNOWN_STATUS)

        private fun asError(
            field: NotificationTextField,
            flaw: TextFlaw,
        ): NotificationError =
            when (flaw) {
                TextFlaw.NotPlainText -> NotificationError.NotPlainText(field)
                is TextFlaw.TooShort -> NotificationError.TooShort(field, flaw.length, flaw.minimum)
                is TextFlaw.TooLong -> NotificationError.TooLong(field, flaw.length, flaw.maximum)
                is TextFlaw.ContactDetails -> NotificationError.NotPlainText(field)
            }
    }
}
