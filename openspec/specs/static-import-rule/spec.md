# static-import-rule Specification

## Purpose

The `UseStaticImports` rule: which type-qualified static members it reports, the length floor and its
one-directional character, the hardcoded exclusion list of factory-shaped names, conflict and
shadowing detection, per-file reporting, the class-literal exemption, its minimum language version,
the mandatory `TooManyStaticImports` exclusion, and its documentation, test and mutation-coverage
obligations.

## Requirements

### Requirement: UseStaticImports reports type-qualified static members
The artifact SHALL provide a rule named `UseStaticImports` that reports a static member — method or
field — accessed through its declaring type where a static import should be used instead.

The rule SHALL be implemented in Java against the PMD 7 Java AST, visiting method calls and field
accesses whose qualifier is a type expression. It SHALL NOT attempt to resolve whether a member is
static: Java forbids instance access through a type, so a type-qualified member is necessarily
static. This keeps the rule working without an `auxclasspath`, because PMD takes the type-ness of the
qualifier from the import declaration rather than from the class file.

Where PMD cannot disambiguate a qualifier, the rule SHALL under-report rather than guess.

#### Scenario: A type-qualified static method is reported
- **WHEN** a method body contains `Mockito.doReturn(true)` and `Mockito` is a type
- **THEN** the rule reports a violation

#### Scenario: A type-qualified static field is reported
- **WHEN** a method body contains `AccessType.WRITE`
- **THEN** the rule reports a violation

#### Scenario: An instance method call is not reported
- **WHEN** a method body contains `list.forEach(…)` where `list` is a variable
- **THEN** the rule reports no violation

#### Scenario: A member already used through a static import is not reported
- **WHEN** a file statically imports `assertThat` and calls it unqualified
- **THEN** the rule reports no violation, because there is no type-qualified access

### Requirement: The length threshold is a floor and the rule never forbids an import
The rule SHALL report only when the member's simple name is longer than three characters. A name of
three characters or fewer SHALL NOT be reported.

The threshold SHALL be a floor, not a ceiling: the rule SHALL NOT report a static import as
unnecessary, and SHALL NOT prevent a developer from importing a shorter name by hand. A developer who
disagrees with a report SHALL be able to suppress it.

Short static members in the JDK are overwhelmingly the ambiguous ones — `of`, `get`, `min`, `max`,
`now`, `abs`, `sum` — so the floor protects exactly the names that read worst unqualified.

#### Scenario: A three-character member is not reported
- **WHEN** a method body contains `Math.max(a, b)`
- **THEN** the rule reports no violation

#### Scenario: A four-character member is reported
- **WHEN** a method body contains `Math.sqrt(x)`
- **THEN** the rule reports a violation

#### Scenario: A hand-written static import of a short name is not reported
- **WHEN** a file statically imports `java.lang.Math.PI` and uses `PI`
- **THEN** the rule reports no violation

#### Scenario: Suppression is available
- **WHEN** a type-qualified static call carries `@SuppressWarnings("PMD.UseStaticImports")`
- **THEN** the rule reports no violation

### Requirement: Factory-shaped member names are excluded
The rule SHALL NOT report a member whose simple name is in a fixed exclusion list. The list SHALL be
hardcoded; the rule SHALL declare no properties, and `@SuppressWarnings` SHALL be the escape for a
consumer's own factory-shaped members.

The list encodes **uninformative** names — those where the member name omits the type and the
declaring class was the only thing supplying it — and nothing else. Ambiguity between owners is
handled by conflict detection, not by this list.

Exact names:

`value`, `values`, `valueOf`, `empty`, `create`, `builder`, `parse`, `now`, `between`, `copyOf`,
`getInstance`, `newInstance`, `INSTANCE`

Prefixes, matched at a camelCase boundary:

`of` followed by an uppercase letter, and `from` followed by an uppercase letter.

The camelCase boundary SHALL be required, so that `ofSeconds` matches and `offer` does not.

`copyOf` is excluded on the same uninformative grounds as the rest — a copy of what, into what, is
carried by `List`, `Set` or `Arrays` and not by the member name. The exclusion additionally resolves
a direct conflict with Error Prone's `BadImport` check, which lists `copyOf` among the names that
must never be statically imported: without the exclusion a consumer running both tools receives two
reports no single edit can satisfy.

#### Scenario: An excluded exact name is not reported
- **WHEN** a method body contains `Optional.empty()`
- **THEN** the rule reports no violation

#### Scenario: An excluded prefix is not reported
- **WHEN** a method body contains `Duration.ofSeconds(3)`
- **THEN** the rule reports no violation

#### Scenario: A camelCase boundary is required for a prefix match
- **WHEN** a method body contains `Queue.offer(x)` on a type-qualified static named `offer`
- **THEN** the rule reports a violation, because `offer` is not `of` followed by an uppercase letter

#### Scenario: The INSTANCE field is not reported
- **WHEN** a method body contains `Registry.INSTANCE`
- **THEN** the rule reports no violation

#### Scenario: A sole owner of copyOf is not reported
- **WHEN** a file contains only `List.copyOf(xs)`
- **THEN** the rule reports no violation, even though the bare name would be unambiguous in that
  file

#### Scenario: A self-describing member of an excluded class's neighbour is still reported
- **WHEN** a method body contains `Collections.unmodifiableList(xs)`
- **THEN** the rule reports a violation, because exclusion is by member name and there is no owner
  axis

#### Scenario: Collectors members are reported
- **WHEN** a method body contains `Collectors.toList()`
- **THEN** the rule reports a violation

