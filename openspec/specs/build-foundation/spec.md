# build-foundation Specification

## Purpose

The Gradle build that produces the published PMD rules artifact: module layout, the shared
`conventions` plugin, language level, the test and mutation-testing stacks, version derivation via
`release-please`, the CI workflows, and publishing to Maven Central.

## Requirements

### Requirement: Module layout
The build SHALL consist of exactly three Gradle projects: `buildSrc` (the convention plugin),
`dependencies` (a `java-platform` holding every external version), and `rules` (the published
artifact). No `bom` module SHALL be created — a single published artifact does not need a platform.

#### Scenario: Settings declare only the rules module
- **WHEN** `settings.gradle` is inspected
- **THEN** it includes `rules` and `dependencies` and nothing else

#### Scenario: All external versions live in the platform
- **WHEN** any module's `build.gradle` declares an external dependency
- **THEN** it declares no version, and the version is constrained by `project(':dependencies')`

### Requirement: Convention plugin
The convention plugin SHALL be named `conventions` (applied as `id 'conventions'`) and SHALL be
adapted from `jspecify`'s, retaining: Spotless with Palantir Java Format, PMD, CodeNarc, Error Prone
with NullAway in JSpecify mode, the `test` / `integrationTest` split by JUnit tag, Pitest, and the
publishing, signing and metadata wiring.

The convention plugin SHALL NOT compute a version itself: it SHALL neither invoke `git describe` nor
assign `project.version`. It SHALL instead apply `io.github.joke.conventional-version`, which does
(see Version derivation). The application SHALL be in the convention plugin's `plugins` block, so
that the version is assigned before the script body creates the `MavenPublication` that reads it.

The Spock and Groovy test configuration SHALL NOT be carried over (see Test stack).

#### Scenario: Plugin id is unprefixed
- **WHEN** a module applies the convention plugin
- **THEN** it uses `id 'conventions'`

#### Scenario: NullAway is configured as in jspecify
- **WHEN** the convention plugin's NullAway block is inspected
- **THEN** `jspecifyMode`, `onlyNullMarked`, `treatGeneratedAsUnannotated`, `checkContracts` and
  `acknowledgeRestrictiveAnnotations` are enabled and `RequireExplicitNullMarking` is an error

#### Scenario: The convention plugin does not compute the version
- **WHEN** the convention plugin is inspected
- **THEN** it contains no `git describe` invocation
- **AND** it does not assign `version`

#### Scenario: The convention plugin applies the versioning plugin
- **WHEN** the convention plugin's `plugins` block is inspected
- **THEN** it applies `io.github.joke.conventional-version` without a version

#### Scenario: Configuration cache survives a version query
- **WHEN** `./gradlew help` runs twice with `org.gradle.configuration-cache=true`
- **THEN** the second run reuses the configuration cache

### Requirement: CodeNarc is retained but inert
The convention plugin SHALL retain its CodeNarc configuration and the repository SHALL retain
`.codenarc.groovy`, even though no module applies the `groovy` plugin. The configuration is guarded
by `pluginManager.withPlugin('groovy')` and therefore never activates here; it is retained because
the convention plugin is destined for extraction and reuse by projects that do use Groovy.

#### Scenario: CodeNarc never runs in this repository
- **WHEN** `./gradlew check` runs
- **THEN** no `CodeNarc` task executes

#### Scenario: The configuration survives for extraction
- **WHEN** the convention plugin and the repository root are inspected
- **THEN** the CodeNarc block is present with all three violation thresholds at zero
- **AND** `.codenarc.groovy` exists

### Requirement: Language level and toolchain
Main source SHALL target Java 11 (`options.release = 11`). Test source SHALL NOT be constrained and
SHALL compile at the toolchain's own release level, because test code is never published and
constraining it forfeits text blocks, records and pattern matching in fixtures for no benefit.

