## Why

The composition that makes these rules usable — six PMD stock categories, twenty-two exclusions and
three property overrides — lives in `.pmd.xml`, a loose file at the repository root. It is not
published, so every project that wants the same analysis transcribes it by hand. That is the exact
complaint the bootstrap proposal opened with, one layer up: the rules stopped being copied between
repositories, and the ruleset that arranges them took their place.

Publishing the composition removes the last copied artifact and lets a consuming build name a
ruleset instead of maintaining one.

## What Changes

- **New shipped resource `rulesets/java/joke-strict.xml`** — the current contents of `.pmd.xml`:
  `bestpractices`, `codestyle`, `design`, `errorprone`, `multithreading` and `performance` with
  their exclusions, the three `design` property overrides (`CognitiveComplexity` at 5,
  `AvoidDeeplyNestedIfStmts` at 2, `NPathComplexity` at 5), and `rulesets/java/joke.xml`.
- **`category/java/joke.xml` and `rulesets/java/joke.xml` are untouched.** They keep referencing
  nothing outside this artifact, so the "resolves under any PMD 7.x" promise stays true of them.
  A consumer who wants the rules without the opinions still has that door.
- **`.pmd.xml` is deleted.** This repository analyses itself through the strict ruleset it
  publishes, which makes the dogfooding stronger than it is today: the composition is now the thing
  under test, not a local file that happens to resemble one.
- **`conventions.gradle` declares `ruleSets = ['rulesets/java/joke-strict.xml']`** and drops
  `ruleSetFiles`. The staleness bug that forced `ruleSetFiles` does not apply to a ruleset inside a
  jar: the jar reaches the task through the `pmd` configuration, which Gradle tracks as
  `@Classpath`, so editing the resource rebuilds the jar and re-runs the analysis.
- **The rules dependency stays with each project, not the convention plugin.** `rules/build.gradle`
  keeps `ownRules project(':rules')`; an extracted consumer declares the published coordinate. The
  plugin therefore never needs to know its own version, and self-hosting needs no escape hatch.
- **`rulesets/java/joke-strict.xml` carries a narrower support window than the rules do.** The rules
  hold their PMD 7.0.0 floor. The strict ruleset is promised only on the PMD versions the build
  verifies it against, and names 7.26.0 as its floor. It is known to reference at least one stock
  rule that 7.0.0 does not have — `ImplicitFunctionalInterface`, which `.pmd.xml` excludes and which
  PMD added after 7.0.0. Whether an `<exclude>` of an absent rule is fatal or merely warned about
  under 7.0.0 has not been established, and the artifact should not ship a promise resting on the
  answer.
- **README** replaces the worked-example section with the strict ruleset, and states the two support
  windows side by side.

No change is breaking for existing consumers: the published surface only grows, and both existing
resources keep their behaviour and their guarantee.

## Capabilities

### New Capabilities

_None._ The strict ruleset is a third shipped resource governed by the requirements that already
describe the shipped resource layout, not a separate capability.

### Modified Capabilities

- `rule-distribution`: the resource-layout requirement admits a third shipped resource, and the
  requirement forbidding external ruleset references narrows from every shipped resource to the
  catalogue and the convenience ruleset. A new requirement fixes the strict ruleset's contents and
  its distinct support window, and the cross-version requirement gains a case asserting it loads at
  its own floor.
- `build-foundation`: the requirement mandating `ruleSetFiles` over `ruleSets` inverts, because its
  stated reason — that Gradle cannot track a `List<String>` as a file input — stops applying once
  the ruleset is a classpath resource rather than a file. The dogfooding requirement stops naming
  `.pmd.xml` and names the strict ruleset.

## Impact

- **Added**: `rules/src/main/resources/rulesets/java/joke-strict.xml`.
- **Deleted**: `.pmd.xml`.
- **Modified**: `buildSrc/src/main/groovy/conventions.gradle` (the `pmd` block), `README.md`
  (the "Use it" and "PMD versions" sections, and the dogfooding section that points at `.pmd.xml`).
- **Tests**: `RulesetDistributionIT` gains coverage of the new resource. The per-version
  integration tasks in `rules/build.gradle` must distinguish the two support windows — every version
  loads `rulesets/java/joke.xml`, only 7.26.0 and later load `rulesets/java/joke-strict.xml`.
- **Dependencies**: none. The published POM stays empty; the strict ruleset adds no runtime
  requirement, only a resolution-time expectation about the consumer's PMD version.
- **Risk**: the artifact now names stock rules it does not own, which is the coupling the current
  specs were written to avoid. It is confined to one file with a stated support window, and the
  cross-version matrix is what keeps the window honest.
