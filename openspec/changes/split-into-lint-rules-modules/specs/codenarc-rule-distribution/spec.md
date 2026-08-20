## ADDED Requirements

### Requirement: CodeNarc rules are implemented in Java
Every CodeNarc rule SHALL be a Java class extending CodeNarc's rule base types — `AbstractRule`,
`AbstractAstVisitorRule` and `AbstractAstVisitor` — which are themselves Java classes. No rule SHALL
be defined as a `.groovy` rule script referenced from a shipped ruleset.

Java rather than Groovy because it keeps the CodeNarc rules inside the same quality gates the PMD
rules sit in: Error Prone, NullAway in JSpecify mode, Spotless with Palantir Java Format, `-Werror`
and Pitest at the repository's thresholds. Groovy source forfeits the first four outright, and
Groovy bytecode carries enough synthetic scaffolding that a 100% mutation threshold is not reachable
without exclusions.

Rule classes SHALL live under `io.github.joke.lint.codenarc.rules.spock`.

#### Scenario: Rules are compiled Java
- **WHEN** `codenarc-rules/src/main` is inspected
- **THEN** every rule is a `.java` file under `io.github.joke.lint.codenarc.rules.spock`
- **AND** no `.groovy` rule script is shipped in the jar

#### Scenario: Rule classes are subject to the quality gates
- **WHEN** `./gradlew check` runs
- **THEN** Spotless, Error Prone, NullAway and Pitest all analyse the CodeNarc rule classes

### Requirement: CodeNarc is a compile-only dependency at the supported floor
The `codenarc-rules` module SHALL declare `org.codenarc:CodeNarc` as `compileOnly` at version
**4.0.0**, the oldest CodeNarc on the Groovy 5 line, and SHALL declare the same coordinate and
version as `testImplementation` so that the harness is exercised at the floor.

It SHALL also declare `org.apache.groovy:groovy` as `compileOnly`. CodeNarc's POM places every Groovy
module at `runtime` scope, so the CodeNarc declaration alone does not put `org.codehaus.groovy.ast.*`
on the compile classpath, and a Java rule extending `AbstractAstVisitor` fails to compile with
`cannot access ClassCodeVisitorSupport`. Being `compileOnly`, it reaches the published POM no more
than the CodeNarc declaration does.

Compiling against the floor rather than the newest release follows the same reasoning as the PMD
module: rules compiled against an older API run on newer CodeNarc, whereas rules compiled against a
newer API fail inside the consumer's analysis. Raising the floor SHALL be an explicit decision
recorded in a change, not a side effect of a dependency update.

There is no separate test artifact to depend on. CodeNarc ships `AbstractRuleTestCase` in its main
jar and it is already JUnit 5, so the test harness and the compile target are the same coordinate.

#### Scenario: CodeNarc is compile-only at the floor
- **WHEN** `codenarc-rules/build.gradle` and the `dependencies` platform are inspected
- **THEN** `org.codenarc:CodeNarc` is declared `compileOnly` and constrained to 4.0.0

#### Scenario: Groovy is on the compile classpath
- **WHEN** `codenarc-rules/build.gradle` is inspected
- **THEN** `org.apache.groovy:groovy` is declared `compileOnly`

#### Scenario: The harness runs at the floor
- **WHEN** the rule tests run
- **THEN** `AbstractRuleTestCase` comes from the same CodeNarc version the rules compile against

### Requirement: The supported window is the Groovy 5 line
The artifact SHALL claim support for CodeNarc 4.0.0 or later **on the Groovy 5 line only**, and SHALL
claim nothing about CodeNarc's Groovy 3 line (`3.x` with no suffix) or its Groovy 4 line
(`3.x-groovy-4.0`).

The Groovy 4 line SHALL NOT be claimed even though the rule classes would run there. The ported
composition names `SpockMissingAssert`, which CodeNarc added in 3.3.0, so the Groovy 4 line cannot
carry the shipped composition at its own oldest release; claiming it would mean a second, narrower
support window for the composition alone.

CodeNarc's compatibility surface is two-dimensional in a way PMD's is not: the artifact coordinate
itself encodes the Groovy line. The rule classes reference only `AbstractAstVisitorRule`,
`AbstractAstVisitor`, `Violation`, `SourceCode` and `org.codehaus.groovy.ast.*`, all of which are
plausibly stable across all three lines — but nothing in this build tests that, and a plausible
claim is not a supported one.

Widening the window SHALL be a change carrying a test matrix, not a README edit.

#### Scenario: The claimed window is stated in the README
- **WHEN** the CodeNarc versions section of `README.md` is inspected
- **THEN** it names 4.0.0 as the floor
- **AND** it states that the Groovy 3 and Groovy 4 lines are unsupported