The release level SHALL NOT be lowered to 8. At `--release 8` javac cannot resolve
`ElementType.MODULE` while reading JSpecify's `@NullMarked` — that constant is Java 9+ — and emits
`unknown enum constant ElementType.MODULE`, which no `-Xlint` category suppresses and which
`-Werror` turns into a build failure. Java 8 and NullAway are therefore mutually exclusive in this
build. NullAway is retained; the reach forfeited is limited to Maven and Ant builds running PMD on a
Java 8 JVM, since any Gradle 9 build already runs PMD on Java 17 or later. Analysing Java 8 *source*
is unaffected — that is a property of the consumer's PMD configuration, not of this artifact's
bytecode.

All Java compilation SHALL use `-Werror`, and no lint category SHALL be suppressed.

The build SHALL run on the JDK pinned in `.mise.toml`.

#### Scenario: Main source targets Java 11
- **WHEN** the `compileJava` task is configured
- **THEN** `options.release` is 11

#### Scenario: Test source is not constrained to the published release level
- **WHEN** the `compileTestJava` task is configured
- **THEN** `options.release` is not set

#### Scenario: Werror is retained with no lint suppressed
- **WHEN** the `compileJava` task's compiler arguments are inspected
- **THEN** they contain `-Werror`
- **AND** they contain no `-Xlint:-` suppression

#### Scenario: The main source set compiles with NullAway
- **WHEN** `./gradlew compileJava` runs on the pinned JDK against `@NullMarked` source
- **THEN** it succeeds and does not fail with `warnings found and -Werror specified`

### Requirement: Test stack
Tests SHALL be written as JUnit 5 tests in Java. The `rules` module SHALL NOT apply the `groovy`
plugin and SHALL NOT depend on Spock, Groovy or `SpockConfig.groovy`.

The `test` task SHALL run tests tagged `unit` and the `integrationTest` task SHALL run tests tagged
`integration`, and `check` SHALL depend on `integrationTest`.

#### Scenario: No Groovy test stack
- **WHEN** `rules/build.gradle` and the `dependencies` platform are inspected
- **THEN** neither declares Spock or Groovy
- **AND** no `SpockConfig.groovy` exists

#### Scenario: Tests are split by tag
- **WHEN** the `test` and `integrationTest` tasks are configured
- **THEN** `test` includes the `unit` tag and `integrationTest` includes the `integration` tag

### Requirement: Mutation testing thresholds
Pitest SHALL be configured with `mutationThreshold`, `coverageThreshold` and
`testStrengthThreshold` all at 100, and `check` SHALL depend on `pitest`. The
`-Dspock.parallel.disabled=true` JVM argument SHALL NOT be carried over, as no Spock tests exist.

#### Scenario: Check runs mutation testing
- **WHEN** `./gradlew check` runs
- **THEN** the `pitest` task executes and fails the build below any 100% threshold

#### Scenario: No Spock argument remains
- **WHEN** the Pitest configuration is inspected
- **THEN** its `jvmArgs` contain no `spock.parallel.disabled` property

### Requirement: Publishing
The `rules` module SHALL be published to Maven Central via the Central Portal, signed, with sources
and javadoc jars, at the coordinates `io.github.joke.pmd:rules`.

Release publishing SHALL run only on a commit for which `release-please` created a release, and
SHALL use `publishAggregationToCentralPortal`.

#### Scenario: Artifact is publishable
- **WHEN** the publication is configured
- **THEN** its group is `io.github.joke.pmd` and its artifactId is `rules`
- **AND** it carries a sources jar, a javadoc jar, license, developer and SCM metadata, and a
  signature

#### Scenario: An ordinary push to main publishes no release
- **WHEN** a commit is pushed to `main` and `release-please` does not cut a release
- **THEN** `publishAggregationToCentralPortal` does not run

### Requirement: Version derivation
The project version SHALL be calculated by the `io.github.joke.conventional-version` plugin, which
predicts the version `release-please` will cut next from the conventional commits since the released
version recorded in the manifest. The plugin SHALL be applied at project level by the `conventions`
plugin and SHALL be the only thing in the build that assigns a version. No workflow, task or script
SHALL pass a flag, property or stage to select between a release and a snapshot version.

