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
    inputs.file(layout.projectDirectory.file("CODEOWNERS"))
    inputs.file(layout.projectDirectory.file("scripts/agent-review.sh"))
    inputs.dir(layout.projectDirectory.dir("src/main/kotlin/com/housedash/domain"))
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
    outputs.file(layout.buildDirectory.file("reports/integration-source/verified.txt"))
    doLast {
        if (integrationSource.isEmpty) {
            throw GradleException(
                "src/integrationTest has no source files; a NO-SOURCE integrationTest run must not pass as green",
            )
        }
        outputs.files.singleFile.apply {
            parentFile.mkdirs()
            writeText("integrationTest source set holds ${integrationSource.files.size} files\n")
        }
    }
}

tasks.register("verifyNoSuppressions") {
    group = "verification"
    val kotlinSource = files(sourceSets.flatMap { it.allSource.matching { include("**/*.kt") } })
    val forbidden = listOf("@Suppress", "@file:Suppress", "detekt-disable", "ktlint-disable")
    inputs.files(kotlinSource)
    outputs.file(layout.buildDirectory.file("reports/suppressions/verified.txt"))
    doLast {
        val offences = mutableListOf<String>()
        for (file in kotlinSource.files) {
            for ((index, line) in file.readLines().withIndex()) {
                if (forbidden.any { line.contains(it) }) {
                    offences.add("${file.path}:${index + 1}: ${line.trim()}")
                }
            }
        }
        if (offences.isNotEmpty()) {
            throw GradleException(
                "suppressions are not used in this codebase; if a rule objects, the code changes:\n" +
                    offences.joinToString("\n"),
            )
        }
        outputs.files.singleFile.apply {
            parentFile.mkdirs()
            writeText("no suppressions in ${kotlinSource.files.size} kotlin files\n")
        }
    }
}

tasks.check {
    dependsOn(tasks.named("integrationTest"))
    dependsOn(tasks.jacocoTestCoverageVerification)
    dependsOn(tasks.named("verifyIntegrationTestSourceSetNotEmpty"))
    dependsOn(tasks.named("verifyNoSuppressions"))
    dependsOn(tasks.named("detektMain"))
    dependsOn(tasks.named("detektTest"))
    dependsOn(tasks.named("detektIntegrationTest"))
}
