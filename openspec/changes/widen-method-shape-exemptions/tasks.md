## 1. Verify the PMD 7.0.0 floor before writing anything

- [x] 1.1 Confirm every AST accessor the named-constructor check needs exists at PMD **7.0.0**, not merely at the version the build resolves: the method's result-type node, its simple name, the enclosing type's simple name, and the directly declared interface names. `compileOnly` at the floor means a missing method surfaces as `NoSuchMethodError` inside a consumer's analysis, never here
- [x] 1.2 Record in `design.md` the exact accessors chosen, so a later PMD upgrade cannot silently swap them for a newer spelling
- [x] 1.3 Confirm reading the `implements` clause needs no type resolution and no `auxclasspath` — design D1 depends on it

## 2. StaticMethodsModifyStaticState — named constructors

- [x] 2.1 Add the named-constructor predicate: the result type's simple name equals the enclosing type's simple name, or matches a name in its directly declared `implements` clause
- [x] 2.2 Wire it into `isJustified` alongside the existing three justifications
- [x] 2.3 Confirm `void` and a primitive return name no type and are still reported
- [x] 2.4 Confirm a superclass return type and a transitively inherited interface are still reported — only the declaring type and directly declared interfaces are exempt

## 3. StaticMethodsModifyStaticState — Lombok @UtilityClass

- [x] 3.1 Add a `UTILITY_CLASS_MARKERS` set of simple names seeded with `UtilityClass`, following the existing `STATIC_REQUIRED_BY_FRAMEWORK` pattern
- [x] 3.2 Check the marker in `declaredInUtilityClass` **before** the structural test — design D2; under `@UtilityClass` both halves of the structural test fail on a type that is a utility class once compiled
- [x] 3.3 Confirm no Lombok import or dependency is introduced; the published POM still declares none

## 4. AvoidPrivateAndProtectedMethods — marked protected

- [x] 4.1 Add a `PROTECTED_INTENT_MARKERS` set with `OverrideOnly` and `VisibleForTesting`. A separate `ApiStatus.OverrideOnly` entry was planned but is unmatchable — PMD's simple name is the last identifier and asserts it contains no dot — so the one entry covers both spellings; design D3 and the delta spec updated to say so
- [x] 4.2 Extend the report condition so a `protected` method carrying one is not reported, while an unmarked `protected` still is
- [x] 4.3 Confirm a marker on a `private` method does **not** excuse it — no marker makes `private` reachable
- [x] 4.4 Confirm the existing `static` deferral to `StaticMethodsModifyStaticState` and the `@Override` exemption are unchanged

## 5. Test data

- [x] 5.1 `StaticMethodsModifyStaticState.xml`: cases for a factory returning its own type, a directly declared interface, an unrelated type, a superclass, `void`, and a primitive
- [x] 5.2 `StaticMethodsModifyStaticState.xml`: cases for `@UtilityClass` on a `public class` with non-static-looking methods, and for the marker matched from an arbitrary package
- [x] 5.3 `AvoidPrivateAndProtectedMethods.xml`: cases for each marker, for **both** `@OverrideOnly` and `@ApiStatus.OverrideOnly` spellings, for an unmarked `protected`, and for a marked `private`
- [x] 5.4 Add the negative direction for every new branch — an exemption that never fails to fire is indistinguishable from an exemption that always fires
- [x] 5.5 Add a case proving a marked `protected` in a `final` class is not reported by this rule, per the stock-rule scenario in the spec

## 6. Documentation

- [x] 6.1 `category/java/joke.xml`: extend the `StaticMethodsModifyStaticState` `<description>` and `<example>` with both new exemptions, including the simple-name-matching trade-off from design D1
- [x] 6.2 `category/java/joke.xml`: extend the `AvoidPrivateAndProtectedMethods` `<description>` and `<example>` with the two markers, and state why the set is not configurable — design D4
- [x] 6.3 `README.md`: update the `StaticMethodsModifyStaticState` and `AvoidPrivateAndProtectedMethods` sections
- [x] 6.4 `README.md`: update *The rules cascade* — a `static` named constructor now exits the cascade instead of being reported by it
- [x] 6.5 Record in the README that permitting `protected` does **not** soften the rule: the count of undeclared legal forms is still zero

## 7. Verify and land

- [x] 7.1 Run the full build and confirm pitest still reports 100/100/100 — three new branches with several shapes each; the nested `@ApiStatus.OverrideOnly` spelling is the likeliest surviving mutant
- [x] 7.2 Confirm the project still passes its own rules on itself
- [x] 7.3 Confirm the shipped resources reference nothing outside this artifact, and that no rule became stricter — no consumer's build may newly fail
- [x] 7.4 Run the PMD-range coverage check so the new paths are exercised at the 7.0.0 floor, not only at the resolved version
- [x] 7.5 Run `/opsx:sync` to fold the delta specs into the main specs
- [x] 7.6 Commit with `/commit-commands:commit`
- [ ] 7.7 **Release** — cut a version and publish it; `percolate`'s `adopt-pmd-for-method-shape` pins a released version and cannot start against a snapshot
- [ ] 7.8 Tell the `percolate` change which version to pin, and re-measure its baseline: the two static exemptions should erase roughly 53 of its violations
