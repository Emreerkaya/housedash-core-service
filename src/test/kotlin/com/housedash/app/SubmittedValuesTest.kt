package com.housedash.app

import com.housedash.domain.case.CaseError
import com.housedash.domain.case.Description
import com.housedash.domain.shared.ContactDetail
import com.housedash.domain.shared.Outcome
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SubmittedValuesTest {
    @Test
    fun `submitted text reaches the domain filter, which is the only place the I7 rule is written`() {
        assertEquals(
            CaseError.ContactDetailsInDescription(setOf(ContactDetail.PhoneNumber)),
            assertIs<Outcome.Err<CaseError>>(SubmittedText(PHONE_IN_A_DESCRIPTION).asDescription()).error,
        )
        assertEquals(
            TAP_DESCRIPTION,
            assertIs<Outcome.Ok<Description>>(SubmittedText(TAP_DESCRIPTION).asDescription()).value.text,
        )
    }

    @Test
    fun `submitted text has one way out and it is the guarded one`() {
        val declared =
            SubmittedText::class.java.declaredMethods
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet()
        assertEquals(
            setOf("asDescription"),
            declared,
            "the wire carries free text as a type rather than a String so that the I7 filter is the only exit. " +
                "A second exit is a second boundary and the guard in CasesWireFormatTest reads the same claim " +
                "from the other side. Synthetic members are dropped because the coverage agent adds one at " +
                "run time and a guard that counts it measures the agent",
        )
    }

    @Test
    fun `a submitted identifier reads as a photograph or as an owner and each refuses the other shape`() {
        assertIs<Outcome.Ok<*>>(SubmittedId("ph_1").asPhotoId())
        assertIs<Outcome.Err<CaseError>>(SubmittedId("ph_1").asOwner())
        assertIs<Outcome.Ok<*>>(SubmittedId("ns_1").asOwner())
        assertIs<Outcome.Err<CaseError>>(SubmittedId("ns_1").asPhotoId())
    }

    @Test
    fun `an intake key is equal by its token and refuses a token outside the identifier shape`() {
        val one = assertIs<Outcome.Ok<IntakeKey>>(SubmittedKey("intake-1").asIntakeKey()).value
        val again = assertIs<Outcome.Ok<IntakeKey>>(SubmittedKey("intake-1").asIntakeKey()).value
        val other = assertIs<Outcome.Ok<IntakeKey>>(SubmittedKey("intake-2").asIntakeKey()).value
        assertEquals(one, again)
        assertEquals(one.hashCode(), again.hashCode())
        assertTrue(one != other)
        assertTrue(!one.equals(TAP_DESCRIPTION))
        assertIs<Outcome.Err<CreateCaseFailure>>(SubmittedKey("intake 1").asIntakeKey())
    }

    @Test
    fun `the random identifier source mints a case identifier the domain accepts, and a fresh one each time`() {
        val source = RandomCaseIdentifiers()
        val first = assertIs<Outcome.Ok<*>>(source.next()).value
        val second = assertIs<Outcome.Ok<*>>(source.next()).value
        assertTrue(first != second)
    }
}
