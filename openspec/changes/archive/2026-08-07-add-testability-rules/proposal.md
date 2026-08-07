## Why

`java-coding-conventions/SKILL.md` already requires the shape this change enforces — instance
methods over `static`, wider-than-private visibility for helpers, `@VisibleForTesting` on the
resulting seam — and that skill is advisory. An agent writing Java can read it and route around it,
and in this repository it did: `UseVarForLocalVariables` has three `private static` helpers and a
`@DoNotMutate` on `visit()` justified by a Javadoc paragraph explaining why the branch could not be
covered.

That justification is a consequence of the shape rather than a fact about PMD. It reads:

> Constructing an AST node to assert the return directly is not possible either: PMD's parsing
> helpers live in a test-jar this project does not depend on.

True, and irrelevant. With `isRewritableAsVar` reachable from a test, a mocked node and a spied rule
cover both branches of `visit()` without a parser. The exemption exists because the helper is
`private static`, not because the code is untestable.

So the fix is not more guidance. It is three rules that fail the build, on the same footing as every
other rule this project publishes, applied to this project first.

The archived `dogfood-own-rules` change named this as the open question it deliberately left behind
— "whether *protected over private, instance over static* applies to PMD rule classes" — and
predicted that answering it would flag `UseVarForLocalVariables`. It does, five times. That was the
intended pressure.

## What Changes

Three rules, one seam form. The canonical shape becomes:

```java
@VisibleForTesting
boolean declaresAnInferableType(final ASTLocalVariableDeclaration node) { … }
```

- **`StaticMethodsModifyStaticState`** — a `static` method is reported unless it writes a private
  static field, or its declaring type is a utility class. The point is not mockability (Mockito's
  inline mock maker and Spock's `SpyStatic` both handle statics) but meaning: after this rule,
  `static` on a method tells the reader exactly one thing, that the method writes class-level state.
- **`AvoidPrivateAndProtectedMethods`** — the only legal method visibilities are `public` and
  package-private. `@Override` is exempt, because an overriding method's visibility is not the
  author's to choose.
- **`UseVisibleForTestingAnnotation`** — a package-private method must carry `@VisibleForTesting`,
  so the wider visibility reads as a test seam rather than an accident.

Package-private rather than `protected` is the load-bearing choice. It is the tighter seam — visible
to the test package, not to every subclass — and it collides with nothing: `AvoidProtectedMethodIn`
`FinalClassNotExtending` and `AvoidProtectedFieldInFinalClass` (both live in `.pmd.xml` today via
`category/java/codestyle.xml`) only fire on `protected`. **No stock rule needs excluding.**

Supporting changes:

- Add Mockito to the `rules` module's test runtime, spy-test `visit()`, and delete the
  `@DoNotMutate` it no longer needs. Without this the change ships seams nothing uses while keeping
  the exemption it exists to remove.
- Add `org.jetbrains:annotations` as a `compileOnly` dependency for `@VisibleForTesting` itself.
- Rewrite the five `private static` helpers in `UseVarForLocalVariables` and `RulesetDistributionIT`
  into the canonical form.

## Capabilities

### New Capabilities

- `static-method-state-rule`: the `StaticMethodsModifyStaticState` rule — what counts as writing a
  private static field, the utility-class exception, the framework carve-outs that have no compliant
  rewrite, and its documentation and coverage obligations.
- `method-visibility-rule`: the `AvoidPrivateAndProtectedMethods` rule — the two legal visibilities,
  the `@Override` exemption, and why the cross-module subclass check it does not attempt is not
  implementable in PMD.
- `visible-for-testing-rule`: the `UseVisibleForTestingAnnotation` rule — simple-name matching, the
  recognised test annotations, and why this is a custom rule rather than configuration of
  `CommentDefaultAccessModifier`.

### Modified Capabilities

- `build-foundation`: extends **Test stack** with Mockito for spying the subject under test. The
  existing prohibition on Groovy and Spock is unchanged and restated — Mockito sits alongside the
  JUnit 5 tests already there, and adding Spock would mean adding Groovy to a project that
  deliberately has none.

## Impact

- **New**: three rule classes, three `pmd-test` XML fixture files, three test classes, three entries
  in `category/java/joke.xml` and `rulesets/java/joke.xml`, three README sections.
- **`rules/build.gradle`**: `compileOnly 'org.jetbrains:annotations'`, `testImplementation
  'org.mockito:mockito-core'`.
- **`dependencies/build.gradle`**: constraints for both.
- **`UseVarForLocalVariables`**: three helpers change visibility and lose `static`; `@DoNotMutate`
  and its four-line justification are deleted; a new unit test covers both branches of `visit()`.
- **`RulesetDistributionIT`**: two helpers change shape; its expected-rule-name assertions gain three
  entries.
- **`.pmd.xml`**: unchanged. `CommentDefaultAccessModifier` stays excluded — `UseVisibleForTesting`
  `Annotation` supersedes it for methods.
- **Consumers**: three rules that will report existing code. Every violation has either a mechanical
  rewrite or `@SuppressWarnings`, which stays available deliberately.
- **Not in scope**: a rule banning `@DoNotMutate` (rejected — making the helpers testable removes the
  reason to reach for it, which is the better fix); a rule on `final` methods (unnecessary, Mockito's
  inline mock maker mocks them); a cross-module "is this `protected` method actually extended?"
  check (not implementable in PMD — see `design.md`); fields, constructors and nested classes, which
  `UseVisibleForTestingAnnotation` does not cover; and updating
  `java-coding-conventions/SKILL.md`, which lives in another repository and now contradicts
  `AvoidPrivateAndProtectedMethods` by recommending `protected`.
