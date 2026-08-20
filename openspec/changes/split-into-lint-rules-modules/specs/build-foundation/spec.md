## MODIFIED Requirements

### Requirement: Module layout
The build SHALL consist of exactly four Gradle projects: `buildSrc` (the convention plugin),
`dependencies` (a `java-platform` holding every external version), `pmd-rules` and `codenarc-rules`
(the two published artifacts). No `bom` module SHALL be created — two published artifacts that are
never consumed together do not need a platform.

Each published module's Gradle project name SHALL equal its artifactId, so that no publication
declares an `artifactId` that diverges from the project it is built from and
`metadata { readableName = project.name }` stays correct.

`dependencies` SHALL remain unpublished: it applies `java-platform` and `conventions` but not
`maven-publish`, so the convention plugin's publishing wiring never fires for it.

#### Scenario: Settings declare the four projects
- **WHEN** `settings.gradle` is inspected
- **THEN** it includes `dependencies`, `pmd-rules` and `codenarc-rules` and nothing else

#### Scenario: Project names match artifactIds
- **WHEN** the publications of `:pmd-rules` and `:codenarc-rules` are inspected
- **THEN** neither overrides `artifactId`
- **AND** each artifactId equals its Gradle project name

#### Scenario: All external versions live in the platform
- **WHEN** any module's `build.gradle` declares an external dependency
- **THEN** it declares no version, and the version is constrained by `project(':dependencies')`

#### Scenario: The platform is not published
- **WHEN** the publishing tasks are inspected
- **THEN** `:dependencies` produces no publication

### Requirement: Convention plugin
The convention plugin SHALL be named `conventions` (applied as `id 'conventions'`) and SHALL be
adapted from `jspecify`'s, retaining: Spotless with Palantir Java Format, PMD, CodeNarc, Error Prone
with NullAway in JSpecify mode, the `test` / `integrationTest` split by JUnit tag, Pitest, and the
publishing, signing and metadata wiring.

The convention plugin SHALL NOT compute a version itself: it SHALL neither invoke `git describe` nor
assign `project.version`. It SHALL instead apply `io.github.joke.conventional-version`, which does
(see Version derivation). The application SHALL be in the convention plugin's `plugins` block, so
that the version is assigned before the script body creates the `MavenPublication` that reads it.

The plugin SHALL set `group` to `io.github.joke.lint` as a constant, since both published modules
share it.

The plugin SHALL NOT set the artifact `description`. A description is per-artifact, and a plugin
serving two artifacts cannot state one that is true of both. Each module SHALL declare its own.

#### Scenario: Plugin id is unprefixed
- **WHEN** a module applies the convention plugin
- **THEN** it does so with `id 'conventions'`

#### Scenario: The group is set once
- **WHEN** the convention plugin is inspected
- **THEN** it assigns `group = 'io.github.joke.lint'`
- **AND** neither module's `build.gradle` assigns a group

#### Scenario: The description is per-module
- **WHEN** the convention plugin's metadata block is inspected
- **THEN** it assigns no `description`
- **AND** `pmd-rules/build.gradle` and `codenarc-rules/build.gradle` each assign their own

#### Scenario: The plugin assigns no version
- **WHEN** the convention plugin is inspected
- **THEN** it contains no `git describe` invocation and no assignment to `project.version`

### Requirement: Test stack
Tests SHALL be written as JUnit 5 tests in Java in the `pmd-rules` module, and as Spock
specifications in Groovy in the `codenarc-rules` module. The `pmd-rules` module SHALL NOT apply the
`groovy` plugin and SHALL NOT depend on Spock or Groovy.

The `codenarc-rules` module SHALL apply the `groovy` plugin, with Java rule classes under
`src/main/java` and Spock specifications under `src/test/groovy`. Spock and Groovy SHALL be declared
on test configurations only. Spock is readmitted for exactly one reason: the specifications are the
Groovy corpus that module's own published rules analyse, which is what makes the CodeNarc artifact
dogfooded rather than merely unit-tested.

Every Spock specification SHALL carry a `@spock.lang.Tag`, either `unit` or `integration`, because
the `test` and `integrationTest` tasks select on it and an untagged specification runs in neither.
Rule specifications SHALL be tagged `unit`, which is also what Pitest's `includedGroups` selects.

