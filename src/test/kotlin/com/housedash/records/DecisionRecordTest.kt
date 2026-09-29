package com.housedash.records

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ADR_DIRECTORY = "docs/adr"

private const val DOMAIN_SOURCE_ROOT = "src/main/kotlin/com/housedash/domain"

private val A_CONSTANT_THE_DOMAIN_DECLARES =
    Regex("""(?m)^\p{Zs}*(private |internal |public )?const val ([A-Z][A-Z_0-9]*) =\p{Zs}?(.*)$""")

private const val VISIBILITY_GROUP = 1

private const val KEPT_PRIVATE = "private "

private const val NAME_GROUP = 2

private const val VALUE_GROUP = 1

private const val VALUE_OF_A_DECLARATION_GROUP = 3

private val CONSTANTS_BOUND_TO_A_SHIPPED_RECORD =
    mapOf(
        "FEWEST_DIGITS_IN_THE_LAST_PHONE_GROUP" to "$DOMAIN_SOURCE_ROOT/shared/NumberShape.kt",
        "DIGITS_IN_A_DIALABLE_NUMBER" to "$DOMAIN_SOURCE_ROOT/shared/NumberShape.kt",
        "MOST_CHARACTERS_THE_GUARD_READS" to "$DOMAIN_SOURCE_ROOT/shared/ContactDetail.kt",
        "MAX_PHOTOS" to "$DOMAIN_SOURCE_ROOT/case/Case.kt",
        "MIN_LENGTH" to "$DOMAIN_SOURCE_ROOT/case/Description.kt",
        "PERCENT_OF_THE_ACCEPTED_TOTAL" to "$DOMAIN_SOURCE_ROOT/money/CallOutFee.kt",
        "CAP_CENTS" to "$DOMAIN_SOURCE_ROOT/money/CallOutFee.kt",
        "ATTEMPT_CAP" to "$DOMAIN_SOURCE_ROOT/identity/IssuedOtp.kt",
    )

private val CONSTANTS_THAT_CARRY_ANOTHER_CONSTANTS_RECORD =
    mapOf(
        "MAX_LENGTH" to "MOST_CHARACTERS_THE_GUARD_READS",
    )

private val CONSTANTS_THE_DOMAIN_KEEPS_PRIVATE =
    setOf(
        "BETWEEN_TWO_SPACED_OUT_CHARACTERS",
        "BRACKETED_AT",
        "BRACKETED_DOT",
        "BRACKET_CLOSING_A_WORDED_SEPARATOR",
        "BRACKET_OPENING_A_WORDED_SEPARATOR",
        "DECIMAL_SHIFT",
        "DIGITS_IN_AN_EXCHANGE_GROUP",
        "DIGITS_IN_A_THOUSANDS_GROUP",
        "DIGITS_IN_THE_LINE_GROUP",
        "DOMAIN_LABEL",
        "EMAIL_DOMAIN_GROUP",
        "FEWEST_DOMAIN_LABELS",
        "FEWEST_FURTHER_CHARACTERS_IN_A_SPACED_OUT_RUN",
        "FEWEST_GROUPS_NO_OTHER_NUMBER_USES",
        "FIRST_LETTER_VALUE",
        "GROUPS_FROM_THE_END_TO_THE_LINES_OWN_GROUP",
        "HALF_OF_THE_DENOMINATOR",
        "IBAN_COUNTRY_AND_CHECK_DIGITS",
        "IBAN_MODULUS",
        "IBAN_REMAINDER_OF_A_VALID_NUMBER",
        "INTERNATIONAL_PREFIX",
        "LETTER_SHIFT",
        "LONE_SURROGATE",
        "MINIMUM_BODY_LENGTH",
        "MOST_CHARACTERS_AN_IDENTIFIER_HOLDS",
        "EMAIL_LOCAL_PART",
        "EMAIL_DOMAIN_LABEL",
        "MOST_DIGITS_BEFORE_A_THOUSANDS_SEPARATOR",
        "MOST_DIGITS_IN_A_PHONE_GROUP",
        "MOST_GROUPS_A_CANDIDATE_MAY_HOLD",
        "MOST_PHONE_GROUPS",
        "MOST_PUNCTUATED_SEPARATORS",
        "MOST_SPACES_AROUND_AN_AT_SIGN",
        "NOTHING_PRECEDES",
        "NO_MARK",
        "ONE_CHARACTER",
        "ONE_CHUNK",
        "ONE_DIGIT",
        "ONE_GROUP",
        "PERCENT_DENOMINATOR",
        "PHONE_CUE_WINDOW",
        "PHONE_SEPARATOR_OR_NONE",
        "PUNCTUATION_A_SPACED_OUT_RUN_KEEPS",
        "SEPARATOR_BETWEEN_TWO_GROUPS",
        "SEPARATOR_OR_NONE_BETWEEN_TWO_GROUPS",
        "SINGLE_CHARACTER_TOKEN",
        "WHOLE",
        "WHOLE_GROUP_OF_DIGITS",
        "WORDED_AT_HOWEVER_SPACED",
        "WORDED_DOT_BETWEEN_DOMAIN_LABELS",
        "WORDED_DOT_HOWEVER_SPACED",
        "ZERO_WIDTH_JOINER",
    )