#### Scenario: No untested line is claimed
- **WHEN** the shipped resources and `README.md` are inspected
- **THEN** neither claims compatibility with a `-groovy-4.0` or suffix-free 3.x release

#### Scenario: The composition resolves at the floor
- **WHEN** `rulesets/groovy/joke-strict.groovy` is loaded at the declared floor
- **THEN** every rule name in it resolves
- **AND** no second support window is documented for it

### Requirement: The published POM declares no dependencies
The published artifact SHALL declare no `compile`- or `runtime`-scope dependency. The consuming
project supplies CodeNarc at a version it chooses, via Gradle's `codenarc` configuration, and this
project SHALL NOT impose one transitively.

As in the PMD module, this SHALL be secured by configuration choice rather than by a verification
task: every dependency the `codenarc-rules` module declares SHALL sit on a configuration that
structurally cannot reach the POM. No `implementation`, `api` or `runtimeOnly` declaration SHALL be
added.

Spock and Groovy SHALL be declared on test configurations only, so that a test-only stack cannot
reach a consumer.

#### Scenario: The published POM is empty of dependencies
- **WHEN** the generated POM is inspected
- **THEN** it declares no dependency
- **AND** neither `org.codenarc:CodeNarc`, Groovy nor Spock appears in it

#### Scenario: Every declaration is on a non-publishing configuration
- **WHEN** the `dependencies` block of `codenarc-rules/build.gradle` is inspected
- **THEN** every declaration is `compileOnly`, `annotationProcessor`, `pmd`, `codenarc`,
  `testImplementation`, `testCompileOnly` or `testRuntimeOnly`
- **AND** none is `implementation`, `api` or `runtimeOnly`

### Requirement: Shipped rulesets use CodeNarc's Groovy DSL
The artifact SHALL ship exactly two rule resources:

- `rulesets/groovy/joke.groovy` — every rule this artifact defines, each referenced by class.
- `rulesets/groovy/joke-strict.groovy` — the whole analysis this project runs on itself: the stock
  composition together with `ruleset('rulesets/groovy/joke.groovy')`.

Both SHALL be written in CodeNarc's Groovy ruleset DSL rather than its XML form. CodeNarc's
`RuleRegistryInitializer` instantiates only `PropertiesFileRuleRegistry`, whose properties filename
is hardcoded to `codenarc-base-rules.properties`, so bare rule names resolve only for rules
registered inside CodeNarc's own jar. The XML form would therefore require a fully-qualified class
name for each of the stock rules in the composition, where the DSL accepts the bare names.

This artifact's own rules SHALL be referenced by class rather than by bare name, because that same
registry mechanism offers no way for a third-party jar to register a name.

There is no `category` resource. CodeNarc has no category concept, so the PMD module's three-file
split reduces to two here.

#### Scenario: Both resources are published
- **WHEN** the published jar is inspected
- **THEN** it contains `rulesets/groovy/joke.groovy` and `rulesets/groovy/joke-strict.groovy`

#### Scenario: Own rules are referenced by class
- **WHEN** `rulesets/groovy/joke.groovy` is inspected
- **THEN** each rule this artifact defines is referenced by its fully-qualified class
- **AND** no bare name is used for a rule this artifact defines

#### Scenario: The convenience ruleset selects every rule this artifact defines
- **WHEN** `rulesets/groovy/joke.groovy` is loaded
- **THEN** the resulting rule set contains every rule class this artifact ships

### Requirement: The convenience ruleset references nothing outside this artifact
`rulesets/groovy/joke.groovy` SHALL reference only classes this artifact defines, and SHALL NOT name
a CodeNarc stock rule or stock ruleset.

A reference to a stock name resolves against the consumer's CodeNarc version, where a renamed or
removed rule is a hard ruleset-load failure. Referencing only its own classes is what lets this
resource carry the artifact's own floor rather than a narrower one.

`rulesets/groovy/joke-strict.groovy` is the single exception and SHALL be the only shipped resource
permitted to name a rule this artifact does not define.

#### Scenario: The convenience ruleset names no stock rule
- **WHEN** `rulesets/groovy/joke.groovy` is inspected
- **THEN** it references no CodeNarc stock rule or stock ruleset

#### Scenario: The strict ruleset is the only file naming external rules
- **WHEN** the shipped resources are inspected
- **THEN** `rulesets/groovy/joke-strict.groovy` is the only one naming a rule this artifact does not
  define

### Requirement: The strict ruleset publishes the composition previously kept at the repository root
`rulesets/groovy/joke-strict.groovy` SHALL contain every rule currently listed in the repository-root
`.codenarc.groovy`, retaining its grouping comments, together with a reference to
`rulesets/groovy/joke.groovy`. `.codenarc.groovy` SHALL then be deleted, so that the composition
exists in exactly one place and that place is the published artifact.

This mirrors the migration the PMD side already performed when `.pmd.xml` became
`rulesets/java/joke-strict.xml`.

