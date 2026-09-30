plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.spring.boot)
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
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.jdbc)
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)
    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(kotlin("test"))
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.spring.boot.starter.test)
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
    extendsFrom(configurations.implementation.get())
}

configurations.getByName("integrationTestRuntimeOnly") {
    extendsFrom(configurations.testRuntimeOnly.get())
    extendsFrom(configurations.runtimeOnly.get())
}

dependencies {
    "integrationTestImplementation"(platform(libs.testcontainers.bom))
    "integrationTestImplementation"(libs.testcontainers.postgresql)
    "integrationTestImplementation"(libs.testcontainers.junit.jupiter)
    "integrationTestImplementation"("org.springframework.boot:spring-boot-testcontainers")
}

tasks.register<Test>("integrationTest") {
    description = "Runs tests that need real infrastructure."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}

tasks.jar {
    enabled = false
}

tasks.test {
    useJUnitPlatform()
    inputs.file(layout.projectDirectory.file("CODEOWNERS"))
    inputs.file(layout.projectDirectory.file("scripts/agent-review.sh"))
    inputs.dir(layout.projectDirectory.dir("src/main/kotlin/com/housedash/domain"))
    inputs.dir(layout.projectDirectory.dir("docs/adr"))
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
    val integrationSourceFiles = integrationSource.files
    inputs.files(integrationSource)
    outputs.file(layout.buildDirectory.file("reports/integration-source/verified.txt"))
    doLast {
        if (integrationSourceFiles.isEmpty()) {
            throw GradleException(
                "src/integrationTest has no source files; a NO-SOURCE integrationTest run must not pass as green",
            )
        }
        outputs.files.singleFile.apply {
            parentFile.mkdirs()
            writeText("integrationTest source set holds ${integrationSourceFiles.size} files\n")
        }
    }
}

tasks.register("verifyNoSuppressions") {
    group = "verification"
    val kotlinSource = files(sourceSets.flatMap { it.allSource.matching { include("**/*.kt") } })
    val forbidden =
        listOf(
            """@(?:[A-Za-z]+\s*:\s*)?(?:kotlin\s*\.\s*)?Suppress""",
            """import\s+kotlin\s*\.\s*Suppress""",
            """detekt-disable""",
            """ktlint-disable""",
        ).map { Regex(it) }
    inputs.files(kotlinSource)
    outputs.file(layout.buildDirectory.file("reports/suppressions/verified.txt"))
    doLast {
        val offences = mutableListOf<String>()
        for (file in kotlinSource.files) {
            val text = file.readText()
            for (pattern in forbidden) {
                for (hit in pattern.findAll(text)) {
                    val line = text.take(hit.range.first).count { it == '\n' } + 1
                    offences.add("${file.path}:$line: ${hit.value.replace("\n", " ")}")
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
