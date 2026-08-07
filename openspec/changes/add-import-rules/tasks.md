## 1. UseStaticImports

- [x] 1.1 Implement the rule class visiting method calls and field accesses whose qualifier is a type expression. Verify against the PMD 7.0.0 floor that a type-qualified access is distinguishable from an instance access without resolving the member
- [x] 1.2 Implement the length floor: report only when the member's simple name is longer than three characters
- [x] 1.3 Implement the hardcoded exclusion list — exact names `value values valueOf empty create builder parse now between getInstance newInstance INSTANCE`, plus `of` and `from` followed by an uppercase letter. Assert the camelCase boundary so `offer` is not matched by the `of` prefix
- [x] 1.4 Implement conflict detection: no report when the same simple name is contributed by more than one declaring type in the file, or is already bound by a field, local variable or method of the enclosing type
- [x] 1.5 Exclude class literals — **no code needed.** `Foo.class` is an `ASTClassLiteral`, a different node from `ASTFieldAccess`, so the rule never visits one. An explicit exemption would have been a branch no test could take, and pitest would have flagged it
- [x] 1.6 Report at most once per `(declaring type, member name)` pair per file, anchored at the first occurrence
- [x] 1.7 Write the `pmd-test` XML fixture covering every scenario in `specs/static-import-rule/spec.md`, including the conflict, shadowing, class-literal and camelCase-boundary cases
- [x] 1.8 Add a fixture asserting one violation for eighteen occurrences of the same member, and two for two distinct members
- [x] 1.9 Add a fixture asserting `@SuppressWarnings("PMD.UseStaticImports")` suppresses the violation
- [x] 1.10 Write the JUnit 5 test class extending `SimpleAggregatorTst`, tagged `unit`
- [x] 1.11 Add the rule to `category/java/joke.xml` with `minimumLanguageVersion="1.5"`. Confirm PMD accepts that version string — `java 1.7` is known to work in a fixture `source-type`, so the `1.x` scheme is valid. The description states the threshold is a floor and that imports are never forbidden
- [x] 1.12 Add a `source-type` fixture confirming the rule contributes nothing below Java 5

## 2. UseTypeImports

- [x] 2.1 Implement the rule class reporting a fully-qualified type name used in code
- [x] 2.2 Exclude import declarations and package declarations — **no code needed**, verified empirically: PMD stores their names as plain strings, so no `ASTClassType` node exists for them
- [x] 2.3 Implement the split with the stock rule: report only when the simple name is **not** already in scope via an existing import, `java.lang`, or the same package. Fixtures cover all three in-scope paths. The complement claim rests on the stock rule's documented "already in scope" semantics and message text; it was not exercised by running both rules together
- [x] 2.4 Implement conflict detection for simple names claimed by two types in one file
- [x] 2.5 Report at most once per fully-qualified name per file
- [x] 2.6 Confirm a qualified nested type is reported without prescribing which import to add
- [x] 2.7 Confirm no `minimumLanguageVersion` is declared
- [x] 2.8 Write the `pmd-test` XML fixture covering every scenario in `specs/type-import-rule/spec.md`
- [x] 2.9 Write the JUnit 5 test class, tagged `unit`
- [x] 2.10 Add the rule to `category/java/joke.xml` with a class, message, description, priority and both examples

## 3. Resolve the conflict with TooManyStaticImports

- [x] 3.1 Exclude `TooManyStaticImports` from `category/java/codestyle.xml` in `.pmd.xml`, with a comment recording that it is in direct opposition to `UseStaticImports` and that no configuration satisfies both
- [x] 3.2 Confirm `ExcessiveImports` is already excluded from `design.xml` and needs no change
- [x] 3.3 Confirm no other stock rule caps or prohibits imports in a way that fights either new rule

## 4. Bring this repository into compliance

Unlike the last change, this one has real dogfood violations — roughly ten under per-file reporting.

- [x] 4.1 Revert the 27 hand-written qualifications in `VisitDelegationTest`: static-import `doReturn` and `never`, restoring six static imports in that file. This is the change proving itself, since those qualifications exist only because `TooManyStaticImports` capped the file at four
- [x] 4.2 Static-import the `Visibility` constants (`V_PRIVATE`, `V_PACKAGE`, `V_PROTECTED`) and `AccessType.WRITE` in the rule classes that use them
- [x] 4.3 Static-import `Modifier.isPrivate`, `Files.write` and `Collectors.toList`
- [x] 4.4 Confirm `PmdAnalysis.create` is **not** reported, because `create` is in the exclusion list
- [x] 4.5 Confirm no `Foo.class` literal is reported anywhere in the repository
- [x] 4.6 Run `./gradlew pmdMain pmdTest` and confirm zero violations
- [x] 4.7 Confirm `UseTypeImports` reports nothing here, as expected — every fully-qualified name in this repository's Java sits inside an import declaration
- [x] 4.8 Update `RulesetDistributionIT`'s expected rule names to include `UseStaticImports` and `UseTypeImports`

## 5. Documentation

- [x] 5.1 Add a README section for each rule under `## Rules`, matching the existing format
- [x] 5.2 Lead the `UseStaticImports` section with the `TooManyStaticImports` exclusion, since a consumer enabling `codestyle` without it gets contradictory demands — a worse first experience than any previous rule caused
- [x] 5.3 State that the threshold is a floor: the rule never forbids an import, a shorter name may still be imported by hand, and a disagreement is a suppression away
- [x] 5.4 Document the exclusion list and the principle behind it — factory-shaped names where the class supplies the type the member name omits — and that it is hardcoded with suppression as the escape
- [x] 5.5 Explain that conflict detection, not the exclusion list, handles ambiguity between owners, with `Arrays.copyOf` / `List.copyOf` as the worked example
- [x] 5.6 Document the split between `UseTypeImports` and PMD's `UnnecessaryFullyQualifiedName`, so a reader understands why both exist and that they never double-report
- [x] 5.7 State that violations are reported once per file per member or type, so a first-run count reflects import lines rather than occurrences

## 6. Verification

- [x] 6.1 Run the integration matrix and confirm both rules load under PMD 7.0.0 and 7.26.0
- [x] 6.2 Confirm `rulesets/java/joke.xml` needs no edit, since it references `category/java/joke.xml` whole
- [x] 6.3 Confirm no violation is reported against the `pmd-test` XML fixtures or the `category/java/joke.xml` examples
- [x] 6.4 Run `./gradlew clean check` to verify everything. NEVER continue if there are violations
- [x] 6.5 Commit the completed change with /commit-commands:commit

## 7. Follow-ups (not part of this change)

- [ ] 7.1 Correct `java-coding-conventions/SKILL.md` in the `claude-plugins` repository: it lists `emptyList` and `singletonList` as static-import-worthy while listing `Collections.unmodifiableList` as qualify-worthy, with no distinguishing principle. All three are `Collections` members and this change imports all of them. This is the second correction that skill needs — the `protected` one is recorded in the archived `add-testability-rules` follow-ups
