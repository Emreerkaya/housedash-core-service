# 4. The Gradle wrapper is pinned to 9.x

## Status

Accepted, 2026-09-28.

## Context

The wrapper on this repository is Gradle 9.8.0. Running the build with `-Dorg.gradle.deprecation.trace=true --warning-mode all` shows exactly two deprecation warnings, and both originate from the same plugin, detekt 1.23.8, which is this build's only linter for Kotlin idioms and the sole enforcement point for the no-comment rule (D155). Nothing else registered in `build.gradle.kts` or `gradle/libs.versions.toml` emits a deprecation warning of any kind.

Two deprecated methods, three call sites. `javap -c -p` over `DetektPlugin.class` in `detekt-gradle-plugin-1.23.8.jar` shows one `ReportingExtension.file` invocation, in `apply`, and **two** `Configuration.setVisible` invocations: one in `configurePluginDependencies` and one in `configurePluginDependencies$lambda$14`, the lambda that method registers. Two warnings and three call sites, because Gradle reports a deprecated method once however many times it is called. The line numbers below are the ones the trace prints; the count above is what the bytecode holds, and where the two disagree the bytecode is the thing that can be re-checked.

The first warning: "The ReportingExtension.file(String) method has been deprecated. This is scheduled to be removed in Gradle 10. Please use the getBaseDirectory().file(String) or getBaseDirectory().dir(String) method instead.", raised at `org.gradle.api.reporting.ReportingExtension.file(ReportingExtension.java:102)`, called from `io.gitlab.arturbosch.detekt.DetektPlugin.apply(DetektPlugin.kt:28)`.

The second warning: "The Configuration.setVisible(boolean) method has been deprecated. This is scheduled to be removed in Gradle 11.", raised at `org.gradle.api.internal.artifacts.configurations.DefaultConfiguration.setVisible(DefaultConfiguration.java:376)`, called from `io.gitlab.arturbosch.detekt.DetektPlugin.configurePluginDependencies(DetektPlugin.kt:99)`, itself called from `DetektPlugin.apply(DetektPlugin.kt:36)`. This is the first of that method's two call sites; the second is inside the configuration action it registers.

Both calls sit inside the plugin's own `apply` function, which runs the moment `alias(libs.plugins.detekt)` is applied, before any project-specific configuration executes. There is no configuration flag, extension property, or alternative entry point in detekt 1.23.8 that avoids either call; the only way to stop them firing is to not apply the plugin. 1.23.8 is detekt's latest published release, so there is no newer version to move to that fixes this.

## Decision

The Gradle wrapper stays on the 9.x line, specifically 9.8.0, for as long as detekt is in this build. Gradle 10 removes `ReportingExtension.file(String)` outright, so upgrading the wrapper while keeping detekt 1.23.8 applied would fail the build at configuration time on every invocation, which fails `just lint`, which fails `check`, which fails both `process-mr` and `process-merge`. detekt is not optional here: it is the only tool that reads Kotlin idioms and complexity, and its `ForbiddenComment` rule is the only enforcement of D155's no-comment rule. Dropping it to unblock a wrapper bump would remove more than it would gain.

The wrapper is revisited only when detekt ships a release built against a Gradle version that does not call either deprecated method, or when Gradle publishes a compatibility shim that keeps both methods working past the stated removal version. A routine `gradle wrapper --gradle-version <latest>` is not run against this repository without first checking that detekt's release notes say the plugin no longer touches `ReportingExtension.file` or `Configuration.setVisible`.

## Consequences

This repository will lag the newest Gradle release for as long as detekt does not update, which may be a long time given detekt's own release cadence has already left it a full major version behind Gradle. That lag is accepted in exchange for keeping the lint gate intact.

A secondary risk travels with the same pin: detekt 1.23.x analyses Kotlin source with an embedded Kotlin 1.9 frontend, while the Kotlin Gradle plugin in this build is 2.4.20. Kotlin syntax introduced after 1.9 can fail to parse in detekt rather than fail a specific rule, which reads as a tooling crash rather than a lint finding. That risk is independent of the Gradle version and is not solved by this decision; it is recorded here because it shares a root cause, an unmaintained-relative-to-its-dependents plugin, with the deprecation this ADR is about.

Any future change that bumps the wrapper must re-run the same trace command used here and confirm zero deprecations before merging, not assume detekt has been fixed because a release number went up.

## Alternatives considered

Bumping the wrapper anyway and accepting the deprecation warnings as noise was rejected. `ReportingExtension.file(String)` is not merely deprecated in Gradle 10, it is removed, so this is not a warning to tolerate but a build that will not configure.

Removing detekt and relying on ktlint and SonarCloud alone was rejected on the same grounds ADR-0002 rejected it: ktlint formats, it does not judge complexity or idiom, and SonarCloud's free tier does not read Kotlin as well as detekt does.

Forking or patching detekt's Gradle plugin locally to avoid the two calls was rejected as disproportionate: it would make this repository responsible for maintaining a patched copy of a third-party plugin for a problem that resolves itself the day detekt publishes a compatible release.
