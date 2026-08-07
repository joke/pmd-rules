## ADDED Requirements

### Requirement: AvoidPrivateAndProtectedMethods permits only public and package-private methods
The artifact SHALL provide a rule named `AvoidPrivateAndProtectedMethods` that reports a method
declared `private` or `protected`. `public` and package-private methods SHALL NOT be reported.

The rule SHALL be implemented in Java against the PMD 7 Java AST, and SHALL NOT declare a
`minimumLanguageVersion`.

The rationale is that the house test style stubs a sibling method on a spy of the subject under test.
A `private` method is not reachable from the test and cannot be stubbed. `protected` is reachable,
but is the wrong seam: it widens the API to every subclass in every consumer's codebase, where
package-private widens it only to the package the test lives in.

Exactly two legal visibilities, with no "sometimes package-private is acceptable" middle ground, is
deliberate — the rule exists to remove discretion, and a third permitted form reintroduces it.

#### Scenario: A private method is reported
- **WHEN** a class declares `private boolean check() { return true; }`
- **THEN** the rule reports a violation

#### Scenario: A protected method is reported
- **WHEN** a class declares `protected boolean check() { return true; }`
- **THEN** the rule reports a violation

#### Scenario: A package-private method is not reported
- **WHEN** a class declares `boolean check() { return true; }`
- **THEN** the rule reports no violation

#### Scenario: A public method is not reported
- **WHEN** a class declares `public boolean check() { return true; }`
- **THEN** the rule reports no violation

### Requirement: Constructors are out of scope
The rule SHALL NOT report constructors of any visibility. A constructor is not spied, and a `private`
constructor is required by the utility-class exception in `static-method-state-rule` and by the stock
`UseUtilityClass` rule.

#### Scenario: A private constructor is not reported
- **WHEN** a class declares `private Example() { }`
- **THEN** the rule reports no violation

#### Scenario: A protected constructor is not reported
- **WHEN** a class declares `protected Example() { }`
- **THEN** the rule reports no violation

### Requirement: Overriding methods are exempt
The rule SHALL NOT report a method annotated `@Override`. An overriding method's visibility is fixed
by its supertype and is not the author's to choose — Java forbids narrowing it, and a framework
superclass that declares a `protected` hook requires a `protected` override.

The annotation SHALL be matched by simple name.

#### Scenario: A protected override is not reported
- **WHEN** a class declares `@Override protected void setUp() { }`
- **THEN** the rule reports no violation

#### Scenario: A protected method without @Override is reported
- **WHEN** a class declares `protected void setUp() { }` with no `@Override`
- **THEN** the rule reports a violation

### Requirement: Static methods are left to the static rule
The rule SHALL NOT report a `static` method, regardless of its visibility.
`StaticMethodsModifyStaticState` reports it first; once staticness is fixed this rule reports the
visibility on the next run.

Cascading is preferred to simultaneous reporting: one method then produces one violation with one
obvious fix, rather than two reports on one line.

#### Scenario: A private static method is reported once, by the static rule
- **WHEN** a class with instance methods declares `private static boolean check() { return true; }`
- **THEN** `AvoidPrivateAndProtectedMethods` reports no violation
- **AND** `StaticMethodsModifyStaticState` reports one

#### Scenario: A private method in a utility class is reported
- **WHEN** a utility class declares `private static boolean check()`
- **THEN** `StaticMethodsModifyStaticState` reports no violation, because the type is exempt
- **AND** `AvoidPrivateAndProtectedMethods` reports no violation, because the method is static

### Requirement: The rule does not attempt cross-file subclass analysis
The rule SHALL report `protected` methods unconditionally rather than only when no subclass uses
them, and this SHALL be recorded as a deliberate limit rather than an omission.

PMD cannot perform the check. `Rule.deepCopy()` gives each analysis thread its own rule copy, so
instance state does not aggregate across files; every `RuleContext.addViolation` overload requires a
live `Node`, so nothing can be reported after a file's listener closes; and `Rule.end(RuleContext)`
fires per thread-batch against the last file processed. PMD's unit of analysis is one compilation
unit, and "does any subclass exist" is a whole-module question.

The one case provable within a single file — `protected` in a `final` class, where no subclass can
exist — is already covered by the stock `AvoidProtectedMethodInFinalClassNotExtending`, which
`.pmd.xml` enables.

A genuine extension point SHALL be handled with `@SuppressWarnings`.

#### Scenario: A protected method with a subclass in the same file is still reported
- **WHEN** a class declares `protected void hook()` and a nested subclass overrides it
- **THEN** the rule reports a violation on the declaration

#### Scenario: Suppression is available for an extension point
- **WHEN** a `protected` method carries
  `@SuppressWarnings("PMD.AvoidPrivateAndProtectedMethods")`
- **THEN** the rule reports no violation

### Requirement: No stock rule is excluded to accommodate this rule
Choosing package-private over `protected` SHALL leave `.pmd.xml` unchanged.
`AvoidProtectedMethodInFinalClassNotExtending` and `AvoidProtectedFieldInFinalClass`, both enabled
through `category/java/codestyle.xml`, fire only on `protected` and therefore never conflict.

#### Scenario: The repository ruleset gains no exclusions
- **WHEN** `.pmd.xml` is inspected after this change
- **THEN** it excludes neither `AvoidProtectedMethodInFinalClassNotExtending` nor
  `AvoidProtectedFieldInFinalClass`

### Requirement: The rule is documented in the category file and the README
`category/java/joke.xml` SHALL declare the rule with its implementing class, a message, a
description, a priority and both a violating and a compliant example. `rulesets/java/joke.xml` SHALL
include it. `README.md` SHALL document it, including the `@Override` exemption and the suppression
path for extension points.

#### Scenario: The category entry is complete
- **WHEN** the `AvoidPrivateAndProtectedMethods` entry in `category/java/joke.xml` is inspected
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
