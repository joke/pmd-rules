## REMOVED Requirements

### Requirement: Rules are implemented in Java
**Reason**: The capability is renamed. `rule-distribution` was written when this repository published
one artifact for one tool, and its every requirement is PMD-specific. Beside a new
`codenarc-rule-distribution` sibling, the unqualified name reads as the shared or generic one when it
is nothing of the sort.

**Migration**: Carried over verbatim to `pmd-rule-distribution`, extended with the
`io.github.joke.lint.pmd.rules.java` package requirement.

### Requirement: PMD is a compile-only dependency at the supported floor
**Reason**: Capability renamed to `pmd-rule-distribution`.

**Migration**: Carried over to `pmd-rule-distribution` with `rules/build.gradle` requalified as
`pmd-rules/build.gradle`. The 7.0.0 floor and its rationale are unchanged.

### Requirement: The published POM declares no dependencies
**Reason**: Capability renamed to `pmd-rule-distribution`.

**Migration**: Carried over to `pmd-rule-distribution` with the module path requalified. The
configuration allowlist and the prohibition on reinstating `verifyPomHasNoDependencies` are
unchanged.

### Requirement: Resource layout follows PMD's category and ruleset split
**Reason**: Capability renamed to `pmd-rule-distribution`.

**Migration**: Carried over to `pmd-rule-distribution`, extended with a requirement that the three
resource paths do not change when the artifact's coordinates change.

### Requirement: Shipped resources reference no external ruleset
**Reason**: Capability renamed to `pmd-rule-distribution`.

**Migration**: Carried over verbatim to `pmd-rule-distribution`.

### Requirement: The ruleset is loaded by classpath reference
**Reason**: Capability renamed to `pmd-rule-distribution`.

**Migration**: Carried over verbatim to `pmd-rule-distribution`. The equivalent guarantee for the
CodeNarc artifact is stated separately in `codenarc-rule-distribution`, because CodeNarc resolves
rulesets through a different mechanism.

### Requirement: The strict ruleset publishes the composition and declares its own support window
**Reason**: Capability renamed to `pmd-rule-distribution`.

**Migration**: Carried over verbatim to `pmd-rule-distribution`.

### Requirement: Consumer documentation states both support windows
**Reason**: Capability renamed to `pmd-rule-distribution`.

**Migration**: Carried over verbatim to `pmd-rule-distribution`.

### Requirement: Consumer wiring is documented
**Reason**: Capability renamed to `pmd-rule-distribution`.

**Migration**: Carried over to `pmd-rule-distribution`, extended to name the artifact at its new
coordinates `io.github.joke.lint:pmd-rules`.

### Requirement: Rule test fixtures stay in XML
**Reason**: Capability renamed to `pmd-rule-distribution`.

**Migration**: Carried over to `pmd-rule-distribution` with `rules/src/test/resources` requalified as
`pmd-rules/src/test/resources`. The requirement applies to the PMD module only; CodeNarc has no XML
fixture harness and `codenarc-rule-distribution` states its own testing requirement.