The `dependencies` platform SHALL declare the JUnit 5 BOM, not JUnit 6. `pmd-test` at the 7.0.0 floor
is built against JUnit 5.8.2, and that is the harness the rule tests run inside. Moving to JUnit 6 is
a change of its own, to be taken deliberately with the `pmd-test` interaction examined, and SHALL NOT
happen as a side effect of a routine dependency bump.

The `pmd-rules` module SHALL depend on Mockito for unit tests, so that a method under test can be
isolated from the sibling methods it calls by spying the subject and stubbing those siblings. This is
the test style the rules in `static-method-state-rule`, `method-visibility-rule` and
`visible-for-testing-rule` exist to keep possible, and without it those rules would create seams
nothing uses.

Mockito rather than Spock in `pmd-rules`, because Spock there would mean adding Groovy to a module
that has no use for it — PMD does not analyse Groovy, so it would buy no dogfooding — and would
forfeit the `pmd-test` harness the XML fixture requirement depends on.

`pmd-test` XML fixtures SHALL remain the end-to-end check of PMD rule behaviour. Mockito SHALL NOT
replace them; it covers the branches fixtures cannot reach, such as a rule's `visit` method
delegating to a helper.

The `test` task SHALL run tests tagged `unit` and the `integrationTest` task SHALL run tests tagged
`integration`, and `check` SHALL depend on `integrationTest`. The `integrationTest` task SHALL use
the test source set's own runtime classpath, and therefore SHALL resolve PMD at the floor version the
`dependencies` platform declares.

The `pmd-rules` module SHALL declare a `compileOnly` dependency providing the `@VisibleForTesting`
annotation. It SHALL NOT be `api` or `implementation`: the published POM declares no dependencies,
and the annotation has no runtime retention requirement for consumers.

#### Scenario: The PMD module has no Groovy test stack
- **WHEN** `pmd-rules/build.gradle` is inspected
- **THEN** it declares neither Spock nor Groovy
- **AND** it does not apply the `groovy` plugin

#### Scenario: The CodeNarc module tests in Spock
- **WHEN** `codenarc-rules/build.gradle` is inspected
- **THEN** it applies the `groovy` plugin
- **AND** Spock and Groovy are declared on test configurations only

#### Scenario: Specifications are tagged
- **WHEN** a Spock specification is inspected
- **THEN** it carries `@spock.lang.Tag` naming either `unit` or `integration`
- **AND** every rule specification is tagged `unit`

#### Scenario: The JUnit line stays at 5
- **WHEN** the `dependencies` platform is inspected
- **THEN** the JUnit BOM it declares is a 5.x release
- **AND** the reason — `pmd-test` at the floor being built against JUnit 5.8.2 — is recorded

#### Scenario: Mockito is available to unit tests
- **WHEN** `pmd-rules/build.gradle` is inspected
- **THEN** Mockito is declared on `testImplementation`
- **AND** its version is constrained in the `dependencies` platform

#### Scenario: The annotation dependency does not reach the POM
- **WHEN** the generated POM is inspected after the annotation dependency is added
- **THEN** it declares no dependency, because the declaration is `compileOnly`

#### Scenario: Tests are split by tag
- **WHEN** the `test` and `integrationTest` tasks are configured
- **THEN** `test` includes the `unit` tag and `integrationTest` includes the `integration` tag

#### Scenario: Integration tests run at the compile floor
- **WHEN** `integrationTest` runs
- **THEN** the PMD it resolves is the version the `dependencies` platform constrains, not the
  version the convention plugin declares for analysis

#### Scenario: Fixtures remain the end-to-end check
- **WHEN** the PMD rule tests are inspected
- **THEN** each rule still has a `pmd-test` XML descriptor exercising it through PMD

### Requirement: Mutation testing thresholds
Pitest SHALL be configured with `mutationThreshold`, `coverageThreshold` and
`testStrengthThreshold` all at 100, and `check` SHALL depend on `pitest`. The thresholds SHALL apply
unchanged to both published modules.

