## Why

`add-testability-rules` made every method reachable from a test: not `static` without cause, not
`private`, not `protected`, and marked `@VisibleForTesting` when package-private. It left one place
where logic can still hide from a test entirely — inside something with no name.

```java
items.forEach(item -> {
    validate(item, context);      // no name, no caller can stub it,
    save(item, context);          // no test can reach it
});
```

A lambda block body is anonymous by construction. It cannot be called, cannot be stubbed, and its
branches are only reachable through whatever pipeline encloses it. The same is true of an anonymous
class body, more so — it can hold several methods and fields.

The two must land together, because each is the other's bypass. A rule against lambda block bodies
alone is escapable in one edit:

```java
new Function<Item, Result>() {
    @Override
    public Result apply(Item item) { …the exact logic just banned… }
}
```

Every rule in the set waves that through — it is not static, its method is `public` and `@Override`,
and it is not a lambda. Shipping `AvoidLambdaBlockBodies` on its own would be shipping it with a
documented one-line workaround into a construct that is *less* testable than what it replaces.

## What Changes

- **`AvoidLambdaBlockBodies`** — reports a lambda whose body is a block. An empty block is exempt.
- **`AvoidAnonymousClasses`** — reports an anonymous class declaration with a non-empty body. Enum
  constant bodies and empty bodies are exempt.

Both are cheap: `ASTLambdaExpression.isBlockBody()` and `ASTAnonymousClassDeclaration` both exist at
the PMD 7.0.0 floor, and neither rule needs symbol resolution or an enclosing-type walk.

The rule that matters most for adoption is what `AvoidLambdaBlockBodies` does **not** demand. It
does not require a method reference. An expression lambda delegating to a named method is compliant,
so a lambda closing over locals degrades gracefully rather than becoming impossible:

```java
items.forEach(item -> process(item, context));   // compliant

@VisibleForTesting
void process(final Item item, final Context context) { … }
```

PMD's stock `LambdaCanBeMethodReference` — already enabled in `.pmd.xml` through
`category/java/codestyle.xml` — then pushes a bare delegation the rest of the way to `this::process`.
The two compose rather than collide.

## Capabilities

### New Capabilities

- `lambda-block-body-rule`: the `AvoidLambdaBlockBodies` rule — what it reports, the empty-block
  exemption, the deliberate limit that it says nothing about logic inside an expression body, its
  minimum language version, and its documentation and coverage obligations.
- `anonymous-class-rule`: the `AvoidAnonymousClasses` rule — what it reports, the empty-body and
  enum-constant-body exemptions, and its documentation and coverage obligations.

### Modified Capabilities

_None._ No new dependency, no build change, and `.pmd.xml` needs no exclusion — PMD ships no
anonymous-class rule at all, and `LambdaCanBeMethodReference` composes with the lambda rule instead
of contradicting it.

## Impact

- **New**: two rule classes, two `pmd-test` XML fixture files, two test classes, two entries in
  `category/java/joke.xml`, two README sections.
- **`rulesets/java/joke.xml`**: unchanged — it references the category whole, so both rules join it
  automatically.
- **This repository lands green.** Verified before proposing: no Java source here has a block lambda
  or an anonymous class. The only `-> {` occurrences are inside the `pmd-test` XML fixtures and the
  `category/java/joke.xml` examples, which are invisible to `pmdMain`/`pmdTest` because they are XML
  — and all of them are the empty `() -> { }` form this change exempts anyway. The consequence is
  that **dogfooding gives no signal on these two rules**; their fixtures are the only evidence they
  work, which is the opposite of the last change and worth stating rather than discovering.
- **Consumers**: a known conflict exists between `AvoidLambdaBlockBodies` and
  `StaticMethodsModifyStaticState` for a block lambda in a static field initializer. Extracting it
  produces a static method that Rule A then reports, and no form satisfies both. `@SuppressWarnings`
  is the answer, and the README documents the better fix — making the field non-static — because the
  shape that generates these in bulk is the static dispatch table.
- **Not in scope**: a rule about long call chains or branching inside an *expression* lambda body,
  which is a real gap this change deliberately leaves open; making `StaticMethodsModifyStaticState`
  aware of static initializers (rejected — see `design.md`); and named inner classes, which are
  reachable and need no rule.
