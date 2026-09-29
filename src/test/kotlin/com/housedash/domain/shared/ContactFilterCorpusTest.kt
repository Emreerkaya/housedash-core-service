package com.housedash.domain.shared

import com.housedash.domain.case.CaseError
import com.housedash.domain.case.Description
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

private const val CORPUS_RESOURCE = "i7-corpus.tsv"

private const val ROWS_THE_CORPUS_HOLDS = 140

private const val LABELLED_ROWS_THE_CORPUS_HOLDS = 130

private const val DISAGREEMENTS_PINNED_AT_THIS_COMMIT = 41

private const val ROWS_TOO_SHORT_TO_BE_A_DESCRIPTION = 62

private const val ROWS_THE_PLAIN_TEXT_RULE_REFUSES = 2

private const val COLUMNS_IN_A_ROW = 5

private const val WANT_COLUMN = 0

private const val BEHAVIOUR_COLUMN = 1

private const val KINDS_COLUMN = 2

private const val INPUT_COLUMN = 3

private const val NOTE_COLUMN = 4

private const val REJECT = "REJECT"

private const val ACCEPT = "ACCEPT"

private const val UNLABELLED = "QUESTION"

private val WANTS_THE_CORPUS_DEFINES = setOf(REJECT, ACCEPT, UNLABELLED)

private val BEHAVIOURS_THE_CORPUS_DEFINES = setOf(REJECT, ACCEPT)

private val LINE_BREAK_ESCAPE = Regex("""\\n""")

private val BETWEEN_KINDS = Regex("""\p{Zs}+""")

private data class CorpusRow(
    val line: Int,
    val want: String,
    val behaviour: String,
    val kinds: Set<String>,
    val input: String,
    val note: String,
) {
    val isLabelled: Boolean get() = want in BEHAVIOURS_THE_CORPUS_DEFINES
}

private fun kindsTheGuardCanReport(): Set<String> =
    ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.housedash.domain.shared")
        .filter { type -> type.rawInterfaces.any { it.simpleName == ContactDetail::class.simpleName } }
        .map { it.simpleName }
        .toSet()

private fun corpusRows(): List<CorpusRow> {
    val stream =
        ContactFilterCorpusTest::class.java.getResourceAsStream(CORPUS_RESOURCE)
            ?: fail("$CORPUS_RESOURCE is not on the test classpath, so the corpus measured nothing")
    val lines = stream.bufferedReader().use { it.readLines() }.filter { it.isNotEmpty() }
    val header = lines.first().split('\t')
    assertEquals(
        listOf("want", "behaviour", "kinds", "input", "note"),
        header,
        "the corpus header changed shape, so the columns this test reads are no longer the columns it means",
    )
    return lines.drop(1).mapIndexed { index, line ->
        val columns = line.split('\t')
        assertEquals(
            COLUMNS_IN_A_ROW,
            columns.size,
            "corpus line ${index + 2} holds ${columns.size} tab separated columns, not $COLUMNS_IN_A_ROW: $line",
        )
        CorpusRow(
            line = index + 2,
            want = columns[WANT_COLUMN],
            behaviour = columns[BEHAVIOUR_COLUMN],
            kinds = columns[KINDS_COLUMN].split(BETWEEN_KINDS).filter { it.isNotBlank() }.toSet(),
            input = LINE_BREAK_ESCAPE.replace(columns[INPUT_COLUMN], "\n"),
            note = columns[NOTE_COLUMN],
        )
    }
}

private fun kindsFound(input: String): Set<String> = contactDetailsIn(input).mapNotNull { it::class.simpleName }.toSet()

private fun behaviourOfTheGuard(input: String): String = if (contactDetailsIn(input).isEmpty()) ACCEPT else REJECT

private sealed interface ThroughTheAggregate {
    data class Measured(
        val behaviour: String,
    ) : ThroughTheAggregate

    data class OutOfReach(
        val reason: String,
    ) : ThroughTheAggregate
}

private fun behaviourOfTheAggregate(input: String): ThroughTheAggregate =
    when (val outcome = Description.of(input)) {
        is Outcome.Ok -> ThroughTheAggregate.Measured(ACCEPT)
        is Outcome.Err ->
            when (val error = outcome.error) {
                is CaseError.ContactDetailsInDescription -> ThroughTheAggregate.Measured(REJECT)
                else -> ThroughTheAggregate.OutOfReach(error::class.simpleName.orEmpty())
            }
    }

