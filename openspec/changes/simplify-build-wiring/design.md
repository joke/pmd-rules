## Context

The build reached a state where several mechanisms existed to defend properties that were already
structurally guaranteed, and one mechanism existed purely to route around a Gradle plugin's
behaviour. Each was individually justified when written. Together they made three build files
harder to read than the thing they build.

The current state before this change:

```
tool supply        pmd { toolVersion = '7.26.0' }
                          │
                          │ leaves the `pmd` configuration's dependency set empty,
                          │ so PmdPlugin.defaultDependencies fires and supplies PMD
                          ▼
self-analysis      configurations.ownRules  ──extendsFrom──▶  configurations.pmd
                     └── project(':rules')
                     (indirection exists ONLY to keep the dependency set empty)

cross-version      integrationTest                    → PMD 7.0.0, joke.xml only
                   integrationTestPmd7_26_0           → PMD 7.26.0, both rulesets
                     + pmdRuntime7_26_0 configuration
                     + strictRulesetVersions ⊆ matrixVersions guard

POM guard          verifyPomHasNoDependencies → XmlSlurper over pom-default.xml

null marking       hand-written package-info.java per package
```

Constraints that do not move: the published artifact declares no dependencies, rule classes compile
against PMD 7.0.0, the three shipped resources are unchanged, and `check` stays at 100/100/100 on
Pitest.

## Goals / Non-Goals

**Goals:**

- Remove machinery whose cost exceeds its signal, without weakening any property a consumer relies
  on.
- Make PMD's version a visible coordinate rather than a plugin extension property.
- Make `@NullMarked` structural rather than a file someone must remember to write.
- Establish `openspec/specs/` as the single home for build rationale.
- Configure Lombok so that adopting it later is a code decision, not a build decision.

**Non-Goals:**

- Changing anything a consumer sees. Same coordinates, same resources, same empty POM, same 7.0.0
  compile floor.
- Adopting Lombok in any rule class. There is no candidate (see Decisions).
- Raising or lowering the PMD compile floor.
- Reintroducing cross-version testing in CI as a workflow matrix. If the two remaining signals prove
  insufficient, that is a separate change with its own evidence.

## Decisions

### `pmd-dist` on the `pmd` configuration, not `toolVersion`

`toolVersion` is Gradle's abstraction over a set of PMD artifacts that varies by Gradle version.
Declaring `net.sourceforge.pmd:pmd-dist:7.26.0` directly names one artifact that transitively pulls
what PMD itself considers a distribution.

The decisive consequence is on the `pmd` configuration's dependency set. `PmdPlugin` supplies the
tool through `defaultDependencies`, which by contract applies only while the configuration has no
dependencies of its own. Under `toolVersion`, therefore, *anything* declared on `pmd` — including
this project's own rules artifact — displaces the tool. That is the sole reason `ownRules` existed.

```
before                                  after
──────                                  ─────
pmd { toolVersion = '7.26.0' }          pmd { dependencies { pmd 'net.sourceforge.pmd:pmd-dist:7.26.0' } }
configurations.ownRules { … }
configurations.pmd.extendsFrom ownRules
dependencies { ownRules project(':rules') }
                                        dependencies { pmd project(':rules') }
```

Once tool supply is explicit, `defaultDependencies` is *meant* not to fire, and the indirection has
nothing left to protect. Twenty lines and a configuration disappear as a consequence, not as a
separate act of tidying.

*Alternative considered:* keep `toolVersion` and keep `ownRules`. Rejected — it preserves a
workaround for a mechanism the build no longer wants active, and hides the PMD version behind a
property that reads like configuration rather than a dependency.

*Alternative considered:* declare `pmd-core` + `pmd-java` + `pmd-ant` by hand instead of `pmd-dist`.
Rejected — that is the hardcoded artifact list `toolVersion` exists to avoid, and it drifts as PMD
reorganises its modules.

### The cross-version matrix is removed; floor and ceiling remain covered

The matrix ran the integration tests against every declared PMD version. What survives covers the
two ends:

| signal | PMD version | source of that version | ruleset | code |
|---|---|---|---|---|
| `integrationTest` | 7.0.0 | `dependencies` platform, via `testRuntimeClasspath` | both shipped rulesets | synthetic fixtures |
| `pmdMain` / `pmdTest` | 7.26.0 | the `pmd-dist` coordinate | `joke-strict.xml` | this repository's real source |

The failure mode the matrix existed to catch is a rule compiled against API absent from the floor.
That is caught at 7.0.0, by `integrationTest`, exactly as before. The complementary failure — a rule
or ruleset that breaks on a *newer* PMD — is caught by `pmdMain` at 7.26.0 against real source,
which is a stronger probe than the synthetic fixtures the matrix ran. Interior versions between
7.0.0 and 7.26.0 were never where the risk lived; a compile-floor break shows at the floor and an
API removal shows at the ceiling.

Adopting a new PMD release is therefore: raise the `pmd-dist` coordinate, run `check`, read the
result. That is a smaller and more honest procedure than declaring a version in two lists and
letting a subset guard police the pair.

*Trade-off accepted:* a regression confined strictly to an interior version now reaches a consumer
before it reaches this build. Judged acceptable — no such regression has occurred, and PMD's own
compatibility policy makes the ends the load-bearing cases.

### `verifyPomHasNoDependencies` is removed; the requirement it guarded is not

The task parsed the generated POM and failed `check` on any declared dependency. The property it
defended is real and stays specified. The guard is dropped because every dependency the `rules`
module declares now sits on a configuration that structurally cannot reach the POM —
`compileOnly`, `annotationProcessor`, `testImplementation`, `testCompileOnly`, `testRuntimeOnly` —
and because a `check`-time XML parse of a build output is a heavy way to state "do not use
`implementation` here".

*Alternative considered:* keep the task. Rejected as an explicit preference; the maintainer does not
want it. The mitigation is that the requirement stays in the spec with a scenario describing what a
leak would look like, so a future reviewer has the property written down even without a task
enforcing it.

### The strict ruleset's floor becomes a promise rather than an enforced constraint

`rulesets/java/joke-strict.xml` declares a 7.26.0 floor in its `<description>` because it names
`ImplicitFunctionalInterface`, which PMD added after 7.0.0. The previous design deliberately
declined to depend on how PMD 7.0.0 treats an `<exclude>` of an absent rule, and enforced the
distinction by excluding `StrictRulesetDistributionIT` from the floor task.

With the matrix gone there is one integration run, at the floor, and it loads the strict ruleset.
Empirically that passes today. This change does not turn that observation into a promise: the
declared floor stays at 7.26.0 in the file and the README, as the version range the project supports
and tests against, and the fact that a lower version happens to load the file is incidental rather
than guaranteed.

*Alternative considered:* lower the declared floor to 7.0.0 on the strength of the passing test.
Rejected — a single green run is not a support commitment, and PMD's handling of an absent
`<exclude>` target is not documented behaviour to build a promise on.

*Alternative considered:* keep excluding the strict IT from the floor run. Rejected — it would
reintroduce version-conditional test filtering, which is the machinery this change is removing, to
protect a claim nothing else depends on.

### `@NullMarked` is generated by an annotation processor

`io.github.joke.jspecify:processor` on the main source set's `annotationProcessor` path emits, per
package:

```java
@javax.annotation.processing.Generated("io.github.joke.jspecify.processor.NullMarkedProcessor")
@org.jspecify.annotations.NullMarked
package io.github.joke.pmd.rules.java;
```

This makes marking structural. A new package is marked because it exists, not because someone
remembered; NullAway's `RequireExplicitNullMarking`, which is configured as an error, changes role
from a check that catches the omission afterwards to a check that can no longer fire.

The hand-written `package-info.java` is deleted rather than kept alongside — two sources for one
annotation is a conflict waiting to be discovered at compile time.

### Lombok is configured but deliberately unused

`lombok.config` at the repository root, `config.stopBubbling = true`, and the dependency on both
`compileOnly` and `annotationProcessor`.