Mutation analysis in `codenarc-rules` runs against Java bytecode, because that module's rules are
Java. That its tests are Spock changes only which engine executes them: Pitest's JUnit 5 plugin runs
the JUnit Platform, and Spock specifications carrying `@spock.lang.Tag('unit')` are selected by
`includedGroups`. No threshold SHALL be lowered for that module on the grounds that its tests are
Groovy.

The `-Dspock.parallel.disabled=true` JVM argument SHALL NOT be carried over.

#### Scenario: Check runs mutation testing
- **WHEN** `./gradlew check` runs
- **THEN** the `pitest` task executes and fails the build below any 100% threshold

#### Scenario: Thresholds are uniform across modules
- **WHEN** the Pitest configuration is inspected
- **THEN** no module lowers any of the three thresholds

#### Scenario: Spock specifications are selected by mutation analysis
- **WHEN** `pitest` runs for `:codenarc-rules`
- **THEN** it executes the Spock specifications rather than reporting no tests

#### Scenario: No Spock parallel argument is set
- **WHEN** the Pitest configuration is inspected
- **THEN** its `jvmArgs` contain no `spock.parallel.disabled` property

### Requirement: Publishing
Both `pmd-rules` and `codenarc-rules` SHALL be published to Maven Central via the Central Portal,
signed, with sources and javadoc jars, at the coordinates `io.github.joke.lint:pmd-rules` and
`io.github.joke.lint:codenarc-rules`.

The retired coordinate `io.github.joke.pmd:rules` SHALL receive one final publication: a POM-only
artifact carrying `<distributionManagement><relocation>` naming the new group and artifactId, so
that a consumer who never reads a changelog is told by their build tool. That publication SHALL be
removed in a follow-up change once it has been released, because retaining it would emit a fresh
relocation POM at every subsequent version under a coordinate nobody is pinned to.

Release publishing SHALL run only on a commit for which `release-please` created a release, and
SHALL use `publishAggregationToCentralPortal`.

#### Scenario: Both artifacts are publishable
- **WHEN** the publications are configured
- **THEN** their group is `io.github.joke.lint` and their artifactIds are `pmd-rules` and
  `codenarc-rules`
- **AND** each carries a sources jar, a javadoc jar, license, developer and SCM metadata, and a
  signature

#### Scenario: The retired coordinate forwards
- **WHEN** the relocation publication is generated
- **THEN** its coordinates are `io.github.joke.pmd:rules`
- **AND** its POM declares a `relocation` naming `io.github.joke.lint:pmd-rules`
- **AND** it carries no jar

#### Scenario: An ordinary push to main publishes no release
- **WHEN** a commit is pushed to `main` and `release-please` does not cut a release
- **THEN** `publishAggregationToCentralPortal` does not run

### Requirement: Version derivation
The project version SHALL be calculated by the `io.github.joke.conventional-version` plugin, which
predicts the version `release-please` will cut next from the conventional commits since the released
version recorded in the manifest. The plugin SHALL be applied at project level by the `conventions`
plugin and SHALL be the only thing in the build that assigns a version. No workflow, task or script
SHALL pass a flag, property or stage to select between a release and a snapshot version.

`pmd-rules` and `codenarc-rules` SHALL version independently, each resolving against its own entry in
the multi-package manifest. `pmd-rules` SHALL carry its version forward from `0.1.0` across the
coordinate move; `codenarc-rules` SHALL start fresh.

Every project that applies `conventions` SHALL therefore be versioned. The root project applies no
plugin and SHALL keep Gradle's default `unspecified`; this is permitted because the root project has
no `build.gradle`, declares no publication, and nothing in the build or in nmcp reads its version.

The version policy SHALL live entirely in `release-please-config.json` and
`.release-please-manifest.json`, which the build reads and never writes. `release-please` SHALL
remain the sole author of tags, of the CHANGELOG and of the manifest; the build SHALL NOT create or
push tags.

#### Scenario: The release commit yields the release version
- **WHEN** `HEAD` is the commit `release-please` released as `1.2.3` for a package
- **THEN** that package's module version is `1.2.3`
- **AND** it does not end in `-SNAPSHOT`

