package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val SOURCES_THE_FILTER_IS_WRITTEN_IN =
    listOf(
        "src/main/kotlin/com/housedash/domain/shared/ContactDetail.kt",
        "src/main/kotlin/com/housedash/domain/shared/NumberShape.kt",
    )

private val OPENS_A_DECLARATION =
    Regex("""^(\p{Zs}*)(?:private |internal |public )?(?:const )?val ([A-Za-z][A-Za-z0-9_]*) =(.*)$""")

private const val INDENT_GROUP = 1

private const val NAME_GROUP = 2

private const val REST_OF_THE_OPENING_LINE_GROUP = 3

private const val DECLARATIONS_THE_FILTER_HOLDS = 137

private const val PATTERNS_THE_FILTER_DECLARES = 54

private val OPENS_A_PATTERN = Regex("""^(?:Regex\(|")""")

private val WRAPS_A_PATTERN_IN_A_COMPILED_ONE = Regex("""^Regex\((.*)\)$""")

private val TRAILING_COMMA = Regex(""",$""")

private val COMPILES_A_PATTERN = Regex("""\bRegex\(""")

private const val WRAPPED_GROUP = 1

private data class Declaration(
    val file: String,
    val name: String,
    val text: String,
    val lines: IntRange,
) {
    val isPattern: Boolean get() = OPENS_A_PATTERN.containsMatchIn(text)

    val shape: String
        get() =
            TRAILING_COMMA
                .replace(text, "")
                .let { WRAPS_A_PATTERN_IN_A_COMPILED_ONE.find(it)?.groupValues?.get(WRAPPED_GROUP) ?: it }
                .trim()
}

private fun sourceFile(path: String): File {
    val file = File(path)
    assertTrue(
        file.isFile,
        "$path is where part of the filter's pattern vocabulary is declared and this test cannot find it, so " +
            "it is scanning less than it says it is",
    )
    return file
}

private fun continuesADeclaration(
    line: String,
    indent: Int,
): Boolean =
    line.isNotBlank() &&
        line.takeWhile { it == ' ' }.length > indent &&
        OPENS_A_DECLARATION.matchEntire(line) == null

private fun endOfDeclaration(
    lines: List<String>,
    from: Int,
    indent: Int,
): Int {
    var next = from
    while (next < lines.size && continuesADeclaration(lines[next], indent)) {
        next += 1
    }
    return next
}

private fun declarationsIn(path: String): List<Declaration> {
    val file = sourceFile(path)
    val lines = file.readLines()
    val found = mutableListOf<Declaration>()
    var index = 0
    while (index < lines.size) {
        val opening = OPENS_A_DECLARATION.matchEntire(lines[index])
        if (opening == null) {
            index += 1
            continue
        }
        val end = endOfDeclaration(lines, index + 1, opening.groupValues[INDENT_GROUP].length)
        val text =
            (listOf(opening.groupValues[REST_OF_THE_OPENING_LINE_GROUP]) + lines.subList(index + 1, end))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
        found.add(Declaration(file.name, opening.groupValues[NAME_GROUP], text, index until end))
        index = end
    }
    return found
}

private val FIXED_LENGTH = Regex("""^\{\d+\}$""")

private val OPENS_A_UNICODE_PROPERTY = Regex("""^[pP]$""")

private const val QUANTIFIERS_THE_FILTER_HOLDS = 99

private const val ESCAPE_AND_ONE_CHARACTER = 2

private const val GROUP_OPENING_AND_ITS_QUESTION_MARK = 2

private const val POSSESSIVE = '+'

private data class Quantifier(
    val holder: String,
    val text: String,
) {
    val isSafe: Boolean get() = text.last() == POSSESSIVE || FIXED_LENGTH.matches(text)
}

private fun quantifierAt(
    pattern: String,
    index: Int,
): String? {
    val base =
        when (pattern[index]) {
            '{' -> pattern.indexOf('}', index).takeIf { it > index }?.let { pattern.substring(index, it + 1) }
            '*', '+', '?' -> pattern[index].toString()
            else -> null
        } ?: return null
    val after = index + base.length
    val modifier = pattern.getOrNull(after)
    return if (modifier == '+' || modifier == '?') base + modifier else base
}

private fun endOfAnInterpolation(
    pattern: String,
    from: Int,
): Int {
    var depth = 0
    var index = from
    while (index < pattern.length) {
        if (pattern[index] == '{') depth += 1
        if (pattern[index] == '}') {
            depth -= 1
            if (depth == 0) return index + 1
        }
        index += 1
    }
    return pattern.length
}

