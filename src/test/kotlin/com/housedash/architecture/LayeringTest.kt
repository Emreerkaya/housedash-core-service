package com.housedash.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class LayeringTest {
    private val domain = ClassFileImporter().importPackages("com.housedash.domain")

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
    fun `domain never reads the clock`() {
        noClasses().should().callMethod(java.time.Instant::class.java, "now").check(domain)
        noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.time.Clock")
            .check(domain)
    }

    @Test
    fun `domain never generates its own identifiers`() {
        noClasses().should().callMethod(java.util.UUID::class.java, "randomUUID").check(domain)
    }

    @Test
    fun `domain holds no floating point`() {
        noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.lang.Double")
            .check(domain)
        noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.lang.Float")
            .check(domain)
    }
}
