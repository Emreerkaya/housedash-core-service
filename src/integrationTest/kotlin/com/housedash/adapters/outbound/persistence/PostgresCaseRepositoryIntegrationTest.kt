package com.housedash.adapters.outbound.persistence

import com.housedash.app.IntakeAttempt
import com.housedash.app.IntakeKey
import com.housedash.app.SubmittedKey
import com.housedash.domain.case.Case
import com.housedash.domain.case.CaseError
import com.housedash.domain.case.CaseFault
import com.housedash.domain.case.CaseId
import com.housedash.domain.case.CorruptCase
import com.housedash.domain.case.DescribedCase
import com.housedash.domain.case.Description
import com.housedash.domain.case.DraftCase
import com.housedash.domain.case.PhotoId
import com.housedash.domain.case.ownerOf
import com.housedash.domain.shared.NesterId
import com.housedash.domain.shared.Outcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val POSTGRES_IMAGE = "postgres:16"

private const val AN_OWNER = "ns_1"

private val CREATED_AT: Instant = Instant.parse("2026-09-24T10:00:00Z")

private val DESCRIBED_AT: Instant = Instant.parse("2026-09-24T10:05:00Z")

private const val A_DESCRIPTION = "kitchen tap drips from the base and needs a look"

private fun <T, E> valueOf(outcome: Outcome<T, E>): T = assertIs<Outcome.Ok<T>>(outcome).value

private fun ownerId(raw: String = AN_OWNER): NesterId = valueOf(ownerOf(raw))

private fun caseIdOf(raw: String): CaseId = valueOf(CaseId.of(raw))

private fun key(raw: String): IntakeKey = valueOf(SubmittedKey(raw).asIntakeKey())

