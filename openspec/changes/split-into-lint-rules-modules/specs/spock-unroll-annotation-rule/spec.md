## ADDED Requirements

### Requirement: AvoidUnrollAnnotation reports @Unroll on a specification or a feature method
The artifact SHALL provide a CodeNarc rule named `AvoidUnrollAnnotation` that reports the
`@Unroll` annotation wherever it is declared — on a specification class or on a feature method.

The rule SHALL be implemented in Java against the Groovy AST, extending
`AbstractAstVisitorRule` with a nested `AbstractAstVisitor`.

Spock 2 unrolls every data-driven feature by default, so the annotation changes nothing. Left in
place it reads as though it were switching a behaviour on, which sends a reader looking for the
un-annotated features that supposedly behave differently.

#### Scenario: An annotated feature method is reported
- **WHEN** a specification declares `@Unroll` on `def 'a feature'() { }`
- **THEN** the rule reports one violation

#### Scenario: An annotated specification class is reported
- **WHEN** a specification class carries `@Unroll`
- **THEN** the rule reports one violation

#### Scenario: Both a class and a method annotation are reported
- **WHEN** a specification class and one of its feature methods both carry `@Unroll`
- **THEN** the rule reports two violations

#### Scenario: An unannotated specification is not reported
- **WHEN** a specification declares a feature method with no annotation
- **THEN** the rule reports no violation

#### Scenario: An unrelated annotation is not reported
- **WHEN** a feature method carries `@Override`
- **THEN** the rule reports no violation

### Requirement: The annotation is matched by name, not resolved
The rule SHALL match the annotation on the name as written, accepting both the simple form
`@Unroll` and the fully qualified form `@spock.lang.Unroll`. It SHALL NOT attempt to resolve the
annotation to a type.

CodeNarc analyses source without a compile classpath, so a rule that resolved the annotation would
report nothing whenever the classpath was incomplete. Matching the written name accepts a
deliberately misleading `Unroll` from another package, which is the cheaper failure.

#### Scenario: The simple name is matched
- **WHEN** a feature method carries `@Unroll`
- **THEN** the rule reports a violation

#### Scenario: The qualified name is matched
- **WHEN** a feature method carries `@spock.lang.Unroll`
- **THEN** the rule reports a violation

#### Scenario: A name that merely ends in Unroll is not matched
- **WHEN** a feature method carries `@NotUnroll`
- **THEN** the rule reports no violation

### Requirement: The rule carries a configurable name and priority
The rule SHALL expose `name` and `priority` as read-write properties, defaulting to
`AvoidUnrollAnnotation` and priority 2, because CodeNarc configures a rule by setting them from the
ruleset that declares it.

The class SHALL suppress PMD's `DataClass` rule with a comment recording why: CodeNarc's
`AbstractRule` declares both properties abstract, so four of the class's methods are accessors it
cannot decline to have, and every rule class this artifact ships will carry the same four. The
suppression SHALL be per class rather than an exclusion in `rulesets/java/joke-strict.xml`, because
that ruleset is published to consumers and the collision is internal to this repository.

#### Scenario: The defaults are the documented ones
- **WHEN** a freshly constructed rule is inspected
- **THEN** its name is `AvoidUnrollAnnotation` and its priority is 2

#### Scenario: A ruleset can override both
- **WHEN** a ruleset sets the rule's name and priority
- **THEN** the rule reports them as set

#### Scenario: The suppression is local and explained
- **WHEN** the rule class is inspected
- **THEN** it carries `@SuppressWarnings("PMD.DataClass")` with a comment giving the reason
- **AND** `rulesets/java/joke-strict.xml` does not exclude `DataClass`

### Requirement: The visitor does not call the empty base hooks
The visitor's `visitClassEx` and `visitMethodEx` overrides SHALL NOT call `super`.

Both are empty hooks on `AbstractAstVisitor` — the traversal is driven by the `final` `visitClass`
and `visitMethod` that call them — so a `super` call is a statement whose absence no test can
detect. At a 100% mutation threshold it is an equivalent mutant that survives and fails the build.

#### Scenario: Mutation analysis reaches 100%
- **WHEN** `./gradlew :codenarc-rules:pitest` runs
- **THEN** mutation coverage, line coverage and test strength are each 100%

#### Scenario: No super call is present
- **WHEN** the visitor's `visitClassEx` and `visitMethodEx` are inspected
- **THEN** neither calls `super`