No rule class uses Lombok and none currently can. All eight have zero instance fields: PMD reuses a
rule instance across files within a thread, so per-file state in a rule is a defect, and the classes
are stateless behaviour by construction. Lombok generates fields, accessors, constructors and
equality — every one of which presupposes state.

The wiring is added as a configured baseline so that the first class that genuinely wants it faces a
code decision rather than a build decision. The specs record the absence as intentional, so it is
not later mistaken for an oversight and repaired by annotating a stateless rule.

`lombok.config` settings and why each is set:

| setting | value | reason |
|---|---|---|
| `config.stopBubbling` | `true` | the repository root is the top of the configuration tree; nothing above it should contribute |
| `lombok.addLombokGeneratedAnnotation` | `true` | marks generated members `@lombok.Generated` so Pitest and JaCoCo exclude them, which matters at a 100% mutation threshold |
| `lombok.addNullAnnotations` | `jspecify` | generated members carry JSpecify annotations, consistent with the rest of the source |
| `lombok.experimental.flagUsage` | `ALLOW` | experimental features are permitted rather than warned on |

### Build rationale moves from comments into specs

About ninety lines of explanatory comment leave `conventions.gradle`, `dependencies/build.gradle`
and `rules/build.gradle`. Every non-obvious decision they recorded — the `--release 11` /
`ElementType.MODULE` interaction, `ruleSets` versus `ruleSetFiles` and the UP-TO-DATE trap, the PMD
compile floor, `compileOnly` for the `@VisibleForTesting` annotation, Mockito over Spock — is
already stated in `openspec/specs/`, at greater length and with scenarios.

Duplicating it in comments meant two copies that drift. This change names the specs as the single
home. The build files become a statement of what the build does; the specs remain the statement of
why.

## Risks / Trade-offs

- **NullAway may be silently disabled on the rule classes.** The convention plugin sets
  `treatGeneratedAsUnannotated = true`, and the generated `package-info.java` carries `@Generated`.
  If NullAway reads that annotation as applying to the package's contents, null-safety checking is
  off and every build is green for the wrong reason. → Verify empirically before this change is
  considered done: introduce a deliberate null dereference in a rule class and confirm `compileJava`
  fails. A green build is not evidence; a green build is exactly what a disabled checker produces.
- **`lombok.addLombokGeneratedAnnotation = true` plus `treatGeneratedAsUnannotated = true` means
  Lombok-generated members will be invisible to NullAway once Lombok is used.** → Not an issue while
  usage is zero. Recorded here so the first adopter sees it; revisit the pairing at that point rather
  than pre-emptively.
- **An interior PMD version regression now reaches consumers first.** → Accepted, per the matrix
  decision above. The recovery is cheap: bump `pmd-dist`, run `check`.
- **A dependency leak into the POM is now unguarded.** → The requirement remains specified with a
  scenario. Configuration choice, not a task, is what prevents it; every declaration in
  `rules/build.gradle` is on a non-publishing configuration.
- **`pmd-dist` pulls a larger dependency graph onto the `pmd` configuration than `toolVersion` did.**
  → Analysis-time only, never published, and it is the artifact PMD itself ships for this purpose.
- **Rationale in specs is further from the code that embodies it.** → Accepted deliberately. The
  alternative was two copies, and the copies had already begun to diverge in wording.

## Migration Plan

No consumer migration. Internally the steps are ordered so the build stays green throughout:
declare `pmd-dist` before removing `ownRules` (removing it first would leave `project(':rules')`
displacing the tool), and add the annotation processor before deleting the hand-written
`package-info.java` (deleting it first would fail `RequireExplicitNullMarking`).

Rollback is a revert; nothing here writes state outside the repository.

## Open Questions

- Is NullAway actually still enforcing on the rule classes? The first risk above must be settled by
  experiment, not reasoning. Everything else in this change is verified by the build already being
  green; that one is not, because a disabled checker and a correct codebase are indistinguishable
  from the outside.
- Should `treatGeneratedAsUnannotated` be reconsidered now that the marking itself is generated? It
  was set for Lombok-style output. Its interaction with a generated `package-info.java` is the
  subject of the question above; the answer may make the setting itself worth revisiting in a later
  change.
