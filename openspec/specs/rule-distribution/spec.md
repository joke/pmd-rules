# rule-distribution Specification

## Purpose

How the rules are packaged and consumed: rules implemented in Java, PMD as a compile-only dependency
at the supported floor, a dependency-free published POM, the `category` / `rulesets` resource split,
cross-version compatibility verification, and the documented consumer wiring.

## Requirements

### Requirement: Rules are implemented in Java
Every rule SHALL be a Java class extending PMD's Java rule base types. No rule SHALL be defined as
an inline `net.sourceforge.pmd.lang.rule.xpath.XPathRule` expression in the shipped resources,
because such a rule is exempt from every quality gate the build applies — Error Prone, NullAway,
Spotless and Pitest all require compiled Java.

#### Scenario: The category file declares only Java-backed rules
- **WHEN** `category/java/joke.xml` is inspected
- **THEN** every `<rule>` element carries a `class` attribute naming a class in this artifact
- **AND** no `<rule>` element declares an `xpath` property

#### Scenario: Rule classes are subject to the quality gates
- **WHEN** `./gradlew check` runs
- **THEN** Spotless, Error Prone, NullAway and Pitest all analyse the rule classes

### Requirement: PMD is a compile-only dependency at the supported floor
The `rules` module SHALL declare `net.sourceforge.pmd:pmd-core` and `net.sourceforge.pmd:pmd-java`
as `compileOnly` at version **7.0.0**, which is the lowest supported PMD version.

Compiling against the floor rather than the newest available PMD is deliberate: rules compiled
against an older API run on newer PMD, whereas rules compiled against a newer API fail with
`NoSuchMethodError` inside the consumer's analysis. Raising the floor SHALL be an explicit decision
recorded in a change, not a side effect of a dependency update.

Rules SHALL NOT use PMD API annotated `@InternalApi`.

#### Scenario: PMD is compile-only at 7.0.0
- **WHEN** `rules/build.gradle` and the `dependencies` platform are inspected
- **THEN** `pmd-core` and `pmd-java` are declared `compileOnly` and constrained to 7.0.0

#### Scenario: No internal API is used
- **WHEN** the rule classes are compiled
- **THEN** no reference to a type or member annotated `@InternalApi` is present

### Requirement: The published POM declares no dependencies
The published artifact SHALL declare no `compile`- or `runtime`-scope dependency. The consuming
project supplies PMD at a version it chooses, via Gradle's `pmd` configuration, and this project
SHALL NOT impose one transitively.

A task SHALL verify this by parsing the generated POM and failing `check` if any dependency is
declared, because a leak is otherwise silent until it manifests as a version conflict in a
consumer's build.

#### Scenario: The published POM is empty of dependencies
- **WHEN** the generated POM is inspected
- **THEN** it declares no dependency
- **AND** neither `pmd-core` nor `pmd-java` appears in it

#### Scenario: A leaked dependency fails the build
- **WHEN** a dependency is declared at a scope that reaches the POM
- **THEN** `./gradlew check` fails with a message naming the leaked coordinates

#### Scenario: Test dependencies do not reach the POM
- **WHEN** `pmd-test` is declared `testImplementation`
- **THEN** the published POM still declares no dependency

### Requirement: Resource layout follows PMD's category and ruleset split
The artifact SHALL ship exactly two kinds of rule resource:

- `category/java/joke.xml` — the catalogue, declaring every rule with its implementing class,
  message, description, priority and at least one example.
- `rulesets/java/joke.xml` — a convenience ruleset selecting every rule in the category file, so
  that a consumer can adopt all of them with a single reference.

#### Scenario: Both resources are published
- **WHEN** the published jar is inspected
- **THEN** it contains `category/java/joke.xml` and `rulesets/java/joke.xml`

#### Scenario: The category file documents every rule
- **WHEN** a rule is added to the artifact
- **THEN** `category/java/joke.xml` declares it with a message, a description, a priority and an
  example

#### Scenario: The convenience ruleset selects every rule
- **WHEN** `rulesets/java/joke.xml` is loaded
- **THEN** the resulting rule set contains every rule declared in `category/java/joke.xml`

### Requirement: Shipped resources reference no external ruleset
Neither shipped resource SHALL reference PMD's stock categories, nor `<exclude>` any rule that this
artifact does not define.

A reference to a stock category resolves against the consumer's PMD version, and PMD renames and
removes rules across minor releases, where a stale name is a hard ruleset-load failure. Referencing
only its own files decouples the artifact from PMD's rule catalogue as well as from its binary API.

#### Scenario: No stock category is referenced
- **WHEN** `category/java/joke.xml` and `rulesets/java/joke.xml` are inspected
- **THEN** no `<rule ref="category/java/...">` names a PMD-provided category
- **AND** no `<exclude>` element is present

#### Scenario: The ruleset loads under a PMD version this project never saw
- **WHEN** `rulesets/java/joke.xml` is loaded by any PMD 7.x
- **THEN** it resolves without referencing any rule outside this artifact

### Requirement: Cross-version compatibility is verified by the build
The build SHALL register one `integrationTest`-tagged task per supported PMD version, each resolving
`pmd-core` and `pmd-java` at that version and running the built rules against a fixture tree. The
set SHALL include the floor (7.0.0) and the newest supported version. `check` SHALL depend on all of
them, so that `./gradlew check` reproduces the matrix locally.

These tests SHALL NOT use `pmd-test`, because `RuleTst` is itself versioned and would confound a
PMD-version failure with a harness-version failure. They SHALL drive `PMDConfiguration`,
`RuleSetLoader`, `PmdAnalysis` and `RuleViolation` directly.

#### Scenario: Every supported version is exercised
- **WHEN** `./gradlew check` runs
- **THEN** an integration test task runs for PMD 7.0.0 and for the newest supported version

#### Scenario: A binary incompatibility fails the build
- **WHEN** a rule uses API absent from the floor version
- **THEN** the integration test at 7.0.0 fails rather than the incompatibility reaching a consumer

#### Scenario: The matrix uses the stable API subset only
- **WHEN** the cross-version integration tests are inspected
- **THEN** they do not reference `pmd-test`

### Requirement: The ruleset is loaded by classpath reference
The cross-version integration tests SHALL load `rulesets/java/joke.xml` by classpath reference,
exactly as a consuming Gradle build does, rather than by filesystem path. Classpath resolution from
the `pmd` configuration is the single mechanism the distribution model depends on, so it SHALL be
exercised on every build rather than discovered by the first consumer.

#### Scenario: Loading mirrors consumer wiring
- **WHEN** an integration test loads the ruleset
- **THEN** it uses the classpath reference `rulesets/java/joke.xml` and not an absolute or
  project-relative file path

#### Scenario: A missing or misnamed resource fails the build
- **WHEN** the ruleset resource is absent from the jar or named differently
- **THEN** the integration tests fail

### Requirement: Consumer wiring is documented
The `README.md` SHALL document consumption as adding the artifact to the Gradle `pmd` configuration
and referencing the ruleset, and SHALL state the supported PMD version range.

#### Scenario: README shows the two-line wiring
- **WHEN** `README.md` is inspected
- **THEN** it shows the artifact added to the `pmd` configuration
- **AND** it shows `rulesets/java/joke.xml` referenced from the `pmd` extension

#### Scenario: README states the supported range
- **WHEN** `README.md` is inspected
- **THEN** it names the lowest supported PMD version