#### Scenario: A commit past a release yields the next planned version as a snapshot
- **WHEN** `HEAD` is past the released version `1.2.3` and a `feat:` commit is in the range
- **THEN** the module version is `1.3.0-SNAPSHOT`
- **AND** it does not contain the commit hash

#### Scenario: The two modules version independently
- **WHEN** a commit touching only `pmd-rules/` is released
- **THEN** `:pmd-rules` takes a new version
- **AND** `:codenarc-rules` keeps the version it already had

#### Scenario: No stage or version property is set by the build
- **WHEN** `gradle.properties`, `settings.gradle` and both workflow files are inspected
- **THEN** no version, stage, scope or tag-prefix property is declared

#### Scenario: Every publishable module is versioned
- **WHEN** the version of `:pmd-rules`, `:codenarc-rules` and `:dependencies` is queried
- **THEN** none is `unspecified`

#### Scenario: A module absent from the manifest still resolves a version
- **WHEN** `:dependencies`, which has no manifest entry, is queried
- **THEN** the build resolves a version for it rather than failing

#### Scenario: The root project is unversioned and unpublished
- **WHEN** the root project's version is queried
- **THEN** it is `unspecified`
- **AND** the root project declares no publication

#### Scenario: A missing release-please configuration fails the build
- **WHEN** `release-please-config.json` or `.release-please-manifest.json` is absent
- **THEN** the build fails rather than inventing a version

### Requirement: release-please manifest mode
`release-please` SHALL be configured in manifest mode with **two packages**:
`release-please-config.json` SHALL declare `pmd-rules` and `codenarc-rules`, each with
`release-type: simple`, and `.release-please-manifest.json` SHALL record the released version of
each. The workflow SHALL NOT pass `release-type` inline, since the action reads both files itself.

`release-please` routes a commit to a package by the paths it touches, not by conventional-commit
scope. A commit touching only `buildSrc/`, `dependencies/` or the repository root therefore maps to
no package and cuts no release. A change that alters what a module publishes — a dependency floor
among them — SHALL therefore touch that module, so that the release it warrants is actually cut.

#### Scenario: Configuration lives in the repository
- **WHEN** the repository root is inspected
- **THEN** both `release-please-config.json` and `.release-please-manifest.json` exist
- **AND** each declares an entry for `pmd-rules` and for `codenarc-rules`

#### Scenario: The workflow carries no release configuration
- **WHEN** the `release-please` job in `release.yml` is inspected
- **THEN** the action step declares no `release-type` input

#### Scenario: A floor bump reaches the module it affects
- **WHEN** a dependency floor is raised
- **THEN** the commit touches the `build.gradle` of every module whose published artifact changes

### Requirement: Repository configuration
`.github/settings.yml` SHALL be authored for this repository rather than copied from another
project. It SHALL name the repository `lint-rules`, describe it as a set of custom PMD and CodeNarc
rules, and declare branch protection on `main` requiring the `build` check.

#### Scenario: Settings name this repository
- **WHEN** `.github/settings.yml` is inspected
- **THEN** its `repository.name` is `lint-rules`
- **AND** it names no other project

#### Scenario: The description covers both artifacts
- **WHEN** the `repository.description` and `repository.topics` are inspected
- **THEN** they name both PMD and CodeNarc

#### Scenario: Branch protection requires the build check
- **WHEN** the `branches` section is inspected
- **THEN** `main` requires the `build` status check and a linear history

### Requirement: The build runs the rules it publishes
Each published module SHALL be analysed by the artifacts this repository builds:

- `pmd-rules` SHALL put `project(':pmd-rules')` on its `pmd` configuration.
- `codenarc-rules` SHALL put `project(':pmd-rules')` on its `pmd` configuration and
  `project(':codenarc-rules')` on its `codenarc` configuration.

PMD SHALL analyse with `rulesets/java/joke-strict.xml` and CodeNarc with
`rulesets/groovy/joke-strict.groovy`, the resources this project publishes, so that the compositions
under test are the shipped ones rather than local files resembling them.

Neither artifact SHALL be published without the build having run it over real source. For
`pmd-rules` that source is the Java of both modules; for `codenarc-rules` it is the Spock
specifications in its own test source set, which are Groovy of the kind its rules exist to analyse.