class ContactFilterCorpusTest {
    @Test
    fun `the corpus resource holds every row it is supposed to hold`() {
        val rows = corpusRows()
        assertEquals(
            ROWS_THE_CORPUS_HOLDS,
            rows.size,
            "the corpus lost or gained rows; a smaller corpus measures less and says nothing about it",
        )
        assertEquals(
            LABELLED_ROWS_THE_CORPUS_HOLDS,
            rows.count { it.isLabelled },
            "the labelled row count changed, and the disagreement total is only readable against it",
        )
        val unknownVocabulary =
            rows.filterNot { it.want in WANTS_THE_CORPUS_DEFINES && it.behaviour in BEHAVIOURS_THE_CORPUS_DEFINES }
        assertTrue(
            unknownVocabulary.isEmpty(),
            "these corpus rows carry a label this test does not understand, so they were silently skipped:\n" +
                unknownVocabulary.joinToString("\n") { "line ${it.line}: ${it.want}/${it.behaviour}" },
        )
    }

    @Test
    fun `every corpus row carries its provenance and a kinds column that agrees with its verdict`() {
        corpusRows().forEach { row ->
            assertTrue(
                row.note.isNotBlank(),
                "corpus line ${row.line} carries no provenance, so nothing says which review or commit put it here",
            )
            assertEquals(
                row.behaviour == REJECT,
                row.kinds.isNotEmpty(),
                "corpus line ${row.line} pins behaviour ${row.behaviour} beside kinds ${row.kinds}, and a " +
                    "rejection is exactly a non-empty set of kinds",
            )
        }
    }

    @Test
    fun `the guard finds the same kinds on every corpus row that the corpus pins`() {
        val rows = corpusRows()
        val flips =
            rows.mapNotNull { row ->
                val measured = kindsFound(row.input)
                if (measured == row.kinds) {
                    null
                } else {
                    "line ${row.line}: pinned ${row.kinds.ifEmpty { "nothing" }}, measured " +
                        "${measured.ifEmpty { "nothing" }}, want ${row.want} " +
                        "[${row.note}] ${'"'}${row.input.replace("\n", "\\n")}${'"'}"
                }
            }
        assertTrue(
            flips.isEmpty(),
            "${flips.size} of ${rows.size} corpus rows no longer report the kinds pinned against them. A row " +
                "rejected for a different kind than before is a change of behaviour that a reject-or-accept " +
                "column cannot see, and the kind is what the Nester is shown. Each line below is a flip to " +
                "explain and then record in the corpus by hand; a flip nobody can explain is a change that is " +
                "not ready:\n" + flips.joinToString("\n"),
        )
    }

    @Test
    fun `the corpus holds a row for every kind the guard can report`() {
        val rows = corpusRows()
        val held = rows.flatMap { it.kinds }.toSet()
        assertEquals(
            kindsTheGuardCanReport(),
            held,
            "the guard can report kinds the corpus holds no row for, so those kinds have never produced a " +
                "corpus delta on any commit and nothing would notice a row moving between them",
        )
    }

    @Test
    fun `the corpus disagreement total this commit claims is measured, not copied from a column`() {
        val rows = corpusRows()
        val disagreements = rows.filter { it.isLabelled && it.want != behaviourOfTheGuard(it.input) }
        val wrongly = disagreements.groupBy { it.want }
        assertEquals(
            DISAGREEMENTS_PINNED_AT_THIS_COMMIT,
            disagreements.size,
            "the guard now disagrees with what the corpus wants on ${disagreements.size} of " +
                "${rows.count { it.isLabelled }} labelled rows, not $DISAGREEMENTS_PINNED_AT_THIS_COMMIT: " +
                "${wrongly[REJECT].orEmpty().size} wanted rejected and are accepted, " +
                "${wrongly[ACCEPT].orEmpty().size} wanted accepted and are rejected. This total is measured " +
                "by running the guard rather than by comparing two columns of the data file, so a code change " +
                "moves it. Move this number only together with the rows that moved it:\n" +
                disagreements.joinToString("\n") { "line ${it.line}: want ${it.want} [${it.note}]" },
        )
    }

