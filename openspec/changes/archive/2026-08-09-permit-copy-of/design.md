## Context

`UseStaticImports` suppresses factory-shaped member names through a hardcoded `UNINFORMATIVE_NAMES`
set plus two camelCase prefixes (`of…`, `from…`). `copyOf` is in neither, so a file whose only
`copyOf` owner is `List` gets told to statically import it. Error Prone's `BadImport` check ships a
list of names it refuses as static imports, and `copyOf` is on it — a consumer running both tools
therefore receives one report demanding the import and one rejecting it, with no edit satisfying
both.

The existing spec, the category description, the README and the test fixture all use
`Arrays.copyOf` / `List.copyOf` as the worked example of conflict detection. Excluding `copyOf`
makes those examples inert: they would still report zero violations, but for the wrong reason, and
the conflict-detection branch in `isUnambiguous` would lose its illustration in every place a reader
looks.

## Goals / Non-Goals

**Goals:**

- Stop `UseStaticImports` reporting any type-qualified `copyOf`.
- Keep conflict detection demonstrated by a name that reaches the conflict check, in the spec, the
  category example, the README and the test fixture alike.
- Tell consumers why `copyOf` is absent, so the exclusion does not read as an oversight.

**Non-Goals:**

- Adopting the rest of Error Prone's `BadImport` list (`newBuilder`, `getDefaultInstance`, …). Each
  of those deserves its own argument on uninformativeness; only `copyOf` is asked for here.
- Making the exclusion list configurable. The rule declares no properties by design, and
  `@SuppressWarnings` remains the escape.
- Any change to how conflict detection itself works.

## Decisions

**Exclude `copyOf` as an exact name, not as a prefix or an owner rule.** The set already holds exact
names, and `copyOf` needs no more than that — there is no family of `copyOf…` members to catch, and
an owner-keyed exclusion (`List.*`) would introduce an axis the rule deliberately does not have.
Alternative considered: exclude only when the owner is a collection interface, which would leave
`Arrays.copyOf` reportable. Rejected — it reintroduces owner-awareness for one name and still
collides with `BadImport`, which keys on the member name.

**`copyOf` earns its place on uninformativeness, not only on the tool conflict.** `copyOf(xs)` says
it produces a copy but not of what — `List`, `Set`, `Map` and `Arrays` all contribute one, and the
class was carrying the type. That is the same argument as `valueOf` and `create`, so the entry does
not weaken the list's stated criterion. The Error Prone overlap is corroboration, and is recorded as
such rather than as the sole justification, so the list stays explicable without reference to another
tool.

**Replace the conflict example with `Arrays.toString` / `Objects.toString`.** Both are real JDK
statics, the name is eight characters so it clears the floor, it is not on the exclusion list and not
prefix-matched, and two owners are reachable in one short file — exactly what the old pair provided.
Alternative considered: `Arrays.asList` / `List.of`, rejected because `of` is under the floor and
`asList` has only one owner. Alternative considered: leaving the examples on `copyOf` and adding a
note, rejected because a zero-violation example that passes through the exclusion list teaches the
reader the wrong mechanism.

**Document the overlap in the README beside `TooManyStaticImports`.** Both entries answer the same
consumer question — what happens when another tool disagrees about static imports. The two differ in
who resolves it: `TooManyStaticImports` needs an exclusion in the consumer's `.pmd.xml`, while
`BadImport` is handled inside this rule and needs nothing from the consumer. The README says so
explicitly, so nobody adds a pointless Error Prone suppression.

## Risks / Trade-offs

**A codebase that wants `copyOf` imported no longer gets the nudge** → Accepted, and consistent with
the rule's one-directional contract: the rule never forbids an import, so anyone who wants
`import static java.util.List.copyOf` may still write it by hand. No new report appears anywhere, so
no consumer build breaks.

**The fixture change could mask a regression in conflict detection** → The replacement pair keeps
both conflict fixtures (two owners → zero violations, one owner → one violation), so the mutation
that flips `isUnambiguous` still gets killed. Pitest at 100% is the check: if the new pair failed to
exercise the branch, the mutation score would drop and the build would fail.

**`Objects.toString` in the fixture could be confused with an override** → The fixture class declares
no `toString`, so `declaredNames` stays empty of it and the shadowing branch is not entered. Adding
one would silently convert the single-owner fixture into a shadowing test; the fixture is kept
minimal to prevent that.
