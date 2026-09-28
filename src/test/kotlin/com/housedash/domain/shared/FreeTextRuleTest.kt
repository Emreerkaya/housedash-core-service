package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FreeTextRuleTest {
    private val rule = FreeTextRule(minimumCharacters = 5, maximumCharacters = 10)

    @Test
    fun `accepts text inside the bounds and returns it trimmed`() {
        assertEquals(Outcome.Ok("hello"), rule.check("   hello   "))
    }

    @Test
    fun `reports the length it measured when the text is too short`() {
        assertEquals(Outcome.Err(TextFlaw.TooShort(4, 5)), rule.check("  four "))
    }

    @Test
    fun `reports the length it measured when the text is too long`() {
        assertEquals(Outcome.Err(TextFlaw.TooLong(11, 10)), rule.check("abcdefghijk"))
    }

    @Test
    fun `accepts text at exactly the minimum and the maximum`() {
        assertIs<Outcome.Ok<String>>(rule.check("a".repeat(5)))
        assertIs<Outcome.Ok<String>>(rule.check("a".repeat(10)))
    }

    @Test
    fun `rejects text that is not plain before it measures the length`() {
        assertEquals(Outcome.Err(TextFlaw.NotPlainText), rule.check("a\u0000b"))
    }

    @Test
    fun `zero width padding cannot satisfy the minimum`() {
        assertEquals(Outcome.Err(TextFlaw.NotPlainText), rule.check("ab" + "\u200B".repeat(30)))
    }

    @Test
    fun `bounds are measured in code points so astral text is not double counted`() {
        assertIs<Outcome.Ok<String>>(rule.check("\uD83D\uDE00".repeat(10)))
        assertEquals(Outcome.Err(TextFlaw.TooLong(11, 10)), rule.check("\uD83D\uDE00".repeat(11)))
    }

    @Test
    fun `text is normalised to composed form before it is measured`() {
        val decomposed = "e\u0301".repeat(6)
        val composed = "\u00E9".repeat(6)
        assertEquals(Outcome.Ok(composed), rule.check(decomposed))
    }

    @Test
    fun `a non breaking space padding cannot satisfy the minimum`() {
        assertEquals(Outcome.Err(TextFlaw.TooShort(3, 5)), rule.check("tap" + "\u00A0".repeat(30)))
    }

    @Test
    fun `text is normalised to composed form and never to compatibility form`() {
        assertEquals(Outcome.Ok("\u00BD inch"), rule.check("\u00BD inch"))
        assertEquals(Outcome.Ok("a \u2122 b"), rule.check("a \u2122 b"))
        val fullwidth = "\uFF19\uFF11\uFF17\uFF15\uFF15"
        assertEquals(Outcome.Ok(fullwidth), rule.check("  $fullwidth  "))
    }

    @Test
    fun `an emoji newer than the running jdk's unicode version is accepted`() {
        val newEmoji = "\uD83E\uDE8F\uD83E\uDEE9\uD83E\uDEC6\uD83E\uDE89\uD83E\uDEDC"
        assertEquals(Outcome.Ok(newEmoji), rule.check(newEmoji))
    }

    @Test
    fun `an emoji joined by a zero width joiner is accepted and counted by code point`() {
        val mechanic = "\uD83E\uDDD1\u200D\uD83D\uDD27"
        assertEquals(Outcome.Ok(mechanic.repeat(2) + "a"), rule.check(mechanic.repeat(2) + "a"))
        assertEquals(7, characterCount(mechanic.repeat(2) + "a"))
    }
}
