## REMOVED Requirements

### Requirement: Cross-version compatibility is verified by the build
**Reason**: The matrix registered one configuration and one `Test` task per supported PMD version,
plus a `strictRulesetVersions` declaration and a subset guard policing the pair — roughly a hundred
lines of Gradle to cover versions that were never where the risk lived. The two failure modes it
existed to catch are each still covered, at the two ends of the supported range:

| signal | PMD version | ruleset | code analysed |
|---|---|---|---|
| `integrationTest` | the floor, from the `dependencies` platform | both shipped rulesets | synthetic fixtures |
| `pmdMain` / `pmdTest` | the version the convention plugin declares for `pmd-dist` | `joke-strict.xml` | this repository's real source |

A rule compiled against API absent from the floor fails `integrationTest` at the floor, exactly as it
did before. A rule or ruleset broken by a newer PMD fails `pmdMain` at the declared version, against
this repository's real source, which is a stronger probe than the synthetic fixtures the matrix ran
against. Interior versions between the two ends are accepted as uncovered.

**Migration**: Adopting a new PMD release is now a single coordinate change: raise
`net.sourceforge.pmd:pmd-dist` in the convention plugin and run `./gradlew check`. There is no
version list to extend, no per-version task to expect in the build log, and no subset guard to
satisfy. Should the uncovered interior prove to matter, reintroducing coverage is a change of its own
with evidence to justify it.

## MODIFIED Requirements

### Requirement: The published POM declares no dependencies
The published artifact SHALL declare no `compile`- or `runtime`-scope dependency. The consuming
project supplies PMD at a version it chooses, via Gradle's `pmd` configuration, and this project
SHALL NOT impose one transitively.

This SHALL be secured by configuration choice rather than by a verification task. Every dependency
the `rules` module declares SHALL sit on a configuration that structurally cannot reach the POM —
`compileOnly`, `annotationProcessor`, `testImplementation`, `testCompileOnly` or `testRuntimeOnly`.
No `implementation`, `api` or `runtimeOnly` declaration SHALL be added to the `rules` module.

The previous `verifyPomHasNoDependencies` task, which parsed the generated POM and failed `check` on
any declared dependency, SHALL NOT be reinstated. The property it defended is stated here; a
`check`-time XML parse of a build output is a heavy way to state "do not use `implementation` here".

#### Scenario: The published POM is empty of dependencies
- **WHEN** the generated POM is inspected
- **THEN** it declares no dependency
- **AND** neither `pmd-core` nor `pmd-java` appears in it

#### Scenario: Every declaration is on a non-publishing configuration
- **WHEN** the `dependencies` block of `rules/build.gradle` is inspected
- **THEN** every declaration is `compileOnly`, `annotationProcessor`, `pmd`, `testImplementation`,
  `testCompileOnly` or `testRuntimeOnly`
- **AND** none is `implementation`, `api` or `runtimeOnly`

#### Scenario: Test and processor dependencies do not reach the POM
- **WHEN** `pmd-test` is declared `testImplementation` and Lombok `annotationProcessor`
- **THEN** the published POM still declares no dependency

#### Scenario: No verification task guards this
- **WHEN** the `rules` module's tasks are inspected
- **THEN** no task parses the generated POM
- **AND** `check` depends on no such task

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
