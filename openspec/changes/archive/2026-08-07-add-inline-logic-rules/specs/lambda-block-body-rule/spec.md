## ADDED Requirements

### Requirement: AvoidLambdaBlockBodies reports lambdas with a block body
The artifact SHALL provide a rule named `AvoidLambdaBlockBodies` that reports a lambda expression
whose body is a block.

The rule SHALL be implemented in Java against the PMD 7 Java AST, visiting `ASTLambdaExpression`.

Block-ness SHALL be determined from `getBlockBody() != null` rather than from `isBlockBody()`. The
two say the same thing — the accessor returns null exactly when the body is an expression — but PMD
declares it `@Nullable`, so under NullAway the `isBlockBody()` form additionally requires a null
check that can never be false. That would leave a branch no test can reach and a mutant nothing can
kill, against the project's 100% thresholds.

A lambda block body is anonymous by construction: it cannot be called from a test, cannot be stubbed
by a caller that wants to be tested in isolation, and its branches are reachable only through the
pipeline that encloses it. Logic belongs in something with a name.

#### Scenario: A multi-statement block body is reported
- **WHEN** a method body contains `items.forEach(item -> { validate(item); save(item); });`
- **THEN** the rule reports a violation on the lambda

#### Scenario: A single-statement block body is reported
- **WHEN** a method body contains `list.forEach(item -> { save(item); });`
- **THEN** the rule reports a violation, because the block converts mechanically to an expression

#### Scenario: A block body returning a value is reported
- **WHEN** a method body contains `map(x -> { return x + 1; })`
- **THEN** the rule reports a violation

#### Scenario: An expression body is not reported
- **WHEN** a method body contains `map(x -> x + 1)`
- **THEN** the rule reports no violation

#### Scenario: A method reference is not reported
- **WHEN** a method body contains `list.forEach(this::save)`
- **THEN** the rule reports no violation

### Requirement: The rule does not require a method reference
The rule SHALL accept any non-block body. An expression lambda delegating to a named method SHALL
NOT be reported, even when it is not reducible to a method reference.

This SHALL be stated explicitly in the rule's description and in `README.md`. The intuitive reading —
"use a method reference" — would make the rule close to unusable, because a lambda closing over a
local variable cannot become a method reference at all. Requiring only a non-block body keeps the fix
mechanical in every case.

PMD's stock `LambdaCanBeMethodReference` reports a bare delegation and completes the conversion. The
two rules compose; this rule SHALL NOT duplicate that check.

#### Scenario: An expression lambda closing over a local is not reported
- **WHEN** a method body contains `items.forEach(item -> process(item, context));` where `context`
  is a local variable
- **THEN** the rule reports no violation

#### Scenario: A bare delegation is not reported by this rule
- **WHEN** a method body contains `list.forEach(item -> save(item))`
- **THEN** `AvoidLambdaBlockBodies` reports no violation, leaving the method-reference conversion to
  PMD's stock `LambdaCanBeMethodReference`

### Requirement: An empty block body is exempt
The rule SHALL NOT report a lambda whose block body is empty.

`() -> { }` contains nothing to extract, so a violation on it would be unfixable — and an unfixable
violation is worse than a missed one, the standard `var-local-variables-rule` sets. The no-op
`Runnable` is a common idiom.

#### Scenario: An empty block body is not reported
- **WHEN** a method body contains `Runnable task = () -> { };`
- **THEN** the rule reports no violation

#### Scenario: An empty block body with explicit parameters is not reported
- **WHEN** a method body contains `names.forEach((String s) -> { });`
- **THEN** the rule reports no violation

#### Scenario: A block containing only a comment is not reported
- **WHEN** a lambda body is a block whose only content is a comment
- **THEN** the rule reports no violation, because it declares no statements

### Requirement: The rule does not report logic inside an expression body
The rule SHALL NOT report branching, chaining or any other logic that appears within an expression
body. Block-versus-expression is a syntactic proxy for "logic hiding in an anonymous place"; it is a
good proxy and a cheap one to determine, and it is deliberately the start rather than the whole
answer.

This limit SHALL be stated in the rule's description so the rule does not read as arbitrary.

#### Scenario: A branching expression body is not reported
- **WHEN** a method body contains `map(x -> x > 0 ? positive(x) : negative(x))`
- **THEN** the rule reports no violation

#### Scenario: A long chain in an expression body is not reported
- **WHEN** a method body contains
  `map(x -> x.getA().getB().stream().filter(Objects::nonNull).count())`
- **THEN** the rule reports no violation

### Requirement: The rule declares a minimum language version
The rule SHALL declare `minimumLanguageVersion="8"` in `category/java/joke.xml`, because lambda
expressions do not exist before Java 8.

#### Scenario: The rule is skipped below Java 8
- **WHEN** PMD analyses source at Java 7
- **THEN** the rule contributes no violations

### Requirement: The conflict with StaticMethodsModifyStaticState is documented, not resolved in code
Neither this rule nor `StaticMethodsModifyStaticState` SHALL be changed to accommodate the other,
and the conflict between them SHALL be documented instead.

A block lambda in a static field initializer has no extraction that satisfies both: the extracted
method must be `static`, and `StaticMethodsModifyStaticState` reports a static method that neither
writes private static state nor sits in a utility class.

`README.md` SHALL document
`@SuppressWarnings` as the available escape, and SHALL document the better fix — making the field
non-static, so the extracted methods become instance methods — because the shape that produces these
in bulk is the static dispatch table.

`StaticMethodsModifyStaticState` SHALL NOT gain an exemption for static methods called only from
static initializers. Such an exemption is provable only for a `private static` method, whose call
sites are all in one compilation unit; it is not provable for the package-private method a test can
actually call. It would therefore bless the unreachable form while still reporting the reachable one.

#### Scenario: The README documents both escapes
- **WHEN** the `AvoidLambdaBlockBodies` section of `README.md` is inspected
- **THEN** it documents `@SuppressWarnings` for a block lambda in a static field initializer
- **AND** it documents making the field non-static as the preferred fix

### Requirement: The rule is documented in the category file and the README
`category/java/joke.xml` SHALL declare the rule with its implementing class, a message, a
description, a priority and both a violating and a compliant example. `rulesets/java/joke.xml` SHALL
include it.

#### Scenario: The category entry is complete
- **WHEN** the `AvoidLambdaBlockBodies` entry in `category/java/joke.xml` is inspected
- **THEN** it declares a `class`, a `message`, a `<description>`, a `<priority>` and an `<example>`
- **AND** the description states that a method reference is not required

#### Scenario: The convenience ruleset includes the rule
- **WHEN** `rulesets/java/joke.xml` is loaded
- **THEN** it contains `AvoidLambdaBlockBodies`

### Requirement: The rule is covered by unit tests and mutation testing
The rule SHALL be unit-tested with JUnit 5 using `pmd-test`, with a fixture covering every reported
and every non-reported case above, and SHALL meet the project's 100% mutation, coverage and test
strength thresholds.

The fixtures SHALL be treated as the only evidence the rule works. This repository contains no block
lambda in Java source, so dogfooding cannot catch a mistake in this rule.

The rule class SHALL itself comply with every rule this artifact publishes.

#### Scenario: Every specified case has a fixture
- **WHEN** the rule's test data is inspected
- **THEN** it contains a case for each reported and each non-reported scenario in this spec

#### Scenario: Mutation thresholds are met
- **WHEN** `./gradlew pitest` runs
- **THEN** the rule class meets 100% mutation coverage, line coverage and test strength
