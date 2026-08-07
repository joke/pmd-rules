## ADDED Requirements

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
