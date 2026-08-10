## 1. Rule

- [x] 1.1 Add `copyOf` to `UNINFORMATIVE_NAMES` in `UseStaticImports`, keeping the set's existing
      ordering shape (exact names grouped as documented)
- [x] 1.2 Extend the `UNINFORMATIVE_NAMES` javadoc so it states that `copyOf` is uninformative on the
      same grounds as the rest, and notes the Error Prone `BadImport` overlap as corroboration

## 2. Tests

- [x] 2.1 Re-point the `two owners of the same simple name are both left alone` fixture in
      `UseStaticImports.xml` from `Arrays.copyOf` / `List.copyOf` to `Arrays.toString` /
      `Objects.toString`, importing `java.util.Objects`, expecting 0 problems
- [x] 2.2 Re-point the `a single owner of that name is reported` fixture to `Arrays.toString(a)`,
      expecting 1 problem, and confirm the fixture class declares no `toString` so the shadowing
      branch stays untouched
- [x] 2.3 Add a fixture `a sole owner of copyOf is not reported` containing only `List.copyOf(xs)`,
      expecting 0 problems
- [x] 2.4 Run `./gradlew :rules:test` and confirm all `UseStaticImports` fixtures pass

## 3. Documentation

- [x] 3.1 Update the `UseStaticImports` description in `category/java/joke.xml`: add `copyOf` to the
      documented exclusion list and re-point the ambiguity sentence to `Arrays.toString` /
      `Objects.toString`
- [x] 3.2 Update the `<example>` block in `category/java/joke.xml` so the conflict lines use
      `Arrays.toString(a)` / `Objects.toString(x)` with their comments, and add a `List.copyOf(xs)`
      not-reported line to the compliant examples
- [x] 3.3 Update `README.md`: add `copyOf` to the exact-name exclusion list, re-point the ambiguity
      paragraph to the `toString` pair, and add the `copyOf` line to the reported/not-reported code
      block
- [x] 3.4 Add the Error Prone `BadImport` note to the `UseStaticImports` section of `README.md`
      beside the `TooManyStaticImports` warning, stating that the consumer needs no suppression
      because the exclusion lives in the rule

## 4. Verification

- [x] 4.1 Run `./gradlew build` and confirm the rule class still passes the artifact's own ruleset
- [x] 4.2 Run `./gradlew pitest` and confirm `UseStaticImports` still meets 100% mutation, line
      coverage and test strength
- [x] 4.3 Confirm the documented exclusion list in `README.md` and `category/java/joke.xml` matches
      `UNINFORMATIVE_NAMES` exactly
