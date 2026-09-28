plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    jacoco
}

group = "com.housedash"
version = "0.1.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
    }
}

configurations.getByName("integrationTestImplementation") {
    extendsFrom(configurations.testImplementation.get())
}

configurations.getByName("integrationTestRuntimeOnly") {
    extendsFrom(configurations.testRuntimeOnly.get())
}

tasks.register<Test>("integrationTest") {
    description = "Runs tests that need real infrastructure."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    basePath = rootDir.absolutePath
    source.setFrom(
        files(
            "src/main/kotlin",
            "src/test/kotlin",
            "src/integrationTest/kotlin",
        ),
    )
}

jacoco {
    toolVersion = "0.8.14"
}

val coverageData = fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") }

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    mustRunAfter(tasks.named("integrationTest"))
    executionData.setFrom(coverageData)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    dependsOn(tasks.named("integrationTest"))
    executionData.setFrom(coverageData)
    violationRules {
        rule {
            element = "PACKAGE"
            includes = listOf("com.housedash.domain", "com.housedash.domain.*")
            limit {
                counter = "LINE"
                minimum = "0.90".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "LINE"
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}

tasks.register("verifyIntegrationTestSourceSetNotEmpty") {
    group = "verification"
    val integrationSource = sourceSets["integrationTest"].allSource
    inputs.files(integrationSource)
    doLast {
        if (integrationSource.isEmpty) {
            throw GradleException(
                "src/integrationTest has no source files; a NO-SOURCE integrationTest run must not pass as green",
            )
        }
    }
}

tasks.check {
    dependsOn(tasks.named("integrationTest"))
    dependsOn(tasks.jacocoTestCoverageVerification)
    dependsOn(tasks.named("verifyIntegrationTestSourceSetNotEmpty"))
}
