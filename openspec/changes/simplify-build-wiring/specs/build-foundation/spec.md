## MODIFIED Requirements

### Requirement: The build runs the rules it publishes
The `rules` module SHALL put `project(':rules')` on its `pmd` configuration, so that PMD analyses
this project's own source with the artifact this project builds. It SHALL be analysed with
`rulesets/java/joke-strict.xml`, the resource this project publishes, so that the composition under
test is the shipped one rather than a local file resembling it.

The rules dependency SHALL be declared by each consuming project rather than by the convention
plugin. A plugin that declared it would need to name a version, and for this repository the answer is
`project(':rules')`, which no coordinate expresses; leaving the declaration to each build removes the
need for a self-hosting escape hatch and keeps the plugin ignorant of its own version.

The dependency SHALL be declared on the `pmd` configuration directly. This is possible because the
convention plugin supplies the PMD tool by an explicit dependency rather than through
`toolVersion`, so `PmdPlugin`'s `defaultDependencies` mechanism — which applies only while the
configuration's own dependency set is empty — is not relied upon and need not be kept armed. No
intermediate configuration SHALL exist for the sole purpose of keeping that dependency set empty.

The rules SHALL apply to both the `pmdMain` and `pmdTest` source sets.

`project(':rules')` is preferred over `files(sourceSets.main.output)` because it puts the packaged
artifact on the analysis classpath — the shape a consumer actually receives — and can therefore catch
a packaging fault such as a resource missing from the jar.

#### Scenario: The pmd configuration carries the project's own artifact
- **WHEN** `rules/build.gradle` is inspected
- **THEN** `project(':rules')` is declared on the `pmd` configuration directly
- **AND** no intermediate configuration is declared that `pmd` extends from

#### Scenario: The convention plugin declares no rules dependency
- **WHEN** the convention plugin is inspected
- **THEN** it declares no dependency on this artifact and names no version of it

#### Scenario: The PMD tool is still available to analysis
- **WHEN** `pmdMain` runs
- **THEN** it does not fail with `ClassNotFoundException` for a PMD class
- **AND** the tool comes from the coordinate the convention plugin declares, not from
  `PmdPlugin`'s `defaultDependencies`

#### Scenario: The repository analyses itself with the shipped composition
- **WHEN** `pmdMain` runs
- **THEN** the rules applied are those of `rulesets/java/joke-strict.xml` as published in the jar
- **AND** no ruleset file exists at the repository root

#### Scenario: Both source sets are analysed
- **WHEN** `./gradlew check` runs
- **THEN** `pmdMain` and `pmdTest` both apply the rules from `rulesets/java/joke-strict.xml`

#### Scenario: A violation in this repository fails the build
- **WHEN** source is added to this repository that violates a rule this project publishes
- **THEN** `./gradlew check` fails

### Requirement: Test stack
Tests SHALL be written as JUnit 5 tests in Java. The `rules` module SHALL NOT apply the `groovy`
plugin and SHALL NOT depend on Spock, Groovy or `SpockConfig.groovy`.

The `dependencies` platform SHALL declare the JUnit 5 BOM, not JUnit 6. `pmd-test` at the 7.0.0 floor
is built against JUnit 5.8.2, and that is the harness the rule tests run inside. Moving to JUnit 6 is
a change of its own, to be taken deliberately with the `pmd-test` interaction examined, and SHALL NOT
happen as a side effect of a routine dependency bump.

The `rules` module SHALL depend on Mockito for unit tests, so that a method under test can be
isolated from the sibling methods it calls by spying the subject and stubbing those siblings. This is
the test style the rules in `static-method-state-rule`, `method-visibility-rule` and
`visible-for-testing-rule` exist to keep possible, and without it those rules would create seams
nothing uses.

Mockito rather than Spock, because Spock would mean adding Groovy to a project that deliberately has
none, and Mockito sits alongside the JUnit 5 tests already present. Mockito's default inline mock
maker also mocks `final` methods, which is why no rule about `final` is needed.

