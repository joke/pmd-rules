# anonymous-class-rule Specification

## Purpose

The `AvoidAnonymousClasses` rule: which anonymous class declarations it reports, the empty-body and
enum-constant-body exemptions, why it ships together with `lambda-block-body-rule` as each is the
other's bypass, and its documentation, test and mutation-coverage obligations.

## Requirements

### Requirement: AvoidAnonymousClasses reports anonymous classes with a body
The artifact SHALL provide a rule named `AvoidAnonymousClasses` that reports an anonymous class
declaration whose body is not empty.

The rule SHALL be implemented in Java against the PMD 7 Java AST, visiting
`ASTAnonymousClassDeclaration`, which exists at the supported PMD 7.0.0 floor.

The rule SHALL NOT declare a `minimumLanguageVersion`: anonymous classes predate every version PMD 7
analyses.

An anonymous class body holds logic that has no name, cannot be instantiated by a test, and cannot be
stubbed. It is the less testable of the two anonymous forms — unlike a lambda it can declare several
methods and its own fields. The compliant form is a named class, whose methods are reachable through
the type.

#### Scenario: An anonymous class with a method body is reported
- **WHEN** a method body contains
  `new Function<Item, Result>() { @Override public Result apply(Item i) { return convert(i); } }`
- **THEN** the rule reports a violation on the anonymous class

#### Scenario: An anonymous class with a field is reported
- **WHEN** an anonymous class declares only a field and no method
- **THEN** the rule reports a violation

#### Scenario: A named nested class is not reported
- **WHEN** the same logic is declared as a named nested class and instantiated with `new Converter()`
- **THEN** the rule reports no violation

#### Scenario: A lambda is not reported
- **WHEN** a method body contains `map(x -> convert(x))`
- **THEN** the rule reports no violation, because `AvoidLambdaBlockBodies` owns lambdas

### Requirement: This rule and AvoidLambdaBlockBodies close each other's bypass
Both rules SHALL ship together. Neither is sound alone: a rule against lambda block bodies is escaped
by rewriting the lambda as an anonymous class, and the anonymous class is the less testable of the
two.

#### Scenario: The lambda workaround is also reported
- **WHEN** a reported lambda block body is rewritten as an anonymous class implementing the same
  functional interface
- **THEN** `AvoidAnonymousClasses` reports the result

### Requirement: An empty anonymous class body is exempt
The rule SHALL NOT report an anonymous class whose body declares nothing.

The empty body is the mechanism in the runtime type-capture idiom — `new TypeToken<List<String>>() {}`
and `new TypeReference<Map<String, X>>() {}` exist precisely to create an anonymous subclass carrying
a generic signature. There is nothing to extract and no rewrite preserves the behaviour, so a
violation would be unfixable, and an unfixable violation is worse than a missed one.

#### Scenario: A type-capture idiom is not reported
- **WHEN** a method body contains `new TypeToken<java.util.List<String>>() { }`
- **THEN** the rule reports no violation

#### Scenario: An anonymous class with an empty body is not reported
- **WHEN** a method body contains `new Object() { }`
- **THEN** the rule reports no violation

### Requirement: Enum constant bodies are exempt
The rule SHALL NOT report an anonymous class declaration that is the body of an enum constant.

PMD represents an enum constant body as an anonymous class: `ASTEnumConstant.isAnonymousClass()`
reports it and `ASTEnumConstant.getAnonymousClass()` returns the very node this rule visits. Without
this exemption the rule reports the strategy enum, which has no anonymous-free rewrite that keeps the
enum — an unfixable violation.

This is the exemption most easily missed, because nothing in the source text of an enum constant body
says "anonymous class".

#### Scenario: A strategy enum is not reported
- **WHEN** a source file declares
  `enum Op { PLUS { int apply(int a, int b) { return a + b; } }; abstract int apply(int a, int b); }`
- **THEN** the rule reports no violation

#### Scenario: An anonymous class inside an enum method is still reported
- **WHEN** a method declared on an enum contains an anonymous class with a non-empty body
- **THEN** the rule reports a violation, because it is not the body of an enum constant

### Requirement: The rule is documented in the category file and the README
`category/java/joke.xml` SHALL declare the rule with its implementing class, a message, a
description, a priority and both a violating and a compliant example. `rulesets/java/joke.xml` SHALL
include it.

`README.md` SHALL document the rule alongside `AvoidLambdaBlockBodies`, including that the two exist
together because each is the other's bypass, and both exemptions.

#### Scenario: The category entry is complete
- **WHEN** the `AvoidAnonymousClasses` entry in `category/java/joke.xml` is inspected
- **THEN** it declares a `class`, a `message`, a `<description>`, a `<priority>` and an `<example>`

#### Scenario: The convenience ruleset includes the rule
- **WHEN** `rulesets/java/joke.xml` is loaded
- **THEN** it contains `AvoidAnonymousClasses`

### Requirement: The rule is covered by unit tests and mutation testing
The rule SHALL be unit-tested with JUnit 5 using `pmd-test`, with a fixture covering every reported
and every non-reported case above, and SHALL meet the project's 100% mutation, coverage and test
strength thresholds.

The fixtures SHALL be treated as the only evidence the rule works: this repository declares no
anonymous class, so dogfooding cannot catch a mistake in this rule.

The rule class SHALL itself comply with every rule this artifact publishes.

#### Scenario: Every specified case has a fixture
- **WHEN** the rule's test data is inspected
- **THEN** it contains a case for each reported and each non-reported scenario in this spec

#### Scenario: Mutation thresholds are met
- **WHEN** `./gradlew pitest` runs
- **THEN** the rule class meets 100% mutation coverage, line coverage and test strength
