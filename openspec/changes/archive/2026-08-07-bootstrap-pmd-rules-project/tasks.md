## 1. Repository scaffold

- [x] 1.1 Copy the Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) from `jspecify` unchanged
- [x] 1.2 Copy `gradle.properties` from `jspecify` unchanged, keeping the pitest isolated-projects comment
- [x] 1.3 Copy `.gitignore`, `.pre-commit-config.yaml` and `.mise.toml` from `jspecify`; drop the openspec npm pin only if a newer version is in use
- [x] 1.4 Copy `LICENSE` from `jspecify`
- [x] 1.5 Write `settings.gradle`: nmcp settings plugin, `dependencyResolutionManagement` repositories, `rootProject.name = 'pmd-rules'`, `include 'dependencies'`, `include 'rules'`
- [x] 1.6 Write `release-please-config.json` (single root package, `release-type: simple`) and `.release-please-manifest.json` seeded at `0.0.0`
- [x] 1.7 Fill `openspec/config.yaml` with this project's context: PMD 7 rules, Java 8 main target on Liberica 25, JUnit 5 + pmd-test, conventional commits, `./gradlew check` as the gate

## 2. Convention plugin

- [x] 2.1 Copy `buildSrc/build.gradle` from `jspecify`, dropping nothing yet — all plugin versions stay declared here
- [x] 2.2 Copy `buildSrc/src/main/groovy/conventions.gradle` from `jspecify` as the starting point
- [x] 2.3 Change `group` to `io.github.joke.pmd`
- [x] 2.4 Set `options.release = 11` on the main compile task only; leave `compileTestJava` at the toolchain level
- [x] 2.5 Keep `-Werror` with no lint suppression (Java 8 abandoned — see design.md; it is incompatible with NullAway)
- [x] 2.6 Remove the `testRuntimeOnly` Mockito wiring and the `-Dspock.parallel.disabled=true` pitest JVM arg
- [x] 2.7 Keep the CodeNarc block and copy `.codenarc.groovy` from `jspecify` unchanged, knowing both are inert here
- [x] 2.8 Update the `metadata` block: description, `github { org = 'joke'; repo = rootProject.name; issues() }`, no `pages()`
- [x] 2.9 Copy `.pmd.xml` from `jspecify` for this repository's own build, removing the inline `UseVarForLocalVariables` XPath rule
- [x] 2.10 Verify `./gradlew help` runs twice and reuses the configuration cache

## 3. Dependency platform

- [x] 3.1 Create `dependencies/build.gradle` applying `java-platform` and `conventions` with `allowDependencies()`
- [x] 3.2 Constrain `net.sourceforge.pmd:pmd-core` and `net.sourceforge.pmd:pmd-java` at 7.0.0
- [x] 3.3 Constrain `net.sourceforge.pmd:pmd-test`, the JUnit 5 BOM and AssertJ for tests
- [x] 3.4 Remove the Groovy, Spock, compile-testing, AutoService, JSpecify and Mockito constraints carried over from `jspecify`

## 4. Rules module skeleton

- [x] 4.1 Create `rules/build.gradle` applying `java`, `maven-publish` and `conventions` — no `groovy` plugin
- [x] 4.2 Declare `pmd-core` and `pmd-java` as `compileOnly` against the platform
- [x] 4.3 Declare `pmd-test`, JUnit 5 and AssertJ as `testImplementation` against the platform
- [x] 4.4 Port `verifyPomHasNoDependencies` from `jspecify`'s `processor/build.gradle` and wire it into `check`
- [x] 4.5 Add `package-info.java` with `@NullMarked` for the rules package
- [x] 4.6 Confirm `./gradlew :rules:build` succeeds on an empty source set

## 5. Seed rule: UseVarForLocalVariables

- [x] 5.1 Implement the rule class against the PMD 7 Java AST, reporting explicitly typed local variable declarations
- [x] 5.2 Exclude declarations with no initializer, with a `null` initializer, and with array-initializer shorthand
- [x] 5.3 Exclude declarations initialized with a lambda or a method reference
- [x] 5.4 Exclude multi-variable declarations
- [x] 5.5 Confirm fields, method and constructor parameters, catch parameters and lambda parameters are never visited
- [x] 5.6 Handle enhanced and basic for-loop variables as reportable
- [x] 5.7 Verify no `@InternalApi` PMD type or member is referenced