The composition is an allowlist of individually named stock rules rather than whole stock rulesets
with exclusions. That is what `.codenarc.groovy` is today and it SHALL be carried over as such: an
allowlist adopts no rule a future CodeNarc adds, which is the safer default for a file whose every
name is a potential load failure.

The strict ruleset SHALL declare its own support window in a `description`, stating that it names
rules this artifact does not define and therefore carries a narrower promise than
`rulesets/groovy/joke.groovy`. As on the PMD side, that floor is a stated promise and SHALL NOT be
asserted by any task, guard or version list in the build.

Exclusions added to make the composition workable against Spock sources SHALL each carry a comment
naming what misfired, so that the exclusion reads as evidence for a future rule rather than as an
unexplained omission.

#### Scenario: The root file is gone and its content shipped
- **WHEN** the repository root is inspected
- **THEN** `.codenarc.groovy` does not exist
- **AND** `rulesets/groovy/joke-strict.groovy` contains every rule it previously listed

#### Scenario: The composition is an allowlist
- **WHEN** `rulesets/groovy/joke-strict.groovy` is inspected
- **THEN** it names stock rules individually
- **AND** it does not reference a whole CodeNarc stock ruleset

#### Scenario: The support window travels with the resource
- **WHEN** `rulesets/groovy/joke-strict.groovy` is inspected
- **THEN** its `description` states that it names rules this artifact does not define
- **AND** no build task asserts that window

#### Scenario: Every exclusion is explained
- **WHEN** a stock rule is excluded or omitted to make the composition pass against Spock sources
- **THEN** a comment records which construct it misfired on

### Requirement: The ruleset is resolved from the classpath through a stub
The convention plugin SHALL point CodeNarc at the shipped ruleset by supplying a stub whose only
content is a classpath reference, and SHALL NOT reference `$rootDir` or any other
repository-relative path.

Gradle offers no classpath option for a CodeNarc ruleset: `CodeNarcActionParameters` exposes only
`config`, `compilationClasspath`, the three violation thresholds, reports, `ignoreFailures` and
`source`, and `CodeNarcInvoker` passes the ruleset to the Ant task as `"file:" + config`. CodeNarc's
own `RuleSetUtil` does resolve nested `ruleset(...)` references from the classpath, so a `file:`
stub containing one such reference reaches the shipped resource.

The mechanism SHALL satisfy the same three properties the PMD block already satisfies: no
repository-relative path, the policy living inside the published jar rather than a loose file, and
Gradle input tracking preserved through the `codenarc` configuration's classpath.

The stub SHALL be written in the Groovy DSL. `RuleSetUtil` dispatches on file extension — XML, then
JSON, then Groovy DSL as the fallback — so a stub materialised without a meaningful extension is
parsed as Groovy.

The stub SHALL nest its classpath reference inside a `ruleset { … }` block. `RuleSetBuilder` exposes
only `ruleset(Closure)` at the top level, and `ruleset(String)` is a method on the closure's
delegate; a bare top-level `ruleset('…')` fails at analysis time with a `MissingMethodException`.

#### Scenario: The plugin carries no repository-relative path
- **WHEN** the convention plugin's `codenarc` block is inspected
- **THEN** it contains no `$rootDir` reference
- **AND** it names the shipped resource by its classpath path

#### Scenario: The shipped composition is the one analysis uses
- **WHEN** `codenarcTest` runs
- **THEN** the rules applied are those of `rulesets/groovy/joke-strict.groovy` as published in the jar
- **AND** no ruleset file exists at the repository root

#### Scenario: Editing the ruleset re-runs analysis
- **WHEN** the content of `rulesets/groovy/joke-strict.groovy` changes
- **THEN** `codenarcTest` re-runs rather than reporting `UP-TO-DATE`

### Requirement: Rule tests are Spock specifications
The `codenarc-rules` module SHALL test its rules with Spock specifications under
`codenarc-rules/src/test/groovy`, driving CodeNarc's `AbstractRuleTestCase` where it is useful.

There is no XML fixture harness for CodeNarc, so the PMD module's requirement that test data stay in
XML has no counterpart here: violating and compliant samples are embedded in the specification as
Groovy strings.

Every specification SHALL carry a `@spock.lang.Tag`, either `unit` or `integration`, because the
`test` / `integrationTest` split selects on it and an untagged specification runs in neither task.
Rule specifications SHALL be tagged `unit`, so that Pitest's `includedGroups` selects them.

The classpath resolution of the shipped rulesets SHALL be exercised by a specification tagged
`integration`, mirroring the PMD module's ruleset distribution tests: resolution from the `codenarc`
configuration is the single mechanism the distribution model depends on, so it SHALL be exercised on
every build rather than discovered by the first consumer.

