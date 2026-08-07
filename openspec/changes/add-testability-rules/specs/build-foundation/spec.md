## MODIFIED Requirements

### Requirement: Test stack
Tests SHALL be written as JUnit 5 tests in Java. The `rules` module SHALL NOT apply the `groovy`
plugin and SHALL NOT depend on Spock, Groovy or `SpockConfig.groovy`.

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
`integration`, and `check` SHALL depend on `integrationTest`.

The `rules` module SHALL declare a `compileOnly` dependency providing the `@VisibleForTesting`
annotation. It SHALL NOT be `api` or `implementation`: the published POM declares no dependencies,
and the annotation has no runtime retention requirement for consumers.

#### Scenario: No Groovy test stack
- **WHEN** `rules/build.gradle` and the `dependencies` platform are inspected
- **THEN** neither declares Spock or Groovy
- **AND** no `SpockConfig.groovy` exists

#### Scenario: Mockito is available to unit tests
- **WHEN** `rules/build.gradle` is inspected
- **THEN** Mockito is declared on `testImplementation`
- **AND** its version is constrained in the `dependencies` platform

#### Scenario: The annotation dependency does not leak into the POM
- **WHEN** `./gradlew :rules:verifyPomHasNoDependencies` runs after the annotation dependency is
  added
- **THEN** it passes, because the dependency is `compileOnly`

#### Scenario: Tests are split by tag
- **WHEN** the `test` and `integrationTest` tasks are configured
- **THEN** `test` includes the `unit` tag and `integrationTest` includes the `integration` tag

#### Scenario: Fixtures remain the end-to-end check
- **WHEN** the rule tests are inspected
- **THEN** each rule still has a `pmd-test` XML descriptor exercising it through PMD