Every project that applies `conventions` SHALL therefore be versioned. The root project applies no
plugin and SHALL keep Gradle's default `unspecified`; this is permitted because the root project has
no `build.gradle`, declares no publication, and nothing in the build or in nmcp reads its version.

The version policy SHALL live entirely in `release-please-config.json` and
`.release-please-manifest.json`, which the build reads and never writes. `release-please` SHALL
remain the sole author of tags, of the CHANGELOG and of the manifest; the build SHALL NOT create or
push tags.

#### Scenario: The release commit yields the release version
- **WHEN** `HEAD` is the commit `release-please` released as `1.2.3`
- **THEN** the project version is `1.2.3`
- **AND** it does not end in `-SNAPSHOT`

#### Scenario: A commit past a release yields the next planned version as a snapshot
- **WHEN** `HEAD` is past the released version `1.2.3` and a `feat:` commit is in the range
- **THEN** the project version is `1.3.0-SNAPSHOT`
- **AND** it does not contain the commit hash

#### Scenario: No stage or version property is set by the build
- **WHEN** `gradle.properties`, `settings.gradle` and both workflow files are inspected
- **THEN** no version, stage, scope or tag-prefix property is declared

#### Scenario: Every publishable module is versioned
- **WHEN** the version of `:rules` and of `:dependencies` is queried
- **THEN** neither is `unspecified`

#### Scenario: The root project is unversioned and unpublished
- **WHEN** the root project's version is queried
- **THEN** it is `unspecified`
- **AND** the root project declares no publication

#### Scenario: A missing release-please configuration fails the build
- **WHEN** `release-please-config.json` or `.release-please-manifest.json` is absent
- **THEN** the build fails rather than inventing a version

### Requirement: release-please manifest mode
`release-please` SHALL be configured in manifest mode: `release-please-config.json` SHALL declare a
single root package with `release-type: simple`, and `.release-please-manifest.json` SHALL record
the released version. The workflow SHALL NOT pass `release-type` inline, since the action reads both
files itself.

#### Scenario: Configuration lives in the repository
- **WHEN** the repository root is inspected
- **THEN** both `release-please-config.json` and `.release-please-manifest.json` exist

#### Scenario: The workflow carries no release configuration
- **WHEN** the `release-please` job in `release.yml` is inspected
- **THEN** the action step declares no `release-type` input

### Requirement: Snapshot publishing
Snapshots SHALL be published to the Central Portal snapshot repository via
`publishAggregationToCentralSnapshots`, on every push to `main` for which `release-please` did not
cut a release. Snapshot publishing SHALL NOT run for pull requests.

#### Scenario: A push to main publishes a snapshot
- **WHEN** a commit is pushed to `main` and `release-please` does not cut a release
- **THEN** `publishAggregationToCentralSnapshots` runs
- **AND** the published version ends in `-SNAPSHOT`

#### Scenario: A release commit publishes no snapshot
- **WHEN** `release-please` cuts a release on a push to `main`
- **THEN** `publishAggregationToCentralSnapshots` does not run
- **AND** only the release version is published

#### Scenario: Pull requests publish nothing
- **WHEN** a pull request is opened or updated
- **THEN** no publishing task runs

### Requirement: Publish signing
Both publishing jobs SHALL sign through the same mechanism. Each SHALL import the GPG key with
`crazy-max/ghaction-import-gpg`, reading `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE` and `GPG_FINGERPRINT`,
and the build SHALL sign every publication it produces. Signing SHALL be required in both, which is
Gradle's default; the build SHALL NOT declare a `required` predicate that varies signing by task
graph.

The purpose is that snapshot publishing rehearses release publishing, so a signing fault surfaces on
a snapshot that can simply be republished rather than on a Central release that cannot be withdrawn.

#### Scenario: Release publishing signs
- **WHEN** the `publish` job runs `publishAggregationToCentralPortal`
- **THEN** it has imported the GPG key beforehand
- **AND** the published artifacts carry `.asc` signatures

#### Scenario: Snapshot publishing signs
- **WHEN** the `snapshot` job runs `publishAggregationToCentralSnapshots`
- **THEN** it has imported the GPG key beforehand
- **AND** the published artifacts carry `.asc` signatures

