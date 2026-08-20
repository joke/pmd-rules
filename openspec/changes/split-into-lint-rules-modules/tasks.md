## 1. Spike the CodeNarc ruleset mechanism

Everything downstream assumes a classpath-resolved ruleset works. A negative result changes the
mechanism, not the design, but it changes it before any of it is built.

- [x] 1.1 In a throwaway build outside this repository, produce a jar containing
      `rulesets/groovy/joke-strict.groovy` and put it on a project's `codenarc` configuration
- [x] 1.2 Set `config = resources.text.fromString("ruleset { ruleset('rulesets/groovy/joke-strict.groovy') }")`
      and confirm `codenarcMain` resolves the shipped ruleset rather than failing to load it. The
      nesting is required: `RuleSetBuilder` exposes only `ruleset(Closure)` at the top level
- [x] 1.3 Confirm the stub is parsed as Groovy DSL — `RuleSetUtil` falls through to `GroovyDslRuleSet`
      only when the materialised file has neither an `.xml` nor a `.json` extension
- [x] 1.4 Confirm a rule class inside that jar is resolvable by `rule(<Class>)` from the ruleset
- [x] 1.5 If 1.2 fails, try `resources.text.fromArchiveEntry(configurations.codenarc, …)`; if that
      fails too, generate the stub into `layout.buildDirectory` with a controlled extension
- [x] 1.6 Record the outcome in `design.md` Decision 6, replacing the stated mechanism if it changed

## 2. Rename and split the existing module

No behaviour change in this group. `./gradlew check` must be green at the end of it.

- [x] 2.1 `git mv rules pmd-rules`
- [x] 2.2 `settings.gradle`: set `rootProject.name = 'lint-rules'` and include `dependencies` and
      `pmd-rules`
- [x] 2.3 Move the Java packages from `io.github.joke.pmd.rules.java` to
      `io.github.joke.lint.pmd.rules.java` across main source, test source and the test resource
      directories that mirror the package
- [x] 2.4 Update every `class` attribute in `pmd-rules/src/main/resources/category/java/joke.xml` to
      the new package
- [x] 2.5 Convention plugin: change `group` to `io.github.joke.lint`
- [x] 2.6 Convention plugin: delete the hardcoded `description` from the metadata block
- [x] 2.7 `pmd-rules/build.gradle`: declare its own `description`, and change the self-analysis
      dependency to `pmd project(':pmd-rules')`
- [x] 2.8 Update the committed Eclipse `.project` / `.classpath` and `.idea` module files to the new
      module name
- [x] 2.9 Run `./gradlew check` — green before continuing

## 3. Extend the dependency platform

- [x] 3.1 Add the CodeNarc floor `org.codenarc:CodeNarc:3.1.0-groovy-4.0` as a constraint, with the
      same "raising this is a decision, not a dependency bump" comment the PMD floor carries
- [x] 3.2 Add Groovy 4.0.x and `org.spockframework:spock-core:2.4-groovy-4.0` constraints
- [x] 3.3 Record the Groovy-line coupling in a comment: CodeNarc parses `.groovy` source with its own
      embedded Groovy, so the Groovy, Spock and CodeNarc coordinates move together

## 4. Add the codenarc-rules module

- [x] 4.1 Create `codenarc-rules/` with `build.gradle` applying `groovy`, `maven-publish` and
      `conventions`, and include it in `settings.gradle`
- [x] 4.2 Declare dependencies: `compileOnly` CodeNarc, `testImplementation` CodeNarc + Spock,
      `annotationProcessor` for the JSpecify processor and Lombok, `pmd project(':pmd-rules')`,
      `codenarc project(':codenarc-rules')`
- [x] 4.3 Declare the module's own `description`
- [x] 4.4 Verify no declaration sits on `implementation`, `api` or `runtimeOnly`, and that the
      generated POM has no dependencies
- [x] 4.5 Create `src/main/resources/rulesets/groovy/joke.groovy` — a valid ruleset declaring no rules
      yet, with a `description`
- [x] 4.6 Port every rule from the repository-root `.codenarc.groovy` into
      `src/main/resources/rulesets/groovy/joke-strict.groovy`, keeping its grouping comments, and add
      `ruleset('rulesets/groovy/joke.groovy')`
- [x] 4.7 State the strict ruleset's support window in its `description`: it names rules this
      artifact does not define and therefore carries a narrower promise than `joke.groovy`
- [x] 4.8 Delete `.codenarc.groovy` from the repository root

## 5. Activate CodeNarc in the convention plugin

- [x] 5.1 Replace `configFile = file("$rootDir/.codenarc.groovy")` with the mechanism the spike
      settled on, referencing `rulesets/groovy/joke-strict.groovy` by classpath path
- [x] 5.2 Declare the CodeNarc tool as a coordinate on the `codenarc` configuration, on the Groovy 4
      line and at or above the floor; do not set `toolVersion`