`pmd-test` XML fixtures SHALL remain the end-to-end check of rule behaviour. Mockito SHALL NOT
replace them; it covers the branches fixtures cannot reach, such as a rule's `visit` method
delegating to a helper.

The `test` task SHALL run tests tagged `unit` and the `integrationTest` task SHALL run tests tagged
`integration`, and `check` SHALL depend on `integrationTest`. The `integrationTest` task SHALL use
the test source set's own runtime classpath, and therefore SHALL resolve PMD at the floor version the
`dependencies` platform declares.

The `rules` module SHALL declare a `compileOnly` dependency providing the `@VisibleForTesting`
annotation. It SHALL NOT be `api` or `implementation`: the published POM declares no dependencies,
and the annotation has no runtime retention requirement for consumers.

#### Scenario: No Groovy test stack
- **WHEN** `rules/build.gradle` and the `dependencies` platform are inspected
- **THEN** neither declares Spock or Groovy
- **AND** no `SpockConfig.groovy` exists

#### Scenario: The JUnit line stays at 5
- **WHEN** the `dependencies` platform is inspected
- **THEN** the JUnit BOM it declares is a 5.x release
- **AND** the reason — `pmd-test` at the floor being built against JUnit 5.8.2 — is recorded

#### Scenario: Mockito is available to unit tests
- **WHEN** `rules/build.gradle` is inspected
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
- **WHEN** the rule tests are inspected
- **THEN** each rule still has a `pmd-test` XML descriptor exercising it through PMD

## ADDED Requirements

### Requirement: The PMD tool is supplied by an explicit dependency
The convention plugin SHALL supply PMD by declaring `net.sourceforge.pmd:pmd-dist` on the `pmd`
configuration, and SHALL NOT set `toolVersion`.

`pmd-dist` rather than an enumeration of `pmd-core`, `pmd-java`, `pmd-ant` and their siblings,
because that list is exactly what `toolVersion` exists to abstract and it drifts as PMD reorganises
its modules. `pmd-dist` is the artifact PMD publishes for this purpose.

The version SHALL be visible as a coordinate rather than as an extension property, so that the PMD
used for analysis reads as what it is — a dependency — and so that declaring the project's own rules
artifact on the same configuration does not displace the tool.

The declared version SHALL be at or above the floor stated by `rulesets/java/joke-strict.xml`,
because `pmdMain` resolves that ruleset. Raising it is an ordinary dependency bump: change the
coordinate and run `check`.

#### Scenario: The tool is a coordinate, not a property
- **WHEN** the convention plugin's `pmd` block is inspected
- **THEN** it declares `net.sourceforge.pmd:pmd-dist` with a version on the `pmd` configuration
- **AND** it does not set `toolVersion`

#### Scenario: The declared version honours the strict floor
- **WHEN** the declared `pmd-dist` version is compared with the floor stated in
  `rulesets/java/joke-strict.xml`
- **THEN** it is at or above that floor

#### Scenario: Adopting a newer PMD is a coordinate change
- **WHEN** a newer PMD release is adopted
- **THEN** the only build change required is the `pmd-dist` version
- **AND** no version list, matrix entry or subset guard needs updating

### Requirement: Null marking is generated, not hand-written
The `rules` module SHALL apply `io.github.joke.jspecify:processor` on the main source set's
`annotationProcessor` path, which emits a `package-info.java` carrying `@NullMarked` for every
package in that source set. No `package-info.java` SHALL be hand-written for the purpose of
declaring `@NullMarked`.

Generating it makes marking structural: a new package is null-marked because it exists, rather than
because someone remembered to add a file whose only possible content is fixed. NullAway's
`RequireExplicitNullMarking`, which remains configured as an error, thereby changes role from a check
that catches the omission after the fact to a check that can no longer fire.

A hand-written and a generated declaration SHALL NOT coexist for the same package, as two sources for
one annotation is a duplicate-class failure at compile time.

