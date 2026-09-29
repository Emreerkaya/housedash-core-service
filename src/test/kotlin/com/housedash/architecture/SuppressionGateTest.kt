package com.housedash.architecture

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val BUILD_SCRIPT = "build.gradle.kts"

private val PATTERNS_THE_GATE_HOLDS =
    Regex("""val forbidden =\s*\n\s*listOf\(\s*\n((?:\s*"{3}.*"{3},\s*\n)+)\s*\)\.map \{ Regex\(it\) }""")

private const val PATTERN_BLOCK_GROUP = 1

private val ONE_RAW_PATTERN = Regex(""""{3}(.*?)"{3}""")

private const val RAW_PATTERN_GROUP = 1

private const val PATTERNS_THE_GATE_IS_WRITTEN_WITH = 4

private const val MARK_OPENING_AN_ANNOTATION = "@"

private const val NAME_D155_FORBIDS = "S" + "uppress"

private const val QUALIFIED_NAME_D155_FORBIDS = "kotlin." + NAME_D155_FORBIDS

private const val ARGUMENT_A_SUPPRESSION_CARRIES = "(\"unused\")"

private val TARGETS_A_SUPPRESSION_MAY_BE_WRITTEN_AGAINST =
    listOf("file", "get", "set", "property", "field", "param", "setparam", "receiver", "delegate")

private fun targeted(
    target: String,
    name: String,
    between: String,
): String = "$MARK_OPENING_AN_ANNOTATION$target$between$name$ARGUMENT_A_SUPPRESSION_CARRIES"

private val SPELLINGS_KOTLIN_ACCEPTS: Map<String, String> =
    buildMap {
        put("bare name", "$MARK_OPENING_AN_ANNOTATION$NAME_D155_FORBIDS$ARGUMENT_A_SUPPRESSION_CARRIES")
        put(
            "fully qualified name",
            "$MARK_OPENING_AN_ANNOTATION$QUALIFIED_NAME_D155_FORBIDS$ARGUMENT_A_SUPPRESSION_CARRIES",
        )
        TARGETS_A_SUPPRESSION_MAY_BE_WRITTEN_AGAINST.forEach { target ->
            put("$target use-site target", targeted(target, NAME_D155_FORBIDS, ":"))
        }
        put("file target on the qualified name", targeted("file", QUALIFIED_NAME_D155_FORBIDS, ":"))
        put("getter target on the qualified name", targeted("get", QUALIFIED_NAME_D155_FORBIDS, ":"))
        put("whitespace around the target colon", targeted("get", NAME_D155_FORBIDS, " : "))
        put("a line break after the target colon", targeted("get", NAME_D155_FORBIDS, ":\n    "))
        put("the name imported under an alias", "import $QUALIFIED_NAME_D155_FORBIDS as Hush")
    }

private const val SPELLINGS_MEASURED_TO_COMPILE = 16

private val SPELLINGS_THE_GATE_MUST_LEAVE_ALONE =
    listOf(
        "private val suppressionsAreNotUsedHere = 1",
        "fun doesNotSuppressAnything() = Unit",
        "import kotlin.test.assertTrue",
        "private const val NAME = \"a rule objects, the code changes\"",
        "@Test",
        "@JvmStatic",
        "import kotlin.collections.List",
    )

private fun gatePatterns(): List<Regex> {
    val script = File(BUILD_SCRIPT)
    assertTrue(
        script.isFile,
        "$BUILD_SCRIPT is where the suppression gate is declared and this test cannot find it, so it is " +
            "asserting the extent of nothing",
    )
    val block =
        PATTERNS_THE_GATE_HOLDS.find(script.readText())?.groupValues?.get(PATTERN_BLOCK_GROUP)
            ?: error(
                "the suppression gate's pattern list is no longer written as a list of raw strings this test " +
                    "can read out of $BUILD_SCRIPT. This test reads the gate's own patterns rather than " +
                    "restating them, because a guard and its extent check written twice are two guards that " +
                    "disagree (D344)",
            )
    return ONE_RAW_PATTERN.findAll(block).map { Regex(it.groupValues[RAW_PATTERN_GROUP]) }.toList()
}

class SuppressionGateTest {
    @Test
    fun `the gate is written with the patterns this test reads and not with more`() {
        assertEquals(
            PATTERNS_THE_GATE_IS_WRITTEN_WITH,
            gatePatterns().size,
            "a pattern added to or removed from the gate changes what it enforces, and the spelling census " +
                "below reads the gate's own list, so the count is pinned here and moving it is a deliberate edit",
        )
    }

    @Test
    fun `every spelling of the forbidden annotation that Kotlin accepts is caught by the gate`() {
        val patterns = gatePatterns()
        val missed =
            SPELLINGS_KOTLIN_ACCEPTS.filterValues { spelling ->
                patterns.none { it.containsMatchIn(spelling) }
            }
        assertEquals(
            emptyMap(),
            missed,
            "D155 forbids this annotation outright, and a rule stated as absolute is enforced only over the " +
                "spellings its pattern reaches. Each spelling below was written into a Kotlin file under " +
                "src/test and compiled before being listed here, so none of them is hypothetical: the " +
                "use-site-targeted forms all compiled and all passed the gate at 92aa2d7, and one of them was " +
                "silencing a real deprecation warning out of compileTestKotlin with the build green. The " +
                "spellings are assembled from parts rather than written out, because a file holding them " +
                "literally would be caught by the gate it exists to measure. Not caught: " +
                missed.keys.joinToString(", "),
        )
        assertEquals(
            SPELLINGS_MEASURED_TO_COMPILE,
            SPELLINGS_KOTLIN_ACCEPTS.size,
            "this is the denominator, and without it a census that lost a spelling would satisfy the " +
                "assertion above by asking about fewer forms. A seventeenth spelling is added here with its " +
                "own measurement that it compiles",
        )
    }

    @Test
    fun `the gate leaves alone the lines that merely talk about suppression`() {
        val patterns = gatePatterns()
        val wrongly =
            SPELLINGS_THE_GATE_MUST_LEAVE_ALONE.filter { line -> patterns.any { it.containsMatchIn(line) } }
        assertEquals(
            emptyList(),
            wrongly,
            "the widening from two spellings to every one Kotlin accepts costs nothing on this tree, and this " +
                "is the leg that says so: an annotation the codebase does use, an import, and a name that " +
                "merely contains the word are all left alone. Wrongly caught: " + wrongly.joinToString(", "),
        )
    }
}