The rules dependency SHALL be declared by each consuming project rather than by the convention
plugin. A plugin that declared it would need to name a version, and for this repository the answer is
`project(':pmd-rules')` or `project(':codenarc-rules')`, which no coordinate expresses; leaving the
declaration to each build removes the need for a self-hosting escape hatch and keeps the plugin
ignorant of its own version.

The dependencies SHALL be declared on the `pmd` and `codenarc` configurations directly. This is
possible because the convention plugin supplies both tools by explicit dependency rather than through
`toolVersion`, so neither plugin's `defaultDependencies` mechanism — which applies only while the
configuration's own dependency set is empty — is relied upon and neither need be kept armed. No
intermediate configuration SHALL exist for the sole purpose of keeping a dependency set empty.

`project(':…')` is preferred over `files(sourceSets.main.output)` because it puts the packaged
artifact on the analysis classpath — the shape a consumer actually receives — and can therefore catch
a packaging fault such as a resource missing from the jar.

#### Scenario: Each module carries the analysis dependencies
- **WHEN** `pmd-rules/build.gradle` and `codenarc-rules/build.gradle` are inspected
- **THEN** `project(':pmd-rules')` is declared on each module's `pmd` configuration directly
- **AND** `project(':codenarc-rules')` is declared on `:codenarc-rules`' `codenarc` configuration
- **AND** no intermediate configuration is declared that `pmd` or `codenarc` extends from

#### Scenario: The convention plugin declares no rules dependency
- **WHEN** the convention plugin is inspected
- **THEN** it declares no dependency on either artifact and names no version of either

#### Scenario: Both tools are still available to analysis
- **WHEN** `pmdMain` and `codenarcTest` run
- **THEN** neither fails with `ClassNotFoundException` for a PMD or CodeNarc class
- **AND** each tool comes from the coordinate the convention plugin declares, not from a plugin's
  `defaultDependencies`

#### Scenario: The repository analyses itself with the shipped compositions
- **WHEN** `pmdMain` and `codenarcTest` run
- **THEN** the rules applied are those of `rulesets/java/joke-strict.xml` and
  `rulesets/groovy/joke-strict.groovy` as published in the jars
- **AND** no ruleset file exists at the repository root

#### Scenario: A violation in this repository fails the build
- **WHEN** Java source is added that violates a PMD rule this project publishes, or a Spock
  specification is added that violates a CodeNarc rule this project publishes
- **THEN** `./gradlew check` fails

### Requirement: The recovery path is documented
`README.md` SHALL document, in its build section, how to build past a rule that is breaking the
build: excluding `pmdMain`, `pmdTest` and `codenarcTest`.

This is needed because the rules are analysed by the modules that build them, so a rule that throws
during analysis fails its own module's analysis task and therefore `check`, and the repair is to edit
the rule that is currently breaking the build. Someone hitting that is mid-incident and will not work
the escape hatch out for themselves.

#### Scenario: The README states how to build past a broken rule
- **WHEN** the build section of `README.md` is inspected
- **THEN** it documents excluding `pmdMain`, `pmdTest` and `codenarcTest` to build while a rule is
  broken
- **AND** it explains that the rules are applied by the modules that build them

### Requirement: Null marking is generated, not hand-written
Every module with a Java main source set SHALL apply `io.github.joke.jspecify:processor` on that
source set's `annotationProcessor` path, which emits a `package-info.java` carrying `@NullMarked` for
every package in it. No `package-info.java` SHALL be hand-written for the purpose of declaring
`@NullMarked`.

Generating it makes marking structural: a new package is null-marked because it exists, rather than
because someone remembered to add a file whose only possible content is fixed. NullAway's
`RequireExplicitNullMarking`, which remains configured as an error, thereby changes role from a check
that catches the omission after the fact to a check that can no longer fire.

A hand-written and a generated declaration SHALL NOT coexist for the same package, as two sources for
one annotation is a duplicate-class failure at compile time.

Null-safety checking SHALL be verified to be in force rather than assumed from a green build,
because a disabled checker and a correct codebase are indistinguishable from the outside.

Groovy source is out of scope. NullAway and Error Prone are javac plugins and do not run over
`GroovyCompile`, so the Spock specifications in `codenarc-rules` are unmarked and unchecked. This is
accepted because that source set is tests, is never published, and is covered instead by CodeNarc.

