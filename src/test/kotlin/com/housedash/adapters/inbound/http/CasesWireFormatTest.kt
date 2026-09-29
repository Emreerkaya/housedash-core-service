package com.housedash.adapters.inbound.http

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.RestController
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WIRE_PACKAGE = "com.housedash.adapters.inbound.http"

private const val SPRING_WEB_BINDING = "org.springframework.web.bind.annotation"

private val TEXT_TYPES = setOf<Class<*>>(String::class.java, CharSequence::class.java, Char::class.java)

private class ASecondFreeTextField(
    val title: String,
)

private class ASecondFreeTextFieldReachedWithoutAGetter {
    @JvmField
    var title: String = ""
}

private fun typesNamedIn(named: Type): List<Class<*>> =
    when (named) {
        is Class<*> -> listOf(named) + typesNamedIn(named.componentType ?: return listOf(named))
        is ParameterizedType -> typesNamedIn(named.rawType) + named.actualTypeArguments.flatMap(::typesNamedIn)
        is GenericArrayType -> typesNamedIn(named.genericComponentType)
        is WildcardType -> (named.upperBounds + named.lowerBounds).flatMap(::typesNamedIn)
        is TypeVariable<*> -> named.bounds.flatMap(::typesNamedIn)
        else -> emptyList()
    }

private fun declaredMethodsOf(type: Class<*>): List<Method> {
    val owners = mutableListOf<Class<*>>()
    var walked: Class<*>? = type
    while (walked != null && walked != Any::class.java) {
        owners.add(walked)
        walked = walked.superclass
    }
    return owners.flatMap { it.declaredMethods.toList() }.filterNot { it.isSynthetic }
}

private fun fieldTypesOf(type: Class<*>): List<Class<*>> =
    type.declaredFields.filterNot { it.isSynthetic }.flatMap { typesNamedIn(it.genericType) }

private fun textYieldingMembersOf(type: Class<*>): List<String> =
    declaredMethodsOf(type)
        .filter { method -> typesNamedIn(method.genericReturnType).any(TEXT_TYPES::contains) }
        .map { "${type.simpleName}#${it.name}" } +
        type.declaredFields
            .filterNot { it.isSynthetic }
            .filter { field -> typesNamedIn(field.genericType).any(TEXT_TYPES::contains) }
            .filterNot {
                java.lang.reflect.Modifier
                    .isPrivate(it.modifiers)
            }.map { "${type.simpleName}.${it.name}" }

class CasesWireFormatTest {
    private val production =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("com.housedash")

    private val controllers: List<Class<*>> =
        production
            .filter { it.isAnnotatedWith(RestController::class.java) }
            .map { Class.forName(it.name) }
            .sortedBy { it.name }

    private val handlers: List<Method> =
        controllers
            .flatMap { declaredMethodsOf(it) }
            .filter { method ->
                method.annotations.any { it.annotationClass.java.packageName == SPRING_WEB_BINDING }
            }.sortedBy { it.name }

    private val wireTypes: Set<Class<*>> =
        buildSet {
            val pending = ArrayDeque(handlers.flatMap { it.genericParameterTypes.flatMap(::typesNamedIn) })
            while (pending.isNotEmpty()) {
                val named = pending.removeFirst()
                if (add(named) && named.packageName == WIRE_PACKAGE) {
                    pending.addAll(fieldTypesOf(named))
                }
            }
        }

    @Test
    fun `the instrument reads the handlers that exist rather than a list of names`() {
        assertEquals(
            listOf(
                "com.housedash.adapters.inbound.http.CaseController",
                "com.housedash.adapters.inbound.http.OtpController",
            ),
            controllers.map { it.name },
            "the controllers are found by their annotation, so a second one joins this list and whoever adds " +
                "it decides then what its wire format may carry",
        )
        assertEquals(
            listOf("create", "issue", "verify"),
            handlers.map { it.name },
            "a handler is found by carrying a $SPRING_WEB_BINDING annotation. A handler that left this list " +
                "took its parameters out of every assertion below with it",
        )
    }