private fun charactersInAnEscape(
    pattern: String,
    index: Int,
): Int {
    val opensAProperty =
        OPENS_A_UNICODE_PROPERTY.matches(pattern.getOrNull(index + 1)?.toString().orEmpty()) &&
            pattern.getOrNull(index + 2) == '{'
    if (!opensAProperty) return ESCAPE_AND_ONE_CHARACTER
    return (pattern.indexOf('}', index + 2).takeIf { it > index }?.plus(1) ?: pattern.length) - index
}

private fun charactersThatAreNotAQuantifier(
    pattern: String,
    index: Int,
): Int =
    when {
        pattern[index] == '\\' -> charactersInAnEscape(pattern, index)
        pattern[index] == '(' && pattern.getOrNull(index + 1) == '?' -> GROUP_OPENING_AND_ITS_QUESTION_MARK
        pattern[index] == '$' && pattern.getOrNull(index + 1) == '{' ->
            endOfAnInterpolation(pattern, index + 1) - index
        pattern[index] == '$' ->
            1 + pattern.drop(index + 1).takeWhile { it == '_' || it.isLetterOrDigit() }.length
        else -> 0
    }

private data class StepThroughAPattern(
    val characters: Int,
    val quantifier: String?,
    val insideACharacterClass: Boolean,
)

private fun stepThrough(
    pattern: String,
    index: Int,
    insideACharacterClass: Boolean,
): StepThroughAPattern {
    val notAQuantifier = charactersThatAreNotAQuantifier(pattern, index)
    if (notAQuantifier > 0) return StepThroughAPattern(notAQuantifier, null, insideACharacterClass)
    if (insideACharacterClass) return StepThroughAPattern(1, null, pattern[index] != ']')
    if (pattern[index] == '[') return StepThroughAPattern(1, null, true)
    val quantifier = quantifierAt(pattern, index)
    return StepThroughAPattern(quantifier?.length ?: 1, quantifier, false)
}

private fun quantifiersIn(declaration: Declaration): List<Quantifier> {
    val pattern = declaration.text
    val found = mutableListOf<Quantifier>()
    var index = 0
    var insideACharacterClass = false
    while (index < pattern.length) {
        val step = stepThrough(pattern, index, insideACharacterClass)
        step.quantifier?.let { found.add(Quantifier("${declaration.file}:${declaration.name}", it)) }
        insideACharacterClass = step.insideACharacterClass
        index += step.characters
    }
    return found
}

private fun linesCompilingAPattern(path: String): List<Int> =
    sourceFile(path)
        .readLines()
        .mapIndexedNotNull { index, line -> index.takeIf { COMPILES_A_PATTERN.containsMatchIn(line) } }

private val QUANTIFIERS_THAT_CAN_BACKTRACK_BY_DESIGN =
    mapOf(
        ("ContactDetail.kt:EMAIL_TOP_LEVEL_LABEL" to "{2,24}") to
            "matched against one already-extracted label with matches rather than searched for, so there is " +
            "nothing for it to backtrack over",
        ("ContactDetail.kt:BANK_ACCOUNT" to "{6,12}") to
            "followed by a negative lookahead for another digit, which is what a possessive count would have " +
            "done, so making it possessive changes nothing and removing the lookahead is the real risk",
        ("ContactDetail.kt:IBAN_CANDIDATE" to "{1,3}") to
            "the last of an alternation inside an optional group, and every group around it is possessive",
        ("NumberShape.kt:UNQUALIFIED_NUMBER_CUE" to "{0,4}") to
            "inside a negative lookbehind, whose width Java already requires to be bounded",
        ("NumberShape.kt:UNQUALIFIED_NUMBER_CUE" to "?") to
            "one optional letter on a literal word, where possessive and greedy accept the same inputs",
    )

class FilterPatternVocabularyTest {
    @Test
    fun `no two names in the filter stand for the same pattern`() {
        val patterns = SOURCES_THE_FILTER_IS_WRITTEN_IN.flatMap(::declarationsIn).filter { it.isPattern }
        val sharing =
            patterns
                .groupBy { it.shape }
                .filterValues { it.size > 1 }
                .map { (shape, holders) ->
                    "${holders.joinToString(" and ") { "${it.file}:${it.name}" }} all stand for $shape"
                }
        assertTrue(
            sharing.isEmpty(),
            "two names for one pattern are either one name too many or two values that were meant to differ " +
                "and do not, and nothing tells the next reader which. Give the pattern one name, or give each " +
                "name the value it was written for. Names are compared on the pattern inside a Regex(...) " +
                "wrapper as well as on a bare string, because a second name for one shape was reintroduced in " +
                "the compiled spelling and both legs of this test stayed green:\n" + sharing.joinToString("\n"),
        )
    }

