## 1. Take over PMD tool supply

- [x] 1.1 Replace `toolVersion = '7.26.0'` in `buildSrc/src/main/groovy/conventions.gradle` with a
      `dependencies { pmd 'net.sourceforge.pmd:pmd-dist:7.26.0' }` block inside the `pmd` extension
- [x] 1.2 Leave `consoleOutput`, `rulesMinimumPriority` and the `ruleSets` assignment untouched — the
      `ruleSets`-not-`ruleSetFiles` decision is unaffected by this change
- [x] 1.3 Confirm `pmdMain` still resolves and runs, so `defaultDependencies` no longer firing has
      genuinely been compensated for rather than merely bypassed

## 2. Collapse the self-analysis wiring

- [x] 2.1 Delete the `ownRules` configuration and the `configurations.named('pmd') { extendsFrom … }`
      block from `rules/build.gradle`
- [x] 2.2 Declare `pmd project(':rules')` directly
- [x] 2.3 Confirm `pmdMain` and `pmdTest` still apply `rulesets/java/joke-strict.xml` from the jar,
      not from a stale classpath entry

## 3. Remove the cross-version matrix

- [x] 3.1 Delete `pmdFloorVersion`, `additionalPmdVersions`, `strictRulesetVersions`, the
      `matrixVersions` subset guard and the `strictRulesetIT` filter from `rules/build.gradle`
- [x] 3.2 Delete the `additionalPmdVersions.each { … }` loop, with its per-version `pmdRuntime*`
      configurations and `integrationTestPmd*` tasks, and the `check` dependencies on them
- [x] 3.3 Delete the conditional exclusion of `StrictRulesetDistributionIT` from the base
      `integrationTest` task
- [x] 3.4 Keep both `RulesetDistributionIT` and `StrictRulesetDistributionIT` — the split by resource
      survives; only the split by PMD version is removed
- [x] 3.5 Confirm `integrationTest` runs both classes and resolves PMD at the platform floor
- [x] 3.6 Update the class comment on `StrictRulesetDistributionIT`, which still describes itself as
      running "only on the versions `rules/build.gradle` declares for the strict ruleset" and being
      excluded by "the floor task" — neither exists now

## 4. Remove the POM verification task

- [x] 4.1 Delete `verifyPomHasNoDependencies` and the `check` dependency on it from
      `rules/build.gradle`
- [x] 4.2 Generate the POM once by hand (`./gradlew :rules:generatePomFileForMavenPublication`) and
      confirm it declares no dependency, so the property is verified at the moment the guard is
      withdrawn rather than assumed
- [x] 4.3 Confirm every declaration in `rules/build.gradle` is on `compileOnly`,
      `annotationProcessor`, `pmd`, `testImplementation`, `testCompileOnly` or `testRuntimeOnly` —
      this configuration discipline is now the only thing keeping the POM empty

## 5. Generate the null marking

- [x] 5.1 Constrain `io.github.joke.jspecify:processor` in `dependencies/build.gradle`
- [x] 5.2 Declare it on the `rules` module's `annotationProcessor` path, with
      `annotationProcessor platform(project(':dependencies'))` alongside
- [x] 5.3 Keep `org.jspecify:jspecify` on `compileOnly` — the annotations are still referenced at
      compile time
- [x] 5.4 Delete `rules/src/main/java/io/github/joke/pmd/rules/java/package-info.java`, only after the
      processor is in place
- [x] 5.5 Confirm the generated `package-info.java` appears under
      `rules/build/generated/sources/annotationProcessor/java/main` and carries `@NullMarked`
- [x] 5.6 **Verify NullAway is still enforcing.** Temporarily introduce a dereference of a `@Nullable`
      value in a rule class and confirm `compileJava` fails with a NullAway error, then revert. The
      convention plugin sets `treatGeneratedAsUnannotated = true` and the generated `package-info`
      carries `@Generated`; a green build proves nothing here, because that is exactly what a
      disabled checker produces
- [x] 5.7 If 5.6 shows checking is off, stop and resolve it before proceeding — the alternative is a
      build that has silently lost a quality gate. **Not triggered**: 5.6 produced
      `error: [NullAway] dereferenced expression qualifier is @Nullable`, so `treatGeneratedAsUnannotated`
      does not disable checking on a package marked by a `@Generated` `package-info`

## 6. Wire Lombok

- [x] 6.1 Add `lombok.config` at the repository root with `config.stopBubbling`,
      `addLombokGeneratedAnnotation`, `addNullAnnotations = jspecify` and
      `experimental.flagUsage = ALLOW`
- [x] 6.2 Constrain `org.projectlombok:lombok` in `dependencies/build.gradle`
- [x] 6.3 Declare it on the `rules` module's `compileOnly` and `annotationProcessor` paths, never on
      `implementation` or `api`
- [x] 6.4 Add no Lombok annotation to any rule class — all eight are stateless by construction and
      Lombok has nothing to generate for them
- [x] 6.5 Confirm Pitest still reports 100/100/100 with Lombok on the annotation processor path, so
      the `addLombokGeneratedAnnotation` / coverage interaction is observed rather than assumed

## 7. Move rationale from comments into specs

- [x] 7.1 Remove the `--release 11` / `ElementType.MODULE` comment from `conventions.gradle` —
      recorded by the *Language level and toolchain* requirement
- [x] 7.2 Remove the `ruleSets` versus `ruleSetFiles` comment — recorded by *The ruleset is a tracked
      build input*
- [x] 7.3 Remove the JUnit 5 line, `@VisibleForTesting` and Mockito comments from
      `dependencies/build.gradle` — recorded by *Test stack*
- [x] 7.4 Remove the `ownRules`, `compileOnly`, POM and matrix comments from `rules/build.gradle`
      along with the code they described
- [x] 7.5 Keep the PMD compile floor comment in `dependencies/build.gradle` — it sits directly above
      the coordinate it constrains and warns against a change that reads as routine
- [x] 7.6 Diff the removed comments against `openspec/specs/` and confirm no decision was recorded
      only in a comment

## 8. Update the documentation that describes the removed machinery

- [x] 8.1 Rewrite `README.md` lines around 491–499: drop "The integration matrix owns that question",
      the `additionalPmdVersions` procedure and the `strictRulesetVersions` guard; state instead that
      `integrationTest` covers the floor and `pmdMain` covers the declared `pmd-dist` version
- [x] 8.2 Fix the `README.md` reference at line 83 to a matrix run "before release"
- [x] 8.3 Remove "Every version the floor admits is exercised by this project's cross-version
      integration matrix" from the `<description>` of
      `rules/src/main/resources/rulesets/java/joke-strict.xml`, keeping the 7.26.0 floor statement
      and its reason
- [x] 8.4 State in the README that adopting a newer PMD is raising the `pmd-dist` coordinate and
      running `check`

## 9. Verify and land

- [x] 9.1 `./gradlew clean check` green from scratch, including `pmdMain`, `pmdTest`,
      `integrationTest` and `pitest`
- [x] 9.2 Confirm the build log shows no `integrationTestPmd*` task and no
      `verifyPomHasNoDependencies`
- [x] 9.3 Confirm the published jar still contains all three rule resources
- [x] 9.4 `openspec validate simplify-build-wiring`
- [ ] 9.5 Commit as `refactor:` with a body naming the removed matrix and POM task, since both are
      deliberate reductions in verification and a reader of the log should not have to infer that