    @Test
    fun `every type the wire carries is the set this test names`() {
        assertEquals(
            setOf(
                "com.housedash.adapters.inbound.http.CreateCaseRequest",
                "com.housedash.adapters.inbound.http.IssueOtpRequestBody",
                "com.housedash.adapters.inbound.http.VerifyOtpRequestBody",
                "com.housedash.app.SubmittedCode",
                "com.housedash.app.SubmittedId",
                "com.housedash.app.SubmittedIdentifier",
                "com.housedash.app.SubmittedIp",
                "com.housedash.app.SubmittedKey",
                "com.housedash.app.SubmittedText",
                "java.util.List",
            ),
            wireTypes.map { it.name }.toSortedSet().toSet(),
            "the closure starts at the handler's own parameters and walks into the fields of every type " +
                "declared in $WIRE_PACKAGE, so a new field on a request record and a new parameter on a " +
                "handler both land here by name. D202 asked for an enumeration of the free-text fields; this " +
                "is the enumeration, and it is over every field rather than over the ones somebody " +
                "remembered. It covers what comes in and not what goes out, because a case identifier " +
                "leaving as text is legitimate and the outbound records are pinned field by field below",
        )
    }

    @Test
    fun `no type the wire carries can hand back the text it was given`() {
        val leaking =
            wireTypes
                .filterNot { it.packageName == WIRE_PACKAGE }
                .filterNot { Collection::class.java.isAssignableFrom(it) }
                .flatMap(::textYieldingMembersOf)
        assertEquals(
            emptyList(),
            leaking,
            "I7 is enforced by Description.of and nothing may re-implement it at the edge, so the wire carries " +
                "free text as a type whose only exit runs that filter. A field typed String satisfies no such " +
                "rule and is caught here by the type rather than by its name: String declares members that " +
                "return text, so description: String reddens this leg the same way a second or a third field " +
                "would. Leaking: $leaking",
        )
    }

    @Test
    fun `the leak rule catches a raw text field and a second free text field added to a record`() {
        assertTrue(
            textYieldingMembersOf(String::class.java).isNotEmpty(),
            "the rule above is satisfied by every type that cannot yield text, so it says nothing unless a " +
                "type that can yield text is caught. String is the control and it was not caught",
        )
        assertEquals(
            listOf("ASecondFreeTextField#getTitle"),
            textYieldingMembersOf(ASecondFreeTextField::class.java),
            "this is the plant D202 describes, kept as a control rather than run once: a record carrying a " +
                "second field of free text. Kotlin keeps the backing field private, so the getter is what " +
                "catches it, and neither leg is a name check",
        )
        assertEquals(
            listOf("ASecondFreeTextFieldReachedWithoutAGetter.title"),
            textYieldingMembersOf(ASecondFreeTextFieldReachedWithoutAGetter::class.java),
            "the second control, because the first one alone would let the method leg carry the whole rule: a " +
                "field declared with JvmField has no getter at all and is reachable by name from Java and " +
                "from Jackson",
        )
    }

    @Test
    fun `each record the wire carries declares the fields this test names and each is of a guarded type`() {
        assertEquals(
            listOf("description: SubmittedText", "nesterId: SubmittedId", "photoIds: List"),
            CreateCaseRequest::class.java.declaredFields
                .filterNot { it.isSynthetic }
                .map { "${it.name}: ${it.type.simpleName}" }
                .sorted(),
        )
        assertEquals(
            listOf("reason: String"),
            CaseRefusal::class.java.declaredFields
                .filterNot { it.isSynthetic }
                .map { "${it.name}: ${it.type.simpleName}" },
            "a refusal travels outward and carries a reason the client can branch on. What it must never " +
                "carry is the text that was refused, which is what the controller test asserts on the body",
        )
        assertEquals(
            listOf("caseId: String", "state: String"),
            CreateCaseResponse::class.java.declaredFields
                .filterNot { it.isSynthetic }
                .map { "${it.name}: ${it.type.simpleName}" }
                .sorted(),
            "the response exposes no domain type; the identifier and the state both travel as text",
        )
    }
}
