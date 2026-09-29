package com.housedash.adapters.outbound.persistence

import com.housedash.app.CaseRepository
import com.housedash.app.IntakeAttempt
import com.housedash.app.StoredCase
import com.housedash.domain.case.Case
import com.housedash.domain.case.CaseId
import com.housedash.domain.case.CaseRow
import com.housedash.domain.case.CaseState
import com.housedash.domain.case.DescribedCase
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp

@Repository
open class PostgresCaseRepository(
    private val jdbcTemplate: JdbcTemplate,
) : CaseRepository {
    @Transactional
    override fun storeUnlessAlreadyStored(
        attempt: IntakeAttempt,
        case: DescribedCase,
    ): StoredCase {
        val claimed = jdbcTemplate.update(CLAIM_INTAKE_SQL, attempt.owner.value, attempt.key.value, case.id.value)
        if (claimed == NO_ROWS_CLAIMED) return StoredCase(existingCaseIdFor(attempt), alreadyStored = true)
        insertCase(case)
        insertPhotos(case)
        return StoredCase(case.id, alreadyStored = false)
    }

    open fun caseAt(id: CaseId): Case? = caseAtRawId(id.value)

    private fun existingCaseIdFor(attempt: IntakeAttempt): CaseId {
        val rawId =
            jdbcTemplate.queryForObject(
                SELECT_INTAKE_SQL,
                { rs, _ -> rs.getString(CASE_ID_COLUMN) },
                attempt.owner.value,
                attempt.key.value,
            ) ?: error(INTAKE_WINNER_MUST_EXIST)
        return caseAtRawId(rawId)?.id ?: error(INTAKE_WINNER_MUST_EXIST)
    }

    private fun caseAtRawId(rawId: String): Case? {
        val row = rowAt(rawId) ?: return null
        return Case.rehydrate(row, photoIdsAt(rawId))
    }

    private fun insertCase(case: DescribedCase) {
        jdbcTemplate.update(
            INSERT_CASE_SQL,
            case.id.value,
            case.owner.value,
            CaseState.DESCRIBED.name,
            case.description.text,
            Timestamp.from(case.createdAt),
            Timestamp.from(case.describedAt),
        )
    }

    private fun insertPhotos(case: DescribedCase) {
        case.photos.forEachIndexed { slot, photo ->
            jdbcTemplate.update(INSERT_PHOTO_SQL, case.id.value, slot, photo.value)
        }
    }

    private fun rowAt(rawId: String): CaseRow? = jdbcTemplate.query(SELECT_CASE_SQL, CaseRowMapper, rawId).firstOrNull()

    private fun photoIdsAt(rawId: String): List<String> =
        jdbcTemplate.query(SELECT_PHOTOS_SQL, { rs, _ -> rs.getString(PHOTO_ID_COLUMN) }, rawId)

    private object CaseRowMapper : RowMapper<CaseRow> {
        override fun mapRow(
            rs: ResultSet,
            rowNum: Int,
        ): CaseRow =
            CaseRow(
                id = rs.getString("id"),
                owner = rs.getString("owner"),
                state = rs.getString("state"),
                description = rs.getString("description"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                describedAt = rs.getTimestamp("described_at")?.toInstant(),
            )
    }

    private companion object {
        const val NO_ROWS_CLAIMED = 0

        const val CASE_ID_COLUMN = "case_id"

        const val PHOTO_ID_COLUMN = "photo_id"

        const val INTAKE_WINNER_MUST_EXIST =
            "the unique constraint refused the claim, so the row that won it must exist"

        const val CLAIM_INTAKE_SQL =
            "INSERT INTO case_intakes (owner, intake_key, case_id) VALUES (?, ?, ?) " +
                "ON CONFLICT (owner, intake_key) DO NOTHING"

        const val SELECT_INTAKE_SQL = "SELECT case_id FROM case_intakes WHERE owner = ? AND intake_key = ?"

        const val INSERT_CASE_SQL =
            "INSERT INTO cases (id, owner, state, description, created_at, described_at) VALUES (?, ?, ?, ?, ?, ?)"

        const val INSERT_PHOTO_SQL = "INSERT INTO case_photos (case_id, slot, photo_id) VALUES (?, ?, ?)"

        const val SELECT_CASE_SQL =
            "SELECT id, owner, state, description, created_at, described_at FROM cases WHERE id = ?"

        const val SELECT_PHOTOS_SQL = "SELECT photo_id FROM case_photos WHERE case_id = ? ORDER BY slot"
    }
}
