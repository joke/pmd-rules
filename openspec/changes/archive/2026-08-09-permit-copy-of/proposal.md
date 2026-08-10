## Why

`UseStaticImports` demands a static import for `List.copyOf(xs)` whenever that file is the only
place `copyOf` appears, but Error Prone's `BadImport` check lists `copyOf` among the names that must
never be statically imported. A consumer running both tools gets an unfixable pair of reports: one
tool asks for the import, the other rejects it. `copyOf` is also exactly the shape the exclusion list
already describes — the member says it produces a copy but not of what, and the class was the only
thing carrying the type.

## What Changes

- Add `copyOf` to the hardcoded `UNINFORMATIVE_NAMES` set in `UseStaticImports`, so a type-qualified
  `copyOf` is never reported regardless of how many owners contribute it.
- Replace the conflict-detection illustration, which currently uses `Arrays.copyOf` / `List.copyOf`
  in the spec, the category description, the README and the test fixture. With `copyOf` excluded
  those examples no longer demonstrate conflict detection — they would pass for the wrong reason.
  `Arrays.toString` / `Objects.toString` becomes the replacement pair.
- Document `copyOf` in the README exclusion list and record the Error Prone interaction alongside the
  existing `TooManyStaticImports` note.

No behaviour is removed and nothing that was previously silent becomes reported, so this is not
breaking for consumers.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `static-import-rule`: the exclusion list gains `copyOf`; the conflict-detection scenarios stop
  using `copyOf` as their example name; the rule's documented interactions gain Error Prone's
  `BadImport`.

## Impact

- `rules/src/main/java/io/github/joke/pmd/rules/java/UseStaticImports.java` — one entry in
  `UNINFORMATIVE_NAMES`.
- `rules/src/main/resources/category/java/joke.xml` — rule description and example.
- `rules/src/test/resources/io/github/joke/pmd/rules/java/xml/UseStaticImports.xml` — the two
  conflict fixtures re-pointed, plus a new fixture asserting `copyOf` is not reported with a single
  owner.
- `README.md` — exclusion list, ambiguity paragraph, Error Prone note.
- No dependency, API or published-POM change. Mutation thresholds are unaffected: the new name is
  data in an existing set, not a new branch.
