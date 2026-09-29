package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

private val MARKS_A_BANK_DETAIL_MAY_BE_GROUPED_WITH =
    listOf('-', ' ', '.', '/', '*', ',', ':', '|', '_', '\u2022', '\u3002')

class BankDetailSeparatorTest {
    @Test
    fun `a sort code and an iban read the same separators as every other arm that reads one`() {
        MARKS_A_BANK_DETAIL_MAY_BE_GROUPED_WITH.forEach { mark ->
            assertTrue(
                ContactDetail.PaymentLink in contactDetailsIn("sort code 12${mark}34${mark}56 for the transfer"),
                (
                    "a sort code grouped with U+%04X is not read, so this arm holds its own separator " +
                        "vocabulary and the question of what a separator is has two answers again"
                ).format(mark.code),
            )
            val iban = "GB82${mark}WEST${mark}1234${mark}5698${mark}7654${mark}32"
            assertTrue(
                ContactDetail.PaymentLink in contactDetailsIn("pay it to $iban when the work is done"),
                "an iban grouped with U+%04X is not read, for the same reason".format(mark.code),
            )
        }
        assertNothingFound("sort code 12x34x56 for the transfer")
        assertNothingFound("pay it to GB82xWESTx1234x5698x7654x32 when the work is done")
        assertNothingFound("pay it to GB83 WEST 1234 5698 7654 32 when the work is done")
    }
}
