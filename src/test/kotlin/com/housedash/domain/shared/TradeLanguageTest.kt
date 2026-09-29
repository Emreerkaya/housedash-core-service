package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

private const val TRADE_LANGUAGE_RESOURCE = "trade-language.tsv"

private const val SENTENCES_THE_SET_HOLDS = 74

private const val FAMILIES_THE_SET_HOLDS = 16

private const val SENTENCES_THE_FILTER_REFUSES = 11

private const val COLUMNS_IN_A_ROW = 3

private const val FAMILY_COLUMN = 0

private const val INPUT_COLUMN = 1

private const val WHY_COLUMN = 2

private data class TradeSentence(
    val line: Int,
    val family: String,
    val input: String,
    val why: String,
)

private fun tradeSentences(): List<TradeSentence> {
    val stream =
        TradeLanguageTest::class.java.getResourceAsStream(TRADE_LANGUAGE_RESOURCE)
            ?: fail("$TRADE_LANGUAGE_RESOURCE is not on the test classpath, so this instrument measured nothing")
    val lines = stream.bufferedReader().use { it.readLines() }.filter { it.isNotEmpty() }
    assertEquals(
        listOf("family", "input", "why"),
        lines.first().split('\t'),
        "the trade-language header changed shape, so the columns this test reads are no longer the columns it means",
    )
    return lines.drop(1).mapIndexed { index, line ->
        val columns = line.split('\t')
        assertEquals(
            COLUMNS_IN_A_ROW,
            columns.size,
            "trade-language line ${index + 2} holds ${columns.size} tab separated columns, not $COLUMNS_IN_A_ROW",
        )
        TradeSentence(
            line = index + 2,
            family = columns[FAMILY_COLUMN],
            input = columns[INPUT_COLUMN],
            why = columns[WHY_COLUMN],
        )
    }
}

private fun refusalOf(input: String): Set<String> = contactDetailsIn(input).mapNotNull { it::class.simpleName }.toSet()

class TradeLanguageTest {
    @Test
    fun `the trade-language set holds every sentence and every family it is supposed to hold`() {
        val sentences = tradeSentences()
        assertEquals(
            SENTENCES_THE_SET_HOLDS,
            sentences.size,
            "this set is one of the two instruments every widening is priced against and a smaller one prices " +
                "less. Every sentence in it is a description someone would plausibly type about a home repair " +
                "and none of them holds a contact detail, so every one of them wants to be accepted",
        )
        assertEquals(
            FAMILIES_THE_SET_HOLDS,
            sentences.map { it.family }.distinct().size,
            "the families are what make this set readable: a refusal count with no family beside it says " +
                "nothing about which trade vocabulary the filter is damaging",
        )
    }

    @Test
    fun `no two sentences in the set are one sentence, and each carries the note that argues for it`() {
        val sentences = tradeSentences()
        assertTrue(
            sentences.all { it.why.isNotBlank() },
            "a sentence with no note beside it is a sentence nobody can argue with, which is the whole value " +
                "of a held-out set over a flip count",
        )
        assertEquals(
            emptyList(),
            sentences
                .groupBy { it.input }
                .filterValues { it.size > 1 }
                .keys
                .toList(),
            "two rows holding one sentence make the refusal count and the total count both count it twice, and " +
                "both are consistent with the repeat because consistency is all they check",
        )
    }

    @Test
    fun `the trade-language refusal count this commit claims is measured by running the filter`() {
        val sentences = tradeSentences()
        val refused = sentences.filter { refusalOf(it.input).isNotEmpty() }
        val byFamily = refused.groupingBy { it.family }.eachCount().toSortedMap()
        assertEquals(
            SENTENCES_THE_FILTER_REFUSES,
            refused.size,
            "this set measures one direction only and can be satisfied by a filter that accepts everything, " +
                "which is why it is pinned at the number measured rather than asserted to be small. The corpus " +
                "measures the other direction and cannot be satisfied that way, so a widening is priced " +
                "against both and a commit that changes the filter reports both numbers. " +
                "Refused by family: $byFamily. Each refusal:\n" +
                refused.joinToString("\n") {
                    "line ${it.line} [${it.family}] ${refusalOf(it.input)} ${'"'}${it.input}${'"'}"
                },
        )
    }

    @Test
    fun `plain prose is not touched at all, so the damage is confined to numeric trade vocabulary`() {
        val prose = tradeSentences().filter { it.family in FAMILIES_THAT_CARRY_NO_DIGIT_RUN }
        val refused = prose.filter { refusalOf(it.input).isNotEmpty() }
        assertEquals(
            emptyList(),
            refused.map { "line ${it.line} [${it.family}] ${refusalOf(it.input)}" },
            "the refusal total above is weighted toward families already known broken, so it is not a " +
                "false-positive rate. This leg is the part that is not weighted: the families that carry no " +
                "digit run at all must be untouched, and if one of them starts being refused the filter has " +
                "stopped being a filter on numeric shapes and become indiscriminate",
        )
        assertEquals(
            PROSE_SENTENCES_THE_SET_HOLDS,
            prose.size,
            "the denominator of the leg above; without it a family renamed out of the list would satisfy it " +
                "by asking about nothing",
        )
    }

    private companion object {
        val FAMILIES_THAT_CARRY_NO_DIGIT_RUN =
            setOf("ordinary", "measurement", "date-time", "model-number", "dimension", "address-adjacent")

        const val PROSE_SENTENCES_THE_SET_HOLDS = 31
    }
}