#### Scenario: The marking is generated
- **WHEN** `compileJava` runs in either published module
- **THEN** a `package-info.java` annotated `@NullMarked` is produced under the annotation processor's
  generated sources for every package in that module's main source set

#### Scenario: No hand-written marking survives
- **WHEN** `pmd-rules/src/main/java` and `codenarc-rules/src/main/java` are inspected
- **THEN** neither contains a `package-info.java` declaring `@NullMarked`

#### Scenario: A new package is marked without any file being written
- **WHEN** a class is added in a package that did not previously exist
- **THEN** `compileJava` succeeds without `RequireExplicitNullMarking` reporting

#### Scenario: NullAway is demonstrably still enforcing
- **WHEN** a deliberate dereference of a `@Nullable` value is introduced into a rule class in either
  module
- **THEN** `compileJava` fails with a NullAway error

#### Scenario: Groovy source is not marked
- **WHEN** `codenarc-rules/src/test/groovy` is inspected
- **THEN** it contains no `package-info` and no null-safety annotation

### Requirement: Lombok is configured but unused
The repository SHALL carry a `lombok.config` at its root and both published modules SHALL declare
`org.projectlombok:lombok` on both `compileOnly` and `annotationProcessor`.

No rule class uses Lombok, and this SHALL NOT be treated as an oversight. All rule classes have zero
instance fields: PMD reuses a rule instance across files within a thread, so per-file state in a rule
is a defect, and the classes are stateless behaviour by construction. CodeNarc's rules carry
configuration properties on the rule object, but this artifact's rules SHALL declare only the `name`
and `priority` its base class requires. Lombok generates fields, accessors, constructors and
equality, every one of which presupposes state. Lombok annotations SHALL NOT be added to a stateless
rule class to justify the dependency.

The wiring exists as a configured baseline, so that the first class that genuinely warrants Lombok
faces a code decision rather than a build decision.

`lombok.config` SHALL set `config.stopBubbling = true` (the repository root is the top of the
configuration tree), `lombok.addLombokGeneratedAnnotation = true` (so generated members are excluded
from coverage and mutation analysis, which matters at a 100% threshold),
`lombok.addNullAnnotations = jspecify` (so generated members carry the same annotations as the rest of
the source) and `lombok.experimental.flagUsage = ALLOW`.

The dependency SHALL be `compileOnly`, never `api` or `implementation`, so it cannot reach either
published POM.

#### Scenario: The configuration is present and terminal
- **WHEN** `lombok.config` is inspected
- **THEN** it sets `config.stopBubbling = true`, `lombok.addLombokGeneratedAnnotation = true`,
  `lombok.addNullAnnotations = jspecify` and `lombok.experimental.flagUsage = ALLOW`

#### Scenario: Lombok does not reach either published artifact
- **WHEN** the generated POMs are inspected
- **THEN** `org.projectlombok:lombok` appears in neither

#### Scenario: Stateless rules stay unannotated
- **WHEN** a rule class with no instance fields is inspected
- **THEN** it carries no Lombok annotation

## ADDED Requirements

### Requirement: CodeNarc is load-bearing
The convention plugin's CodeNarc configuration SHALL be active, applying to every module that
applies the `groovy` plugin, with all three violation thresholds at zero.

It SHALL point at the ruleset published by `codenarc-rules` rather than at a file in this
repository, and SHALL therefore contain no `$rootDir` reference — the same constraint the `pmd`
block already carries, for the same reason: the plugin is destined for extraction and a path into
this repository's layout would not survive it.

`.codenarc.groovy` SHALL NOT exist at the repository root. Its content is published as
`rulesets/groovy/joke-strict.groovy`.

#### Scenario: CodeNarc runs in this repository
- **WHEN** `./gradlew check` runs
- **THEN** `codenarcTest` executes for `:codenarc-rules`

#### Scenario: The thresholds stay at zero
- **WHEN** the convention plugin's `codenarc` block is inspected
- **THEN** all three violation thresholds are zero