That specification SHALL initialise CodeNarc's rule registry before loading a ruleset.
`CodeNarcRunner` populates the registry on a real run and nothing populates it when `RuleSetUtil` is
driven directly, so without the initialisation the strict ruleset fails to load with `No such rule
named [...]` — a message that reads as a distribution fault rather than an uninitialised registry.

#### Scenario: Specifications are tagged
- **WHEN** a Spock specification in `codenarc-rules` is inspected
- **THEN** it carries `@spock.lang.Tag` naming either `unit` or `integration`

#### Scenario: Rule specifications reach mutation analysis
- **WHEN** `./gradlew test` and `./gradlew pitest` run
- **THEN** both execute the `unit`-tagged rule specifications

#### Scenario: A missing or misnamed ruleset resource fails the build
- **WHEN** a shipped ruleset is absent from the jar or named differently
- **THEN** the `integration`-tagged specification fails

#### Scenario: Samples live in the specification
- **WHEN** a rule's test data is inspected
- **THEN** every violating and compliant sample is embedded in the specification
- **AND** no separate fixture file carries a deliberate violation

### Requirement: The Spock specifications are the corpus the rules analyse
The `codenarc-rules` module SHALL declare `codenarc project(':codenarc-rules')`, so that CodeNarc
analyses this module's Spock specifications with the artifact this module builds.

This is what makes the CodeNarc artifact dogfooded rather than merely unit-tested. The rules are
Spock-focused and the specifications are Spock, so the corpus is the kind of source the rules exist
to analyse.

`project(':codenarc-rules')` is preferred over the module's own class output because it puts the
packaged artifact on the analysis classpath — the shape a consumer receives — and can therefore catch
a packaging fault such as a ruleset resource missing from the jar.

The module SHALL also declare `pmd project(':pmd-rules')`, so that its Java rule classes are analysed
by the PMD artifact this repository publishes.

#### Scenario: The module analyses itself with its own artifact
- **WHEN** `codenarc-rules/build.gradle` is inspected
- **THEN** `project(':codenarc-rules')` is declared on the `codenarc` configuration
- **AND** `project(':pmd-rules')` is declared on the `pmd` configuration

#### Scenario: A violation in the specifications fails the build
- **WHEN** a Spock specification is added that violates a rule this module publishes
- **THEN** `./gradlew check` fails

#### Scenario: Both analyses run
- **WHEN** `./gradlew check` runs
- **THEN** `pmdMain` analyses the Java rule classes
- **AND** `codenarcTest` analyses the Spock specifications

### Requirement: Consumer wiring is documented
`README.md` SHALL document consumption of the CodeNarc artifact as adding
`io.github.joke.lint:codenarc-rules` to the Gradle `codenarc` configuration and pointing the
`codenarc` extension at a local stub that references the shipped ruleset.

It SHALL state plainly that the local stub is required because Gradle accepts only a file, and that
the stub is a pointer rather than a policy — the composition lives in the artifact.

#### Scenario: README shows the wiring
- **WHEN** `README.md` is inspected
- **THEN** it shows `io.github.joke.lint:codenarc-rules` added to the `codenarc` configuration
- **AND** it shows a stub referencing `rulesets/groovy/joke-strict.groovy`

#### Scenario: The reason for the stub is stated
- **WHEN** the CodeNarc usage section of `README.md` is inspected
- **THEN** it states that Gradle cannot take a classpath ruleset directly

### Requirement: The first release ships exactly one rule
`rulesets/groovy/joke.groovy` SHALL declare `AvoidUnrollAnnotation` from the first release, and the
remaining Spock rules SHALL land as separate changes, one rule at a time, matching how the PMD rules
landed.

The module SHALL NOT ship with no rules at all. Pitest is configured with
`failWhenNoMutations = true` and `check` depends on it, so a module with no rule classes has nothing
to mutate and cannot build. Relaxing that flag for this module was rejected: a quality gate switched
off "for now" is the one most likely to stay off, and one real rule proves the whole loop — Pitest
mutating rule code, PMD analysing it, and CodeNarc analysing the specifications that test it — which
an empty module could only assert.

#### Scenario: The convenience ruleset declares the first rule
- **WHEN** `rulesets/groovy/joke.groovy` is loaded
- **THEN** the resulting rule set contains `AvoidUnrollAnnotation`

#### Scenario: Mutation analysis has something to measure
- **WHEN** `./gradlew :codenarc-rules:pitest` runs
- **THEN** it reports mutations rather than failing with "No mutations found"
- **AND** `failWhenNoMutations` is not relaxed for this module

#### Scenario: The strict ruleset is usable on its own
- **WHEN** `rulesets/groovy/joke-strict.groovy` is loaded from the first release
- **THEN** it resolves the stock composition
- **AND** it resolves every rule declared in `rulesets/groovy/joke.groovy`
