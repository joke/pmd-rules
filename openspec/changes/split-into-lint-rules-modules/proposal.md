## Why

The opinions this repository encodes exist in two enforcement backends, not one: PMD covers Java,
CodeNarc covers the Groovy and Spock that these projects test with. The Spock conventions actually in
use — no `setup:`/`given:` block, value assertions in `expect` and interactions in `then`, strict
mocking closed by `0 * _`, no `@Unroll` — are enforced by nothing, because CodeNarc ships only two
Spock rules and neither covers them.

A second backend cannot be added to a repository whose name, group, package, module layout, release
manifest and specs all hardcode a single tool. This change performs that transformation, so the
Spock rules themselves can land afterwards one at a time.

## What Changes

- **BREAKING** Coordinates move from `io.github.joke.pmd:rules` to `io.github.joke.lint:pmd-rules`.
  The abandoned coordinate gets one final publish carrying a
  `<distributionManagement><relocation>` POM, so consumer tooling points at the new address.
- **BREAKING** Java packages move from `io.github.joke.pmd.rules.java` to
  `io.github.joke.lint.pmd.rules.java`, aligning package with group and matching the new module's
  `io.github.joke.lint.codenarc.rules.spock`. Referencing a rule class directly was never the
  supported path — consumers reference the shipped rulesets — but the class names in
  `category/java/joke.xml` do change.
- The repository is renamed `lint-rules` and `rootProject.name` follows.
- The `rules` module becomes `pmd-rules`; a new `codenarc-rules` module is added. Project names now
  equal artifactIds, so no publication needs an explicit `artifactId`.
- `codenarc-rules` implements its rules in **Java** — CodeNarc's `AbstractRule`,
  `AbstractAstVisitor` and `AbstractAstVisitorRule` are Java classes, so the module keeps Error
  Prone, NullAway, Spotless, `-Werror` and Pitest — and tests them in **Groovy/Spock**, which is the
  corpus its own published rules then analyse.
- Dogfooding closes over both artifacts: PMD from `:pmd-rules` analyses `codenarc-rules`' Java main
  source, and CodeNarc from `:codenarc-rules` analyses its own Spock test source. Neither artifact
  is published without having been run on real code by the build that produces it.
- `codenarc-rules` ships a Groovy-DSL ruleset carrying every rule currently listed in the
  repository-root `.codenarc.groovy`, which is then deleted. The DSL rather than XML because
  CodeNarc's XML form requires a fully-qualified class name per rule while the DSL accepts the bare
  stock names.
- One Spock rule ships with the transformation: `AvoidUnrollAnnotation`. Pitest runs with
  `failWhenNoMutations = true` and `check` depends on it, so a module with no rule classes cannot
  build; one real rule proves the whole loop rather than relaxing a quality gate. The remaining
  conventions land one change at a time.
- CodeNarc sits on the **Groovy 5** line — CodeNarc 4.0.0, Groovy 5.0.x, `spock-core:2.4-groovy-5.0`
  — because the ported composition names `SpockMissingAssert`, which the Groovy 4 line only gained in
  3.3.0 and so could not carry at its own oldest release.
- The convention plugin's CodeNarc block stops being inert: it loses its `$rootDir` reference in
  favour of a classpath-resolved ruleset, and gains the Groovy/Spock test wiring that
  `build-foundation` currently forbids. Its hardcoded artifact `description` moves to each module,
  and `group` becomes `io.github.joke.lint`.
- Spotless keeps its `java` block only. Groovy source is deliberately left unformatted; CodeNarc
  carries Groovy style.
- `release-please` moves to a **multi-package** manifest so the two artifacts version independently.
  `pmd-rules` carries its version forward from `0.1.0`; `codenarc-rules` starts fresh.

## Capabilities

### New Capabilities

- `pmd-rule-distribution`: how the PMD rules are packaged and consumed — Java rule classes, PMD
  compile-only at the supported floor, a dependency-free POM, the `category` / `rulesets` resource
  split, the two support windows, and the documented consumer wiring. Content carries over from
  `rule-distribution`, requalified for the renamed module, package and coordinates.
- `codenarc-rule-distribution`: how the CodeNarc rules are packaged and consumed — Java rule classes
  against CodeNarc's Java base types, CodeNarc and Groovy compile-only at the 4.0.0 floor on the
  Groovy 5 line, a dependency-free POM, the shipped Groovy-DSL ruleset, classpath ruleset resolution
  through a stub, Spock-based rule tests, and the documented consumer wiring.
- `spock-unroll-annotation-rule`: the `AvoidUnrollAnnotation` rule — what it reports, why it matches
  the annotation by name rather than resolving it, and the two shape constraints the repository's own
  gates impose on every CodeNarc rule class.

### Modified Capabilities

- `build-foundation`: module layout gains a fourth project; "CodeNarc is retained but inert" inverts
  to CodeNarc being load-bearing; the test stack readmits Groovy and Spock scoped to one module;
  publishing covers two artifacts plus a relocation POM at the retired coordinate; version derivation
  moves to a multi-package manifest with per-component tags; "the build runs the rules it publishes"
  extends to both artifacts; Spotless's scope is stated as Java-only.
- `rule-distribution`: every requirement is REMOVED. The capability is renamed — its content
  reappears under `pmd-rule-distribution`, which is PMD-specific and now reads as such beside its
  CodeNarc sibling.
- `type-import-rule`: one scenario illustrates a package declaration with the old package name and is
  restated with the new one. Behaviour is unchanged.

## Impact

- **Consumers**: must change group and artifactId. The relocation POM makes Maven and Gradle warn
  rather than fail silently. Ruleset resource paths (`rulesets/java/joke-strict.xml`,
  `category/java/joke.xml`) are unchanged, so no `ruleSets` configuration changes.
- **Build**: `settings.gradle`, both module `build.gradle` files, `buildSrc/conventions.gradle`, the
  `dependencies` platform (CodeNarc floor and Spock/Groovy versions added).
- **Release**: `release-please-config.json` and `.release-please-manifest.json` become multi-package.
  Path-based commit routing means a commit touching only `buildSrc/`, `dependencies/` or the
  repository root maps to no package and cuts no release — a floor bump must touch the module it
  affects. This is also the first real exercise of `io.github.joke.conventional-version`'s
  multi-package support.
- **Repository**: `.github/settings.yml` (name, description, topics), `README.md` (retitled and split
  per artifact, plus the recovery path for a broken rule in either module), `.codenarc.groovy`
  (deleted, content shipped), `openspec/config.yaml` (context block rewritten), and the Eclipse
  `.project` / IDEA module files.
- **Risk**: the ported stock ruleset may fire against Spock specs — `UnusedVariable` and
  `UnusedPrivateField` can misread `where:`-block variables and `@Shared` fields. Triaging that first
  wave into exclusions is in scope here, and each exclusion is evidence for a follow-up Spock rule.
