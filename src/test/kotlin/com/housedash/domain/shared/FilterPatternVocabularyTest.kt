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

private const val DECLARATIONS_THE_FILTER_HOLDS = 132

private const val PATTERNS_THE_FILTER_DECLARES = 53

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

private fun linesCompilingAPattern(path: String): List<Int> =
    sourceFile(path)
        .readLines()
        .mapIndexedNotNull { index, line -> index.takeIf { COMPILES_A_PATTERN.containsMatchIn(line) } }

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
