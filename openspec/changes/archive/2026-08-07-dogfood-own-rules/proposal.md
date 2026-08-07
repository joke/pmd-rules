## Why

This project publishes PMD rules and does not run them on itself. `UseVarForLocalVariables` is
proven against synthetic fixtures — 21 `pmd-test` cases and five integration tests per PMD version —
but never against a real codebase, and the most convenient real codebase is this one.

The original design deferred dogfooding to a future centralized conventions plugin, on the stated
grounds that this project's main source targeted Java 8, where `var` does not exist. That reasoning
died when the language level moved to 11 in the bootstrap change; the comment recording it in
`.pmd.xml` is now simply wrong. Nothing else blocks it.

The timing matters more than the wiring. Running the shipped ruleset over this repository's four Java
files today produces **zero violations**, verified with PMD 7.26 before proposing this. Enabling it
now is therefore a pure wiring change whose correctness is easy to see. Every rule added afterwards
must leave this repository green as part of its own change, instead of accumulating a debt that some
later change has to pay while also being blamed for it.

## What Changes

- Add `project(':rules')` to the `rules` module's `pmd` configuration, so PMD analyses this
  project's own source with the artifact this project builds.
- Reference `rulesets/java/joke.xml` from `.pmd.xml`, alongside the PMD stock categories already
  there, and delete the stale Java 8 comment that explains its absence.
- Apply the rules to both `pmdMain` and `pmdTest`.
- Document the recovery path in `README.md`: a rule that throws takes out the build that produces it,
  and the fix is to edit the rule that is currently failing the build. `./gradlew check -x pmdMain -x
  pmdTest` is the escape hatch, and the person who needs it will not be in a position to work it out.
- Record that rule test fixtures SHALL stay embedded in the `pmd-test` XML descriptors rather than
  living in `.java` files. Fixtures contain deliberate violations and are invisible to PMD only
  because they are XML; moving them to real source files — which some PMD projects do — would make
  the build flag its own test data.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `build-foundation`: adds a requirement that the build runs the rules it publishes against its own
  source, including the configuration wiring, the source sets covered, and the documented recovery
  path when a rule breaks its own build.
- `rule-distribution`: extends **Cross-version compatibility is verified by the build** — dogfooding
  runs the rules under Gradle's `toolVersion` (7.26.0) against real code, which is a third
  compatibility signal alongside the two integration-matrix runs over synthetic fixtures. Adds a
  requirement that test fixtures stay in XML.

## Impact

- **`rules/build.gradle`**: one dependency on the `pmd` configuration.
- **`.pmd.xml`**: one ruleset reference added, one stale comment removed.
- **`README.md`**: a recovery note in the build section.
- **Task graph**: `:rules:pmdMain` gains a dependency on `:rules:jar`, which in turn depends on
  `:rules:classes`. No cycle, since `jar` does not depend on `pmdMain`. A project depending on
  itself through a configuration is unusual enough that the change verifies it before relying on it;
  if Gradle rejects it, the fallback is `pmd files(sourceSets.main.output)`, which is simpler but
  less faithful to what a consumer does. `project(':rules')` is preferred because it exercises the
  published artifact rather than a loose directory of classes.
- **Build time**: `check` now packages the jar before running PMD.
- **No behaviour change today**: zero violations, verified. The change is expected to be green on
  first run, and a violation appearing would mean the wiring is wrong rather than the code is.
- **Not in scope**: any new rule; the question of whether "protected over private, instance over
  static" applies to PMD rule classes; and migrating `jspecify` off its broken PMD 6 XPath copy of
  this rule.
