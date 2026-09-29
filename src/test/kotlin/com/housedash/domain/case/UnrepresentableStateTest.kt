package com.housedash.domain.case

import com.housedash.domain.shared.Identifier
import com.housedash.domain.shared.IdentifierShape
import com.housedash.domain.shared.Outcome
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UnrepresentableStateTest {
    private val variants = listOf(DraftCase::class.java, DescribedCase::class.java)

    @Test
    fun `the case hierarchy is sealed to exactly the two drawn states`() {
        val permitted =
            Case::class.java.permittedSubclasses
                .orEmpty()
                .map { it.name }
                .toSet()
        assertEquals(variants.map { it.name }.toSet(), permitted)
    }

    @Test
    fun `both case entry points hand back the sealed interface rather than a variant`() {
        val entryPoints =
            Case.Companion::class.java.declaredMethods
                .filter { it.name in setOf("draft", "rehydrate") }
        assertEquals(2, entryPoints.size)
        entryPoints.forEach { assertEquals(Case::class.java, it.returnType, it.name) }
    }

    @Test
    fun `no constructor of any case variant accepts the state as an argument`() {
        variants.forEach { variant ->
            variant.declaredConstructors.forEach { constructor ->
                assertFalse(
                    constructor.parameterTypes.any { it == CaseState::class.java },
                    "${variant.name} takes a state argument",
                )
            }
        }
    }

    @Test
    fun `a draft has no description slot at all, on the interface or on the class`() {
        val accessors = setOf("getDescription", "getDescribedAt", "getPhotos")
        assertTrue(Case::class.java.methods.none { it.name in accessors })
        assertTrue(DraftCase::class.java.methods.none { it.name in accessors })
        assertTrue(
            DescribedCase::class.java.methods
                .map { it.name }
                .containsAll(accessors),
        )
    }

    @Test
    fun `describe exists only on a draft, so describing twice cannot be written`() {
        assertTrue(DraftCase::class.java.methods.any { it.name == "describe" })
        assertFalse(DescribedCase::class.java.methods.any { it.name == "describe" })
        assertFalse(Case::class.java.methods.any { it.name == "describe" })
    }

    @Test
    fun `no case variant, description, photo collection or identifier exposes copy`() {
        val types =
            variants +
                listOf(
                    Description::class.java,
                    CasePhotos::class.java,
                    CaseId::class.java,
                    PhotoId::class.java,
                )
        types.forEach { type ->
            assertFalse(type.methods.any { it.name == "copy" }, "${type.name} exposes copy")
        }
    }

    @Test
    fun `the raw constructor of every case variant is private`() {
        variants.forEach { variant ->
            val declared = variant.declaredConstructors
            assertEquals(2, declared.size, variant.name)
            val raw = declared.single { Modifier.isPrivate(it.modifiers) }
            val reachable = declared.single { Modifier.isPublic(it.modifiers) }
            assertEquals(raw.parameterCount + 1, reachable.parameterCount, variant.name)
        }
    }

    @Test
    fun `a described case cannot be built from a list whose element type is erased`() {
        val constructor = syntheticConstructorOf(DescribedCase::class.java)
        assertEquals(CasePhotos::class.java, constructor.parameterTypes[PHOTO_ARGUMENT])
        assertFalse(constructor.parameterTypes.any { it == List::class.java })
    }

    @Test
    fun `the photo collection rejects an element that is not a photo id`() {
        val constructor = syntheticConstructorOf(CasePhotos::class.java)
        val thrown = thrownBy(constructor, arrayOf(listOf("not-a-photo-id"), null))
        assertEquals(CaseFault.MALFORMED_PHOTO_ID, assertIs<CorruptCase>(thrown).fault)
    }

    @Test
    fun `the photo collection rejects a list mixing photo ids with anything else`() {
        val constructor = syntheticConstructorOf(CasePhotos::class.java)
        val thrown = thrownBy(constructor, arrayOf(listOf(photoId("ph_1"), "not-a-photo-id"), null))
        assertEquals(CaseFault.MALFORMED_PHOTO_ID, assertIs<CorruptCase>(thrown).fault)
    }

    @Test
    fun `the photo collection rejects a list of identifiers of another type`() {
        val constructor = syntheticConstructorOf(CasePhotos::class.java)
        val thrown = thrownBy(constructor, arrayOf(listOf(caseId("cs_1")), null))
        assertEquals(CaseFault.MALFORMED_PHOTO_ID, assertIs<CorruptCase>(thrown).fault)
    }

    @Test
    fun `the photo collection reports a foreign element before the count`() {
        val constructor = syntheticConstructorOf(CasePhotos::class.java)
        val photos = listOf("oops", photoId("ph_1"), photoId("ph_2"), photoId("ph_3"), photoId("ph_4"))
        val thrown = thrownBy(constructor, arrayOf(photos, null))
        assertEquals(CaseFault.MALFORMED_PHOTO_ID, assertIs<CorruptCase>(thrown).fault)
    }

    @Test
    fun `the photo collection reports a foreign element before a repeat of it`() {
        val constructor = syntheticConstructorOf(CasePhotos::class.java)
        val thrown = thrownBy(constructor, arrayOf(listOf("oops", "oops"), null))
        assertEquals(CaseFault.MALFORMED_PHOTO_ID, assertIs<CorruptCase>(thrown).fault)
    }

    @Test
    fun `the photo collection accepts a list of photo ids and copies it`() {
        val constructor = syntheticConstructorOf(CasePhotos::class.java)
        val mutable = mutableListOf(photoId("ph_1"))
        val built = constructor.newInstance(mutable, null) as CasePhotos
        mutable.add(photoId("ph_2"))
        assertEquals(listOf(photoId("ph_1")), built.ids)
    }

    @Test
    fun `the described case has one reachable constructor and every parameter of it is required`() {
        val constructor = syntheticConstructorOf(DescribedCase::class.java)
        assertEquals(PARAMETERS_WITH_MARKER, constructor.parameterCount)
        assertTrue(Modifier.isPublic(constructor.modifiers))
        val arguments =
            arrayOf<Any?>(
                caseId(),
                nesterId(),
                createdAt,
                description(),
                CasePhotos.rehydrated(listOf(photoId())),
                describedAt,
                null,
            )
        (0 until PARAMETERS_WITH_MARKER - 1).forEach { position ->
            val withNull = arguments.copyOf()
            withNull[position] = null
            assertEquals(
                CaseFault.FIELD_ABSENT,
                assertIs<CorruptCase>(thrownBy(constructor, withNull)).fault,
                "argument $position accepted null",
            )
        }
    }

    @Test
    fun `the described case constructor accepts the complete argument list`() {
        val constructor = syntheticConstructorOf(DescribedCase::class.java)
        val built =
            constructor.newInstance(
                caseId(),
                nesterId(),
                createdAt,
                description(),
                CasePhotos.rehydrated(listOf(photoId())),
                describedAt,
                null,
            )
        assertIs<DescribedCase>(built)
        assertEquals(CaseState.DESCRIBED, built.state)
    }

    @Test
    fun `the draft constructor rejects a null owner`() {
        val constructor = syntheticConstructorOf(DraftCase::class.java)
        val thrown = thrownBy(constructor, arrayOf(caseId(), null, createdAt, null))
        assertEquals(CaseFault.FIELD_ABSENT, assertIs<CorruptCase>(thrown).fault)
    }

    @Test
    fun `the base identifier constructor is not reachable from another package`() {
        val constructor = Identifier::class.java.declaredConstructors.single()
        assertFailsWith<IllegalAccessException> { constructor.newInstance("cs_1", IdentifierShape("", 64)) }
    }

    @Test
    fun `the public synthetic constructor of Description cannot build an empty description`() {
        val constructor = Description::class.java.constructors.single()
        assertTrue(Modifier.isPublic(constructor.modifiers))
        assertIs<IllegalArgumentException>(thrownBy(constructor, arrayOf("", null)))
    }

    @Test
    fun `the constructor of Description enforces the shape rules and not the contact filter`() {
        val constructor = Description::class.java.constructors.single()
        assertIs<IllegalArgumentException>(
            thrownBy(constructor, arrayOf("the kitchen tap\u0000 drips all day long", null)),
        )
        val leaked = "call me on 917-555-0199 about the tap"
        assertEquals(leaked, assertIs<Description>(constructor.newInstance(leaked, null)).text)
        assertIs<Outcome.Err<CaseError>>(Description.of(leaked))
    }

    @Test
    fun `the public synthetic constructor of each identifier cannot build a malformed value`() {
        val malformed = listOf("", "'; drop table cases; --", "../../etc/passwd", "x".repeat(1_000))
        listOf(CaseId::class.java, PhotoId::class.java).forEach { type ->
            val constructor = type.constructors.single()
            assertTrue(Modifier.isPublic(constructor.modifiers))
            malformed.forEach { raw ->
                assertIs<IllegalArgumentException>(
                    thrownBy(constructor, arrayOf(raw, null)),
                    "${type.name} accepted a malformed value",
                )
            }
        }
    }

    private fun syntheticConstructorOf(type: Class<*>): Constructor<*> = type.constructors.single()

    private fun thrownBy(
        constructor: Constructor<*>,
        arguments: Array<Any?>,
    ): Throwable =
        try {
            constructor.newInstance(*arguments)
            error("construction succeeded when it should have failed")
        } catch (invocation: InvocationTargetException) {
            invocation.targetException
        }

    private companion object {
        const val PHOTO_ARGUMENT = 4
        const val PARAMETERS_WITH_MARKER = 7
    }
}