private const val A_PIECE_OF_THE_FILTERS_PATTERN_VOCABULARY =
    "a piece of the filter's pattern vocabulary rather than a limit a Nester meets, governed by " +
        "FilterPatternVocabularyTest, which reads every declaration in the two files the filter is written in"

private val CONSTANTS_THAT_DECIDE_NOTHING_A_NESTER_SEES =
    mapOf(
        "MAX_BODY_LENGTH" to
            "the width of an identifier's body, fixed by the identifier shape rather than chosen for a Nester",
        "MARK_BETWEEN_TWO_DIGIT_GROUPS" to A_PIECE_OF_THE_FILTERS_PATTERN_VOCABULARY,
        "MARKS_ONE_SEPARATOR_IS_WRITTEN_WITH" to A_PIECE_OF_THE_FILTERS_PATTERN_VOCABULARY,
        "SEPARATOR_BETWEEN_TWO_DIGIT_GROUPS" to A_PIECE_OF_THE_FILTERS_PATTERN_VOCABULARY,
        "SEPARATOR_OR_NONE_BETWEEN_TWO_DIGIT_GROUPS" to A_PIECE_OF_THE_FILTERS_PATTERN_VOCABULARY,
        "PUNCTUATION_THE_THOUSANDS_ARM_OWNS" to A_PIECE_OF_THE_FILTERS_PATTERN_VOCABULARY,
    )

private val FLOOR_A_LINE_NAMES = Regex("""stays at (\d+)(?!\d)""")

private val INTEGER_IN_A_LINE = Regex("""\d+""")

private const val FEWEST_TIMES_A_RECORD_NAMES_A_CONSTANT = 1

private data class DeclaredConstant(
    val name: String,
    val value: String,
    val keptPrivate: Boolean,
)

private fun constantsTheDomainDeclares(): List<DeclaredConstant> =
    File(DOMAIN_SOURCE_ROOT)
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .flatMap { file -> A_CONSTANT_THE_DOMAIN_DECLARES.findAll(file.readText()) }
        .map {
            DeclaredConstant(
                name = it.groupValues[NAME_GROUP],
                value = it.groupValues[VALUE_OF_A_DECLARATION_GROUP].trim(),
                keptPrivate = it.groupValues[VISIBILITY_GROUP] == KEPT_PRIVATE,
            )
        }.toList()

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
        val published = constantsTheDomainDeclares().filterNot { it.keptPrivate }.map { it.name }.toSet()
        val unaccounted =
            published - CONSTANTS_BOUND_TO_A_SHIPPED_RECORD.keys -
                CONSTANTS_THAT_CARRY_ANOTHER_CONSTANTS_RECORD.keys -
                CONSTANTS_THAT_DECIDE_NOTHING_A_NESTER_SEES.keys
        assertTrue(
            unaccounted.isEmpty(),
            "a constant the domain publishes is a product decision until someone says otherwise, and these are " +
                "bound to no shipped record, carry no other constant's record and are not named here as " +
                "deciding nothing a Nester sees. The value is read as whatever follows the equals sign rather " +
                "than as an anchored integer, because a trailing comment and a sum both hid a constant from " +
                "this scan and MAX_LENGTH was in neither map: $unaccounted",
        )
        assertTrue(
            published.containsAll(CONSTANTS_THAT_DECIDE_NOTHING_A_NESTER_SEES.keys),
            "a name excused here no longer exists, so the excuse is now a silent widening of what this test " +
                "lets through",
        )
    }

    @Test
    fun `the constants the domain keeps private are the set this test names, and no record claims otherwise`() {
        val kept = constantsTheDomainDeclares().filter { it.keptPrivate }.map { it.name }.toSet()
        assertEquals(
            CONSTANTS_THE_DOMAIN_KEEPS_PRIVATE - CONSTANTS_BOUND_TO_A_SHIPPED_RECORD.keys,
            kept - CONSTANTS_BOUND_TO_A_SHIPPED_RECORD.keys,
            "this is the extent of the binding rather than a description of it. A private constant is outside " +
                "the published set above by construction, which is why the constant ADR-0005 exists for and " +
                "the constant that prices the cue arm were both invisible to a derived set that filters by " +
                "visibility. They are pinned as a set of names rather than excused one by one, so adding one " +
                "reddens here with its name and whoever adds it decides then whether it is a product decision",
        )
    }

    @Test
    fun `every constant that carries another constant's record is declared as that constant`() {
        CONSTANTS_THAT_CARRY_ANOTHER_CONSTANTS_RECORD.forEach { (name, carrier) ->
            val declaration = constantsTheDomainDeclares().single { it.name == name }
            assertEquals(
                carrier,
                declaration.value,
                "$name is excused from having a record of its own because it is declared as $carrier, so if it " +
                    "stops being declared as $carrier the excuse is a silent widening",
            )
            assertTrue(
                carrier in CONSTANTS_BOUND_TO_A_SHIPPED_RECORD,
                "$name defers to $carrier and $carrier is bound to no record, so the deferral leads nowhere",
            )
        }
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