Null-safety checking SHALL be verified to be in force rather than assumed from a green build,
because a disabled checker and a correct codebase are indistinguishable from the outside.

#### Scenario: The marking is generated
- **WHEN** `compileJava` runs
- **THEN** a `package-info.java` annotated `@NullMarked` is produced under the annotation processor's
  generated sources for every package in the main source set

#### Scenario: No hand-written marking survives
- **WHEN** `rules/src/main/java` is inspected
- **THEN** it contains no `package-info.java` declaring `@NullMarked`

#### Scenario: A new package is marked without any file being written
- **WHEN** a class is added in a package that did not previously exist
- **THEN** `compileJava` succeeds without `RequireExplicitNullMarking` reporting

#### Scenario: NullAway is demonstrably still enforcing
- **WHEN** a deliberate dereference of a `@Nullable` value is introduced into a rule class
- **THEN** `compileJava` fails with a NullAway error

### Requirement: Lombok is configured but unused
The repository SHALL carry a `lombok.config` at its root and the `rules` module SHALL declare
`org.projectlombok:lombok` on both `compileOnly` and `annotationProcessor`.

No rule class uses Lombok, and this SHALL NOT be treated as an oversight. All rule classes have zero
instance fields: PMD reuses a rule instance across files within a thread, so per-file state in a rule
is a defect, and the classes are stateless behaviour by construction. Lombok generates fields,
accessors, constructors and equality, every one of which presupposes state. Lombok annotations SHALL
NOT be added to a stateless rule class to justify the dependency.

The wiring exists as a configured baseline, so that the first class that genuinely warrants Lombok
faces a code decision rather than a build decision.

`lombok.config` SHALL set `config.stopBubbling = true` (the repository root is the top of the
configuration tree), `lombok.addLombokGeneratedAnnotation = true` (so generated members are excluded
from coverage and mutation analysis, which matters at a 100% threshold),
`lombok.addNullAnnotations = jspecify` (so generated members carry the same annotations as the rest of
the source) and `lombok.experimental.flagUsage = ALLOW`.

The dependency SHALL be `compileOnly`, never `api` or `implementation`, so it cannot reach the
published POM.

#### Scenario: The configuration is present and terminal
- **WHEN** `lombok.config` is inspected
- **THEN** it sets `config.stopBubbling = true`, `lombok.addLombokGeneratedAnnotation = true`,
  `lombok.addNullAnnotations = jspecify` and `lombok.experimental.flagUsage = ALLOW`

#### Scenario: Lombok does not reach the published artifact
- **WHEN** the generated POM is inspected
- **THEN** `org.projectlombok:lombok` does not appear in it

#### Scenario: Stateless rules stay unannotated
- **WHEN** a rule class with no instance fields is inspected
- **THEN** it carries no Lombok annotation

### Requirement: Build rationale lives in the specs
The reasoning behind a non-obvious build decision SHALL be recorded in `openspec/specs/` and SHALL
NOT be duplicated as an explanatory comment in `conventions.gradle`, `dependencies/build.gradle` or
`rules/build.gradle`. The build files state what the build does; the specs state why.

Two copies of the same rationale drift, and the copies had begun to. A comment SHALL be used only
where the specs cannot reach — a warning about a line that reads as a mistake in isolation — and not
to re-explain a decision a requirement already carries.

Removing a comment SHALL NOT remove the reasoning: before a rationale comment is deleted, the
corresponding requirement SHALL be confirmed to state it.

#### Scenario: The build files carry no duplicated rationale
- **WHEN** `conventions.gradle`, `dependencies/build.gradle` and `rules/build.gradle` are inspected
- **THEN** no comment restates a decision already recorded as a requirement in `openspec/specs/`

#### Scenario: No reasoning is lost with the comments
- **WHEN** the comments removed from the build files are compared with `openspec/specs/`
- **THEN** every decision they recorded is stated by a requirement