- [x] 5.3 Confirm the three violation thresholds are still zero and the block carries no `$rootDir`
- [x] 5.4 Confirm `spotless` still declares a `java` block only — no `groovy`, no `groovyGradle`
- [x] 5.5 Confirm the Pitest `jvmArgs` carry no `spock.parallel.disabled` property

## 6. Give the module a corpus and prove the distribution

- [x] 6.1 Write the `integration`-tagged Spock specification that loads
      `rulesets/groovy/joke.groovy` and `rulesets/groovy/joke-strict.groovy` by classpath reference
      and fails if either is missing or misnamed
- [x] 6.2 Confirm `codenarcTest` runs over `src/test/groovy` and that `codenarcMain` and `pmdTest`
      are harmless no-ops for this module
- [x] 6.3 Confirm `pmdMain` analyses the module's Java source with `rulesets/java/joke-strict.xml`
      from `:pmd-rules`
- [x] 6.4 Confirm `@spock.lang.Tag` selection works in both directions: `test` picks up `unit`,
      `integrationTest` picks up `integration`, and `pitest` reports mutations rather than no tests

## 7. Triage the first violation wave

The ported stock rules will fire against Spock sources. This is expected and is the point.

- [x] 7.1 Run `./gradlew codenarcTest` and record every violation the ported composition reports
- [x] 7.2 For each violation that is a rule misreading Spock — `where:`-block variables, `@Shared`
      fields, `def "feature name"()` — exclude the rule from `joke-strict.groovy` with a comment
      naming the construct it misfired on. **No exclusions were needed**: the ported composition ran
      clean against both specifications. The anticipated wave did not materialise at this corpus
      size; it should be re-checked as the specifications grow
- [x] 7.3 For each violation that is genuinely our code, fix the code rather than the ruleset
- [x] 7.4 Confirm every exclusion carries its comment, so each reads as evidence for a follow-up rule

## 8. Rewire the release

- [x] 8.1 `release-please-config.json`: replace the single root package with `pmd-rules` and
      `codenarc-rules`, each `release-type: simple` **and a distinct `component`** — without it the
      version plugin refuses both, since two packages would otherwise release as `v<version>`
- [x] 8.2 `.release-please-manifest.json`: carry `pmd-rules` forward at `0.1.0` and start
      `codenarc-rules` fresh
- [x] 8.3 Verify `io.github.joke.conventional-version` resolves both packages independently
- [x] 8.4 Verify it does not fail on `:dependencies` and the root project, which have no manifest
      entry
- [x] 8.5 Verify it finds component-prefixed tags, and that a package with no tag in history at all
      resolves rather than failing. **Both required seeding**: the plugin refuses to compute a version
      until a tag matching each manifest entry exists, so moving to multi-package is also a tag
      migration. `pmd-rules-v0.1.0` and `codenarc-rules-v0.0.0` were created locally and are NOT
      pushed
- [x] 8.6 Confirm neither workflow file passes a version, stage, scope or tag-prefix property

## 9. Publish the relocation

Ordered last because a published relocation POM cannot be withdrawn from Central.

- [x] 9.1 Add a POM-only publication at `io.github.joke.pmd:rules` whose POM declares
      `<distributionManagement><relocation>` naming `io.github.joke.lint:pmd-rules`, carrying no jar
- [x] 9.2 Confirm it is signed by the same mechanism as the other publications
- [ ] 9.3 Rehearse it against the Central Portal snapshot repository before any release
- [ ] 9.4 Open a follow-up change to remove the publication once it has been released

## 10. Documentation

- [x] 10.1 `README.md`: retitle for the repository, and split usage into a PMD section and a CodeNarc
      section, each naming its own coordinates
- [x] 10.2 Document the CodeNarc wiring: the artifact on the `codenarc` configuration plus a local
      stub, and state that the stub is required because Gradle accepts only a file
- [x] 10.3 Document the CodeNarc support window — floor `3.1.0-groovy-4.0`, Groovy 3 and Groovy 5
      lines unsupported — alongside the existing PMD windows
- [x] 10.4 Extend the recovery path section to name `codenarcTest` alongside `pmdMain` and `pmdTest`
- [x] 10.5 Document the coordinate move and the relocation POM
- [x] 10.6 `.github/settings.yml`: rename the repository to `lint-rules`, update the description and
      topics to name both PMD and CodeNarc
- [x] 10.7 `openspec/config.yaml`: rewrite the context block for two artifacts, two languages and the
      Spock focus of the CodeNarc rules; remove "No Groovy, no Spock"
- [x] 10.8 Check `.github/dependabot.yml` still covers the moved and added modules

## 11. Verify

- [x] 11.1 Run `./gradlew check` and confirm it is green. NEVER continue if there are violations
- [x] 11.2 Confirm both published POMs declare no dependencies
- [x] 11.3 Confirm both jars contain their ruleset resources at the documented paths
- [ ] 11.4 Commit the completed change with `/commit-commands:commit`
