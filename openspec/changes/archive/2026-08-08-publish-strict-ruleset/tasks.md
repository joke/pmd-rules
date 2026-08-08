## 1. Ship the strict ruleset

- [x] 1.1 Create `rules/src/main/resources/rulesets/java/joke-strict.xml` with the six stock category
      references, their exclusions and the three `design` property overrides copied verbatim from
      `.pmd.xml`, plus `<rule ref="rulesets/java/joke.xml"/>`
- [x] 1.2 Carry over the `TooManyStaticImports` exclusion comment recording its opposition to
      `UseStaticImports`
- [x] 1.3 Write the `<description>` so it states what the file composes, that its PMD floor is
      7.26.0, and that this floor is higher than the artifact's own 7.0.0
- [x] 1.4 Confirm `category/java/joke.xml` and `rulesets/java/joke.xml` are left byte-identical

## 2. Split the integration matrix

- [x] 2.1 Add `StrictRulesetDistributionIT` alongside `RulesetDistributionIT`, loading
      `rulesets/java/joke-strict.xml` by classpath reference and driving `PMDConfiguration`,
      `RuleSetLoader`, `PmdAnalysis` and `RuleViolation` directly — no `pmd-test`
- [x] 2.2 Assert the strict ruleset resolves every rule in `category/java/joke.xml` and also rules
      this artifact does not define
- [x] 2.3 Assert a `CognitiveComplexity` property override survives the composition, so a silently
      dropped override fails rather than passing
- [x] 2.4 Declare in `rules/build.gradle` the set of PMD versions that load the strict ruleset, and
      fail the build if it is not a subset of the versions the matrix runs
- [x] 2.5 Exclude `StrictRulesetDistributionIT` from the base `integrationTest` task, which runs the
      7.0.0 floor
- [x] 2.6 Include it only in the per-version tasks whose version is in the strict set

## 3. Rewire the build

- [x] 3.1 Replace `ruleSets = []` / `ruleSetFiles = files("$rootDir/.pmd.xml")` in
      `buildSrc/src/main/groovy/conventions.gradle` with
      `ruleSets = ['rulesets/java/joke-strict.xml']`
- [x] 3.2 Replace the `ruleSetFiles` comment with one explaining why `ruleSets` is now correct: the
      jar is a `@Classpath` input, so tracking is preserved rather than traded away
- [x] 3.3 Confirm `rules/build.gradle` needs no change — the `ownRules` configuration and its
      `extendsFrom` arrangement stay exactly as they are, and the plugin declares no rules dependency
- [x] 3.4 Delete `.pmd.xml`

## 4. Documentation

- [x] 4.1 Rewrite the README "Use it" section to show `rulesets/java/joke-strict.xml` as the
      single-reference path, keeping `rulesets/java/joke.xml` and the per-rule reference below it
- [x] 4.2 State in "Use it" that a consumer wanting the composition minus a rule needs their own
      ruleset file, because `ruleSets` cannot subtract
- [x] 4.3 Rewrite the "PMD versions" section to state both windows — 7.0.0 for the rule classes,
      `category/java/joke.xml` and `rulesets/java/joke.xml`; 7.26.0 for the strict ruleset — with the
      `ImplicitFunctionalInterface` reason
- [x] 4.4 Update the "This project runs its own rules on itself" section: it no longer names
      `.pmd.xml`, and now describes analysis through the published strict ruleset
- [x] 4.5 Note that `toolVersion` and the strict floor move together, and that raising `toolVersion`
      means adding that version to the matrix

## 5. Verify

- [x] 5.1 Confirm `pmdMain` and `pmdTest` re-run rather than reporting `UP-TO-DATE` after editing
      `rulesets/java/joke-strict.xml`, which is the tracking claim the design rests on
- [x] 5.2 Confirm the repository is green under the strict ruleset without any source change — the
      composition is unchanged, so any new violation means the copy was not verbatim
- [x] 5.3 Run `./gradlew check` and fix every violation before continuing. NEVER continue if there
      are violations
- [x] 5.4 Commit with `/commit-commands:commit`
