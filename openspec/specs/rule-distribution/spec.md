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
The artifact SHALL ship exactly three rule resources:

- `category/java/joke.xml` — the catalogue, declaring every rule with its implementing class,
  message, description, priority and at least one example.
- `rulesets/java/joke.xml` — a convenience ruleset selecting every rule in the category file, so
  that a consumer can adopt all of them with a single reference.
- `rulesets/java/joke-strict.xml` — the opinionated composition, selecting `rulesets/java/joke.xml`
  together with PMD's stock categories, so that a consumer can adopt the whole analysis this project
  runs on itself with a single reference.

The name `joke-strict.xml` SHALL be used rather than a name suggesting a superset of this artifact's
own rules, because the file enables six PMD stock categories and a consumer reading the reference
needs the blast radius to be visible in the name.

#### Scenario: All three resources are published
- **WHEN** the published jar is inspected
- **THEN** it contains `category/java/joke.xml`, `rulesets/java/joke.xml` and
  `rulesets/java/joke-strict.xml`

#### Scenario: The category file documents every rule
- **WHEN** a rule is added to the artifact
- **THEN** `category/java/joke.xml` declares it with a message, a description, a priority and an
  example

#### Scenario: The convenience ruleset selects every rule
- **WHEN** `rulesets/java/joke.xml` is loaded
- **THEN** the resulting rule set contains every rule declared in `category/java/joke.xml`

#### Scenario: The strict ruleset selects every rule this artifact defines
- **WHEN** `rulesets/java/joke-strict.xml` is loaded
- **THEN** the resulting rule set contains every rule declared in `category/java/joke.xml`
- **AND** it contains rules this artifact does not define

### Requirement: Shipped resources reference no external ruleset
`category/java/joke.xml` and `rulesets/java/joke.xml` SHALL NOT reference PMD's stock categories,
nor `<exclude>` any rule that this artifact does not define.

A reference to a stock category resolves against the consumer's PMD version, and PMD renames and
removes rules across minor releases, where a stale name is a hard ruleset-load failure. Referencing
only its own files decouples these two resources from PMD's rule catalogue as well as from its
binary API, which is what lets them carry the 7.0.0 floor.

`rulesets/java/joke-strict.xml` is the single exception and SHALL be the only shipped resource
permitted to name a rule this artifact does not define. It carries a narrower support window in
exchange, stated by the requirement that governs it. No further exception SHALL be added without a
support window and a matrix task to enforce it.

#### Scenario: No stock category is referenced by the catalogue or the convenience ruleset
- **WHEN** `category/java/joke.xml` and `rulesets/java/joke.xml` are inspected
- **THEN** no `<rule ref="category/java/...">` names a PMD-provided category
- **AND** no `<exclude>` element is present

#### Scenario: The convenience ruleset loads under a PMD version this project never saw
- **WHEN** `rulesets/java/joke.xml` is loaded by any PMD 7.x
- **THEN** it resolves without referencing any rule outside this artifact

#### Scenario: The strict ruleset is the only file naming external rules
- **WHEN** the shipped resources are inspected
- **THEN** `rulesets/java/joke-strict.xml` is the only one referencing a PMD stock category or
  excluding a rule this artifact does not define

### Requirement: Cross-version compatibility is verified by the build
The build SHALL register one `integrationTest`-tagged task per supported PMD version, each resolving
`pmd-core` and `pmd-java` at that version and running the built rules against a fixture tree. The
set SHALL include the floor (7.0.0) and the newest supported version. `check` SHALL depend on all of
them, so that `./gradlew check` reproduces the matrix locally.

Because the shipped resources carry two different support windows, the matrix SHALL distinguish
them. Every version's task SHALL exercise `rulesets/java/joke.xml`; only the tasks for versions at or
above the strict ruleset's floor SHALL exercise `rulesets/java/joke-strict.xml`. The distinction
SHALL be made by Gradle test filtering against a separate integration-test class, and SHALL NOT be
made by a JUnit assumption on the running PMD version, because an assumption that stops holding
produces a skipped test that a build log does not distinguish from a test that never ran.

