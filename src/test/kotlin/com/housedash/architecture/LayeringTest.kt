package com.housedash.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaCall
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaGenericArrayType
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.domain.JavaParameterizedType
import com.tngtech.archunit.core.domain.JavaType
import com.tngtech.archunit.core.domain.properties.HasName
import com.tngtech.archunit.core.domain.properties.HasOwner
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LayeringTest {
    private val domain =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("com.housedash.domain")

    private val codebase =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("com.housedash")

    private val floatingPointType =
        HasName.Predicates.nameMatching("^(java\\.lang\\.)?(Double|Float|double|float)$|^\\[+[DF]$")

    private val anyFloatingPointParameter =
        DescribedPredicate.describe<List<JavaClass>>("any parameter is floating point") { parameters ->
            parameters.any { floatingPointType.test(it) }
        }

    private val sharedKernelPackage = "com.housedash.domain.shared"

    private val boundedContextPackage = "com.housedash.domain."

    private val persistencePackage = "com.housedash.adapters.outbound.persistence"

    private val inPersistence: (JavaClass) -> Boolean = { clazz ->
        clazz.packageName == persistencePackage || clazz.packageName.startsWith("$persistencePackage.")
    }

    private val inSharedKernel: (JavaClass) -> Boolean = { clazz ->
        clazz.packageName == sharedKernelPackage || clazz.packageName.startsWith("$sharedKernelPackage.")
    }

    private val inBoundedContext: (JavaClass) -> Boolean = { clazz ->
        clazz.packageName.startsWith(boundedContextPackage) && !inSharedKernel(clazz)
    }

    private val closedConstruction =
        DescribedPredicate.describe<JavaClass>(
            "a bounded-context type whose every declared constructor is private or compiler-synthesised",
        ) { candidate ->
            inBoundedContext(candidate) &&
                !candidate.isEnum &&
                candidate.constructors.isNotEmpty() &&
                candidate.constructors.all { constructor ->
                    JavaModifier.PRIVATE in constructor.modifiers || JavaModifier.SYNTHETIC in constructor.modifiers
                }
        }

    private val aggregateVariant =
        DescribedPredicate.describe<JavaClass>(
            "a state-carrying variant of a sealed aggregate declared in its own bounded-context package",
        ) { candidate ->
            closedConstruction.test(candidate) &&
                candidate.rawInterfaces.any { it.packageName == candidate.packageName } &&
                candidate.fields.any { JavaModifier.STATIC !in it.modifiers }
        }

    private val storedRow =
        DescribedPredicate.describe<JavaClass>(
            "a stored-row type of a bounded context, named by the Row suffix its mapper reads",
        ) { candidate -> inBoundedContext(candidate) && candidate.simpleName.endsWith("Row") }

    private val reachableFromOutside: (Set<JavaModifier>) -> Boolean = { modifiers ->
        JavaModifier.PRIVATE !in modifiers && JavaModifier.SYNTHETIC !in modifiers
    }

    private val mintsRatherThanReads: (JavaMethod) -> Boolean = { method ->
        method.rawParameterTypes.isNotEmpty() ||
            JavaModifier.STATIC in method.modifiers ||
            method.owner.simpleName == COMPANION_OBJECT
    }

    private val mintTargetsByProducedType: Map<String, Set<String>> =
        domain
            .flatMap { it.methods }
            .filter { reachableFromOutside(it.modifiers) }
            .filter(mintsRatherThanReads)
            .flatMap { method ->
                typesNamedIn(method.returnType)
                    .filter(closedConstruction::test)
                    .map { produced -> produced.name to "${method.owner.name}#${method.name}" }
            }.groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }

    private val typesWithAReopenedConstructor: Set<String> =
        domain
            .filter { closedConstruction.test(it) }
            .filter { type -> type.constructors.any { reachableFromOutside(it.modifiers) } }
            .map { it.name }
            .toSet()

    private val reconstructionEntryPoints: Set<String> =
        domain
            .flatMap { it.methods }
            .filter { reachableFromOutside(it.modifiers) }
            .filter { method ->
                method.parameterTypes.any { parameter -> typesNamedIn(parameter).any(storedRow::test) }
            }.map { "${it.owner.name}#${it.name}" }
            .toSet()

    private val forgeableRowConstructors: Set<String> =
        domain
            .filter { storedRow.test(it) }
            .filter { row -> row.constructors.any { reachableFromOutside(it.modifiers) } }
            .map { it.name }
            .toSet()

    private fun typesNamedIn(named: JavaType): List<JavaClass> {
        val erasure = named.toErasure()
        val within =
            when {
                named is JavaParameterizedType -> named.actualTypeArguments.flatMap { typesNamedIn(it) }
                named is JavaGenericArrayType -> typesNamedIn(named.componentType)
                else -> erasure.tryGetComponentType().map { typesNamedIn(it) }.orElse(emptyList())
            }
        return listOf(erasure) + within
    }

    private fun callTo(
        methodName: String,
        ownerPackage: String,
    ) = JavaCall.Predicates
        .target(HasOwner.Predicates.With.owner(JavaClass.Predicates.resideInAPackage(ownerPackage)))
        .and(JavaCall.Predicates.target(HasName.Predicates.name(methodName)))

    private fun callToMethodOn(
        methodName: String,
        owner: Class<*>,
    ) = JavaCall.Predicates
        .target(HasOwner.Predicates.With.owner(JavaClass.Predicates.equivalentTo(owner)))
        .and(JavaCall.Predicates.target(HasName.Predicates.name(methodName)))

    private class MintBoundary(
        claim: String,
        private val guarded: DescribedPredicate<JavaClass>,
        private val mintKeys: Set<String>,
        private val permitted: (JavaClass, JavaClass) -> Boolean,
    ) : ArchCondition<JavaClass>(claim) {
        override fun check(
            item: JavaClass,
            events: ConditionEvents,
        ) {
            item.constructorCallsFromSelf
                .filter { guarded.test(it.targetOwner) && !permitted(item, it.targetOwner) }
                .forEach { call ->
                    events.add(
                        SimpleConditionEvent.violated(
                            call,
                            "${call.description} constructs ${call.targetOwner.name}, owned by " +
                                call.targetOwner.packageName,
                        ),
                    )
                }
            item.methodCallsFromSelf
                .filter { "${it.targetOwner.name}#${it.target.name}" in mintKeys }
                .filter { !permitted(item, it.targetOwner) }
                .forEach { call ->
                    events.add(
                        SimpleConditionEvent.violated(
                            call,
                            "${call.description} calls ${call.targetOwner.name}#${call.target.name}, " +
                                "owned by ${call.targetOwner.packageName}",
                        ),
                    )
                }
        }
    }

    @Test
    fun `domain depends on no framework`() {
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "com.fasterxml..",
                "jakarta..",
                "javax..",
                "java.sql..",
                "org.hibernate..",
                "org.flywaydb..",
            ).`as`("domain must import nothing; that is what makes D150's exhaustive explorer possible")
            .check(domain)
    }

    @Test
    fun `domain performs no I O`() {
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "java.io..",
                "java.nio..",
                "java.net..",
                "java.util.logging..",
                "org.slf4j..",
            ).`as`("domain must not perform I O; time and identifiers arrive as parameters instead")
            .check(domain)
    }

    @Test
    fun `domain does not shell out`() {
        noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.lang.ProcessBuilder")
            .`as`("domain must not start external processes; ProcessBuilder is an I O escape and a channel around I7")
            .check(domain)
        noClasses()
            .should()
            .callMethodWhere(callToMethodOn("exec", Runtime::class.java))
            .`as`("domain must not start external processes; Runtime.exec is an I O escape and a channel around I7")
            .check(domain)
    }

    @Test
    fun `domain holds no reflection tool`() {
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("java.lang.reflect..")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.lang.ClassLoader")
            .`as`(
                "a ban keyed on a static type reference cannot see a type named by string, so the tools that " +
                    "name types by string are banned instead of the types they could name",
            ).check(domain)
    }

    @Test
    fun `domain names no type by string and reads no ambient state`() {
        noClasses()
            .should()
            .callMethodWhere(callToMethodOn("forName", Class::class.java))
            .orShould()
            .callMethodWhere(callToMethodOn("getClassLoader", Class::class.java))
            .orShould()
            .callMethodWhere(callToMethodOn("newInstance", Class::class.java))
            .orShould()
            .callMethodWhere(callToMethodOn("getenv", System::class.java))
            .orShould()
            .callMethodWhere(callToMethodOn("getProperty", System::class.java))
            .orShould()
            .callMethodWhere(callToMethodOn("getProperties", System::class.java))
            .`as`(
                "domain must be a pure function of its arguments; Class.forName, Class.getClassLoader, " +
                    "Class.newInstance, System.getenv and System.getProperty are each a way out of that",
            ).check(domain)
    }

    @Test
    fun `domain never reads the clock`() {
        noClasses().should().callMethodWhere(callTo("now", "java.time")).check(domain)
        noClasses()
            .should()
            .callMethod(System::class.java, "currentTimeMillis")
            .orShould()
            .callMethod(System::class.java, "nanoTime")
            .check(domain)
        noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.time.Clock")
            .check(domain)
    }

    @Test
    fun `domain never generates its own randomness`() {
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("kotlin.random..", "kotlin.uuid..")
            .orShould()
            .dependOnClassesThat()
            .haveNameMatching("java\\.util\\.(concurrent\\.ThreadLocalRandom|Random)|java\\.security\\.SecureRandom")
            .check(domain)
        noClasses().should().callMethod(java.util.UUID::class.java, "randomUUID").check(domain)
        noClasses()
            .should()
            .callMethodWhere(JavaCall.Predicates.target(HasName.Predicates.name("shuffled")))
            .check(domain)
    }

    @Test
    fun `domain holds no floating point`() {
        noFields().should().haveRawType(floatingPointType).check(domain)
        noMethods()
            .should()
            .haveRawReturnType(floatingPointType)
            .orShould()
            .haveRawParameterTypes(anyFloatingPointParameter)
            .check(domain)
    }

    @Test
    fun `domain does not reach into app or adapters`() {
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.housedash.app..", "com.housedash.adapters..")
            .`as`(
                "domain must not depend on app or adapters; the package line between them is where a future " +
                    "repository split happens",
            ).check(domain)
    }

    @Test
    fun `bounded contexts do not reach into each other, except through the shared kernel`() {
        slices()
            .matching("com.housedash.domain.(*)..")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(
                DescribedPredicate.alwaysTrue(),
                JavaClass.Predicates.resideInAPackage("$sharedKernelPackage.."),
            ).`as`(
                "no domain.<context> may depend on another domain.<context>; domain.shared is the shared kernel " +
                    "every context may depend on, and which may depend on none of them",
            ).check(domain)
    }

    @Test
    fun `mint tripwire, not a proof, a closed domain type is minted only inside its own package`() {
        classes()
            .should(
                MintBoundary(
                    "only mint a type whose construction its own bounded context closed from inside that package",
                    closedConstruction,
                    mintTargetsByProducedType.values.flatten().toSet(),
                ) { caller, owner -> caller.packageName == owner.packageName },
            ).`as`(
                "a type whose constructors a bounded context made private is minted by that package alone. " +
                    "What is watched is every reachable domain member that names such a type in its return " +
                    "position, directly or as a type argument of a wrapper, and that is given something to " +
                    "build from — it takes at least one argument, or is static, or is declared on a " +
                    "companion — plus every constructor call: " +
                    "${mintTargetsByProducedType.values.sumOf { it.size }} routes over " +
                    "${mintTargetsByProducedType.size} types, $mintTargetsByProducedType. Reading a value is " +
                    "not making one, so a property getter, which takes nothing and hands back what its owner " +
                    "already holds, is not a route; without that the persistence adapter entitled to " +
                    "reconstruct an aggregate by the rule beside this one would be forbidden to serialise it. " +
                    "What is not watched is a route that never names the type it produces — a member declared " +
                    "to return a supertype, an Any, or a value written into a parameter — an instance member " +
                    "that mints from no argument at all, and D173's residual, which is that anyone holding a " +
                    "NesterId may claim it as an owner. Parsing a value object at an inbound boundary is a " +
                    "watched route on purpose and stays one: CaseId.of, PhotoId.of and Description.of are " +
                    "where the identifier shapes and the I7 filter run, and the package that needs them is " +
                    "entitled by name the way $persistencePackage is entitled above, never by a wildcard",
            ).check(codebase)
    }

    @Test
    fun `reconstruction tripwire, not a proof, a stored row is read back only by the repository`() {
        classes()
            .should(
                MintBoundary(
                    "only reconstruct an aggregate from a stored row inside its own package or in the repository",
                    storedRow,
                    reconstructionEntryPoints,
                ) { caller, owner ->
                    caller.packageName == owner.packageName || inPersistence(caller)
                },
            ).`as`(
                "an aggregate is reconstructable by the repository and by nothing else; forging the row and " +
                    "calling the entry point are both the same capability and both belong to $persistencePackage",
            ).check(codebase)
    }

    @Test
    fun `a property getter is not a mint route and a factory that takes an argument is`() {
        val describedCase = domain.single { it.simpleName == "DescribedCase" }
        val readers = describedCase.methods.filter { it.name in READING_MEMBERS }
        assertTrue(readers.size == READING_MEMBERS.size) {
            "the members this test names as readers are $READING_MEMBERS and DescribedCase declares " +
                "${describedCase.methods.map { it.name }}, so the exclusion no longer names what it excludes"
        }
        readers.forEach { reader ->
            assertTrue(typesNamedIn(reader.returnType).any(closedConstruction::test)) {
                "${reader.name} no longer returns a closed domain type, so it can no longer show that " +
                    "reading one is distinguished from minting one"
            }
            assertTrue(!mintsRatherThanReads(reader)) {
                "${reader.name} is counted as a mint route, so the persistence adapter cannot serialise the " +
                    "aggregate the reconstruction rule entitles it to rebuild"
            }
        }
        val factories = domain.flatMap { it.methods }.filter { it.name == "rehydrated" }
        assertTrue(factories.isNotEmpty())
        factories.forEach { factory ->
            assertTrue(mintsRatherThanReads(factory)) { "${factory.owner.name}#rehydrated is no longer watched" }
        }
    }

    @Test
    fun `the construction boundary guards the types this test names and not merely some type`() {
        val matched =
            domain
                .filter { aggregateVariant.test(it) }
                .map { it.name }
                .toSortedSet()
        assertTrue(matched == AGGREGATE_VARIANTS_BY_NAME.toSortedSet()) {
            "the aggregate-variant predicate matches $matched and this test names " +
                "$AGGREGATE_VARIANTS_BY_NAME. A type that quietly left the guarded set makes the predicate " +
                "match a smaller non-empty set, which every assertion over its own matches reports as success"
        }
        val closed =
            domain
                .filter { closedConstruction.test(it) }
                .map { it.name }
                .toSortedSet()
        assertTrue(closed == CLOSED_CONSTRUCTION_BY_NAME.toSortedSet()) {
            "the closed-construction predicate matches $closed and this test names " +
                "$CLOSED_CONSTRUCTION_BY_NAME; Kotlin's internal compiles to a plain public constructor with " +
                "no synthetic flag, so a type can leave this set without any constructor becoming reachable " +
                "by name"
        }
    }

    @Test
    fun `the construction boundary names the routes it watches and not merely some route`() {
        assertTrue(mintTargetsByProducedType == MINT_ROUTES_BY_NAME) {
            "the mint routes found in the domain are $mintTargetsByProducedType and this test names " +
                "$MINT_ROUTES_BY_NAME. A route that quietly left the watched set leaves a non-empty map " +
                "behind, which every assertion over its own contents reports as success; a route erased to a " +
                "raw List or hidden in an array component leaves it silently"
        }
        assertTrue(reconstructionEntryPoints == RECONSTRUCTION_ENTRY_POINTS_BY_NAME) {
            "the reconstruction entry points found in the domain are $reconstructionEntryPoints and this test " +
                "names $RECONSTRUCTION_ENTRY_POINTS_BY_NAME; a repository findAll taking List<CaseRow> is " +
                "exactly the signature that used to leave this set without any assertion noticing"
        }
        assertTrue(typesWithAReopenedConstructor == TYPES_WITH_A_REOPENED_CONSTRUCTOR) {
            "the closed types with a constructor reachable by name are $typesWithAReopenedConstructor and " +
                "this test names $TYPES_WITH_A_REOPENED_CONSTRUCTOR"
        }
        assertTrue(forgeableRowConstructors == FORGEABLE_ROW_CONSTRUCTORS) {
            "the stored-row types with a reachable constructor are $forgeableRowConstructors and this test " +
                "names $FORGEABLE_ROW_CONSTRUCTORS"
        }
    }

    @Test
    fun `the inbound parse boundaries the rule's prose names are each a watched route`() {
        INBOUND_PARSE_BOUNDARIES.forEach { (produced, route) ->
            assertTrue(mintTargetsByProducedType[produced].orEmpty().contains(route)) {
                "the construction rule's prose says $route is a watched route on purpose because it is where " +
                    "the identifier shapes and the I7 filter run, and the routes found for $produced are " +
                    "${mintTargetsByProducedType[produced].orEmpty()}. The per-variant coverage loop cannot " +
                    "reach Description, which is closed but is not a state-carrying variant of a sealed " +
                    "aggregate, so this is the only assertion that holds the prose to the code"
            }
        }
    }

    @Test
    fun `the folding precondition is carried by a type nothing outside its own file can construct`() {
        val folded = codebase.single { it.simpleName == FOLDED_TEXT_TYPE }
        assertTrue(folded.constructors.isNotEmpty()) {
            "$FOLDED_TEXT_TYPE is the type the number shape guard takes instead of a String, and this test " +
                "found no constructor on it at all, so the rest of this test compared nothing"
        }
        val reachable =
            folded.constructors
                .filterNot { it.modifiers.contains(JavaModifier.PRIVATE) }
                .filterNot { constructor ->
                    constructor.rawParameterTypes.any { it.simpleName == KOTLINS_OWN_BRIDGE_PARAMETER }
                }
        assertTrue(reachable.isEmpty()) {
            "the guard used to take a String and carry its precondition in its name, " +
                "holdsPhoneNumberInAlreadyFoldedText, with an ArchUnit rule naming that name: both halves " +
                "failed in one rename, so it was not defence in depth. The precondition is a type now. " +
                "$FOLDED_TEXT_TYPE can only be made by the fold that produces one, because every constructor " +
                "is private to the file the fold lives in, so raw text is not a thing the guard can be handed " +
                "and the compiler says so rather than this test. What this test guards is that the " +
                "constructors stay private; if one opens, the precondition is back to being a promise. One " +
                "constructor is skipped and it is worth knowing why: Kotlin emits a synthetic bridge taking a " +
                "$KOTLINS_OWN_BRIDGE_PARAMETER beside the real one, it is not private in the bytecode, and no " +
                "Kotlin source can name it. So the guarantee here is a Kotlin-source guarantee rather than a " +
                "bytecode one, which is the same gap as a const val compiling to an ldc. Reachable " +
                "constructors: " + reachable.map { it.fullName }
        }
    }

    @Test
    fun `the number shape guard takes the folded text type, so the type above is the way in`() {
        assertTrue(
            codebase
                .single { it.simpleName == NUMBER_SHAPE_FILE }
                .methods
                .any { method -> method.rawParameterTypes.any { it.simpleName == FOLDED_TEXT_TYPE } },
            "no method of $NUMBER_SHAPE_FILE takes a $FOLDED_TEXT_TYPE, so the type above is no longer the way " +
                "in and this test is guarding something nothing uses. Note for whoever writes the next rule " +
                "here: a const val cannot be confined this way at all, because a reader of " +
                "SEPARATOR_BETWEEN_TWO_DIGIT_GROUPS compiles to an ldc of its value with no reference to the " +
                "object it was declared in, so no dependency check can see the read",
        )
    }

    @Test
    fun `both mint boundaries are armed tripwires today because no app or adapters class exists yet`() {
        val outsideTheDomain =
            codebase
                .filter { type -> LAYERS_THE_MINT_RULES_SCOPE_BY.any { type.packageName.startsWith(it) } }
                .map { it.name }
        assertTrue(outsideTheDomain.isEmpty()) {
            "both mint boundaries scope their permission by package, and until Task 3 and Task 4 there is no " +
                "app or adapters package for them to refuse, so neither rule has a subject outside the domain " +
                "and both are tripwires rather than enforcement. These classes now exist: $outsideTheDomain. " +
                "Rename this test and both rules when that is no longer true, because D176's lesson is that a " +
                "test named as a proof gets trusted as one"
        }
    }

    @Test
    fun `both construction boundaries cover a real target rather than nothing`() {
        val variants = domain.filter { aggregateVariant.test(it) }
        assertTrue(variants.isNotEmpty()) {
            "no domain class matched the aggregate-variant predicate; the construction boundary would be vacuous"
        }
        variants.forEach { variant ->
            val covered =
                mintTargetsByProducedType[variant.name].orEmpty() +
                    typesWithAReopenedConstructor.filter { it == variant.name }
            assertTrue(covered.isNotEmpty()) {
                "no reachable mint route was found for ${variant.name}, so the construction boundary enforces " +
                    "nothing over it; mint routes seen across the domain were $mintTargetsByProducedType"
            }
        }
        assertTrue(forgeableRowConstructors.isNotEmpty()) {
            "no stored-row type with a reachable constructor was found, so the row-forging arm enforces nothing"
        }
        assertTrue(reconstructionEntryPoints.isNotEmpty()) {
            "no reachable member takes a stored-row type, so the reconstruction boundary enforces nothing"
        }
    }

    private companion object {
        const val FOLDED_TEXT_TYPE = "FoldedForMatchingOnly"

        const val NUMBER_SHAPE_FILE = "NumberShapeKt"

        const val KOTLINS_OWN_BRIDGE_PARAMETER = "DefaultConstructorMarker"

        const val COMPANION_OBJECT = "Companion"

        val READING_MEMBERS = setOf("getId", "getDescription", "getPhotos")

        const val CASE_PACKAGE = "com.housedash.domain.case"

        val AGGREGATE_VARIANTS_BY_NAME = setOf("$CASE_PACKAGE.DraftCase", "$CASE_PACKAGE.DescribedCase")

        val MINT_ROUTES_BY_NAME: Map<String, Set<String>> =
            mapOf(
                "$CASE_PACKAGE.CaseId" to setOf("$CASE_PACKAGE.CaseId\$Companion#of"),
                "$CASE_PACKAGE.CasePhotos" to
                    setOf(
                        "$CASE_PACKAGE.CasePhotos\$Companion#of",
                        "$CASE_PACKAGE.CasePhotos\$Companion#rehydrated",
                    ),
                "$CASE_PACKAGE.DescribedCase" to
                    setOf(
                        "$CASE_PACKAGE.DescribedCase\$Companion#describing",
                        "$CASE_PACKAGE.DescribedCase\$Companion#rehydrated",
                        "$CASE_PACKAGE.DraftCase#describe",
                    ),
                "$CASE_PACKAGE.Description" to
                    setOf(
                        "$CASE_PACKAGE.Description\$Companion#of",
                        "$CASE_PACKAGE.Description\$Companion#rehydrated",
                    ),
                "$CASE_PACKAGE.DraftCase" to setOf("$CASE_PACKAGE.DraftCase\$Companion#of"),
                "$CASE_PACKAGE.PhotoId" to setOf("$CASE_PACKAGE.PhotoId\$Companion#of"),
            )

        val RECONSTRUCTION_ENTRY_POINTS_BY_NAME = setOf("$CASE_PACKAGE.Case\$Companion#rehydrate")

        val TYPES_WITH_A_REOPENED_CONSTRUCTOR = emptySet<String>()

        val FORGEABLE_ROW_CONSTRUCTORS = setOf("$CASE_PACKAGE.CaseRow")

        val LAYERS_THE_MINT_RULES_SCOPE_BY = listOf("com.housedash.app", "com.housedash.adapters")

        val INBOUND_PARSE_BOUNDARIES =
            listOf(
                "$CASE_PACKAGE.CaseId" to "$CASE_PACKAGE.CaseId\$Companion#of",
                "$CASE_PACKAGE.PhotoId" to "$CASE_PACKAGE.PhotoId\$Companion#of",
                "$CASE_PACKAGE.Description" to "$CASE_PACKAGE.Description\$Companion#of",
            )

        val CLOSED_CONSTRUCTION_BY_NAME =
            setOf(
                "$CASE_PACKAGE.Case\$Companion",
                "$CASE_PACKAGE.CaseError\$DescriptionNotPlainText",
                "$CASE_PACKAGE.CaseError\$NotOwner",
                "$CASE_PACKAGE.CaseId",
                "$CASE_PACKAGE.CaseId\$Companion",
                "$CASE_PACKAGE.CasePhotos",
                "$CASE_PACKAGE.CasePhotos\$Companion",
                "$CASE_PACKAGE.DescribedCase",
                "$CASE_PACKAGE.DescribedCase\$Companion",
                "$CASE_PACKAGE.Description",
                "$CASE_PACKAGE.Description\$Companion",
                "$CASE_PACKAGE.DraftCase",
                "$CASE_PACKAGE.DraftCase\$Companion",
                "$CASE_PACKAGE.PhotoId",
                "$CASE_PACKAGE.PhotoId\$Companion",
            )
    }
}
