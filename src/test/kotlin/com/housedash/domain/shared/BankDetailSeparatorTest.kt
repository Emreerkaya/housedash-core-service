package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

private val MARKS_A_BANK_DETAIL_MAY_BE_GROUPED_WITH =
    listOf('-', ' ', '.', '/', '*', ',', ':', '|', '_', '•', '。')

private val PADDINGS_A_BANK_DETAIL_SEPARATOR_MAY_CARRY =
    listOf<(Char) -> String>({ "$it" }, { "$it " }, { " $it" }, { " $it " }, { "  $it  " })

private fun separatorsTheMarksSpell(): List<List<String>> {
    val uniform =
        MARKS_A_BANK_DETAIL_MAY_BE_GROUPED_WITH.flatMap { mark ->
            PADDINGS_A_BANK_DETAIL_SEPARATOR_MAY_CARRY.map { padding -> listOf(padding(mark)) }
        }
    val mixed =
        MARKS_A_BANK_DETAIL_MAY_BE_GROUPED_WITH.flatMap { first ->
            MARKS_A_BANK_DETAIL_MAY_BE_GROUPED_WITH
                .filterNot { it == first }
                .map { second -> listOf(first.toString(), "$second ") }
        }
    return uniform + mixed
}

private fun spelling(separators: List<String>): String =
    separators.joinToString("/") { separator -> separator.map { "U+%04X".format(it.code) }.joinToString("") }

private fun joinedBy(
    groups: List<String>,
    separators: List<String>,
): String =
    groups
        .mapIndexed { index, group ->
            if (index == 0) group else separators[(index - 1) % separators.size] + group
        }.joinToString("")

class BankDetailSeparatorTest {
    @Test
    fun `a sort code and an iban read the same separators as every other arm that reads one`() {
        val unread =
            separatorsTheMarksSpell().flatMap { separators ->
                val sortCode = joinedBy(listOf("12", "34", "56"), separators)
                val iban = joinedBy(listOf("GB82", "WEST", "1234", "5698", "7654", "32"), separators)
                listOfNotNull(
                    "sort code".takeIf {
                        ContactDetail.PaymentLink !in contactDetailsIn("sort code $sortCode for the transfer")
                    },
                    "iban".takeIf {
                        ContactDetail.PaymentLink !in contactDetailsIn("pay it to $iban when the work is done")
                    },
                ).map { arm -> "$arm grouped with ${spelling(separators)}" }
            }
        assertEquals(
            emptyList(),
            unread,
            "the shared rule decided which marks stand between two digit groups and left how many to each arm, " +
                "so a sort code took one mark, an iban took exactly one, and a phone number took up to eight. " +
                "This fixture is a list of separator strings rather than of characters on purpose: a Char " +
                "cannot express a mark followed by a space, and a list of one Char cannot express a run spelled " +
                "two ways, so the old fixture could not build either counterexample. How many is now decided " +
                "once for every arm. These spellings are not read: " + unread.joinToString(", "),
        )
        assertNothingFound("sort code 12x34x56 for the transfer")
        assertNothingFound("pay it to GB82xWESTx1234x5698x7654x32 when the work is done")
        assertNothingFound("pay it to GB83 WEST 1234 5698 7654 32 when the work is done")
    }
}