The build SHALL fail if the set of versions declared to load the strict ruleset is not a subset of
the versions the matrix runs, so that a typo removes coverage loudly rather than silently.

These tests SHALL NOT use `pmd-test`, because `RuleTst` is itself versioned and would confound a
PMD-version failure with a harness-version failure. They SHALL drive `PMDConfiguration`,
`RuleSetLoader`, `PmdAnalysis` and `RuleViolation` directly.

Dogfooding SHALL NOT be treated as a substitute for the matrix. `pmdMain` and `pmdTest` run the rules
under the single version Gradle's `toolVersion` selects — the newest supported version — against this
repository's real source, which is a third and complementary signal:

| task | PMD version | ruleset | code analysed |
|---|---|---|---|
| `integrationTest` | the floor | `joke.xml` only | synthetic fixtures |
| the additional matrix tasks | each remaining supported version | `joke.xml`, plus `joke-strict.xml` at or above its floor | synthetic fixtures |
| `pmdMain` / `pmdTest` | Gradle's `toolVersion` | `joke-strict.xml` | this repository's real source |

The matrix alone SHALL own the floor, because Gradle runs one PMD version and a green `pmdMain`
therefore says nothing about 7.0.0.

#### Scenario: Every supported version is exercised
- **WHEN** `./gradlew check` runs
- **THEN** an integration test task runs for PMD 7.0.0 and for the newest supported version

#### Scenario: A binary incompatibility fails the build
- **WHEN** a rule uses API absent from the floor version
- **THEN** the integration test at 7.0.0 fails rather than the incompatibility reaching a consumer

#### Scenario: The strict ruleset is not loaded below its floor
- **WHEN** the integration test task for a PMD version below the strict ruleset's floor runs
- **THEN** it does not load `rulesets/java/joke-strict.xml`

#### Scenario: The strict ruleset is loaded at its floor
- **WHEN** the integration test task for the strict ruleset's floor runs
- **THEN** it loads `rulesets/java/joke-strict.xml` and resolves every rule it names

#### Scenario: A miscoded strict version fails the build
- **WHEN** a version is declared to load the strict ruleset that the matrix does not run
- **THEN** the build fails rather than silently dropping the coverage

#### Scenario: The matrix uses the stable API subset only
- **WHEN** the cross-version integration tests are inspected
- **THEN** they do not reference `pmd-test`

#### Scenario: Dogfooding does not cover the floor
- **WHEN** a rule uses API absent from 7.0.0 but present in Gradle's `toolVersion`
- **THEN** `pmdMain` passes
- **AND** the integration test at the floor fails, so `check` still fails

### Requirement: The ruleset is loaded by classpath reference
The cross-version integration tests SHALL load `rulesets/java/joke.xml` and, where the version is at
or above its floor, `rulesets/java/joke-strict.xml`, by classpath reference, exactly as a consuming
Gradle build does, rather than by filesystem path. Classpath resolution from the `pmd` configuration
is the single mechanism the distribution model depends on, so it SHALL be exercised on every build
rather than discovered by the first consumer.

#### Scenario: Loading mirrors consumer wiring
- **WHEN** an integration test loads a ruleset
- **THEN** it uses a classpath reference such as `rulesets/java/joke.xml` and not an absolute or
  project-relative file path

#### Scenario: A missing or misnamed resource fails the build
- **WHEN** a ruleset resource is absent from the jar or named differently
- **THEN** the integration tests fail

### Requirement: The strict ruleset publishes the composition and declares its own support window
`rulesets/java/joke-strict.xml` SHALL contain the composition this repository previously kept in
`.pmd.xml`: PMD's `bestpractices`, `codestyle`, `design`, `errorprone`, `multithreading` and
`performance` categories with their exclusions, the `design` property overrides
(`CognitiveComplexity` `reportLevel` 5, `AvoidDeeplyNestedIfStmts` `problemDepth` 2,
`NPathComplexity` `reportLevel` 5), and a reference to `rulesets/java/joke.xml`.

Its exclusion of `TooManyStaticImports` SHALL be retained with its rationale, because that rule is in
direct opposition to `UseStaticImports`, which this artifact publishes.

