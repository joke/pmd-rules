## ADDED Requirements

### Requirement: UseVisibleForTestingAnnotation requires the annotation on package-private methods
The artifact SHALL provide a rule named `UseVisibleForTestingAnnotation` that reports a
package-private method not annotated `@VisibleForTesting`.

The rule SHALL be implemented in Java against the PMD 7 Java AST, and SHALL NOT declare a
`minimumLanguageVersion`.

`AvoidPrivateAndProtectedMethods` makes package-private the canonical form for an internal method.
This rule makes the widened visibility read as a deliberate test seam rather than a forgotten
modifier, which is the whole reason the wider visibility is acceptable.

Only methods SHALL be in scope. Fields, constructors and nested classes SHALL NOT be reported.

#### Scenario: An unannotated package-private method is reported
- **WHEN** a class declares `boolean check() { return true; }`
- **THEN** the rule reports a violation

#### Scenario: An annotated package-private method is not reported
- **WHEN** a class declares `@VisibleForTesting boolean check() { return true; }`
- **THEN** the rule reports no violation

#### Scenario: A public method is not reported
- **WHEN** a class declares `public boolean check() { return true; }`
- **THEN** the rule reports no violation

#### Scenario: A private method is not reported
- **WHEN** a class declares `private boolean check() { return true; }`
- **THEN** the rule reports no violation, because `AvoidPrivateAndProtectedMethods` owns that case

#### Scenario: A package-private field is not reported
- **WHEN** a class declares `String name;`
- **THEN** the rule reports no violation

### Requirement: The annotation is matched by simple name
The rule SHALL recognise any annotation whose simple name is `VisibleForTesting`, regardless of
package.

Four incompatible declarations are in common use — `org.jetbrains.annotations`,
`com.google.common.annotations`, `androidx.annotation` and `co.elastic.clients.util` — and the
annotation is a marker in all of them. Matching by simple name works for every consumer without
configuration and leaves no fully-qualified name list to go stale.

Simple-name matching also avoids depending on PMD type resolution, which requires an `auxclasspath`
that consumers frequently do not configure and which would make the rule silently pass when
misconfigured.

#### Scenario: The JetBrains annotation is recognised
- **WHEN** a package-private method carries
  `@org.jetbrains.annotations.VisibleForTesting`
- **THEN** the rule reports no violation

#### Scenario: The Guava annotation is recognised
- **WHEN** a package-private method carries
  `@com.google.common.annotations.VisibleForTesting`
- **THEN** the rule reports no violation

#### Scenario: A different annotation is not recognised
- **WHEN** a package-private method carries only `@Deprecated`
- **THEN** the rule reports a violation

### Requirement: Overriding and test methods are exempt
The rule SHALL NOT report a method annotated `@Override`, whose visibility is fixed by its supertype
and is therefore not a seam its author chose.

The rule SHALL NOT report a method carrying a recognised test annotation. JUnit 5 test and lifecycle
methods are conventionally package-private — the stock `JUnitJupiterTestShouldBePackagePrivate` rule
requires it — and annotating them `@VisibleForTesting` would be nonsense.

The recognised test annotations SHALL be, matched by simple name: `Test`, `ParameterizedTest`,
`RepeatedTest`, `TestFactory`, `TestTemplate`, `BeforeAll`, `BeforeEach`, `AfterAll`, `AfterEach`.

This set mirrors the defaults of PMD's own `CommentDefaultAccessModifier`, which treats the same
annotations as markers that a package-private member is intentional.

#### Scenario: A package-private override is not reported
- **WHEN** a class declares `@Override void run() { }`
- **THEN** the rule reports no violation

#### Scenario: A JUnit test method is not reported
- **WHEN** a test class declares `@Test void reportsViolation() { }`
- **THEN** the rule reports no violation

#### Scenario: A JUnit lifecycle method is not reported
- **WHEN** a test class declares `@BeforeEach void setUp() { }`
- **THEN** the rule reports no violation

#### Scenario: An unannotated helper in a test class is reported
- **WHEN** a test class declares `String fixture() { return "x"; }` with no annotation
- **THEN** the rule reports a violation

### Requirement: The rule is custom rather than configuration of a stock rule
The behaviour SHALL be delivered as a rule class in this artifact and SHALL NOT be delivered by
configuring PMD's `CommentDefaultAccessModifier`, whose `regex` and `ignoredAnnotations` properties
could approximate it.

`rule-distribution` requires that shipped resources reference no external ruleset, so a stock rule
with property overrides cannot be included in `rulesets/java/joke.xml`. It would instead be copied
configuration in every consumer's own ruleset, and would have to restate that rule's full
`ignoredAnnotations` default list, because PMD multi-value properties replace the default rather
than append and `org.jetbrains.annotations.VisibleForTesting` is absent from it upstream.

`CommentDefaultAccessModifier` SHALL remain excluded in `.pmd.xml`.

#### Scenario: The shipped ruleset references no stock rule
- **WHEN** `category/java/joke.xml` and `rulesets/java/joke.xml` are inspected
- **THEN** neither references `CommentDefaultAccessModifier` or any other PMD stock rule

#### Scenario: The repository ruleset keeps its existing exclusion
- **WHEN** `.pmd.xml` is inspected after this change
- **THEN** `CommentDefaultAccessModifier` is still excluded

### Requirement: The rule is documented in the category file and the README
`category/java/joke.xml` SHALL declare the rule with its implementing class, a message, a
description, a priority and both a violating and a compliant example. `rulesets/java/joke.xml` SHALL
include it.

`README.md` SHALL document the rule, SHALL note that the annotation is matched by simple name so any
`VisibleForTesting` declaration works, and SHALL note that a package-private method is stubbable only
from a test in the same package and classloader — true for standard Gradle layouts, false under JPMS
with a sealed module.

#### Scenario: The category entry is complete
- **WHEN** the `UseVisibleForTestingAnnotation` entry in `category/java/joke.xml` is inspected
- **THEN** it declares a `class`, a `message`, a `<description>`, a `<priority>` and an `<example>`

### Requirement: The rule is covered by unit tests and mutation testing
The rule SHALL be unit-tested with JUnit 5 using `pmd-test`, with a fixture covering every reported
and every non-reported case above, and SHALL meet the project's 100% mutation, coverage and test
strength thresholds.

#### Scenario: Every specified case has a fixture
- **WHEN** the rule's test data is inspected
- **THEN** it contains a case for each reported and each non-reported scenario in this spec

#### Scenario: Mutation thresholds are met
- **WHEN** `./gradlew pitest` runs
- **THEN** the rule class meets 100% mutation coverage, line coverage and test strength
