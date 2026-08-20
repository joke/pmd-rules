## ADDED Requirements

### Requirement: Rules are implemented in Java
Every rule SHALL be a Java class extending PMD's Java rule base types. No rule SHALL be defined as
an inline `net.sourceforge.pmd.lang.rule.xpath.XPathRule` expression in the shipped resources,
because such a rule is exempt from every quality gate the build applies — Error Prone, NullAway,
Spotless and Pitest all require compiled Java.

Rule classes SHALL live under `io.github.joke.lint.pmd.rules.java`, so that the package matches the
publishing group and is symmetric with the CodeNarc module's `io.github.joke.lint.codenarc.rules.spock`.

#### Scenario: The category file declares only Java-backed rules
- **WHEN** `category/java/joke.xml` is inspected
- **THEN** every `<rule>` element carries a `class` attribute naming a class in this artifact
- **AND** no `<rule>` element declares an `xpath` property

#### Scenario: Rule classes carry the aligned package
- **WHEN** a rule class is inspected
- **THEN** its package is `io.github.joke.lint.pmd.rules.java`
- **AND** `category/java/joke.xml` names it with that package

#### Scenario: Rule classes are subject to the quality gates
- **WHEN** `./gradlew check` runs
- **THEN** Spotless, Error Prone, NullAway and Pitest all analyse the rule classes

### Requirement: PMD is a compile-only dependency at the supported floor
The `pmd-rules` module SHALL declare `net.sourceforge.pmd:pmd-core` and
`net.sourceforge.pmd:pmd-java` as `compileOnly` at version **7.0.0**, which is the lowest supported
PMD version.

Compiling against the floor rather than the newest available PMD is deliberate: rules compiled
against an older API run on newer PMD, whereas rules compiled against a newer API fail with
`NoSuchMethodError` inside the consumer's analysis. Raising the floor SHALL be an explicit decision
recorded in a change, not a side effect of a dependency update.

Rules SHALL NOT use PMD API annotated `@InternalApi`.

#### Scenario: PMD is compile-only at 7.0.0
- **WHEN** `pmd-rules/build.gradle` and the `dependencies` platform are inspected
- **THEN** `pmd-core` and `pmd-java` are declared `compileOnly` and constrained to 7.0.0

#### Scenario: No internal API is used
- **WHEN** the rule classes are compiled
- **THEN** no reference to a type or member annotated `@InternalApi` is present

### Requirement: The published POM declares no dependencies
The published artifact SHALL declare no `compile`- or `runtime`-scope dependency. The consuming
project supplies PMD at a version it chooses, via Gradle's `pmd` configuration, and this project
SHALL NOT impose one transitively.

This SHALL be secured by configuration choice rather than by a verification task. Every dependency
the `pmd-rules` module declares SHALL sit on a configuration that structurally cannot reach the POM —
`compileOnly`, `annotationProcessor`, `testImplementation`, `testCompileOnly` or `testRuntimeOnly`.
No `implementation`, `api` or `runtimeOnly` declaration SHALL be added to the `pmd-rules` module.

The previous `verifyPomHasNoDependencies` task, which parsed the generated POM and failed `check` on
any declared dependency, SHALL NOT be reinstated. The property it defended is stated here; a
`check`-time XML parse of a build output is a heavy way to state "do not use `implementation` here".

#### Scenario: The published POM is empty of dependencies
- **WHEN** the generated POM is inspected
- **THEN** it declares no dependency
- **AND** neither `pmd-core` nor `pmd-java` appears in it

#### Scenario: Every declaration is on a non-publishing configuration
- **WHEN** the `dependencies` block of `pmd-rules/build.gradle` is inspected
- **THEN** every declaration is `compileOnly`, `annotationProcessor`, `pmd`, `testImplementation`,
  `testCompileOnly` or `testRuntimeOnly`
- **AND** none is `implementation`, `api` or `runtimeOnly`

#### Scenario: Test and processor dependencies do not reach the POM
- **WHEN** `pmd-test` is declared `testImplementation` and Lombok `annotationProcessor`
- **THEN** the published POM still declares no dependency

#### Scenario: No verification task guards this
- **WHEN** the `pmd-rules` module's tasks are inspected
- **THEN** no task parses the generated POM
- **AND** `check` depends on no such task

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

These resource paths SHALL NOT change when the artifact's coordinates change, so that a consumer
moving to the new coordinates edits their dependency declaration and nothing else.

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

#### Scenario: The coordinate move leaves resource paths untouched
- **WHEN** the artifact published at `io.github.joke.lint:pmd-rules` is compared with the one
  previously published at `io.github.joke.pmd:rules`
- **THEN** all three resource paths are unchanged

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
support window stated in the resource itself, so that the narrower promise travels with the file that
carries it.

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

### Requirement: The ruleset is loaded by classpath reference
The integration tests SHALL load `rulesets/java/joke.xml` and `rulesets/java/joke-strict.xml` by
classpath reference, exactly as a consuming Gradle build does, rather than by filesystem path.
Classpath resolution from the `pmd` configuration is the single mechanism the distribution model
depends on, so it SHALL be exercised on every build rather than discovered by the first consumer.

