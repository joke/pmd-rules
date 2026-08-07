## MODIFIED Requirements

### Requirement: The ruleset is a tracked build input
The convention plugin SHALL declare the repository ruleset with `ruleSets`, naming the classpath
resource `rulesets/java/joke-strict.xml`, and SHALL NOT declare it with `ruleSetFiles`. `ruleSets`
SHALL be assigned rather than appended, because it otherwise retains Gradle's default of
`category/java/errorprone.xml`.

`ruleSetFiles` was required while the ruleset was a loose file: `ruleSets` is a `List<String>` Gradle
cannot track as a file input, so editing `.pmd.xml` left the PMD tasks `UP-TO-DATE` against the
previous ruleset. That reasoning does not carry over to a ruleset inside a jar. The input Gradle
tracks is then the `pmd` configuration, a `@Classpath` input on the `Pmd` task: editing the resource
rebuilds the jar, the classpath changes, and the task re-runs. Tracking is therefore preserved, not
traded away.

The plugin SHALL NOT reference `$rootDir` or any other repository-relative path from its `pmd` block,
because the plugin is destined for extraction and a path into this repository's layout would not
survive it.

#### Scenario: Editing the ruleset re-runs analysis
- **WHEN** the content of `rulesets/java/joke-strict.xml` changes
- **THEN** `pmdMain` and `pmdTest` re-run rather than reporting `UP-TO-DATE`

#### Scenario: Gradle's default ruleset is not silently added
- **WHEN** the convention plugin's `pmd` block is inspected
- **THEN** `ruleSets` names exactly `rulesets/java/joke-strict.xml`
- **AND** `ruleSetFiles` is not set
- **AND** `category/java/errorprone.xml` is not among the configured rule sets

#### Scenario: The plugin carries no repository-relative path
- **WHEN** the convention plugin's `pmd` block is inspected
- **THEN** it contains no `$rootDir` reference

### Requirement: The build runs the rules it publishes
The `rules` module SHALL put `project(':rules')` on its `pmd` configuration, so that PMD analyses
this project's own source with the artifact this project builds. It SHALL be analysed with
`rulesets/java/joke-strict.xml`, the resource this project publishes, so that the composition under
test is the shipped one rather than a local file resembling it.

The rules dependency SHALL be declared by each consuming project rather than by the convention
plugin. A plugin that declared it would need to name a version, and for this repository the answer is
`project(':rules')`, which no coordinate expresses; leaving the declaration to each build removes the
need for a self-hosting escape hatch and keeps the plugin ignorant of its own version.

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

Gradle's `toolVersion` SHALL be at or above the strict ruleset's declared floor, because `pmdMain`
resolves that ruleset. Raising `toolVersion` SHALL be accompanied by adding that version to the
cross-version matrix.

#### Scenario: The pmd configuration carries the project's own artifact
- **WHEN** `rules/build.gradle` is inspected
- **THEN** `project(':rules')` is declared on a configuration that `pmd` extends from
- **AND** nothing is declared on the `pmd` configuration directly

#### Scenario: The convention plugin declares no rules dependency
- **WHEN** the convention plugin is inspected
- **THEN** it declares no dependency on this artifact and names no version of it

#### Scenario: The PMD tool is still supplied by Gradle
- **WHEN** `pmdMain` runs
- **THEN** it does not fail with `ClassNotFoundException` for a PMD class
- **AND** no PMD tool coordinate is named outside the convention plugin's `toolVersion`

#### Scenario: The repository analyses itself with the shipped composition
- **WHEN** `pmdMain` runs
- **THEN** the rules applied are those of `rulesets/java/joke-strict.xml` as published in the jar
- **AND** no ruleset file exists at the repository root

#### Scenario: toolVersion honours the strict floor
- **WHEN** the convention plugin's `toolVersion` is inspected
- **THEN** it is at or above the floor `rulesets/java/joke-strict.xml` declares

#### Scenario: Both source sets are analysed
- **WHEN** `./gradlew check` runs
- **THEN** `pmdMain` and `pmdTest` both apply the rules from `rulesets/java/joke-strict.xml`

#### Scenario: A violation in this repository fails the build
- **WHEN** source is added to this repository that violates a rule this project publishes
- **THEN** `./gradlew check` fails

#### Scenario: The repository is green when the wiring lands
- **WHEN** the wiring is first enabled
- **THEN** `./gradlew check` passes without any source change, because the repository already
  complies
