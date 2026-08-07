## 1. AvoidLambdaBlockBodies

- [x] 1.1 Implement the rule class against `ASTLambdaExpression`, reporting when `isBlockBody()` is true — **implemented as `getBlockBody() != null`**, which is equivalent; PMD declares that accessor `@Nullable`, so the `isBlockBody()` form needs an extra null check that can never be false, leaving an unkillable mutant. Spec updated to match
- [x] 1.2 Exempt an empty block body. Determine emptiness from declared statements, so a block containing only a comment is also exempt
- [x] 1.3 Confirm nothing in the rule inspects an expression body — branching and chaining inside one are out of scope by design
- [x] 1.4 Write the `pmd-test` XML fixture covering every scenario in `specs/lambda-block-body-rule/spec.md`, including the expression-body cases the rule deliberately does not report
- [x] 1.5 Add a fixture case asserting `@SuppressWarnings("PMD.AvoidLambdaBlockBodies")` suppresses the violation
- [x] 1.6 Write the JUnit 5 test class extending `SimpleAggregatorTst`, tagged `unit`
- [x] 1.7 Add the rule to `category/java/joke.xml` with `minimumLanguageVersion="8"`, a class, message, description, priority and both examples. The description states that a method reference is **not** required and names the expression-body limit
- [x] 1.8 Add a `source-type` fixture confirming the rule contributes nothing below Java 8

## 2. AvoidAnonymousClasses

- [x] 2.1 Implement the rule class against `ASTAnonymousClassDeclaration`
- [x] 2.2 Exempt an empty body, so the `TypeToken` / `TypeReference` type-capture idiom is not reported
- [x] 2.3 Exempt an enum constant body — verify against `ASTEnumConstant.isAnonymousClass()` / `getAnonymousClass()` that PMD routes it through the same node, and confirm the exemption keys on the parent rather than on the body's shape
- [x] 2.4 Confirm no `minimumLanguageVersion` is declared
- [x] 2.5 Write the `pmd-test` XML fixture covering every scenario in `specs/anonymous-class-rule/spec.md`, including a strategy enum and an anonymous class declared inside an enum method
- [x] 2.6 Write the JUnit 5 test class, tagged `unit`
- [x] 2.7 Add the rule to `category/java/joke.xml` with a class, message, description, priority and both examples
- [x] 2.8 Add a fixture proving the pair is closed: the lambda-block workaround rewritten as an anonymous class is reported

## 3. Verify the rules against this repository

Both rules are expected to be green here from the first run — this repository has no block lambda and
no anonymous class in Java source. Dogfooding therefore proves nothing about these rules; the
fixtures are the evidence.

- [x] 3.1 Run `./gradlew pmdMain pmdTest` and confirm zero violations. A violation means the rule is wrong, not that the code is
- [x] 3.2 Confirm no violation is reported against the `pmd-test` XML fixtures or the `category/java/joke.xml` examples, which contain deliberate violations and empty-block lambdas
- [x] 3.3 Confirm the two new rule classes were written in a shape compliant with all five published rules and need no rewrite
- [x] 3.4 Update `RulesetDistributionIT`'s expected rule names to include `AvoidLambdaBlockBodies` and `AvoidAnonymousClasses`

## 4. Check the forced-block volume in test code

- [x] 4.1 Survey how often a forced block body appears in realistic test code — `doAnswer(inv -> { record(inv); return null; })` cannot be an expression, because a void call followed by `return null` is not one
- [x] 4.2 Volume was zero — recorded in `design.md`. If the volume is high, record the finding in `design.md` and decide between accepting site suppressions and reconsidering the rule's scope. Do not widen the empty-block exemption to cover it — that would exempt the wrong thing

## 5. Documentation

- [x] 5.1 Add a README section for each rule under `## Rules`, matching the existing format
- [x] 5.2 State prominently in the `AvoidLambdaBlockBodies` section that a method reference is not required, and that an expression lambda delegating to a named method is compliant
- [x] 5.3 Document the five-stage cascade spanning both rulesets: block body → expression lambda → (stock `LambdaCanBeMethodReference`) → method reference → package-private → `@VisibleForTesting`
- [x] 5.4 Document the conflict with `StaticMethodsModifyStaticState` for a block lambda in a static field initializer: `@SuppressWarnings` is available, and making the field non-static is the better fix. Use the static dispatch table as the worked example, since that is the shape that produces these in bulk
- [x] 5.5 Document both exemptions for `AvoidAnonymousClasses`, and explain that the enum constant body is exempt because PMD represents it as an anonymous class
- [x] 5.6 Explain in the README that the two rules ship together because each is the other's bypass

## 6. Verification

- [x] 6.1 Run the integration matrix and confirm both rules load under PMD 7.0.0 and 7.26.0
- [x] 6.2 Confirm `.pmd.xml` gains no exclusions — PMD ships no anonymous-class rule, and `LambdaCanBeMethodReference` composes with the lambda rule rather than contradicting it
- [x] 6.3 Confirm `rulesets/java/joke.xml` needs no edit, since it references `category/java/joke.xml` whole
- [x] 6.4 Run `./gradlew clean check` to verify everything. NEVER continue if there are violations
- [x] 6.5 Commit the completed change with /commit-commands:commit
