## Why

The build had accumulated three pieces of machinery that each cost more to carry than the signal
they returned: a hand-rolled cross-version PMD matrix (five configurations and a task per version,
plus a subset guard to police itself), a POM-parsing verification task, and an `ownRules`
configuration existing solely to work around `PmdPlugin.defaultDependencies`. All three were
correct. None was worth its weight once the alternatives were on the table.

Alongside that, `@NullMarked` was a hand-written `package-info.java` per package — a file whose only
possible content is fixed, and whose absence NullAway's `RequireExplicitNullMarking` reports as an
error after the fact rather than preventing.

## What Changes

- **PMD is supplied by an explicit `pmd-dist` dependency rather than `toolVersion`.** The convention
  plugin declares `net.sourceforge.pmd:pmd-dist:7.26.0` on the `pmd` configuration.
- **The `ownRules` configuration is removed.** With tool supply taken over explicitly,
  `defaultDependencies` no longer needs to be kept armed, so `project(':rules')` goes on the `pmd`
  configuration directly. This is a consequence of the previous bullet, not an independent decision.
- **BREAKING (to the spec, not to consumers): the cross-version PMD matrix is removed.** The
  per-version configurations, the generated `integrationTestPmd*` tasks, the
  `strictRulesetVersions` declaration and its subset guard all go. Two signals remain and are
  sufficient: `integrationTest` resolves PMD at the 7.0.0 floor from the platform, and
  `pmdMain`/`pmdTest` run the published rules under 7.26.0 via `pmd-dist`. Floor and ceiling are
  both covered; the interior versions were never the risk.
- **The `verifyPomHasNoDependencies` task is removed.** The requirement it guarded stands; the guard
  does not. Every dependency the `rules` module declares is `compileOnly`, `annotationProcessor`,
  `testImplementation` or `testRuntimeOnly`, none of which reach the POM.
- **`rulesets/java/joke-strict.xml` no longer carries an enforced 7.26.0 floor.** It remains a
  stated promise in the file's `<description>` and the README. Nothing in the build asserts it, and
  the integration tests exercise the strict ruleset at whatever version the test classpath resolves.
  A new PMD release is adopted by raising the `pmd-dist` coordinate and seeing whether the build
  stays green.
- **`@NullMarked` is generated.** `io.github.joke.jspecify:processor` runs on the main source set and
  emits an annotated `package-info.java` for every package; the hand-written one is deleted.
- **Lombok is wired but unused.** `lombok.config` at the repository root, the dependency on both
  `compileOnly` and `annotationProcessor`. No rule class uses it today and none can — see below.
- **Build rationale moves from comments into specs.** Roughly ninety lines of explanatory comment are
  removed from `conventions.gradle`, `dependencies/build.gradle` and `rules/build.gradle`. The
  reasoning is not discarded: it lives in `openspec/specs/`, which becomes the single home for it.

### On Lombok being unused

All eight rule classes have zero instance fields. PMD reuses a rule instance across files within a
thread, so per-file state in a rule is a bug — the classes are stateless behaviour by construction.
Lombok's surface is fields and constructors, so there is presently nothing for it to generate.

The wiring is added anyway, as a configured baseline: the first class that would benefit does not
have to relitigate `lombok.config`, the annotation processor path and the NullAway interaction. The
specs record that the absence of Lombok annotations is deliberate, so it is not later read as an
oversight and "fixed" by annotating a stateless rule.

## Capabilities

### New Capabilities

None. Every change here modifies an existing capability.

### Modified Capabilities

- `build-foundation`: how PMD the tool is supplied and versioned; how the rules artifact reaches the
  `pmd` configuration; where build rationale is recorded; the annotation-processor sources of
  `@NullMarked`; the Lombok baseline.
- `rule-distribution`: removal of the cross-version matrix requirement; removal of the POM
  verification task while retaining the dependency-free-POM requirement; the strict ruleset's floor
  demoted from enforced constraint to stated promise.

## Impact

- `buildSrc/src/main/groovy/conventions.gradle` — `pmd` block; comment removal.
- `rules/build.gradle` — `ownRules` configuration, matrix, POM task all removed; `annotationProcessor`
  declarations added; comment removal.
- `dependencies/build.gradle` — constraints for `io.github.joke.jspecify:processor` and
  `org.projectlombok:lombok`; comment removal.
- `lombok.config` — new, at the repository root.
- `rules/src/main/java/io/github/joke/pmd/rules/java/package-info.java` — deleted, now generated.
- `README.md` — the PMD version section must stop describing a matrix that no longer exists and must
  restate the strict ruleset's floor as a promise.
- No change to the published artifact: same coordinates, same three resources, still no POM
  dependencies, rule classes still compiled against PMD 7.0.0.
