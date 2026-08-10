## Why

The first real consumer of this artifact — the `percolate` annotation processor — is adopting
`joke-strict` as the sole owner of its method-shape doctrine, retiring the ArchUnit rules that enforced the
same invariants against bytecode. Running the ruleset there surfaces 97 violations from
`StaticMethodsModifyStaticState` and `AvoidPrivateAndProtectedMethods`, and **not one of them is a method the
bytecode-based rules considered wrong**. They divide into exactly three shapes:

| Count | Shape | Why the rule misses it |
|---|---|---|
| 29 | Lombok `@UtilityClass` | Lombok privatises the constructor and makes members static *after* parsing; the source PMD reads is a `public class` with instance-looking methods, so the utility-class exemption cannot match |
| 24 | named constructor (`OperationSpec.of`, `Port.byType`, `Cost.finite`) | no exemption exists — but a test double over a static factory could only return what the constructor it wraps already returns, so there is nothing to intercept |
| 8 | `protected` on a published abstract base | the rule bans `protected` outright, and these are cross-package inheritance members on a published SPI, where package-private is not reachable by a subclass at all |

The first two are gaps in exemptions the rule already has, and they are wrong for **every** consumer, not just
this one — any Lombok project trips the first, any project with static factories trips the second. The third
is a genuine hole in the doctrine: the rule's reasoning assumes a test seam can always be package-private,
which holds only when the subclass shares the package.

## What Changes

- `StaticMethodsModifyStaticState` gains a **named-constructor exemption**: a `static` method whose return
  type is its own declaring type, or an interface that type declares it implements, is not reported.
- `StaticMethodsModifyStaticState` **recognises Lombok's `@UtilityClass`** as a utility class by simple name,
  short-circuiting the structural test that Lombok's source-level rewriting defeats.
- `AvoidPrivateAndProtectedMethods` **permits `protected` when marked** `@ApiStatus.OverrideOnly` (a genuine
  extension point) or `@VisibleForTesting` (a widened test seam). An unmarked `protected` is still reported,
  so the modifier must always be a declared choice between two meanings rather than a default.
- README and `category/java/joke.xml` documentation updated for all three, including the reasoning that makes
  each exemption safe.

No rule becomes stricter, so no consumer's build newly fails. Every change removes false positives.

## Capabilities

### New Capabilities
<!-- none: all three changes modify existing rule specs -->

### Modified Capabilities
- `static-method-state-rule`: adds the named-constructor exemption, and extends the utility-class exemption
  to recognise Lombok's `@UtilityClass` annotation alongside the structural shape.
- `method-visibility-rule`: `protected` is no longer banned outright but is permitted when marked with one of
  two annotations, which replaces the requirement that no cross-file subclass analysis be attempted with a
  declaration the rule can read from a single compilation unit.

## Impact

- `rules/src/main/java/.../StaticMethodsModifyStaticState.java`: two new predicates in `isJustified`.
- `rules/src/main/java/.../AvoidPrivateAndProtectedMethods.java`: `isVisibilityFixedElsewhere` gains the
  marker check.
- `category/java/joke.xml`: both `<description>` blocks and both `<example>` blocks extended.
- `README.md`: the `StaticMethodsModifyStaticState`, `AvoidPrivateAndProtectedMethods` and *The rules cascade*
  sections.
- Rule test data XML for both rules; pitest must stay at 100/100/100.
- **Constrained by the PMD 7.0.0 floor.** Every AST API used must exist at the compile floor, or consumers on
  older PMD 7 get a `NoSuchMethodError` inside their analysis. The floor is not to be raised for this.
- Unblocks the `adopt-pmd-for-method-shape` change in the `percolate` repository, which pins a released
  version of this artifact as its first task.
