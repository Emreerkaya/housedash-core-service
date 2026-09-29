package com.housedash.records

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ADR_DIRECTORY = "docs/adr"

private const val DOMAIN_SOURCE_ROOT = "src/main/kotlin/com/housedash/domain"

private val A_CONSTANT_THE_PRODUCT_CAN_SEE =
    Regex("""(?m)^\p{Zs}*(?:internal |public )?const val ([A-Z][A-Z_0-9]*) = (\d+)$""")

private const val NAME_GROUP = 1

private const val VALUE_GROUP = 1

private val CONSTANTS_BOUND_TO_A_SHIPPED_RECORD =
    mapOf(
        "FEWEST_DIGITS_IN_THE_LAST_PHONE_GROUP" to "$DOMAIN_SOURCE_ROOT/shared/NumberShape.kt",
        "MOST_CHARACTERS_THE_GUARD_READS" to "$DOMAIN_SOURCE_ROOT/shared/ContactDetail.kt",
        "MAX_PHOTOS" to "$DOMAIN_SOURCE_ROOT/case/Case.kt",
        "MIN_LENGTH" to "$DOMAIN_SOURCE_ROOT/case/Description.kt",
    )

private val CONSTANTS_THAT_DECIDE_NOTHING_A_NESTER_SEES =
    mapOf(
        "MAX_BODY_LENGTH" to
            "the width of an identifier's body, fixed by the identifier shape rather than chosen for a Nester",
    )

private val FLOOR_A_LINE_NAMES = Regex("""stays at (\d+)(?!\d)""")

private val INTEGER_IN_A_LINE = Regex("""\d+""")

private const val FEWEST_TIMES_A_RECORD_NAMES_A_CONSTANT = 1

private fun valueTheCodeHolds(
    name: String,
    path: String,
): String {
    val source = File(path)
    assertTrue(
        source.isFile,
        "$path is where $name is declared and this test cannot find it, so it is comparing a record against " +
            "nothing",
    )
    return Regex("""$name = (\d+)""").find(source.readText())?.groupValues?.get(VALUE_GROUP)
        ?: error("$name is no longer declared in $path as a plain integer")
}

private fun recordsNaming(name: String): List<File> =
    File(ADR_DIRECTORY)
        .listFiles()
        .orEmpty()
        .filter { it.isFile }
        .filter { it.readText().contains(name) }

private fun theRecordNaming(name: String): File {
    val records = recordsNaming(name)
    assertEquals(
        1,
        records.size,
        "a rule enforced by a test and absent from the record that ships is a rule the next person changes by " +
            "accident, so exactly one file under $ADR_DIRECTORY must name $name. Files naming it: " +
            "${records.map { it.name }}",
    )
    return records.single()
}

private fun linesNaming(
    record: File,
    name: String,
): List<String> = record.readLines().filter { it.contains(name) }

class DecisionRecordTest {
    @Test
    fun `every constant the product can see is bound to a record or named as deciding nothing`() {
        val declared =
            File(DOMAIN_SOURCE_ROOT)
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file -> A_CONSTANT_THE_PRODUCT_CAN_SEE.findAll(file.readText()) }
                .map { it.groupValues[NAME_GROUP] }
                .toSet()
        val unaccounted =
            declared - CONSTANTS_BOUND_TO_A_SHIPPED_RECORD.keys - CONSTANTS_THAT_DECIDE_NOTHING_A_NESTER_SEES.keys
        assertTrue(
            unaccounted.isEmpty(),
            "a constant the domain publishes is a product decision until someone says otherwise, and these are " +
                "neither bound to a shipped record nor named here as deciding nothing a Nester sees. The set is " +
                "read from the source rather than listed, so a new one arrives in this failure rather than in " +
                "nobody's inbox: $unaccounted",
        )
        assertTrue(
            declared.containsAll(CONSTANTS_THAT_DECIDE_NOTHING_A_NESTER_SEES.keys),
            "a name excused here no longer exists, so the excuse is now a silent widening of what this test " +
                "lets through",
        )
    }

    @Test
    fun `exactly one shipped record names each constant bound to one`() {
        CONSTANTS_BOUND_TO_A_SHIPPED_RECORD.forEach { (name, _) -> assertTrue(theRecordNaming(name).isFile) }
    }

    @Test
    fun `every value a shipped record names for a bound constant is the value the code holds`() {
        CONSTANTS_BOUND_TO_A_SHIPPED_RECORD.forEach { (name, path) ->
            val declared = valueTheCodeHolds(name, path)
            val record = theRecordNaming(name)
            val named =
                linesNaming(record, name)
                    .flatMap { line -> FLOOR_A_LINE_NAMES.findAll(line).map { it.groupValues[VALUE_GROUP] } }
            assertTrue(
                named.size >= FEWEST_TIMES_A_RECORD_NAMES_A_CONSTANT,
                "no line of ${record.name} that names $name says what it stays at, so this test compared the " +
                    "constant against nothing and passed. A leg that is satisfied by deleting a sentence is " +
                    "not a leg",
            )
            assertEquals(
                List(named.size) { declared },
                named,
                "the code declares $name as $declared and ${record.name} names $named. The value is read with " +
                    "a capture and compared for equality rather than searched for as a prefix, because a " +
                    "record saying it stays at ${declared}0 satisfied a prefix search, and every mention is " +
                    "checked rather than the first",
            )
        }
    }

    @Test
    fun `no line of a record that names a bound constant names another integer`() {
        CONSTANTS_BOUND_TO_A_SHIPPED_RECORD.forEach { (name, path) ->
            val declared = valueTheCodeHolds(name, path)
            val record = theRecordNaming(name)
            val disagreeing =
                linesNaming(record, name)
                    .flatMap { line -> INTEGER_IN_A_LINE.findAll(line).map { it.value } }
                    .filterNot { it == declared }
            assertEquals(
                emptyList(),
                disagreeing,
                "a line of ${record.name} that names $name also names $disagreeing, and the only integer such " +
                    "a line may name is the value itself. What these legs deliver together is that every " +
                    "sentence saying what a bound constant stays at says the value the code holds, and that no " +
                    "line naming it names another number. What they do not deliver is prose elsewhere in a " +
                    "record that describes the value without naming the constant or the words stays at: " +
                    "ADR-0005 discusses the value 2 deliberately, as the alternative it rejects, so no rule " +
                    "against naming another integer anywhere could be written",
            )
        }
    }
}
