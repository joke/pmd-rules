## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: Cross-version compatibility is verified by the build
The build SHALL register one `integrationTest`-tagged task per supported PMD version, each resolving
`pmd-core` and `pmd-java` at that version and running the built rules against a fixture tree. The
set SHALL include the floor (7.0.0) and the newest supported version. `check` SHALL depend on all of
them, so that `./gradlew check` reproduces the matrix locally.

These tests SHALL NOT use `pmd-test`, because `RuleTst` is itself versioned and would confound a
PMD-version failure with a harness-version failure. They SHALL drive `PMDConfiguration`,
`RuleSetLoader`, `PmdAnalysis` and `RuleViolation` directly.

Dogfooding SHALL NOT be treated as a substitute for the matrix. `pmdMain` and `pmdTest` run the rules
under the single version Gradle's `toolVersion` selects — the newest supported version — against this
repository's real source, which is a third and complementary signal:

| task | PMD version | code analysed |
|---|---|---|
| `integrationTest` | the floor | synthetic fixtures |
| the additional matrix tasks | each remaining supported version | synthetic fixtures |
| `pmdMain` / `pmdTest` | Gradle's `toolVersion` | this repository's real source |

The matrix alone SHALL own the floor, because Gradle runs one PMD version and a green `pmdMain`
therefore says nothing about 7.0.0.

#### Scenario: Every supported version is exercised
- **WHEN** `./gradlew check` runs
- **THEN** an integration test task runs for PMD 7.0.0 and for the newest supported version

#### Scenario: A binary incompatibility fails the build
- **WHEN** a rule uses API absent from the floor version
- **THEN** the integration test at 7.0.0 fails rather than the incompatibility reaching a consumer

#### Scenario: The matrix uses the stable API subset only
- **WHEN** the cross-version integration tests are inspected
- **THEN** they do not reference `pmd-test`

#### Scenario: Dogfooding does not cover the floor
- **WHEN** a rule uses API absent from 7.0.0 but present in Gradle's `toolVersion`
- **THEN** `pmdMain` passes
- **AND** the integration test at the floor fails, so `check` still fails
