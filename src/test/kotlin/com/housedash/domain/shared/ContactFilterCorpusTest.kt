package com.housedash.domain.shared

import com.housedash.domain.case.CaseError
import com.housedash.domain.case.Description
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

private const val CORPUS_RESOURCE = "i7-corpus.tsv"

private const val ROWS_THE_CORPUS_HOLDS = 94

private const val LABELLED_ROWS_THE_CORPUS_HOLDS = 87

private const val DISAGREEMENTS_PINNED_AT_THIS_COMMIT = 26

private const val ROWS_TOO_SHORT_TO_BE_A_DESCRIPTION = 61

private const val COLUMNS_IN_A_ROW = 4

private const val WANT_COLUMN = 0

private const val BEHAVIOUR_COLUMN = 1

private const val INPUT_COLUMN = 2

private const val NOTE_COLUMN = 3

private const val REJECT = "REJECT"

private const val ACCEPT = "ACCEPT"

private const val UNLABELLED = "QUESTION"

private val WANTS_THE_CORPUS_DEFINES = setOf(REJECT, ACCEPT, UNLABELLED)

private val BEHAVIOURS_THE_CORPUS_DEFINES = setOf(REJECT, ACCEPT)

private val LINE_BREAK_ESCAPE = Regex("""\\n""")

private data class CorpusRow(
    val line: Int,
    val want: String,
    val behaviour: String,
    val input: String,
    val note: String,
) {
    val isLabelled: Boolean get() = want in BEHAVIOURS_THE_CORPUS_DEFINES

    val isDisagreement: Boolean get() = isLabelled && want != behaviour
}

private fun corpusRows(): List<CorpusRow> {
    val stream =
        ContactFilterCorpusTest::class.java.getResourceAsStream(CORPUS_RESOURCE)
            ?: fail("$CORPUS_RESOURCE is not on the test classpath, so the corpus measured nothing")
    val lines = stream.bufferedReader().use { it.readLines() }.filter { it.isNotEmpty() }
    val header = lines.first().split('\t')
    assertEquals(
        listOf("want", "behaviour", "input", "note"),
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
            input = LINE_BREAK_ESCAPE.replace(columns[INPUT_COLUMN], "\n"),
            note = columns[NOTE_COLUMN],
        )
    }
}

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
        rows.forEach { row ->
            assertTrue(
                row.note.isNotBlank(),
                "corpus line ${row.line} carries no provenance, so nothing says which review or commit put it here",
            )
        }
    }

    @Test
    fun `the guard behaves on every corpus row exactly as the corpus pins it`() {
        val rows = corpusRows()
        val flips =
            rows.mapNotNull { row ->
                val measured = behaviourOfTheGuard(row.input)
                if (measured == row.behaviour) {
                    null
                } else {
                    "line ${row.line}: pinned ${row.behaviour}, measured $measured, want ${row.want} " +
                        "[${row.note}] ${'"'}${row.input.replace("\n", "\\n")}${'"'}"
                }
            }
        assertTrue(
            flips.isEmpty(),
            "${flips.size} of ${rows.size} corpus rows no longer behave as pinned. Each line below is a flip to " +
                "explain and then record in the corpus by hand; a flip nobody can explain is a change that is not " +
                "ready:\n" + flips.joinToString("\n"),
        )
    }

    @Test
    fun `the corpus disagreement total is the one this commit claims`() {
        val rows = corpusRows()
        val disagreements = rows.filter { it.isDisagreement }
        val wrongly = disagreements.groupBy { it.want }
        assertEquals(
            DISAGREEMENTS_PINNED_AT_THIS_COMMIT,
            disagreements.size,
            "the corpus now disagrees with the guard on ${disagreements.size} of " +
                "${rows.count { it.isLabelled }} labelled rows, not $DISAGREEMENTS_PINNED_AT_THIS_COMMIT: " +
                "${wrongly[REJECT].orEmpty().size} wanted rejected and are accepted, " +
                "${wrongly[ACCEPT].orEmpty().size} wanted accepted and are rejected. Move this number only " +
                "together with the rows that moved it",
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
    fun `the corpus states how many of its rows the aggregate cannot be given at all`() {
        val rows = corpusRows()
        val outOfReach =
            rows.mapNotNull { row ->
                (behaviourOfTheAggregate(row.input) as? ThroughTheAggregate.OutOfReach)?.let { row to it.reason }
            }
        assertEquals(
            setOf(CaseError.DescriptionTooShort::class.simpleName),
            outOfReach.map { it.second }.toSet(),
            "a corpus row is unreachable through Description.of for a reason other than its length, so the corpus " +
                "is measuring the guard on inputs the aggregate refuses for an unrelated cause: " +
                "${outOfReach.map { it.second }.toSet()}",
        )
        assertEquals(
            ROWS_TOO_SHORT_TO_BE_A_DESCRIPTION,
            outOfReach.size,
            "${outOfReach.size} of ${rows.size} corpus rows are shorter than a description may be, so the " +
                "aggregate is measured on ${rows.size - outOfReach.size} of them and the rest exercise the guard " +
                "alone. That is the ceiling of what this corpus can say about Description.of, and moving it is a " +
                "deliberate edit",
        )
    }
}
