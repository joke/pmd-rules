# pmd-rules

Custom [PMD 7](https://pmd.github.io/) rules for Java, implemented as Java rule classes and published
as a ruleset any project can put on its `pmd` configuration.

## Use it

```groovy
dependencies {
    pmd 'io.github.joke.pmd:rules:<version>'
}

pmd {
    ruleSets = [
            'category/java/bestpractices.xml',   // PMD's own categories, if you want them
            'rulesets/java/joke.xml',            // every rule this artifact defines
    ]
}
```

`rulesets/java/joke.xml` is a convenience selection of everything here. To pick rules individually,
reference them out of the catalogue instead:

```groovy
pmd {
    ruleSets = ['category/java/joke.xml/UseVarForLocalVariables']
}
```

## PMD versions

The rules are compiled against **PMD 7.0.0** and run on any later PMD 7.x. Compiling against the
floor rather than the newest release is deliberate: rules built against an older API run on newer
PMD, whereas rules built against a newer API fail with `NoSuchMethodError` inside your analysis, at a
point where the stack trace blames PMD rather than this artifact. Every release runs its integration
tests against both the floor and the newest supported version.

The shipped rulesets reference none of PMD's stock categories and exclude nothing from them, so they
resolve identically under every PMD 7 version. Composing them with PMD's own categories — as above —
is yours to do, which is what keeps your PMD version yours to choose.

**The published POM declares no dependencies.** PMD comes from your `pmd` configuration at whatever
version you picked, and this artifact never overrides it. A build verifies the POM is empty on every
run.

The jar itself is Java 11 bytecode, so the JVM running PMD must be Java 11 or later. This says
nothing about the source you analyse: analysing Java 8 code is a property of your PMD language
version, not of this artifact.

## Rules

### UseVarForLocalVariables

Reports local variable declarations written with an explicit type where `var` would compile and
preserve the declared type. Repeating a type the compiler already knows adds no information and makes
the declaration harder to change.

Reported — single-variable local declarations, including basic and enhanced `for` loop variables and
try-with-resources:

```java
String name = "joke";                       // reported
for (int i = 0; i < 3; i++) { }             // reported
for (String s : names) { }                  // reported
try (InputStream in = open()) { }           // reported
```

Not reported, because `var` cannot express them:

```java
int uninitialized;                          // var requires an initializer
String nothing = null;                      // null has no type to infer
int[] shorthand = {1, 2};                   // var forbids the array-initializer form
Runnable task = () -> { };                  // a lambda needs a target type
Supplier<String> make = String::new;        // so does a method reference
int a = 1, b = 2;                           // var forbids multiple declarators
```

Fields and method, constructor, catch and lambda parameters are out of scope entirely.

The rule declares `minimumLanguageVersion="10"`, so PMD skips it for source analysed at an earlier
language version rather than reporting violations you cannot act on.

## Build

```
./gradlew check
```

Runs the unit tests, the integration tests against every supported PMD version, Spotless, PMD,
Error Prone with NullAway, mutation testing at 100% mutation, coverage and test strength, and the
empty-POM check.

### This project runs its own rules on itself

`rules/build.gradle` puts `project(':rules')` on the `pmd` configuration and `.pmd.xml` references
`rulesets/java/joke.xml`, so `pmdMain` and `pmdTest` analyse this repository with the artifact this
repository builds. It is the same wiring the [Use it](#use-it) section describes for consumers, and
it means a new rule has to leave this repository clean as part of the change that adds it.

The consequence is that **a broken rule breaks the build that produces it**, and the repair is to
edit the rule that is currently failing. To build past it:

```
./gradlew check -x pmdMain -x pmdTest
```

Note also that `pmdMain` runs at Gradle's `toolVersion` — a single, recent PMD — so a green run says
nothing about the 7.0.0 floor. The integration matrix owns that question.

### Rule test data stays in XML

The `pmd-test` descriptors under `rules/src/test/resources` deliberately contain violating code, and
the examples in `category/java/joke.xml` do too. Both are invisible to `pmdMain` and `pmdTest` only
because they are XML. Do not move rule fixtures into `.java` files — the build would flag its own
test data, and excluding the fixture path to fix it would silently exclude whatever moved there
next.

## License

[Apache License 2.0](LICENSE)
