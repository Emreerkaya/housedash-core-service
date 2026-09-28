package com.housedash.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaCall
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.properties.HasName
import com.tngtech.archunit.core.domain.properties.HasOwner
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import org.junit.jupiter.api.Test

class LayeringTest {
    private val domain =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("com.housedash.domain")

    private val floatingPointType =
        HasName.Predicates.nameMatching("^(java\\.lang\\.)?(Double|Float|double|float)$|^\\[+[DF]$")

    private val anyFloatingPointParameter =
        DescribedPredicate.describe<List<JavaClass>>("any parameter is floating point") { parameters ->
            parameters.any { floatingPointType.test(it) }
        }

    private fun callTo(
        methodName: String,
        ownerPackage: String,
    ) = JavaCall.Predicates
        .target(HasOwner.Predicates.With.owner(JavaClass.Predicates.resideInAPackage(ownerPackage)))
        .and(JavaCall.Predicates.target(HasName.Predicates.name(methodName)))

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
            ).check(domain)
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
}
