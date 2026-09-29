package com.housedash.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaCall
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaModifier
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

    private val mintTargetsByProducedType: Map<String, Set<String>> =
        domain
            .flatMap { it.methods }
            .filter { reachableFromOutside(it.modifiers) }
            .filter { JavaModifier.STATIC in it.modifiers || it.owner.simpleName == "Companion" }
            .filter { closedConstruction.test(it.rawReturnType) }
            .groupBy({ it.rawReturnType.name }, { "${it.owner.name}#${it.name}" })
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
            .filter { method -> method.rawParameterTypes.any { storedRow.test(it) } }
            .map { "${it.owner.name}#${it.name}" }
            .toSet()

    private val forgeableRowConstructors: Set<String> =
        domain
            .filter { storedRow.test(it) }
            .filter { row -> row.constructors.any { reachableFromOutside(it.modifiers) } }
            .map { it.name }
            .toSet()

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
    fun `a closed domain type is minted only inside its own package, and from a stored row only by the repository`() {
        classes()
            .should(
                MintBoundary(
                    "only mint a type whose construction its own bounded context closed from inside that package",
                    closedConstruction,
                    mintTargetsByProducedType.values.flatten().toSet(),
                ) { caller, owner -> caller.packageName == owner.packageName },
            ).`as`(
                "a type whose constructors a bounded context made private is minted by that package alone; " +
                    "every factory returning one is a mint route and is watched here, not only the constructor",
            ).check(codebase)
        classes()
            .should(
                MintBoundary(
                    "only reconstruct an aggregate from a stored row inside its own package or in the repository",
                    storedRow,
                    reconstructionEntryPoints,
                ) { caller, owner ->
                    caller.packageName == owner.packageName || caller.packageName.startsWith(persistencePackage)
                },
            ).`as`(
                "an aggregate is reconstructable by the repository and by nothing else; forging the row and " +
                    "calling the entry point are both the same capability and both belong to $persistencePackage",
            ).check(codebase)
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
}
