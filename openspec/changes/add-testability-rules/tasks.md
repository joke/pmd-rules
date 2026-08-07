## 1. Dependencies

- [x] 1.1 Add a constraint for `org.mockito:mockito-core` to `dependencies/build.gradle`, on the JUnit 5 line already pinned there
- [x] 1.2 Add a constraint for the `@VisibleForTesting` annotation artifact (`org.jetbrains:annotations`) to `dependencies/build.gradle`
- [x] 1.3 Add `testImplementation 'org.mockito:mockito-core'` and `compileOnly 'org.jetbrains:annotations'` to `rules/build.gradle`
- [x] 1.4 Run `./gradlew :rules:verifyPomHasNoDependencies` and confirm the published POM is still empty
- [x] 1.5 Confirm Mockito's inline mock maker is the default in the resolved version, so no `mock-maker-inline` resource file is needed

## 2. StaticMethodsModifyStaticState

- [x] 2.1 Implement the rule class against `ASTMethodDeclaration`, reporting a `static` method unless it writes a private static field or its declaring type is a utility class
- [x] 2.2 Implement the write check: simple assignment, compound assignment, increment and decrement targeting a private static field declared in the same top-level type. A read, a return, and a method call on the field are not writes
- [x] 2.3 Implement the utility-class check: no declared instance methods, and no `public` or `protected` constructor, evaluating the implicit constructor on the class's own access when none is declared. Instance fields do not disqualify; inherited methods and nested-type methods are not considered
- [x] 2.4 Implement the carve-outs: `public static void main(String[])`, and `@BeforeAll` / `@AfterAll` matched by simple name
- [x] 2.5 Write the `pmd-test` XML fixture covering every scenario in `specs/static-method-state-rule/spec.md`, including the interface, enum-free nested-type and implicit-constructor cases
- [x] 2.6 Write the JUnit 5 test class extending `SimpleAggregatorTst`, tagged `unit`
- [x] 2.7 Add the rule to `category/java/joke.xml` with a class, message, description, priority and both examples. The description argues the invariant — `static` means "writes class-level state" — and explicitly does **not** argue mockability
- [x] 2.8 Add the rule to `rulesets/java/joke.xml` — no edit needed: that file references `category/java/joke.xml` whole, so every catalogue entry joins the convenience ruleset automatically. Same for 3.6 and 4.6, and asserted by `RulesetDistributionIT`

## 3. AvoidPrivateAndProtectedMethods

- [x] 3.1 Implement the rule class reporting `private` and `protected` methods
- [x] 3.2 Exclude constructors, `@Override` methods (matched by simple name), and `static` methods, so the rules cascade rather than double-report
- [x] 3.3 Write the `pmd-test` XML fixture covering every scenario in `specs/method-visibility-rule/spec.md`
- [x] 3.4 Write the JUnit 5 test class, tagged `unit`
- [x] 3.5 Add a fixture case asserting `@SuppressWarnings("PMD.AvoidPrivateAndProtectedMethods")` suppresses the violation
- [x] 3.6 Add the rule to `category/java/joke.xml` and `rulesets/java/joke.xml`

## 4. UseVisibleForTestingAnnotation

- [x] 4.1 Implement the rule class reporting package-private **methods** without an annotation whose simple name is `VisibleForTesting`
- [x] 4.2 Exclude `@Override` and the recognised test annotations: `Test`, `ParameterizedTest`, `RepeatedTest`, `TestFactory`, `TestTemplate`, `BeforeAll`, `BeforeEach`, `AfterAll`, `AfterEach`
- [x] 4.3 Confirm fields, constructors and nested classes are out of scope
- [x] 4.4 Write the `pmd-test` XML fixture covering every scenario in `specs/visible-for-testing-rule/spec.md`, including at least two different `VisibleForTesting` packages
- [x] 4.5 Write the JUnit 5 test class, tagged `unit`
- [x] 4.6 Add the rule to `category/java/joke.xml` and `rulesets/java/joke.xml`
- [x] 4.7 Confirm `.pmd.xml` still excludes `CommentDefaultAccessModifier` and gains no new exclusions

