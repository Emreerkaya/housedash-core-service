package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlainTextTest {
    @Test
    fun `ordinary prose is plain text`() {
        assertTrue(isPlainText("kitchen tap drips from the base"))
    }

    @Test
    fun `newlines and tabs are permitted`() {
        assertTrue(isPlainText("first line\nsecond line\tindented"))
    }

    @Test
    fun `a nul character is not plain text`() {
        assertFalse(isPlainText("tap drips\u0000 under the sink"))
    }

    @Test
    fun `an escape sequence is not plain text`() {
        assertFalse(isPlainText("tap drips \u001B[31mred\u001B[0m"))
    }

    @Test
    fun `a bell character is not plain text`() {
        assertFalse(isPlainText("tap drips\u0007"))
    }

    @Test
    fun `a zero width space is not plain text`() {
        assertFalse(isPlainText("tap\u200Bdrips"))
    }

    @Test
    fun `a byte order mark is not plain text`() {
        assertFalse(isPlainText("\uFEFFtap drips"))
    }

    @Test
    fun `a soft hyphen is not plain text`() {
        assertFalse(isPlainText("tap\u00ADdrips"))
    }

    @Test
    fun `a right to left override is not plain text`() {
        assertFalse(isPlainText("tap\u202Edrips"))
    }

    @Test
    fun `a lone high surrogate is not plain text`() {
        assertFalse(isPlainText("tap \uD83D drips"))
    }

    @Test
    fun `a high surrogate at the very end is not plain text`() {
        assertFalse(isPlainText("tap drips\uD83D"))
    }

    @Test
    fun `a lone low surrogate is not plain text`() {
        assertFalse(isPlainText("tap \uDE00 drips"))
    }

    @Test
    fun `a private use character is not plain text`() {
        assertFalse(isPlainText("tap \uE000 drips"))
    }

    @Test
    fun `a line separator is not plain text`() {
        assertFalse(isPlainText("tap\u2028drips"))
    }

    @Test
    fun `a paragraph separator is not plain text`() {
        assertFalse(isPlainText("tap\u2029drips"))
    }

    @Test
    fun `a well formed surrogate pair is plain text`() {
        assertTrue(isPlainText("the tap leaks \uD83D\uDE00"))
    }

    @Test
    fun `an emoji newer than the running jdk's unicode version is plain text`() {
        listOf(
            "the plumber needs a \uD83E\uDE8F for the drain",
            "i look like this now \uD83E\uDEE9",
            "the leak left a \uD83E\uDEC6 on the wall",
            "a \uD83E\uDE89 would be more use than this boiler",
        ).forEach { text ->
            assertTrue(isPlainText(text), text)
        }
    }

    @Test
    fun `a code point no unicode version has assigned yet is plain text`() {
        assertTrue(isPlainText("tap \u0378 drips"))
    }

    @Test
    fun `an emoji joined by a zero width joiner is plain text`() {
        listOf(
            "the \uD83E\uDDD1\u200D\uD83D\uDD27 is coming tomorrow",
            "my \uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67 cannot shower",
            "the boiler is \u2764\uFE0F\u200D\uD83D\uDD25 at this point",
            "the \uD83C\uDFF3\uFE0F\u200D\uD83C\uDF08 sticker is on the door",
        ).forEach { text ->
            assertTrue(isPlainText(text), text)
        }
    }

    @Test
    fun `a zero width joiner that joins nothing is not plain text`() {
        assertFalse(isPlainText("\u200Dtap drips"))
        assertFalse(isPlainText("tap drips\u200D"))
        assertFalse(isPlainText("tap\u200D\u200Ddrips"))
        assertFalse(isPlainText("tap \u200Ddrips"))
        assertFalse(isPlainText("tap\u200D drips"))
    }

    @Test
    fun `zero width joiners cannot be used as invisible padding`() {
        assertFalse(isPlainText("tap" + "\u200D".repeat(30)))
        assertFalse(isPlainText("tap" + "\u200D ".repeat(30)))
    }

    @Test
    fun `a zero width joiner between letters is plain text`() {
        assertTrue(isPlainText("tap\u200Ddrips"))
    }

    @Test
    fun `a non breaking space is plain text and is stripped by trimming`() {
        assertTrue(isPlainText("tap\u00A0drips"))
        assertEquals("tap drips", "\u00A0tap drips\u00A0".trim())
    }

    @Test
    fun `characters are counted in code points and not utf16 units`() {
        assertEquals(10, characterCount("\uD83D\uDE00".repeat(10)))
        assertEquals(20, "\uD83D\uDE00".repeat(10).length)
    }

    @Test
    fun `an empty string is plain text of zero characters`() {
        assertTrue(isPlainText(""))
        assertEquals(0, characterCount(""))
    }
}