The strict ruleset SHALL declare a PMD floor of 7.26.0, higher than the 7.0.0 floor the rule classes
and the other two resources carry. It references `ImplicitFunctionalInterface`, which PMD added after
7.0.0, so the file names at least one rule the floor version does not have. Whether PMD 7.0.0 treats
an `<exclude>` of an absent rule as a load failure or a warning SHALL NOT be relied upon: the
artifact does not ship a promise that depends on the answer.

The declared floor SHALL be recorded in the file's own `<description>`, so that it travels with the
resource rather than only with the README.

#### Scenario: The composition is published rather than copied
- **WHEN** `rulesets/java/joke-strict.xml` is loaded
- **THEN** it resolves PMD's six stock categories with their exclusions and property overrides
- **AND** it resolves every rule declared in `category/java/joke.xml`

#### Scenario: The opposition to TooManyStaticImports survives
- **WHEN** `rulesets/java/joke-strict.xml` is inspected
- **THEN** it excludes `TooManyStaticImports` from `category/java/codestyle.xml`
- **AND** a comment records that the exclusion is required by `UseStaticImports`

#### Scenario: The support window travels with the resource
- **WHEN** `rulesets/java/joke-strict.xml` is inspected
- **THEN** its `<description>` states its PMD floor and that the floor differs from the artifact's

#### Scenario: The rule classes keep their floor
- **WHEN** the strict ruleset's floor is raised or lowered
- **THEN** the PMD version the rule classes compile against is unchanged at 7.0.0

### Requirement: Consumer documentation states both support windows
`README.md` SHALL present `rulesets/java/joke-strict.xml` as the single-reference path for a consumer
who wants the whole analysis, and SHALL retain `rulesets/java/joke.xml` and the per-rule references
out of `category/java/joke.xml` as the paths for a consumer composing their own.

It SHALL state the two PMD support windows side by side — 7.0.0 for the rule classes and the first
two resources, 7.26.0 for the strict ruleset — and SHALL give the reason, so that a consumer on an
older PMD understands which reference to use rather than discovering it from a load failure.

It SHALL state that a consumer of the strict ruleset cannot subtract from it through Gradle's
`ruleSets`, and that removing a rule from the composition means writing their own ruleset file.

#### Scenario: Both windows are documented
- **WHEN** the PMD versions section of `README.md` is inspected
- **THEN** it states the 7.0.0 floor for the rule classes, `category/java/joke.xml` and
  `rulesets/java/joke.xml`
- **AND** it states the 7.26.0 floor for `rulesets/java/joke-strict.xml` with its reason

#### Scenario: The composing path survives
- **WHEN** the usage section of `README.md` is inspected
- **THEN** it shows `rulesets/java/joke-strict.xml` as the single-reference path
- **AND** it shows `rulesets/java/joke.xml` and a per-rule reference as the composing paths

#### Scenario: The subtraction limit is documented
- **WHEN** the usage section of `README.md` is inspected
- **THEN** it states that a consumer wanting the composition minus a rule needs their own ruleset
  file

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

### Requirement: Rule test fixtures stay in XML
The deliberate rule violations that make up a rule's test data SHALL live inside the `pmd-test` XML
descriptors under `rules/src/test/resources`, and SHALL NOT be moved into `.java` files.

Some PMD projects keep rule test data in real source files under a `testdata` package. Doing that
here would make the build flag its own fixtures, because the module that builds the rules also
analyses its own source with them. The obvious repair — excluding the fixture path from PMD — would
quietly exclude anything else that later moved into that path, so the constraint is stated rather
than left to be rediscovered.

#### Scenario: Fixtures are invisible to PMD
- **WHEN** `pmdMain` and `pmdTest` run over this repository
- **THEN** no violation is reported against rule test data

#### Scenario: Test data lives in the descriptors
- **WHEN** a rule's test data is inspected
- **THEN** every violating and compliant sample is embedded in a `pmd-test` XML descriptor
- **AND** no `.java` file exists whose purpose is to carry a deliberate violation
