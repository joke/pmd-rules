## Why

Six rules so far have all pushed the same way: make code reachable, name what has no name. This pair
is about the other half of readability — stop repeating what an import already says.

```java
Mockito.doReturn(true).when(rule).isHidden(method);   // ×18 in one of our own test files
Mockito.never()                                       // ×6
Visibility.V_PROTECTED                                // ×3
```

Those 27 qualifications in `VisitDelegationTest` are not a style choice. They were written *because*
PMD's stock `TooManyStaticImports` rejected the file at six static imports, so the file was cut to
exactly four and the rest were qualified by hand. That rule caps static imports at **4** by default
and `.pmd.xml` does not exclude it.

Imports are cheap and the IDE folds them away. A cap on how many a file may have buys nothing and
costs exactly the readability it claims to protect.

## What Changes

- **`UseStaticImports`** — reports a type-qualified static member (method or field) that should be
  statically imported.
- **`UseTypeImports`** — reports a fully-qualified type name where adding an import would let the
  simple name be used.
- **`.pmd.xml` excludes `TooManyStaticImports`**, and the README documents the exclusion for
  consumers who enable `category/java/codestyle.xml`.

`UseStaticImports` forces an import when the member's simple name is **longer than 3 characters**,
is not in a fixed exclusion list, and does not collide with another owner in the same file:

```java
Mockito.doReturn(…)      → import static org.mockito.Mockito.doReturn;      // 8 chars, reported
Math.max(a, b)           → left alone                                        // 3 chars
Optional.empty()         → left alone                                        // uninformative name
Duration.ofSeconds(3)    → left alone                                        // of[A-Z] prefix
Foo.INSTANCE             → left alone                                        // uninformative name
```

**The threshold is a floor, not a ceiling.** Neither rule ever forbids an import — a developer who
wants `PI` or `of` imported may still import them, and one who disagrees with a report may suppress
it. That one-directional character is what makes the boundary cases cheap: being under the threshold
means "your call", not "don't".

## Capabilities

### New Capabilities

- `static-import-rule`: the `UseStaticImports` rule — the length floor, the exclusion list, conflict
  handling, per-file reporting, the class-literal exemption, its minimum language version, and the
  mandatory `TooManyStaticImports` exclusion.
- `type-import-rule`: the `UseTypeImports` rule — what it reports, the clean split with PMD's stock
  `UnnecessaryFullyQualifiedName`, conflict handling and per-file reporting.

### Modified Capabilities

_None._ No new dependency and no build change.

## Impact

- **New**: two rule classes, two `pmd-test` XML fixture files, two test classes, two entries in
  `category/java/joke.xml`, two README sections.
- **`.pmd.xml`**: one exclusion. This is the first rule in the artifact that **conflicts head-on**
  with an enabled stock rule rather than composing with one — `UseStaticImports` only ever adds
  static imports and `TooManyStaticImports` only ever caps them, so no configuration satisfies both.
  Every earlier rule either composed (`LambdaCanBeMethodReference`) or was orthogonal.
- **This repository has real violations**, unlike the last change: roughly ten under per-file
  reporting — `Mockito.doReturn`, `Mockito.never`, three `Visibility` constants,
  `Modifier.isPrivate`, `Files.write`, `Collectors.toList`, `AccessType.WRITE`. Fixing them reverts
  the 27 hand-qualifications, which is the change proving itself.
- **`UseTypeImports` lands green here** — every fully-qualified name in this repository's Java sits
  inside an `import` declaration. The inline FQNs live only in `pmd-test` XML fixtures, invisible to
  PMD. So that rule's fixtures are its only evidence.
- **Consumers**: adopting `UseStaticImports` on an existing codebase produces one violation per
  distinct qualified member per file, not one per occurrence. That distinction decides whether the
  first run reads as "a few dozen import lines" or "thousands of problems".
- **Not in scope**: any cap or opinion on the *number* of imports; wildcard imports, which
  `UnnecessaryImport` and the project's Spotless `forbidWildcardImports()` already govern; and making
  the exclusion list configurable — it is hardcoded, and suppression is the escape.
