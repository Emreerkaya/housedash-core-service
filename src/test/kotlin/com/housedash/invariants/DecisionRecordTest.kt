package com.housedash.invariants

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ADR_DIRECTORY = "docs/adr"

private const val NUMBER_SHAPE_SOURCE = "src/main/kotlin/com/housedash/domain/shared/NumberShape.kt"

private const val THE_FLOOR = "FEWEST_DIGITS_IN_THE_LAST_PHONE_GROUP"

private val VALUE_IN_THE_SOURCE = Regex("""$THE_FLOOR = (\d+)""")

private const val VALUE_GROUP = 1

private fun theFloorTheCodeHolds(): String {
    val source = File(NUMBER_SHAPE_SOURCE)
    assertTrue(
        source.isFile,
        "$NUMBER_SHAPE_SOURCE is where the floor is declared and this test cannot find it, so it is comparing " +
            "a record against nothing",
    )
    return VALUE_IN_THE_SOURCE.find(source.readText())?.groupValues?.get(VALUE_GROUP)
        ?: error("$THE_FLOOR is no longer declared in $NUMBER_SHAPE_SOURCE as a plain integer")
}

class DecisionRecordTest {
    @Test
    fun `the value the shipped record names for the last group floor is the value the code holds`() {
        val declared = theFloorTheCodeHolds()
        val recordsNamingTheFloor =
            File(ADR_DIRECTORY)
                .listFiles()
                .orEmpty()
                .filter { it.readText().contains(THE_FLOOR) }
        assertEquals(
            1,
            recordsNamingTheFloor.size,
            "a rule enforced by a test and absent from the record that ships is a rule the next person changes " +
                "by accident, so exactly one file under $ADR_DIRECTORY must name $THE_FLOOR. Files naming it: " +
                "${recordsNamingTheFloor.map { it.name }}",
        )
        val record = recordsNamingTheFloor.single().readText()
        assertTrue(
            record.contains("stays at $declared"),
            "the code declares $THE_FLOOR as $declared and ${recordsNamingTheFloor.single().name} does not say " +
                "it stays at $declared, so the record and the constant have drifted apart and the next reader " +
                "will trust whichever they happen to open",
        )
    }
}