### Requirement: A name contributed by two owners in one file is not reported
The rule SHALL NOT report a member when the same simple name is contributed by more than one
declaring type within the same file, because Java permits at most one single static import of a given
simple name.

Conflict detection SHALL also account for a simple name already bound in the file by a field, a local
variable, or a method of the enclosing type.

In a conflict the rule SHALL report neither member, leaving the developer free to import one, the
other, or neither.

The scenarios below SHALL NOT use a name that the exclusion list already suppresses, so that they
demonstrate conflict detection rather than passing for the wrong reason.

#### Scenario: Two owners of the same simple name are both left alone
- **WHEN** a method body contains both `Arrays.toString(a)` and `Objects.toString(x)`
- **THEN** the rule reports no violation on either

#### Scenario: A single owner of that name is reported
- **WHEN** a method body contains only `Arrays.toString(a)`
- **THEN** the rule reports a violation, because the bare name is unambiguous in this file

#### Scenario: A name shadowed by a local declaration is not reported
- **WHEN** a method declares a local variable named `sqrt` and also calls `Math.sqrt(x)`
- **THEN** the rule reports no violation

### Requirement: Class literals are never reported
The rule SHALL NOT report a class literal such as `Foo.class`. A class literal is not a field access
and cannot be statically imported.

#### Scenario: A class literal is not reported
- **WHEN** a constructor contains `super(ASTMethodDeclaration.class)`
- **THEN** the rule reports no violation

### Requirement: Each qualified member is reported once per file
The rule SHALL report at most one violation per `(declaring type, member name)` pair per file,
anchored at the first occurrence.

One import statement resolves every occurrence, so a violation per occurrence would state a count
disproportionate to the work. A consumer's first run over an existing codebase must report a number
close to the number of import lines to be added.

#### Scenario: Repeated use of one member yields one violation
- **WHEN** a file contains eighteen `Mockito.doReturn(…)` calls
- **THEN** the rule reports exactly one violation

#### Scenario: Two members of the same type yield two violations
- **WHEN** a file contains `Mockito.doReturn(…)` and `Mockito.never()`
- **THEN** the rule reports exactly two violations

### Requirement: The rule declares a minimum language version
The rule SHALL declare `minimumLanguageVersion="1.5"` in `category/java/joke.xml`, because static
imports do not exist before Java 5. Reporting below that version would demand a fix the language does
not permit.

#### Scenario: The rule is skipped below Java 5
- **WHEN** PMD analyses source at Java 1.4
- **THEN** the rule contributes no violations

### Requirement: TooManyStaticImports is excluded from the repository ruleset
`.pmd.xml` SHALL exclude `TooManyStaticImports` from `category/java/codestyle.xml`.

The two rules are in direct opposition and no configuration satisfies both: `UseStaticImports` only
ever adds static imports, and `TooManyStaticImports` caps them at four by default. This is the first
rule in the artifact that conflicts with an enabled stock rule rather than composing with one.

`README.md` SHALL document the exclusion for consumers who enable `category/java/codestyle.xml`, and
SHALL state that imports are deliberately uncapped.

#### Scenario: The repository ruleset excludes the conflicting stock rule
- **WHEN** `.pmd.xml` is inspected
- **THEN** `TooManyStaticImports` is excluded

#### Scenario: The README warns consumers
- **WHEN** the `UseStaticImports` section of `README.md` is inspected
- **THEN** it states that `TooManyStaticImports` must be excluded when `codestyle` is enabled

### Requirement: The Error Prone BadImport overlap is documented
`README.md` SHALL record, in the `UseStaticImports` section, that Error Prone's `BadImport` check
rejects static imports of names the rule would otherwise demand, and that `copyOf` is excluded for
that reason.

The note SHALL sit alongside the existing `TooManyStaticImports` warning, because both describe the
same class of problem for a consumer: a second tool whose position on static imports is not this
rule's. Unlike `TooManyStaticImports`, `BadImport` needs no exclusion on the consumer's side — the
overlap is resolved inside this rule's exclusion list.

#### Scenario: The README names the interaction
- **WHEN** the `UseStaticImports` section of `README.md` is inspected
- **THEN** it states that `copyOf` is excluded because Error Prone's `BadImport` forbids importing it

#### Scenario: The exclusion list in the README matches the rule
- **WHEN** the documented exact-name exclusion list in `README.md` is compared with
  `UNINFORMATIVE_NAMES` in `UseStaticImports`
- **THEN** the two agree, including `copyOf`

### Requirement: The rule is documented in the category file and the README
`category/java/joke.xml` SHALL declare the rule with its implementing class, a message, a
description, a priority and both a violating and a compliant example. `rulesets/java/joke.xml` SHALL
include it.

#### Scenario: The category entry is complete
- **WHEN** the `UseStaticImports` entry in `category/java/joke.xml` is inspected
- **THEN** it declares a `class`, a `message`, a `<description>`, a `<priority>` and an `<example>`
- **AND** the description states that the threshold is a floor and that imports are never forbidden

### Requirement: The rule is covered by unit tests and mutation testing
The rule SHALL be unit-tested with JUnit 5 using `pmd-test`, with a fixture covering every reported
and every non-reported case above, and SHALL meet the project's 100% mutation, coverage and test
strength thresholds.

The rule class SHALL itself comply with every rule this artifact publishes.

#### Scenario: Every specified case has a fixture
- **WHEN** the rule's test data is inspected
- **THEN** it contains a case for each reported and each non-reported scenario in this spec

#### Scenario: Mutation thresholds are met
- **WHEN** `./gradlew pitest` runs
- **THEN** the rule class meets 100% mutation coverage, line coverage and test strength