    @Test
    fun `the aggregate agrees with the guard on every row long enough to be a description`() {
        val rows = corpusRows()
        val divergences = mutableListOf<String>()
        val outOfReach = mutableListOf<String>()
        rows.forEach { row ->
            when (val throughAggregate = behaviourOfTheAggregate(row.input)) {
                is ThroughTheAggregate.Measured ->
                    if (throughAggregate.behaviour != row.behaviour) {
                        divergences.add(
                            "line ${row.line}: the guard says ${row.behaviour} and Description.of says " +
                                "${throughAggregate.behaviour} " +
                                "${'"'}${row.input.replace("\n", "\\n")}${'"'}",
                        )
                    }

                is ThroughTheAggregate.OutOfReach ->
                    outOfReach.add("line ${row.line}: ${throughAggregate.reason}")
            }
        }
        assertTrue(
            divergences.isEmpty(),
            "the description aggregate and the guard it calls disagree on ${divergences.size} rows:\n" +
                divergences.joinToString("\n"),
        )
        assertTrue(
            outOfReach.size < rows.size,
            "no corpus row reached Description.of at all, so this test measured the aggregate on nothing",
        )
    }

    @Test
    fun `the aggregate is handed a rejection of every kind, not only text that passes`() {
        val rows = corpusRows()
        val kindsThroughTheAggregate =
            rows
                .filter { behaviourOfTheAggregate(it.input) is ThroughTheAggregate.Measured }
                .filter { it.behaviour == REJECT }
                .flatMap { it.kinds }
                .toSet()
        assertEquals(
            kindsTheGuardCanReport(),
            kindsThroughTheAggregate,
            "Description.of has never been given a rejection of every kind the guard reports, so the " +
                "cross-check that exists to show the aggregate and the guard agree agrees mostly about text " +
                "that passes. Every payment-brand row used to be shorter than a description may be, which is " +
                "an artefact of writing rows as bare tokens rather than as sentences, and a long row costs no " +
                "more than a short one",
        )
    }

    @Test
    fun `the corpus states how many of its rows the aggregate cannot be given at all, and why`() {
        val rows = corpusRows()
        val outOfReach =
            rows
                .mapNotNull { row ->
                    (behaviourOfTheAggregate(row.input) as? ThroughTheAggregate.OutOfReach)?.reason
                }.groupingBy { it }
                .eachCount()
        assertEquals(
            mapOf(
                CaseError.DescriptionTooShort::class.simpleName.orEmpty() to ROWS_TOO_SHORT_TO_BE_A_DESCRIPTION,
                CaseError.DescriptionNotPlainText::class.simpleName.orEmpty() to ROWS_THE_PLAIN_TEXT_RULE_REFUSES,
            ),
            outOfReach,
            "the reasons Description.of cannot be given a corpus row, and how many rows each one accounts " +
                "for, are the ceiling of what this corpus can say about the aggregate, and moving either is a " +
                "deliberate edit. A row refused for being too short exercises the guard alone. A row refused " +
                "by the plain-text rule is refused at the write boundary by the layer beneath the filter, " +
                "which is load-bearing for I7 and which no reject-or-accept column can carry, so it is " +
                "counted here instead of being silently dropped",
        )
    }

    @Test
    fun `the plain-text rule refuses a number grouped with invisible characters, which the guard accepts`() {
        val notPlainText = ThroughTheAggregate.OutOfReach(CaseError.DescriptionNotPlainText::class.simpleName.orEmpty())
        val refusedByThePlainTextRule = corpusRows().filter { behaviourOfTheAggregate(it.input) == notPlainText }
        assertEquals(
            ROWS_THE_PLAIN_TEXT_RULE_REFUSES,
            refusedByThePlainTextRule.size,
            "the corpus no longer holds a row that the plain-text rule refuses, so nothing here measures the " +
                "layer the corpus's own analysis calls load-bearing for I7",
        )
        refusedByThePlainTextRule.forEach { row ->
            assertEquals(
                ACCEPT,
                behaviourOfTheGuard(row.input),
                "line ${row.line}: the guard is supposed to accept this, because it strips format characters " +
                    "and then sees exactly the bare run the corpus pins as an accepted gap. If the guard " +
                    "starts rejecting it, the plain-text rule is no longer the only thing standing between " +
                    "this text and a stored description",
            )
            assertTrue(
                !isPlainText(row.input),
                "line ${row.line}: this row exists because the plain-text rule refuses it; it no longer does",
            )
        }
    }
}
