# type-import-rule Specification

## Purpose

The `UseTypeImports` rule: which fully-qualified type names it reports, the clean split with PMD's
stock `UnnecessaryFullyQualifiedName`, conflict detection, per-file reporting, nested types, and its
documentation, test and mutation-coverage obligations.

## Requirements

### Requirement: UseTypeImports reports fully-qualified type names
The artifact SHALL provide a rule named `UseTypeImports` that reports a fully-qualified type name
used in code where adding an import would allow the simple name to be used instead.

The rule SHALL be implemented in Java against the PMD 7 Java AST. It SHALL NOT declare a
`minimumLanguageVersion`: imports exist in every Java version PMD 7 analyses.

The rule SHALL apply wherever a type may appear — declarations, parameters, return types, generic
arguments, `extends` and `implements` clauses, annotations, casts and `instanceof`.

#### Scenario: A fully-qualified type in a declaration is reported
- **WHEN** a class declares `private java.util.List names;` and does not import `List`
- **THEN** the rule reports a violation

#### Scenario: A fully-qualified type in a generic argument is reported
- **WHEN** a method signature contains `Map<String, java.time.Duration>` and `Duration` is not
  imported
- **THEN** the rule reports a violation

#### Scenario: A simple name already imported is not reported
- **WHEN** a file imports `java.util.List` and declares `private List names;`
- **THEN** the rule reports no violation

### Requirement: Import and package declarations are out of scope
The rule SHALL NOT report the fully-qualified name inside an import declaration or a package
declaration. Those are where a qualified name belongs.

#### Scenario: An import declaration is not reported
- **WHEN** a file contains `import java.util.List;`
- **THEN** the rule reports no violation

#### Scenario: A package declaration is not reported
- **WHEN** a file contains `package io.github.joke.pmd.rules.java;`
- **THEN** the rule reports no violation

### Requirement: The rule splits cleanly with PMD's UnnecessaryFullyQualifiedName
The rule SHALL NOT report a qualified name whose simple name is already in scope — through an
existing import, through `java.lang`, or through the same package. PMD's stock
`UnnecessaryFullyQualifiedName` reports exactly those, and its fix is different.

```
simple name already in scope   → UnnecessaryFullyQualifiedName (stock)   "drop the qualifier"
simple name not yet in scope   → UseTypeImports (this artifact)          "add an import"
```

The two therefore partition the space with no overlap, and a consumer enabling both never receives
two reports for one qualified name. This SHALL hold regardless of whether the consumer enables
`category/java/codestyle.xml`, because the split is what keeps each rule honest about what its fix
is.

#### Scenario: A java.lang type is left to the stock rule
- **WHEN** a method body contains `java.lang.String name = "joke";`
- **THEN** `UseTypeImports` reports no violation, because `String` is already in scope

#### Scenario: A same-package type is left to the stock rule
- **WHEN** a class references another type in its own package by fully-qualified name
- **THEN** `UseTypeImports` reports no violation

#### Scenario: A type needing a new import is reported
- **WHEN** a class references `java.time.Duration` with no import and no other `Duration` in scope
- **THEN** `UseTypeImports` reports a violation

### Requirement: A simple name claimed by two types in one file is not reported
The rule SHALL NOT report a qualified type when its simple name is claimed by a different type within
the same file, whether by an existing import, a same-package type, or another qualified use. Java
permits at most one import of a given simple name.

In such a conflict the rule SHALL report neither type, leaving the developer free to import one, the
other, or neither.

#### Scenario: Two types with the same simple name are both left alone
- **WHEN** a file uses both `java.util.List` and `java.awt.List`
- **THEN** the rule reports no violation on either

#### Scenario: A qualified type colliding with an existing import is not reported
- **WHEN** a file imports `java.util.List` and also uses `java.awt.List`
- **THEN** the rule reports no violation on `java.awt.List`

### Requirement: Each qualified type is reported once per file
The rule SHALL report at most one violation per fully-qualified type name per file, anchored at the
first occurrence.

One import statement resolves every occurrence, so a violation per occurrence would state a count
disproportionate to the work.

#### Scenario: Repeated use of one qualified type yields one violation
- **WHEN** a file references `java.time.Duration` six times by fully-qualified name
- **THEN** the rule reports exactly one violation

#### Scenario: Two qualified types yield two violations
- **WHEN** a file references `java.time.Duration` and `java.time.Instant` by fully-qualified name
- **THEN** the rule reports exactly two violations

### Requirement: A nested type is reported without prescribing which import to add
The rule SHALL report a fully-qualified nested type such as `java.util.Map.Entry`, and SHALL NOT
prescribe which of the two valid fixes to apply — importing the nested type and using `Entry`, or
importing the outer type and using `Map.Entry`.

#### Scenario: A qualified nested type is reported
- **WHEN** a method signature contains `java.util.Map.Entry<String, String> entry`
- **THEN** the rule reports a violation

#### Scenario: A nested type reached through an imported outer type is not reported
- **WHEN** a file imports `java.util.Map` and uses `Map.Entry`
- **THEN** the rule reports no violation

### Requirement: The rule is documented in the category file and the README
`category/java/joke.xml` SHALL declare the rule with its implementing class, a message, a
description, a priority and both a violating and a compliant example. `rulesets/java/joke.xml` SHALL
include it.

`README.md` SHALL document the rule and the split with `UnnecessaryFullyQualifiedName`, so a reader
understands why both exist.

#### Scenario: The category entry is complete
- **WHEN** the `UseTypeImports` entry in `category/java/joke.xml` is inspected
- **THEN** it declares a `class`, a `message`, a `<description>`, a `<priority>` and an `<example>`

### Requirement: The rule is covered by unit tests and mutation testing
The rule SHALL be unit-tested with JUnit 5 using `pmd-test`, with a fixture covering every reported
and every non-reported case above, and SHALL meet the project's 100% mutation, coverage and test
strength thresholds.

The fixtures SHALL be treated as the only evidence this rule works: every fully-qualified name in
this repository's Java source sits inside an import declaration, so dogfooding cannot catch a mistake
in it.

#### Scenario: Every specified case has a fixture
- **WHEN** the rule's test data is inspected
- **THEN** it contains a case for each reported and each non-reported scenario in this spec

#### Scenario: Mutation thresholds are met
- **WHEN** `./gradlew pitest` runs
- **THEN** the rule class meets 100% mutation coverage, line coverage and test strength
