package com.housedash.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaCall
import com.tngtech.archunit.core.domain.JavaClass
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

    private val aggregateVariant =
        DescribedPredicate.describe<JavaClass>(
            "directly implements an interface declared in its own bounded-context package",
        ) { candidate ->
            candidate.packageName.startsWith("com.housedash.domain.") &&
                !isInSharedKernel(candidate) &&
                candidate.rawInterfaces.any { it.packageName == candidate.packageName }
        }

    private fun isInSharedKernel(clazz: JavaClass): Boolean =
        clazz.packageName == sharedKernelPackage || clazz.packageName.startsWith("$sharedKernelPackage.")

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

    private val constructsAggregateVariantFromOutsideItsPackage =
        object : ArchCondition<JavaClass>(
            "only construct an aggregate variant from within its own bounded-context package",
        ) {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.constructorCallsFromSelf.forEach { call ->
                    val target = call.targetOwner
                    if (aggregateVariant.test(target) && target.packageName != item.packageName) {
                        events.add(
                            SimpleConditionEvent.violated(
                                call,
                                "${call.description} constructs ${target.name}, " +
                                    "an aggregate variant owned by package ${target.packageName}",
                            ),
                        )
                    }
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
    fun `only an aggregate's own package may construct its variants`() {
        classes()
            .should(constructsAggregateVariantFromOutsideItsPackage)
            .check(codebase)
    }

    @Test
    fun `the aggregate-variant predicate matches at least one class`() {
        val matches = domain.filter { aggregateVariant.test(it) }
        assertTrue(matches.isNotEmpty()) {
            "no domain class matched the aggregate-variant predicate; " +
                "the construction-boundary rule above would be vacuous"
        }
    }
}