#### Scenario: No signing predicate varies by task graph
- **WHEN** the `signing` block in the convention plugin is inspected
- **THEN** it declares no `required` predicate
- **AND** it does not inspect `gradle.taskGraph`

### Requirement: Root project repository declaration
The root project SHALL have repositories available even though it applies no build script, because
the nmcp aggregation tasks resolve their own dependencies into a root-project configuration. This
SHALL be declared once via `dependencyResolutionManagement` in `settings.gradle`.

#### Scenario: The aggregation tasks configure
- **WHEN** `publishAggregationToCentralPortal` or `publishAggregationToCentralSnapshots` is
  configured
- **THEN** the build does not fail with "no repositories are defined"

### Requirement: Plugin version declaration
Every Gradle project plugin version SHALL be declared exactly once, as a dependency in
`buildSrc/build.gradle`. `settings.gradle` SHALL NOT contain a `pluginManagement` block. A plugin
applied by the convention plugin rather than by a module SHALL be declared as its plugin-marker
coordinate, `<id>:<id>.gradle.plugin:<version>`.

Settings plugins are exempt and SHALL be declared with their versions in the `settings.gradle`
`plugins` block. `com.gradleup.nmcp.settings` SHALL be the only such plugin, because it is the only
one with no project-level equivalent. `settings.gradle` SHALL otherwise contain only the
`nmcpSettings` configuration, the `dependencyResolutionManagement` repositories, `rootProject.name`
and the project includes.

#### Scenario: No project plugin versions live in settings
- **WHEN** `settings.gradle` is inspected
- **THEN** it declares no `pluginManagement` block
- **AND** it applies only settings plugins

#### Scenario: Only nmcp is exempt
- **WHEN** the `plugins` block of `settings.gradle` is inspected
- **THEN** `com.gradleup.nmcp.settings` is the only plugin it declares

#### Scenario: No plugin version is declared twice
- **WHEN** `settings.gradle` and `buildSrc/build.gradle` are compared
- **THEN** no plugin appears in both with a version

#### Scenario: Pitest is applied by the convention plugin
- **WHEN** a module applying the convention plugin has the `java` plugin
- **THEN** Pitest is applied to it without the module declaring it
- **AND** it is not applied to the `java-platform` module

### Requirement: Version-resolving jobs use full git history
Every CI job that resolves the project version SHALL check out with `fetch-depth: 0`, because the
version is computed from the commits since the released version and the plugin fails the build on a
shallow clone rather than guessing.

#### Scenario: All checkouts are unshallow
- **WHEN** the workflow files are inspected
- **THEN** every `actions/checkout` step that is followed by a Gradle invocation sets
  `fetch-depth: 0`

### Requirement: Repository configuration
`.github/settings.yml` SHALL be authored for this repository rather than copied from another
project. It SHALL name the repository `pmd-rules`, describe it as a set of custom PMD 7 rules, and
declare branch protection on `main` requiring the `build` check.

#### Scenario: Settings name this repository
- **WHEN** `.github/settings.yml` is inspected
- **THEN** its `repository.name` is `pmd-rules`
- **AND** it names no other project

#### Scenario: Branch protection requires the build check
- **WHEN** the `branches` section is inspected
- **THEN** `main` requires the `build` status check and a linear history

### Requirement: No documentation site
The build SHALL define no `antora` task and the workflows SHALL invoke none. No workflow SHALL
declare the `pages: write` or `id-token: write` permission, or a `github-pages` environment, and the
project metadata SHALL NOT advertise a GitHub Pages URL.

#### Scenario: No workflow invokes antora
- **WHEN** the workflow files are inspected
- **THEN** no step runs `./gradlew antora`
- **AND** no `docs` or `deploy` job is defined

#### Scenario: Metadata advertises no site
- **WHEN** the convention plugin's `metadata` block is inspected
- **THEN** its `github` configuration does not call `pages()`

