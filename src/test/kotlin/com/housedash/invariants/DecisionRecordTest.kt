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

private val FLOOR_THE_RECORD_NAMES = Regex("""stays at (\d+)(?!\d)""")

private val INTEGER_IN_A_LINE = Regex("""\d+""")

private const val FEWEST_TIMES_A_RECORD_NAMES_THE_FLOOR = 1

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

private fun recordsNamingTheFloor(): List<File> =
    File(ADR_DIRECTORY)
        .listFiles()
        .orEmpty()
        .filter { it.isFile }
        .filter { it.readText().contains(THE_FLOOR) }

private fun theRecordNamingTheFloor(): File {
    val records = recordsNamingTheFloor()
    assertEquals(
        1,
        records.size,
        "a rule enforced by a test and absent from the record that ships is a rule the next person changes " +
            "by accident, so exactly one file under $ADR_DIRECTORY must name $THE_FLOOR. Files naming it: " +
            "${records.map { it.name }}",
    )
    return records.single()
}

class DecisionRecordTest {
    @Test
    fun `exactly one shipped record names the last group floor`() {
        assertTrue(theRecordNamingTheFloor().isFile)
    }

    @Test
    fun `every value the shipped record names for the floor is the value the code holds`() {
        val declared = theFloorTheCodeHolds()
        val record = theRecordNamingTheFloor()
        val named = FLOOR_THE_RECORD_NAMES.findAll(record.readText()).map { it.groupValues[VALUE_GROUP] }.toList()
        assertTrue(
            named.size >= FEWEST_TIMES_A_RECORD_NAMES_THE_FLOOR,
            "${record.name} never says what the floor stays at, so this test compared the constant against " +
                "nothing and passed. A leg that can be satisfied by deleting a sentence is not a leg",
        )
        assertEquals(
            List(named.size) { declared },
            named,
            "the code declares $THE_FLOOR as $declared and ${record.name} names $named. The value is read " +
                "with a capture and compared for equality rather than searched for as a prefix, because a " +
                "record saying it stays at ${declared}0 satisfied a prefix search, and every mention is " +
                "checked rather than the first",
        )
    }

    @Test
    fun `no line of the record that names the constant names another integer`() {
        val declared = theFloorTheCodeHolds()
        val record = theRecordNamingTheFloor()
        val disagreeing =
            record
                .readLines()
                .filter { it.contains(THE_FLOOR) }
                .flatMap { line -> INTEGER_IN_A_LINE.findAll(line).map { it.value } }
                .filterNot { it == declared }
        assertEquals(
            emptyList(),
            disagreeing,
            "a line of ${record.name} that names $THE_FLOOR also names $disagreeing, and the only integer " +
                "such a line may name is the floor itself. What these legs deliver together is that every " +
                "sentence saying what the floor stays at says $declared, and that no line naming the constant " +
                "names another number. What they do not deliver is prose elsewhere in the record that " +
                "describes the floor without naming the constant or the words stays at: this record discusses " +
                "the value 2 deliberately, as the alternative it rejects, so no rule against naming another " +
                "integer anywhere could be written here",
        )
    }
}
