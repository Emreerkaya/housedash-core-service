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

private const val PATTERNS_THE_FILTER_DECLARES = 30

private const val OPENS_A_STRING = "\""

private data class Pattern(
    val file: String,
    val name: String,
    val text: String,
)

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

private fun patternsIn(path: String): List<Pattern> {
    val file = sourceFile(path)
    val lines = file.readLines()
    val found = mutableListOf<Pattern>()
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
        if (text.startsWith(OPENS_A_STRING)) {
            found.add(Pattern(file.name, opening.groupValues[NAME_GROUP], text))
        }
        index = end
    }
    return found
}

class FilterPatternVocabularyTest {
    @Test
    fun `no two names in the filter stand for the same pattern`() {
        val patterns = SOURCES_THE_FILTER_IS_WRITTEN_IN.flatMap(::patternsIn)
        val sharing =
            patterns
                .groupBy { it.text }
                .filterValues { it.size > 1 }
                .map { (text, holders) ->
                    "${holders.joinToString(" and ") { "${it.file}:${it.name}" }} all stand for $text"
                }
        assertTrue(
            sharing.isEmpty(),
            "two names for one pattern are either one name too many or two values that were meant to differ " +
                "and do not, and nothing tells the next reader which. Give the pattern one name, or give each " +
                "name the value it was written for:\n" + sharing.joinToString("\n"),
        )
    }

    @Test
    fun `this scan still reads every pattern the filter declares, so a pattern cannot leave it unseen`() {
        val patterns = SOURCES_THE_FILTER_IS_WRITTEN_IN.flatMap(::patternsIn)
        assertEquals(
            PATTERNS_THE_FILTER_DECLARES,
            patterns.size,
            "a scan that stops seeing a declaration reports no finding about it, exactly as a loop over a " +
                "table stops visiting a row that has left it, so the number of patterns this scan reaches is " +
                "pinned and moving it is a deliberate edit. What this test delivers is that the names and " +
                "values it compares are all of them. What it does not deliver is a pattern built somewhere " +
                "other than a named declaration in one of these two files. Reached: " +
                patterns.joinToString(", ") { "${it.file}:${it.name}" },
        )
    }
}