    @Test
    fun `this scan reads every declaration in the filter, and every pattern the filter compiles`() {
        val declarations = SOURCES_THE_FILTER_IS_WRITTEN_IN.flatMap(::declarationsIn)
        assertEquals(
            DECLARATIONS_THE_FILTER_HOLDS,
            declarations.size,
            "a scan that stops seeing a declaration reports no finding about it, exactly as a loop over a " +
                "table stops visiting a row that has left it, so the number of declarations this scan reaches " +
                "is pinned and moving it is a deliberate edit. Reached: " +
                declarations.joinToString(", ") { "${it.file}:${it.name}" },
        )
        assertEquals(
            PATTERNS_THE_FILTER_DECLARES,
            declarations.count { it.isPattern },
            "this count is the part of the scan the name comparison above reads. Thirty were read before, " +
                "because the scan kept only declarations whose value begins with a quote, so every pattern the " +
                "filter actually matches with was outside it, including the compiled form of the table whose " +
                "return this test exists to catch. Read as patterns: " +
                declarations.filter { it.isPattern }.joinToString(", ") { "${it.file}:${it.name}" },
        )
    }

    @Test
    fun `every quantifier in the filter is possessive or fixed length, and the exceptions are derived`() {
        val patterns = SOURCES_THE_FILTER_IS_WRITTEN_IN.flatMap(::declarationsIn).filter { it.isPattern }
        val quantifiers = patterns.flatMap(::quantifiersIn)
        assertEquals(
            QUANTIFIERS_THE_FILTER_HOLDS,
            quantifiers.size,
            "this is the denominator, and without it a walk that found nothing would satisfy the assertion " +
                "below by reporting no exception. Move it in the commit that adds or removes a quantifier",
        )
        assertEquals(
            QUANTIFIERS_THAT_CAN_BACKTRACK_BY_DESIGN.keys,
            quantifiers.filterNot { it.isSafe }.map { it.holder to it.text }.toSet(),
            "an exception named here is no longer in the source, so the exception is now a silent widening of " +
                "what this test lets through, and one not named here is new",
        )
    }

    @Test
    fun `no quantifier in the filter can backtrack except the five this test names with a reason`() {
        val patterns = SOURCES_THE_FILTER_IS_WRITTEN_IN.flatMap(::declarationsIn).filter { it.isPattern }
        val backtracking = patterns.flatMap(::quantifiersIn).filterNot { it.isSafe }
        val unexpected =
            backtracking.filterNot { (it.holder to it.text) in QUANTIFIERS_THAT_CAN_BACKTRACK_BY_DESIGN.keys }
        assertEquals(
            emptyList(),
            unexpected,
            "the filter is not a ReDoS because every quantifier in it is possessive or fixed length, and no " +
                "test would have noticed a dropped plus. That claim was made three times and never built, and " +
                "the list of exceptions it named was extended twice by reading the source. This test derives " +
                "the set instead: it walks each pattern, skipping escapes, character classes, Unicode property " +
                "names and group-opening question marks, and reports every quantifier that is neither " +
                "possessive nor a fixed count. Whoever adds one decides here whether it is deliberate. " +
                "Unexpected: " + unexpected.joinToString(", ") { "${it.holder} ${it.text}" },
        )
    }

    @Test
    fun `every line of the filter that compiles a pattern lies inside a declaration this scan reads`() {
        val unreached =
            SOURCES_THE_FILTER_IS_WRITTEN_IN.flatMap { path ->
                val covered = declarationsIn(path).flatMap { it.lines }.toSet()
                linesCompilingAPattern(path).filterNot { it in covered }.map { "$path:${it + 1}" }
            }
        assertEquals(
            emptyList(),
            unreached,
            "the extent of this scan is the thing that was wrong about it, so the extent is asserted rather " +
                "than described: every line of these two files that compiles a pattern lies inside a " +
                "declaration this scan reads. What it still does not reach is a pattern compiled in a third " +
                "file or assembled at call time from values it has already compared. These lines compile a " +
                "pattern outside every declaration the scan reads: " + unreached.joinToString(", "),
        )
    }
}