## 5. Bring this repository into compliance

Expect `pmdMain` and `pmdTest` to fail from the moment task 2.8 lands. `./gradlew check -x pmdMain
-x pmdTest` builds past it, as documented in the README.

- [x] 5.1 Rewrite `isRewritableAsVar`, `declaresAnInferableType` and `isInferableInitializer` in `UseVarForLocalVariables` as `@VisibleForTesting` package-private instance methods
- [x] 5.2 Rewrite `analyse` and `ruleNames` in `RulesetDistributionIT` the same way
- [x] 5.3 Confirm the three new rule classes were written in the compliant shape from the start and need no rewrite
- [x] 5.4 Update `RulesetDistributionIT`'s expected rule-name assertions to include the three new rules
- [x] 5.5 Run `./gradlew pmdMain pmdTest` and confirm zero violations across the repository

## 6. Retire the @DoNotMutate exemption

- [x] 6.1 Write a Mockito-based unit test for `UseVarForLocalVariables.visit`: mock the `ASTLocalVariableDeclaration`, spy the rule, stub `isRewritableAsVar`, and assert both branches — a violation is added when it returns `true` and not when it returns `false`
- [x] 6.2 Run `./gradlew pitest` with the `@DoNotMutate` still in place and confirm the new test kills the mutants on `visit` — **run as a baseline only.** `@DoNotMutate` suppresses mutant *generation*, so with it in place there is nothing on `visit` to kill and this check cannot say what it was written to say. The evidence is 6.4: after removal, `visit`'s return mutant is killed rather than surviving
- [x] 6.3 Delete the `@DoNotMutate` annotation and the four-line Javadoc paragraph justifying it, and delete the now-unused `com.groupcdg.pitest.annotations` import
- [x] 6.4 Re-run `./gradlew pitest` and confirm 100% mutation, coverage and test strength without the exemption
- [x] 6.5 If the spy cannot kill the mutants, keep the annotation, record the verified reason in `design.md`, and proceed — the rules land either way. **Not needed:** the spy killed them; 258/258 mutations at 100% with the annotation gone

## 7. Documentation

- [x] 7.1 Add a README section for each of the three rules under `## Rules`, matching the existing `UseVarForLocalVariables` format
- [x] 7.2 Document in the `StaticMethodsModifyStaticState` section that `@SuppressWarnings` is the expected response for a `@MethodSource` provider or a Spring `static @Bean`, not a sign of failure
- [x] 7.3 Document in the `UseVisibleForTestingAnnotation` section that the annotation is matched by simple name, and that package-private stubbing requires the test to be in the same package and classloader
- [x] 7.4 Note in the README that the three rules cascade: fixing staticness surfaces the visibility violation, which surfaces the missing annotation

## 8. Verification

- [x] 8.1 Run the integration matrix and confirm all three rules load under both PMD 7.0.0 and 7.26.0
- [x] 8.2 Confirm none of the three rules declares a `minimumLanguageVersion`
- [x] 8.3 Confirm no violation is reported against the `pmd-test` XML fixtures or the `category/java/joke.xml` examples, which contain deliberate violations
- [x] 8.4 Run `./gradlew clean check` to verify everything. NEVER continue if there are violations
- [x] 8.5 Commit the completed change with /commit-commands:commit

## 9. Follow-ups (not part of this change)

- [ ] 9.1 Update `java-coding-conventions/SKILL.md` in the `claude-plugins` repository: it recommends `protected`, which `AvoidPrivateAndProtectedMethods` now reports
- [ ] 9.2 File a PMD issue: `org.jetbrains.annotations.VisibleForTesting` is missing from `CommentDefaultAccessModifier`'s `ignoredAnnotations` defaults