The two resources SHALL be exercised by separate integration-test classes, so that a failure names
which resource broke. The split SHALL NOT be made by a JUnit assumption on the running PMD version,
because an assumption that stops holding produces a skipped test that a build log does not
distinguish from a test that never ran.

These tests SHALL NOT use `pmd-test`, because `RuleTst` is itself versioned and would confound a
PMD-version failure with a harness-version failure. They SHALL drive `PMDConfiguration`,
`RuleSetLoader`, `PmdAnalysis` and `RuleViolation` directly.

#### Scenario: Loading mirrors consumer wiring
- **WHEN** an integration test loads a ruleset
- **THEN** it uses a classpath reference such as `rulesets/java/joke.xml` and not an absolute or
  project-relative file path

#### Scenario: A missing or misnamed resource fails the build
- **WHEN** a ruleset resource is absent from the jar or named differently
- **THEN** the integration tests fail

#### Scenario: Each resource has its own test class
- **WHEN** the integration tests are inspected
- **THEN** `rulesets/java/joke.xml` and `rulesets/java/joke-strict.xml` are exercised by separate
  classes
- **AND** neither skips itself based on the running PMD version

#### Scenario: The tests use the stable API subset only
- **WHEN** the integration tests are inspected
- **THEN** they do not reference `pmd-test`

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
7.0.0, so the file names at least one rule the floor version does not have.

That floor is a **stated promise, not an enforced constraint**. No task, guard or version list in the
build asserts it, and the integration tests load the strict ruleset at whatever PMD version the test
classpath resolves — currently the 7.0.0 compile floor, where it happens to load. That observation
SHALL NOT be converted into a support commitment: how PMD 7.0.0 treats an `<exclude>` naming a rule it
does not have is not documented behaviour, and a single green run is not a promise. The declared floor
is the range this project supports and analyses itself with; anything lower is unsupported whether or
not it works.

The declared floor SHALL be recorded in the file's own `<description>`, so that it travels with the
resource rather than only with the README. That description SHALL NOT claim the floor is verified by
a cross-version matrix, because no such matrix exists.

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
- **AND** it does not claim the floor is exercised by a cross-version matrix

#### Scenario: The floor is not enforced by the build
- **WHEN** the build is inspected
- **THEN** no task, version list or guard asserts the strict ruleset's floor
- **AND** nothing excludes the strict ruleset's integration test based on a PMD version

#### Scenario: Loading below the floor is unsupported, not forbidden
- **WHEN** the strict ruleset loads under a PMD older than its declared floor
- **THEN** this is incidental and creates no support commitment

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

`README.md` SHALL NOT describe a cross-version integration matrix, an `additionalPmdVersions` or
`strictRulesetVersions` declaration, or a procedure for adding a version to either. It SHALL instead
describe how the two remaining signals divide the range: `integrationTest` at the compile floor and
`pmdMain` at the version the convention plugin declares for `pmd-dist`, and that adopting a newer PMD
means raising that coordinate and running `check`.

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

#### Scenario: No matrix is described
- **WHEN** `README.md` is inspected
- **THEN** it mentions no cross-version matrix, `additionalPmdVersions` or `strictRulesetVersions`
- **AND** it states that adopting a newer PMD means raising the `pmd-dist` coordinate

### Requirement: Consumer wiring is documented
`README.md` SHALL document consumption as adding the artifact to the Gradle `pmd` configuration and
referencing the ruleset, and SHALL state the supported PMD version range.

The wiring SHALL name the artifact at its current coordinates, `io.github.joke.lint:pmd-rules`.

#### Scenario: README shows the two-line wiring
- **WHEN** `README.md` is inspected
- **THEN** it shows `io.github.joke.lint:pmd-rules` added to the `pmd` configuration
- **AND** it shows `rulesets/java/joke.xml` referenced from the `pmd` extension

#### Scenario: README states the supported range
- **WHEN** `README.md` is inspected
- **THEN** it names the lowest supported PMD version

### Requirement: Rule test fixtures stay in XML
The deliberate rule violations that make up a rule's test data SHALL live inside the `pmd-test` XML
descriptors under `pmd-rules/src/test/resources`, and SHALL NOT be moved into `.java` files.

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

### Requirement: The PMD rules are tested with JUnit, not Spock
The `pmd-rules` module SHALL NOT apply the `groovy` plugin and SHALL NOT declare Spock. Its tests
SHALL remain JUnit 5 tests in Java, driving `pmd-test`'s `RuleTst` against the XML descriptors.

Spock is readmitted to this repository only in `codenarc-rules`, where it is the corpus the CodeNarc
rules analyse. Bringing it here would forfeit the `pmd-test` harness, which is the mechanism the XML
fixture requirement depends on, and would buy no dogfooding: PMD does not analyse Groovy.

#### Scenario: The PMD module stays on JUnit
- **WHEN** `pmd-rules/build.gradle` is inspected
- **THEN** it does not apply the `groovy` plugin
- **AND** it declares no Spock dependency

#### Scenario: No CodeNarc task exists for the PMD module
- **WHEN** `./gradlew check` runs
- **THEN** no `CodeNarc` task executes for `:pmd-rules`
