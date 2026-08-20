## MODIFIED Requirements

### Requirement: AvoidPrivateAndProtectedMethods permits only public and package-private methods
The artifact SHALL provide a rule named `AvoidPrivateAndProtectedMethods` that reports a method
declared `private`, or declared `protected` without one of the two markers below. `public` and
package-private methods SHALL NOT be reported.

The rule SHALL be implemented in Java against the PMD 7 Java AST, and SHALL NOT declare a
`minimumLanguageVersion`.

The rationale is that the house test style stubs a sibling method on a spy of the subject under test.
A `private` method is not reachable from the test and cannot be stubbed. `protected` is reachable,
but is usually the wrong seam: it widens the API to every subclass in every consumer's codebase,
where package-private widens it only to the package the test lives in.

Package-private is therefore the default internal form, and `protected` SHALL be a stated exception
rather than an available alternative. The rule exists to remove discretion; what a marker restores is
a choice between two documented intents, not a choice about whether to declare one.

#### Scenario: A private method is reported
- **WHEN** a class declares `private boolean check() { return true; }`
- **THEN** the rule reports a violation

#### Scenario: An unmarked protected method is reported
- **WHEN** a class declares `protected boolean check() { return true; }`
- **THEN** the rule reports a violation

#### Scenario: A package-private method is not reported
- **WHEN** a class declares `boolean check() { return true; }`
- **THEN** the rule reports no violation

#### Scenario: A public method is not reported
- **WHEN** a class declares `public boolean check() { return true; }`
- **THEN** the rule reports no violation

### Requirement: A marked protected method declares its intent and is not reported
The rule SHALL NOT report a `protected` method that carries `@ApiStatus.OverrideOnly` — an extension
point that implementors override — or `@VisibleForTesting` — a visibility widened to create a test
seam.

Both markers are necessary because package-private is not always a compliant rewrite. A `protected`
member on a published abstract base class whose subclasses live in other packages is unreachable if
narrowed, so the rule as previously written demanded a rewrite that does not compile.

The marker set SHALL be hardcoded rather than exposed as a rule property. A configurable set would
let each consumer choose which annotations legitimise `protected`, which is the per-project drift the
rule exists to prevent. `@SuppressWarnings` remains the documented escape for anything else.

Markers SHALL be matched by simple name. `@ApiStatus.OverrideOnly` is a nested annotation, so a
method carrying it SHALL be exempt whether the annotation is imported directly and written
`@OverrideOnly` or qualified through its outer type and written `@ApiStatus.OverrideOnly`. A single
`OverrideOnly` entry SHALL cover both: PMD's simple name is the last identifier and never contains a
dot, so a dotted entry would be unmatchable.

#### Scenario: A protected extension point is not reported
- **WHEN** a class declares `@ApiStatus.OverrideOnly protected void hook() { }`
- **THEN** the rule reports no violation

#### Scenario: A protected test seam is not reported
- **WHEN** a class declares `@VisibleForTesting protected boolean check() { return true; }`
- **THEN** the rule reports no violation

#### Scenario: Both spellings of the nested annotation match
- **WHEN** a `protected` method carries `@OverrideOnly` imported directly, and another carries
  `@ApiStatus.OverrideOnly` qualified through its outer type
- **THEN** neither is reported

#### Scenario: A marker on a private method does not excuse it
- **WHEN** a class declares `@VisibleForTesting private boolean check() { return true; }`
- **THEN** the rule reports a violation, because no marker makes a `private` method reachable

### Requirement: The rule does not attempt cross-file subclass analysis
The rule SHALL report an unmarked `protected` method unconditionally rather than only when no
subclass uses it, and this SHALL be recorded as a deliberate limit rather than an omission.

PMD cannot perform the check. `Rule.deepCopy()` gives each analysis thread its own rule copy, so
instance state does not aggregate across files; every `RuleContext.addViolation` overload requires a
live `Node`, so nothing can be reported after a file's listener closes; and `Rule.end(RuleContext)`
fires per thread-batch against the last file processed. PMD's unit of analysis is one compilation
unit, and "does any subclass exist" is a whole-module question.

The markers exist so that the question never has to be asked. A declaration is readable from a single
compilation unit, which makes the check correct at every range — including across a module boundary
and into a third-party consumer's subclass, neither of which any import scope can reach.

The one case provable within a single file — `protected` in a `final` class, where no subclass can
exist — is already covered by the stock `AvoidProtectedMethodInFinalClassNotExtending`, which
`.pmd.xml` enables.

#### Scenario: A protected method with a subclass in the same file is still reported
- **WHEN** a class declares `protected void hook()` with no marker and a nested subclass overrides it
- **THEN** the rule reports a violation on the declaration

#### Scenario: Suppression is available for anything the markers do not cover
- **WHEN** a `protected` method carries
  `@SuppressWarnings("PMD.AvoidPrivateAndProtectedMethods")`
- **THEN** the rule reports no violation

### Requirement: No stock rule is excluded to accommodate this rule
Permitting a marked `protected` method SHALL leave `.pmd.xml` unchanged.
`AvoidProtectedMethodInFinalClassNotExtending` and `AvoidProtectedFieldInFinalClass`, both enabled
through `category/java/codestyle.xml`, fire on `protected` in a `final` class and remain correct
where they fire: in a `final` class nothing can override, so `@ApiStatus.OverrideOnly` is meaningless,
and no out-of-package subclass can exist, so a test seam has no reason to widen past package-private.
The markers therefore never put this rule in opposition to those.

#### Scenario: The repository ruleset gains no exclusions
- **WHEN** `.pmd.xml` is inspected after this change
- **THEN** it excludes neither `AvoidProtectedMethodInFinalClassNotExtending` nor
  `AvoidProtectedFieldInFinalClass`

#### Scenario: A marked protected method in a final class is still caught by the stock rule
- **WHEN** a `final` class that extends nothing declares `@VisibleForTesting protected void check() { }`
- **THEN** this rule reports no violation, and the stock
  `AvoidProtectedMethodInFinalClassNotExtending` reports it instead
