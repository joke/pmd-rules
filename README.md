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

### AvoidLambdaBlockBodies

Reports a lambda whose body is a block. Logic belongs in something with a name — a lambda block body
is anonymous by construction, so no test can call it, no caller can stub it, and its branches are
reachable only through the pipeline that encloses it.

**A method reference is not required.** This is the part to read twice, because the intuitive reading
would make the rule close to unusable: a lambda that closes over a local variable cannot become a
method reference at all. Any non-block body satisfies the rule, so such a lambda stays a lambda and
simply delegates:

```java
items.forEach(item -> {                     // reported
    validate(item, context);
    save(item, context);
});

items.forEach(item -> process(item, context));   // fine — still a lambda, just not a block
items.forEach(this::save);                       // fine — no body at all

map(x -> { return x + 1; });                // reported: converts to an expression
map(x -> { save(x); });                     // reported: so does this
map(x -> x + 1);                            // fine
Runnable task = () -> { };                  // fine: nothing to extract
```

An empty block is exempt — `() -> { }` has nothing to extract, and a violation nobody can act on is
worse than one that is missed. A block containing only a comment is exempt for the same reason: a
comment is not a statement.

Logic inside an *expression* body is deliberately not reported:

```java
map(x -> x > 0 ? positive(x) : negative(x));                  // not reported — two branches
map(x -> x.getA().getB().stream().filter(…).count());         // not reported — a long chain
```

Block-versus-expression is a syntactic proxy for "logic hiding in an anonymous place". It is a good
proxy and a cheap one to determine, and it is the start rather than the whole answer.

The rule declares `minimumLanguageVersion="8"`, since lambdas do not exist before Java 8.

#### The one conflict: a block lambda in a static field initializer

Extracting it produces a method that must be `static` — a static initializer calls it — and
`StaticMethodsModifyStaticState` then reports that method. No form satisfies both rules, so
`@SuppressWarnings` is available:

```java
@SuppressWarnings("PMD.StaticMethodsModifyStaticState")
static String makeGreeting() { … }
```

**Usually there is a better fix: drop `static` from the field.** The shape that produces these in
bulk is the static dispatch table, where every handler is forced static and every one is a violation:

```java
// one suppression per handler, and another on every handler added
private static final Map<String, Handler> HANDLERS = Map.of(
        "create", Example::handleCreate,
        "delete", Example::handleDelete);

// no suppressions at all — the handlers are instance methods now
private final Map<String, Handler> handlers = Map.of(
        "create", this::handleCreate,
        "delete", this::handleDelete);
```

### AvoidAnonymousClasses

Reports an anonymous class whose body is not empty. Its logic has no name: nothing can instantiate
it, nothing can stub it. It is the less testable of the two anonymous forms — unlike a lambda it can
declare several methods and carry its own fields.

```java
return new Runnable() {                     // reported
    @Override
    public void run() { doTheWork(); }
};

return new Worker();                        // fine — a named class
```

**The two rules ship together because each is the other's bypass.** A ban on lambda block bodies
alone is escaped in one edit by rewriting the lambda as an anonymous class, and every other rule here
waves that through: it is not static, its method is `public` and `@Override`, and it is not a lambda.
The escape would land you on the *less* testable construct.

Two exemptions, both because the alternative is a violation nobody can act on:

```java
new TypeToken<List<String>>() { }           // not reported: the empty body IS the mechanism

enum Op {
    PLUS { int apply(int a, int b) { return a + b; } },   // not reported: an enum constant body
    MINUS { int apply(int a, int b) { return a - b; } };
}
```

PMD models an enum constant body as an anonymous class, so without that exemption the rule would
report every strategy enum — which has no anonymous-free rewrite that keeps the enum. An anonymous
class declared inside a method *of* an enum is still reported; only the constant's own body is
exempt.

### The rules cascade

They are designed to fire one at a time rather than all at once, so each violation has a single
obvious fix. The full chain spans this ruleset and PMD's own:

```java
items.forEach(item -> { validate(item); save(item); });  // AvoidLambdaBlockBodies: extract the body
items.forEach(item -> process(item));                    // LambdaCanBeMethodReference (PMD stock)
items.forEach(this::process);                            // ↓ now the method itself
private static void process(…)                           // StaticMethodsModifyStaticState: drop static
private void process(…)                                  // AvoidPrivateAndProtectedMethods: widen
void process(…)                                          // UseVisibleForTestingAnnotation: annotate

@VisibleForTesting
void process(final Item item) { … }                      // clean
```

`LambdaCanBeMethodReference` is PMD's own rule, in `category/java/codestyle.xml` — this artifact
ships nothing that references it, but the two compose if you enable that category.

`AvoidPrivateAndProtectedMethods` deliberately skips `static` methods so that
`StaticMethodsModifyStaticState` reports them first, and `AvoidLambdaBlockBodies` stops at the block
body rather than also demanding a method reference. Expect several build runs when adopting these
rules on an existing codebase — each one surfaces the next step, and each step is mechanical.

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
