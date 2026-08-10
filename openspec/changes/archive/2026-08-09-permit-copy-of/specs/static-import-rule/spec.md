## MODIFIED Requirements

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

## ADDED Requirements

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