#### Scenario: No repository-relative path remains
- **WHEN** the convention plugin's `codenarc` block is inspected
- **THEN** it contains no `$rootDir` reference
- **AND** no ruleset file exists at the repository root

### Requirement: The CodeNarc tool is supplied by an explicit dependency
The convention plugin SHALL supply CodeNarc by declaring `org.codenarc:CodeNarc` with a version on
the `codenarc` configuration, and SHALL NOT set `toolVersion`.

The version SHALL be visible as a coordinate rather than as an extension property, so that the
CodeNarc used for analysis reads as what it is — a dependency — and so that declaring the project's
own rules artifact on the same configuration does not displace the tool. This mirrors the treatment
of `pmd-dist` exactly.

The declared version SHALL be on the Groovy 4 line and at or above the floor
`codenarc-rule-distribution` states. Raising it is an ordinary dependency bump: change the coordinate
and run `check`.

#### Scenario: The tool is a coordinate, not a property
- **WHEN** the convention plugin's `codenarc` block is inspected
- **THEN** it declares `org.codenarc:CodeNarc` with a version on the `codenarc` configuration
- **AND** it does not set `toolVersion`

#### Scenario: The declared version honours the floor
- **WHEN** the declared CodeNarc version is compared with the floor `codenarc-rules` compiles against
- **THEN** it is at or above that floor and on the same Groovy line

### Requirement: Spotless formats Java only
The convention plugin's Spotless configuration SHALL declare a `java` block and no `groovy` block.
Groovy source SHALL be left unformatted.

Spotless's Groovy support is `greclipse`, which reformats Spock's labelled-block layout badly enough
to fight the specifications it would be tidying. Groovy style is carried by CodeNarc instead. This is
a deliberate gap, stated so it reads as a decision rather than an oversight.

#### Scenario: No Groovy formatter is configured
- **WHEN** the convention plugin's `spotless` block is inspected
- **THEN** it declares a `java` block
- **AND** it declares no `groovy` or `groovyGradle` block

#### Scenario: Groovy source is not reformatted
- **WHEN** `./gradlew spotlessApply` runs
- **THEN** no file under `codenarc-rules/src/test/groovy` is modified

### Requirement: The Groovy line is chosen once
The Groovy runtime, the Spock variant and the CodeNarc tool coordinate SHALL all sit on the **Groovy
5** line — Groovy 5.0.x, `spock-core:2.4-groovy-5.0` and `org.codenarc:CodeNarc:4.0.0`, where the
artifact suffix that marked the earlier lines is dropped — and the `dependencies` platform SHALL
constrain them so that they cannot drift apart.

They are coupled because CodeNarc parses `.groovy` source with its own embedded Groovy: a
specification using syntax newer than the analysing CodeNarc's parser fails analysis while compiling
perfectly.

Gradle 9 embeds Groovy 4 rather than 5. That SHALL NOT be treated as a conflict: `buildSrc` does not
apply `conventions`, so CodeNarc never analyses the build scripts and the two lines never meet.

Moving off this line SHALL be a change of its own, taken as a set rather than one coordinate at a
time.

#### Scenario: The three coordinates agree
- **WHEN** the `dependencies` platform and the convention plugin are inspected
- **THEN** the Groovy, Spock and CodeNarc coordinates are all Groovy 5 line releases

#### Scenario: The coupling is recorded
- **WHEN** the `dependencies` platform is inspected
- **THEN** a comment records that CodeNarc parses source with its own Groovy and that the three move
  together

## REMOVED Requirements

### Requirement: CodeNarc is retained but inert
**Reason**: The premise no longer holds. The requirement existed because no module applied the
`groovy` plugin, so the CodeNarc configuration never activated, and it was kept only because the
convention plugin is destined for extraction by projects that do use Groovy. `codenarc-rules` now
applies the `groovy` plugin and its Spock specifications are the corpus its own published rules
analyse, so the configuration is load-bearing in this repository rather than dormant in it.

**Migration**: Replaced by "CodeNarc is load-bearing", which keeps the three violation thresholds at
zero and adds the constraint that the ruleset comes from the published artifact rather than from a
repository-root file. `.codenarc.groovy` is deleted and its content ships as
`rulesets/groovy/joke-strict.groovy`; see `codenarc-rule-distribution`.
