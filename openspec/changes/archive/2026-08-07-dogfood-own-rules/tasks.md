## 1. Verify the self-dependency

- [x] 1.1 Add `pmd project(':rules')` to `rules/build.gradle` and run `./gradlew :rules:pmdMain` to confirm Gradle accepts a project depending on itself through a configuration
- [x] 1.2 Confirm no circular task dependency is reported and that `:rules:jar` runs before `:rules:pmdMain`
- [x] 1.3 If Gradle rejects it, fall back to `pmd files(sourceSets.main.output)` and record the substitution in `design.md`; otherwise proceed with `project(':rules')`
- [x] 1.4 Confirm the configuration cache is still reused across two consecutive `./gradlew check` runs

## 2. Wire the ruleset

- [x] 2.1 Add `<rule ref="rulesets/java/joke.xml"/>` to `.pmd.xml` alongside the stock category references
- [x] 2.2 Delete the stale comment claiming the rules are inapplicable because the source targets Java 8
- [x] 2.3 Confirm the ruleset resolves from the `pmd` configuration by classpath reference, not by file path
- [x] 2.4 Confirm `pmdMain` and `pmdTest` both load the rule

## 3. Prove the wiring works

- [x] 3.1 Run `./gradlew check` and confirm it passes with no source change — the repository was verified green before this change was proposed
- [x] 3.2 Temporarily introduce an explicitly typed local variable in `rules/src/main/java`, confirm `pmdMain` fails and names `UseVarForLocalVariables`, then revert it
- [x] 3.3 Repeat 3.2 in `rules/src/test/java` to confirm `pmdTest` is covered, then revert
- [x] 3.4 Confirm no violation is reported against the `pmd-test` XML fixtures or the `category/java/joke.xml` examples, both of which contain deliberate violations

## 4. Documentation

- [x] 4.1 Add a recovery note to the build section of `README.md`: the rules are applied by the module that builds them, so a broken rule fails `check`, and `./gradlew check -x pmdMain -x pmdTest` builds past it
- [x] 4.2 Note in `README.md` or the rule test data that fixtures stay in the `pmd-test` XML descriptors and must not move into `.java` files

## 5. Verification

- [x] 5.1 Confirm `pmdMain` runs at Gradle's `toolVersion` (7.26.0) while the rules still compile against 7.0.0, so dogfooding and the integration matrix remain complementary rather than redundant
- [x] 5.2 Confirm the integration matrix still runs at both the floor and the newest supported version and that `check` depends on all of it
- [x] 5.3 Run `./gradlew clean check` to verify everything. NEVER continue if there are violations
- [x] 5.4 Commit the completed change with /commit-commands:commit
