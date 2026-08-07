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

### StaticMethodsModifyStaticState

Reports a `static` method that neither writes private static state nor belongs to a utility class.

This is about what the modifier tells a reader, not about mocking — Mockito's inline mock maker and
Spock's `SpyStatic` both mock statics, so a mockability argument would simply be false. Left
unconstrained, `static` means any of five things: helper, factory, constant accessor, entry point,
class-state mutator. It therefore means nothing. Under this rule it means exactly one thing, and
seeing it tells you to go find the field.

```java
static String format(int n) { return "" + n; }   // reported: a helper, not state
static Example of(int x) { return new Example(); }  // reported: a constructor is the compliant form
static int get() { return count; }               // reported: a read is not a write
static void register(String k, String v) {       // reported: nothing can tell put from size
    REGISTRY.put(k, v);
}

static void bump() { count++; }                  // not reported: writes private static state
static void reset() { count = 0; }               // not reported: so does this
public static void main(String[] args) { }       // not reported: the JVM requires static
```

A write is an assignment, a compound assignment, an increment or a decrement to a **private static**
field. It follows that a `private static final` field can never justify a static method, because a
final field is only ever assigned in its initializer — a registry built on one must either become a
utility class or move onto an injected instance.

A utility class is exempt: one declaring no instance methods and no public or protected constructor.
A class declaring no constructor is judged on its implicit one, which takes the class's own access,
so a `public class` with no declared constructor is *not* a utility class — which agrees with PMD's
stock `UseUtilityClass`. An interface has no constructor at all, and an enum's implicit constructor
is always private.

`main(String[])` and `@BeforeAll`/`@AfterAll` methods are exempt because the platform forces them to
be static and an unfixable violation is worse than a missed one. **A `@MethodSource` provider and a
Spring `static @Bean` are not exempt** — the annotation names the provider in a string, so nothing
can pick it out from any other static method. `@SuppressWarnings("PMD.StaticMethodsModifyStaticState")`
is the expected response there, not a sign that something has gone wrong.

### AvoidPrivateAndProtectedMethods

Reports a method declared `private` or `protected`. The only legal visibilities are `public` and
package-private.

A `private` method cannot be reached from a test, so it is only ever exercised through whatever
public method calls it, and it cannot be stubbed when that caller is the thing you meant to test.
`protected` is reachable but is the wrong seam: it widens the API to every subclass in every
consumer's codebase, where package-private widens it only to the package the test lives in.
Package-private also collides with nothing, while `protected` opposes PMD's own
`AvoidProtectedMethodInFinalClassNotExtending` and `AvoidProtectedFieldInFinalClass`.

```java
private boolean check() { return true; }      // reported
protected boolean verify() { return true; }   // reported

boolean inspect() { return true; }            // not reported: package-private
public boolean isValid() { return true; }     // not reported: public API
private Example() { }                         // not reported: constructors are out of scope

@Override
protected void hook() { }                     // not reported: visibility is not chosen here
```

Constructors are out of scope — you do not spy a constructor, and a private one is required by
`StaticMethodsModifyStaticState`'s utility-class exception. `@Override` methods are exempt, because
Java forbids narrowing an inherited visibility.

For a genuine extension point, suppress it:

```java
@SuppressWarnings("PMD.AvoidPrivateAndProtectedMethods")
protected void extensionPoint() { }
```

Reporting `protected` only when no subclass actually uses it would be better, and is not possible:
PMD analyses one compilation unit at a time, rules are copied per thread, and every violation must
be attached to a live parsed node. "Does any subclass exist" is a whole-module question. The one
case provable in a single file — `protected` in a `final` class — is already covered by PMD's stock
`AvoidProtectedMethodInFinalClassNotExtending`.

### UseVisibleForTestingAnnotation

Reports a package-private method that does not carry `@VisibleForTesting`.

`AvoidPrivateAndProtectedMethods` makes package-private the canonical form for an internal method.
This rule makes the widened visibility read as a deliberate test seam rather than a forgotten
modifier — which is the only reason the wider visibility was acceptable in the first place.

```java
boolean check() { return true; }              // reported: an unmarked seam

@VisibleForTesting
boolean verify() { return true; }             // not reported

public boolean isValid() { return true; }     // not reported: not package-private

@Test
void reportsTheViolation() { }                // not reported: a JUnit test method
```

**The annotation is matched by simple name**, so any declaration works — JetBrains, Guava, AndroidX
and Elastic all ship a `VisibleForTesting` and all of them are markers. You do not need the one this
project happens to use, and there is no fully-qualified-name list to keep in sync. Matching the name
also avoids PMD's type resolution, which needs an `auxclasspath` that consumers frequently do not
configure and which would make the rule silently pass when misconfigured.

Only methods are in scope; fields, constructors and nested classes are not. `@Override` methods are
exempt, and so are JUnit 5 test and lifecycle methods, which are conventionally package-private and
for which the annotation would be nonsense.

Note that a package-private method is stubbable only from a test in the **same package and the same
classloader**. That holds for a standard Gradle layout and fails under JPMS with a sealed module.

### The three rules cascade

They are designed to fire one at a time rather than all at once, so each violation has a single
obvious fix:

```java
private static boolean check() { … }   // StaticMethodsModifyStaticState: drop static
private boolean check() { … }          // AvoidPrivateAndProtectedMethods: widen to package-private
boolean check() { … }                  // UseVisibleForTestingAnnotation: add @VisibleForTesting

@VisibleForTesting
boolean check() { … }                  // clean
```

`AvoidPrivateAndProtectedMethods` deliberately skips `static` methods so that
`StaticMethodsModifyStaticState` reports them first. Expect to run the build three times when
adopting these rules on an existing codebase.

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