## 6. Rule resources

- [x] 6.1 Create `rules/src/main/resources/category/java/joke.xml` declaring `UseVarForLocalVariables` with `class`, `message`, `<description>`, `<priority>` and both a violating and a compliant `<example>`
- [x] 6.2 Set `minimumLanguageVersion="10"` on the rule
- [x] 6.3 Create `rules/src/main/resources/rulesets/java/joke.xml` referencing `category/java/joke.xml` and nothing else
- [x] 6.4 Confirm neither resource references a PMD stock category or contains an `<exclude>` element

## 7. Unit tests

- [x] 7.1 Add a `pmd-test` `RuleTst` subclass for `UseVarForLocalVariables`, tagged `@Tag("unit")`
- [x] 7.2 Write the XML test descriptor with a case for each reported scenario in the spec
- [x] 7.3 Write a case for each non-reported scenario: no initializer, `null`, array shorthand, lambda, method reference, multi-variable
- [x] 7.4 Write cases for field, method parameter, catch parameter and lambda parameter
- [x] 7.5 Write cases proving the rule is skipped below Java 10 and applies from Java 10
- [x] 7.6 Run `./gradlew :rules:pitest` and add fixtures until mutation, coverage and test strength all reach 100

## 8. Cross-version integration tests

- [x] 8.1 Write a `@Tag("integration")` harness driving `PMDConfiguration`, `RuleSetLoader`, `PmdAnalysis` and `RuleViolation` only — no `pmd-test`
- [x] 8.2 Load the ruleset by the classpath reference `rulesets/java/joke.xml`, exactly as a consumer does
- [x] 8.3 Assert the loaded rule set contains every rule declared in the category file
- [x] 8.4 Assert a violating fixture produces a violation and a compliant fixture produces none
- [x] 8.5 In `rules/build.gradle` (not the convention plugin, which is destined for reuse by non-PMD projects), register one `integrationTest` task per supported PMD version, each with its own configuration resolving `pmd-core` and `pmd-java` at that version plus the built rules jar
- [x] 8.6 Include at least 7.0.0 and the newest supported PMD version in the matrix
- [x] 8.7 Make `check` depend on every registered integration test task
- [x] 8.8 Confirm the matrix runs under `./gradlew check` locally

## 9. CI and repository configuration

- [x] 9.1 Copy `.github/workflows/build.yml` from `jspecify` unchanged
- [x] 9.2 Copy `.github/workflows/release.yml` from `jspecify` unchanged, keeping both the `publish` and `snapshot` jobs and their GPG import steps
- [x] 9.3 Copy `.github/dependabot.yml` and `.github/autoapproval.yml` unchanged
- [x] 9.4 Author `.github/settings.yml` fresh for `pmd-rules`: name, description, topics, merge settings, and `main` branch protection requiring the `build` check and a linear history
- [x] 9.5 Confirm no workflow declares `pages: write` or `id-token: write`, and no `antora` task exists
- [x] 9.6 Confirm every `actions/checkout` preceding a Gradle invocation sets `fetch-depth: 0`

## 10. Documentation

- [x] 10.1 Write `README.md` showing the two-line consumer wiring: the artifact on the `pmd` configuration and `rulesets/java/joke.xml` referenced from the `pmd` extension
- [x] 10.2 State the supported PMD version range and that the published artifact carries no dependencies
- [x] 10.3 Document `UseVarForLocalVariables`: what it reports and the cases it deliberately does not

## 11. Verification

- [x] 11.1 Confirm the generated POM declares no dependency and that `verifyPomHasNoDependencies` fails when one is deliberately added
- [x] 11.2 Confirm `:rules` and `:dependencies` resolve a non-`unspecified` version and the root project stays `unspecified`
- [x] 11.3 Run `./gradlew check` to verify everything. NEVER continue if there are violations
- [x] 11.4 Commit the completed change with `/commit-commands:commit`