private fun aCase(
    id: String,
    owner: String = AN_OWNER,
    photos: List<String> = listOf("ph_1", "ph_2"),
    createdAt: Instant = CREATED_AT,
    describedAt: Instant = DESCRIBED_AT,
): DescribedCase {
    val photoIds = photos.map { valueOf<PhotoId, CaseError>(PhotoId.of(it)) }
    val description = valueOf<Description, CaseError>(Description.of(A_DESCRIPTION))
    val draft = assertIs<DraftCase>(Case.draft(caseIdOf(id), ownerId(owner), createdAt))
    return valueOf(draft.describe(ownerId(owner), description, photoIds, describedAt))
}

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PostgresCaseRepositoryIntegrationTest
    @Autowired
    constructor(
        private val repository: PostgresCaseRepository,
        private val jdbcTemplate: JdbcTemplate,
    ) {
        @AfterEach
        fun cleanUp() {
            jdbcTemplate.update("DELETE FROM case_photos")
            jdbcTemplate.update("DELETE FROM case_intakes")
            jdbcTemplate.update("DELETE FROM cases")
        }

        @Test
        fun `a stored case round trips every field through postgres`() {
            val case = aCase("cs_roundtrip1")
            repository.storeUnlessAlreadyStored(IntakeAttempt(case.owner, key("roundtrip-1")), case)
            val read = assertIs<DescribedCase>(repository.caseAt(case.id))
            assertEquals(case.id, read.id)
            assertEquals(case.owner, read.owner)
            assertEquals(case.description, read.description)
            assertEquals(case.photos, read.photos)
            assertEquals(case.createdAt, read.createdAt)
            assertEquals(case.describedAt, read.describedAt)
        }

        @Test
        fun `four photos round trip in the stored order`() {
            val case = aCase("cs_roundtrip2", photos = (1..FOUR_PHOTOS).map { "ph_$it" })
            repository.storeUnlessAlreadyStored(IntakeAttempt(case.owner, key("roundtrip-2")), case)
            val read = assertIs<DescribedCase>(repository.caseAt(case.id))
            assertEquals(case.photos, read.photos)
        }

        @Test
        fun `a case with no photographs round trips as a case with no photographs`() {
            val case = aCase("cs_roundtrip3", photos = emptyList())
            repository.storeUnlessAlreadyStored(IntakeAttempt(case.owner, key("roundtrip-3")), case)
            assertEquals(emptyList(), assertIs<DescribedCase>(repository.caseAt(case.id)).photos)
        }

        @Test
        fun `a case identifier nothing stored reads back as nothing`() {
            assertNull(repository.caseAt(caseIdOf("cs_nothingstored")))
        }

        @Test
        fun `a repeated attempt stores one row and hands back the identifier of the first`() {
            val attempt = IntakeAttempt(ownerId(), key("repeat-1"))
            val first = aCase("cs_repeat_first")
            val second = aCase("cs_repeat_second")
            val firstStored = repository.storeUnlessAlreadyStored(attempt, first)
            val secondStored = repository.storeUnlessAlreadyStored(attempt, second)
            assertEquals(first.id, secondStored.id)
            assertTrue(secondStored.alreadyStored)
            assertEquals(false, firstStored.alreadyStored)
            assertEquals(1, countOf("cases"))
        }

        @Test
        fun `sixteen threads racing one intake key claim it once, because the unique constraint arbitrates`() {
            val attempt = IntakeAttempt(ownerId(), key("race-1"))
            val candidates = (1..THREADS_RACING).map { aCase("cs_race_$it") }
            val pool = Executors.newFixedThreadPool(THREADS_RACING)
            val won =
                candidates
                    .map { candidate ->
                        pool.submit<Boolean> {
                            !repository.storeUnlessAlreadyStored(attempt, candidate).alreadyStored
                        }
                    }.map { it.get(A_FEW_SECONDS, TimeUnit.SECONDS) }
            pool.shutdown()
            assertEquals(
                1,
                won.count { it },
                "exactly one of the sixteen racing attempts must have won the claim on (owner, intake key)",
            )
            assertEquals(1, countOf("cases"), "the unique constraint on case_intakes must let only one case land")
            assertEquals(1, countOf("case_intakes"))
        }

        @Test
        fun `a described row with no description is refused on load, not silently accepted`() {
            insertRawCase("cs_corrupt1", description = null)
            val thrown = assertFailsWith<CorruptCase> { repository.caseAt(caseIdOf("cs_corrupt1")) }
            assertEquals(CaseFault.DESCRIPTION_ABSENT, thrown.fault)
        }

        @Test
        fun `a described row with a description time before it was created is refused on load`() {
            insertRawCase("cs_corrupt2", describedAt = CREATED_AT.minusSeconds(SECONDS_BEFORE_CREATION))
            val thrown = assertFailsWith<CorruptCase> { repository.caseAt(caseIdOf("cs_corrupt2")) }
            assertEquals(CaseFault.DESCRIBED_BEFORE_CREATED, thrown.fault)
        }

        @Test
        fun `a described row with five stored photos is refused on load, not truncated`() {
            insertRawCase("cs_corrupt3")
            for (slot in 1..FIVE_PHOTOS) insertRawPhoto("cs_corrupt3", slot, "ph_$slot")
            val thrown = assertFailsWith<CorruptCase> { repository.caseAt(caseIdOf("cs_corrupt3")) }
            assertEquals(CaseFault.TOO_MANY_PHOTOS, thrown.fault)
        }

        @Test
        fun `a described row with the same photo stored twice is refused on load, not deduplicated`() {
            insertRawCase("cs_corrupt4")
            insertRawPhoto("cs_corrupt4", 0, "ph_1")
            insertRawPhoto("cs_corrupt4", 1, "ph_1")
            val thrown = assertFailsWith<CorruptCase> { repository.caseAt(caseIdOf("cs_corrupt4")) }
            assertEquals(CaseFault.DUPLICATE_PHOTO, thrown.fault)
        }

        private fun countOf(table: String): Int? =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM $table",
                Int::class.java,
            )

        private fun insertRawCase(
            id: String,
            owner: String = AN_OWNER,
            description: String? = A_DESCRIPTION,
            createdAt: Instant = CREATED_AT,
            describedAt: Instant? = DESCRIBED_AT,
        ) {
            jdbcTemplate.update(
                "INSERT INTO cases (id, owner, state, description, created_at, described_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                id,
                owner,
                DESCRIBED_STATE,
                description,
                Timestamp.from(createdAt),
                describedAt?.let { Timestamp.from(it) },
            )
        }

        private fun insertRawPhoto(
            caseId: String,
            slot: Int,
            photoId: String,
        ) {
            jdbcTemplate.update(
                "INSERT INTO case_photos (case_id, slot, photo_id) VALUES (?, ?, ?)",
                caseId,
                slot,
                photoId,
            )
        }

        private companion object {
            const val DESCRIBED_STATE = "DESCRIBED"

            const val THREADS_RACING = 16

            const val A_FEW_SECONDS = 5L

            const val SECONDS_BEFORE_CREATION = 3600L

            const val FIVE_PHOTOS = 5

            const val FOUR_PHOTOS = 4

            @Container
            @ServiceConnection
            @JvmStatic
            val postgres: PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
        }
    }