### Requirement: The ruleset is a tracked build input
The convention plugin SHALL declare the repository ruleset with `ruleSetFiles`, a `FileCollection`
Gradle tracks as a file input, and SHALL NOT declare it with `ruleSets`, a `List<String>` Gradle
cannot track. `ruleSets` SHALL be cleared, because it otherwise retains Gradle's default of
`category/java/errorprone.xml`.

Without this the PMD tasks stay `UP-TO-DATE` after `.pmd.xml` changes and keep analysing against the
previous ruleset, which makes dogfooding untrustworthy: enabling or disabling a rule appears to have
no effect.

#### Scenario: Editing the ruleset re-runs analysis
- **WHEN** the content of `.pmd.xml` changes
- **THEN** `pmdMain` and `pmdTest` re-run rather than reporting `UP-TO-DATE`

#### Scenario: Gradle's default ruleset is not silently added
- **WHEN** the convention plugin's `pmd` block is inspected
- **THEN** `ruleSets` is empty
- **AND** `ruleSetFiles` names `.pmd.xml`

### Requirement: The build runs the rules it publishes
The `rules` module SHALL put `project(':rules')` on its `pmd` configuration, so that PMD analyses
this project's own source with the artifact this project builds. `.pmd.xml` SHALL reference
`rulesets/java/joke.xml` alongside the PMD stock categories it already composes, which makes the
repository's own configuration a worked example of the wiring the README documents for consumers.

The dependency SHALL reach the `pmd` configuration by inheritance — declared on a separate
configuration that `pmd` extends — and SHALL NOT be declared on `pmd` directly. Gradle's `PmdPlugin`
supplies PMD itself through `defaultDependencies`, which applies only while the configuration's own
dependency set is empty; declaring anything on `pmd` directly therefore replaces the tool rather than
joining it, and re-declaring PMD by hand would hardcode a set of artifacts that varies by Gradle
version. Inheritance leaves the dependency in `allDependencies` but out of `dependencies`, so the
default still fires.

The rules SHALL apply to both the `pmdMain` and `pmdTest` source sets.

`project(':rules')` is preferred over `files(sourceSets.main.output)` because it puts the packaged
artifact on the analysis classpath — the shape a consumer actually receives — and can therefore catch
a packaging fault such as a resource missing from the jar.

#### Scenario: The pmd configuration carries the project's own artifact
- **WHEN** `rules/build.gradle` is inspected
- **THEN** `project(':rules')` is declared on a configuration that `pmd` extends from
- **AND** nothing is declared on the `pmd` configuration directly

#### Scenario: The PMD tool is still supplied by Gradle
- **WHEN** `pmdMain` runs
- **THEN** it does not fail with `ClassNotFoundException` for a PMD class
- **AND** no PMD tool coordinate is named outside the convention plugin's `toolVersion`

#### Scenario: The repository's ruleset references the shipped ruleset
- **WHEN** `.pmd.xml` is inspected
- **THEN** it references `rulesets/java/joke.xml`
- **AND** it contains no comment claiming the rules are inapplicable because the source targets
  Java 8

#### Scenario: Both source sets are analysed
- **WHEN** `./gradlew check` runs
- **THEN** `pmdMain` and `pmdTest` both apply the rules from `rulesets/java/joke.xml`

#### Scenario: A violation in this repository fails the build
- **WHEN** source is added to this repository that violates a rule this project publishes
- **THEN** `./gradlew check` fails

#### Scenario: The repository is green when the wiring lands
- **WHEN** the wiring is first enabled
- **THEN** `./gradlew check` passes without any source change, because the repository already
  complies

### Requirement: The recovery path is documented
`README.md` SHALL document, in its build section, how to build past a rule that is breaking the
build: excluding `pmdMain` and `pmdTest`.

This is needed because the rules are analysed by the module that builds them, so a rule that throws
during analysis fails `pmdMain` and therefore `check`, and the repair is to edit the rule that is
currently breaking the build. Someone hitting that is mid-incident and will not work the escape
hatch out for themselves.

#### Scenario: The README states how to build past a broken rule
- **WHEN** the build section of `README.md` is inspected
- **THEN** it documents excluding `pmdMain` and `pmdTest` to build while a rule is broken
- **AND** it explains that the rules are applied by the module that builds them
