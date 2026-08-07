## Why

Opinionated PMD rules currently live as inline XPath snippets inside individual projects'
`.pmd.xml` files, where they are untested, unversioned, and copied by hand between repositories.
`UseVarForLocalVariables` in `jspecify`'s `.pmd.xml` is the working example: an XPath expression
written against PMD 6 AST attributes that nothing verifies still matches anything under PMD 7.

This project makes those rules a real, published artifact — Java-implemented, unit-tested,
mutation-tested, and consumable by any Gradle project that puts it on its `pmd` configuration. A
later centralized conventions plugin will ship this artifact, so every project gets the same rules
by applying one plugin instead of copying XML.

The repository does not exist yet: it needs the whole build, CI and release apparatus before a
single rule can be written.

## What Changes

- Create the Gradle build as a three-project layout — `buildSrc` (convention plugin),
  `dependencies` (`java-platform`), `rules` (the published artifact) — adapted from `jspecify`.
- Carry over the `conventions` convention plugin: Spotless with Palantir Java Format, PMD, Error
  Prone with NullAway in JSpecify mode, Pitest at 100/100/100, the `test`/`integrationTest` split
  by JUnit tag, and the publishing, signing and metadata wiring.
- Target **Java 11** (`options.release = 11`) for main source only; test source compiles at the
  toolchain's own level. Java 8 was the original intent and was abandoned: at `--release 8` javac
  cannot resolve `ElementType.MODULE` while reading JSpecify's `@NullMarked`, and emits a warning
  no `-Xlint` category suppresses, which `-Werror` turns into a build failure. Java 8 and NullAway
  are mutually exclusive; NullAway is kept.
- Drop the Spock/Groovy test stack in favour of **JUnit 5** with `net.sourceforge.pmd:pmd-test`.
  CodeNarc configuration is retained in the convention plugin (inert here, needed after
  extraction) but no module applies the `groovy` plugin.
- Compile rules against **PMD 7.0.0** as `compileOnly`. The published POM declares no dependencies:
  consuming projects choose their own PMD version, which Gradle's `pmd` configuration supplies at
  analysis time.
- Ship rule resources in PMD's two-file convention: `category/java/joke.xml` defining every rule
  with its metadata, and `rulesets/java/joke.xml` selecting all of them as a convenience ruleset.
  Neither references PMD's stock categories, so the artifact is decoupled from PMD's rule
  catalogue.
- Verify the cross-version claim with an `integrationTest` matrix that runs the built rules under
  several PMD 7.x versions rather than asserting compatibility.
- Port `UseVarForLocalVariables` from `jspecify`'s `.pmd.xml` as the seed rule, reimplemented in
  Java against the PMD 7 AST, proving the layout end to end.
- Carry over CI verbatim where possible: `build.yml`, `release.yml` with release-please manifest
  mode, snapshot-rehearses-release publishing, GPG signing, dependabot, autoapproval and
  pre-commit. `.github/settings.yml` is rewritten for the `pmd-rules` repository rather than
  copied.
- Publish at coordinates `io.github.joke.pmd:rules`.

## Capabilities

### New Capabilities
- `build-foundation`: the Gradle build that produces the published artifact — module layout, the
  shared `conventions` plugin, language level and toolchain, the dependency and test stacks,
  mutation-testing thresholds, version derivation, release-please configuration, and publishing
  and signing to Maven Central.
- `rule-distribution`: how rules are implemented, packaged and consumed — the PMD compile-only
  contract and its version floor, the `category`/`ruleset` resource layout, the empty published
  POM, the cross-version compatibility matrix, and the consumer wiring contract.
- `var-local-variables-rule`: the seed rule requiring local variable declarations to use `var`,
  including its detection semantics and the cases it must not flag.

### Modified Capabilities

_None — this is a new repository with no existing specs._

## Impact

- **New repository content**: everything. The repository currently holds only `.claude/` and
  `openspec/`.
- **Published artifact**: `io.github.joke.pmd:rules` on Maven Central via the Central Portal,
  signed, with sources and javadoc jars.
- **GitHub repository**: named `pmd-rules`, configured by `.github/settings.yml` through the
  Probot settings app, with branch protection requiring the `build` check.
- **Secrets required**: `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`, `GPG_FINGERPRINT`,
  `mavenCentralUsername`, `mavenCentralPassword` — the same set `jspecify` uses.
- **Downstream**: a future centralized conventions plugin will depend on this artifact; `jspecify`
  will eventually replace its inline `UseVarForLocalVariables` with a reference to it. Neither is
  in scope here.
- **Not in scope**: the conventions plugin extraction, dogfooding these rules in this repository's
  own build, any opinionated ruleset combining stock PMD categories, and rules beyond the seed.
