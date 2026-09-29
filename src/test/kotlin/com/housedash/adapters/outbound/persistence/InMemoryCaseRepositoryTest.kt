package com.housedash.adapters.outbound.persistence

import com.housedash.app.CREATED_AT
import com.housedash.app.CreateCase
import com.housedash.app.FIXED_CLOCK
import com.housedash.app.RandomCaseIdentifiers
import com.housedash.app.StoredCase
import com.housedash.app.TAP_DESCRIPTION
import com.housedash.app.intake
import com.housedash.app.valueOf
import com.housedash.domain.case.CaseId
import com.housedash.domain.case.CaseState
import com.housedash.domain.case.DescribedCase
import com.housedash.domain.shared.Outcome
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryCaseRepositoryTest {
    private val cases = InMemoryCaseRepository()

    private val createCase = CreateCase(cases, RandomCaseIdentifiers(), FIXED_CLOCK)

    @Test
    fun `a stored case comes back through the row the repository wrote, which nothing else may read`() {
        val stored = valueOf(createCase.handle(intake()))
        val read = assertIs<DescribedCase>(cases.caseAt(stored.id))
        assertEquals(CaseState.DESCRIBED, read.state)
        assertEquals(TAP_DESCRIPTION, read.description.text)
        assertEquals(listOf("ph_1", "ph_2"), read.photos.map { it.value })
        assertEquals(CREATED_AT, read.createdAt)
        assertEquals(CREATED_AT, read.describedAt)
        assertEquals("ns_1", read.owner.value)
        assertEquals(stored.id, read.id)
    }

    @Test
    fun `a case with no photographs round trips as a case with no photographs`() {
        val stored = valueOf(createCase.handle(intake(photos = null)))
        assertEquals(emptyList(), assertIs<DescribedCase>(cases.caseAt(stored.id)).photos)
    }

    @Test
    fun `a case identifier nothing stored reads back as nothing rather than as a corrupt row`() {
        assertNull(cases.caseAt(assertIs<Outcome.Ok<CaseId>>(CaseId.of("cs_nothingstored")).value))
    }

    @Test
    fun `a repeated attempt stores one row and hands back the identifier of the first`() {
        val first = valueOf(createCase.handle(intake()))
        val second = valueOf(createCase.handle(intake()))
        assertEquals(first.id.value, second.id.value)
        assertTrue(second.alreadyStored)
        assertEquals(1, cases.storedCases())
    }

    @Test
    fun `sixteen threads racing one intake key store one case, because the attempt is claimed atomically`() {
        val pool = Executors.newFixedThreadPool(THREADS_RACING)
        val answers =
            (1..THREADS_RACING)
                .map { pool.submit<StoredCase> { valueOf(createCase.handle(intake(key = "race-1"))) } }
                .map { it.get(A_FEW_SECONDS, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(
            1,
            cases.storedCases(),
            "idempotency that reads and then writes has a window between the two; the attempt is claimed by " +
                "one atomic computeIfAbsent so a repeat cannot make a second case even under a race",
        )
        assertEquals(1, answers.map { it.id.value }.distinct().size)
        assertEquals(1, answers.count { !it.alreadyStored })
    }

    private companion object {
        const val THREADS_RACING = 16

        const val A_FEW_SECONDS = 5L
    }
}
