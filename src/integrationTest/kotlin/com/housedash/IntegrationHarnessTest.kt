package com.housedash

import com.housedash.domain.case.CaseError
import com.housedash.domain.case.Description
import com.housedash.domain.shared.Outcome
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class IntegrationHarnessTest {
    @Test
    fun `the integration test source set runs as part of check and sees production code`() {
        val rejected = Description.of("Kitchen tap drips from the base, reach me on 917,555,0199 today")
        assertEquals(
            CaseError.ContactDetailsInDescription(setOf(com.housedash.domain.shared.ContactDetail.PhoneNumber)),
            assertIs<Outcome.Err<CaseError>>(rejected).error,
            "this suite exists to prove that src/integrationTest is compiled, run by check, and has the main " +
                "output on its classpath. It asserted that two plus two is four, which proves none of those " +
                "three and cannot fail on any change to this repository",
        )
        assertIs<Outcome.Ok<Description>>(Description.of("Kitchen tap drips from the base and needs a look"))
    }
}
