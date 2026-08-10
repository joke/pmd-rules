# pmd-rules

Custom [PMD 7](https://pmd.github.io/) rules for Java, implemented as Java rule classes and published
as a ruleset any project can put on its `pmd` configuration.

## Use it

```groovy
dependencies {
    pmd 'io.github.joke.pmd:rules:<version>'
}

pmd {
    ruleSets = ['rulesets/java/joke-strict.xml']
}
```

That is the whole analysis this project runs on itself: PMD's `bestpractices`, `codestyle`, `design`,
`errorprone`, `multithreading` and `performance` categories, composed with the exclusions and
property overrides that make them workable, plus every rule this artifact defines. It needs **PMD
7.26.0 or later** — see [PMD versions](#pmd-versions).

To keep your own composition and take only the rules from here, reference the convenience ruleset and
add PMD's categories yourself:

```groovy
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

If you enable PMD's `codestyle` category alongside these rules, exclude `TooManyStaticImports`. It is
in direct opposition to `UseStaticImports`, which this artifact defines, and no configuration
satisfies both. `rulesets/java/joke-strict.xml` already excludes it.

**`ruleSets` cannot subtract.** Wanting `joke-strict.xml` minus one rule means writing your own
ruleset file that references it and excludes from it, and pointing `ruleSetFiles` at that:

```xml
<rule ref="rulesets/java/joke-strict.xml">
    <exclude name="SomeRuleYouDisagreeWith"/>
</rule>
```

## PMD versions

The rules are compiled against **PMD 7.0.0** and run on any later PMD 7.x. Compiling against the
floor rather than the newest release is deliberate: rules built against an older API run on newer
PMD, whereas rules built against a newer API fail with `NoSuchMethodError` inside your analysis, at a
point where the stack trace blames PMD rather than this artifact. Every release runs its integration
tests against both the floor and the newest supported version.

The shipped resources do not all carry the same floor:

| resource | PMD floor | why |
|---|---|---|
| the rule classes | 7.0.0 | compiled against the floor API |
| `category/java/joke.xml` | 7.0.0 | names only rules this artifact defines |
| `rulesets/java/joke.xml` | 7.0.0 | references only `category/java/joke.xml` |
| `rulesets/java/joke-strict.xml` | **7.26.0** | names PMD stock rules this artifact does not own |

The first three reference none of PMD's stock categories and exclude nothing from them, so they
resolve identically under every PMD 7 version. Composing them with PMD's own categories is yours to
do, which is what keeps your PMD version yours to choose.

`rulesets/java/joke-strict.xml` is the exception, and it is why the composition can ship at all. A
file naming stock rules is pinned to the PMD versions that still spell those rules the same way: a
renamed or removed rule is a hard ruleset-load failure, not a warning. Its floor is 7.26.0 rather
than 7.0.0 for a concrete reason — it excludes `ImplicitFunctionalInterface`, which PMD added after
7.0.0. That floor is the range this project supports and analyses itself with; no build check
asserts it.

**On a PMD older than 7.26.0, use `rulesets/java/joke.xml`** and compose the stock categories
yourself.

**The published POM declares no dependencies.** PMD comes from your `pmd` configuration at whatever
version you picked, and this artifact never overrides it. Every dependency the module declares sits
on a configuration that cannot reach the POM — `compileOnly`, `annotationProcessor` or a test
configuration — which is what keeps it empty.

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

Reports a `static` method that neither writes private static state, belongs to a utility class, nor
is a named constructor.

This is about what the modifier tells a reader, not about mocking — Mockito's inline mock maker and
Spock's `SpyStatic` both mock statics, so a mockability argument would simply be false. Left
unconstrained, `static` means any of five things: helper, factory, constant accessor, entry point,
class-state mutator. It therefore means nothing. Under this rule it means exactly one thing, and
seeing it tells you to go find the field.

```java
static String format(int n) { return "" + n; }   // reported: a helper, not state
static int get() { return count; }               // reported: a read is not a write
static void register(String k, String v) {       // reported: nothing can tell put from size
    REGISTRY.put(k, v);
}

static void bump() { count++; }                  // not reported: writes private static state
static void reset() { count = 0; }               // not reported: so does this
static Example of(int x) { return new Example(); }  // not reported: a named constructor
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

A type carrying an annotation named `UtilityClass` is exempt without the structural test. Lombok's
`@UtilityClass` privatises the constructor and makes every member static during annotation
processing, so the source PMD reads declares instance-looking methods and no constructor at all,
which fails both halves of the structural test on a type that is a utility class once compiled. The
annotation is matched by **simple name**, so this adds no dependency on Lombok — a project without it
simply never matches the name.

A **named constructor** is exempt: a `static` method whose declared return type is its own declaring
type, or an interface that type *directly declares* it implements. A test double over such a factory
could only return what the constructor it wraps already returns, so there is nothing to intercept and
no seam is lost — it is not a helper hiding behind `static`, it is the constructor, named.

```java
public class Cost implements Comparable<Cost> {
    static Cost finite(int amount) { … }         // not reported: returns its own type
    static Comparable<Cost> comparable(int a) { … }  // not reported: a directly declared interface

    static Number total(int a) { … }             // reported: a factory for something else
    static Cost[] all() { … }                    // reported: an array is not the constructor
    static void configure(int a) { … }           // reported: void names no type
}
```

A superclass return type, or an interface inherited transitively rather than declared here, is still
reported. The comparison is on **simple names**, with no type resolution, for the same reason
`UseVisibleForTestingAnnotation` matches names: resolution needs an `auxclasspath` consumers
frequently do not configure, and a misconfigured one would make the rule silently pass. The cost is
that a factory returning a same-named type from another package is exempted too — a missed report
review can catch, which beats silent under-reporting nobody can see.

`main(String[])` and `@BeforeAll`/`@AfterAll` methods are exempt because the platform forces them to
be static and an unfixable violation is worse than a missed one. **A `@MethodSource` provider and a
Spring `static @Bean` are not exempt** — the annotation names the provider in a string, so nothing
can pick it out from any other static method. `@SuppressWarnings("PMD.StaticMethodsModifyStaticState")`
is the expected response there, not a sign that something has gone wrong.

### AvoidPrivateAndProtectedMethods

Reports a method declared `private`, or declared `protected` without a marker stating why. The legal
visibilities are `public`, package-private, and a `protected` whose intent is declared.

A `private` method cannot be reached from a test, so it is only ever exercised through whatever
public method calls it, and it cannot be stubbed when that caller is the thing you meant to test.
No annotation changes that, so **no marker excuses a `private` method**.

`protected` is reachable but is usually the wrong seam: it widens the API to every subclass in every
consumer's codebase, where package-private widens it only to the package the test lives in. So
package-private is the default internal form and `protected` is a stated exception, permitted when
the method carries one of two markers:

| Marker | Meaning |
|---|---|
| `@ApiStatus.OverrideOnly` | an extension point implementors override |
| `@VisibleForTesting` | a visibility widened to create a test seam |

```java
private boolean check() { return true; }      // reported
protected boolean verify() { return true; }   // reported: unmarked

boolean inspect() { return true; }            // not reported: package-private
public boolean isValid() { return true; }     // not reported: public API
private Example() { }                         // not reported: constructors are out of scope

@Override
protected void hook() { }                     // not reported: visibility is not chosen here

@ApiStatus.OverrideOnly
protected void extend() { }                   // not reported: a declared extension point

@VisibleForTesting
protected boolean seam() { return true; }     // not reported: a declared test seam

@VisibleForTesting
private boolean hidden() { return true; }     // reported: no marker makes private reachable
```

The markers are matched by **simple name**, so both `@OverrideOnly` imported directly and
`@ApiStatus.OverrideOnly` qualified through its outer type are recognised. The set is hardcoded
rather than exposed as a rule property: letting each project choose which annotations legitimise
`protected` reintroduces exactly the per-project drift the rule exists to prevent.

**This does not soften the rule.** The count of *undeclared* legal forms is still zero — an unmarked
`protected` is reported exactly as before. What a marker hands back is a choice between two
*documented* intents, not a choice about whether to declare one, and the rule's purpose of removing
discretion survives intact.

The markers are necessary because package-private is not always a compliant rewrite. A `protected`
member on a published abstract base whose subclasses live in other packages and other modules is
unreachable if narrowed, so the rule without them demanded a rewrite that does not compile.

Constructors are out of scope — you do not spy a constructor, and a private one is required by
`StaticMethodsModifyStaticState`'s utility-class exception. `@Override` methods are exempt, because
Java forbids narrowing an inherited visibility.

For anything the markers do not cover, suppress it:

```java
@SuppressWarnings("PMD.AvoidPrivateAndProtectedMethods")
protected void extensionPoint() { }
```

Reporting `protected` only when no subclass actually uses it would be better, and is not possible:
PMD analyses one compilation unit at a time, rules are copied per thread, and every violation must
be attached to a live parsed node. "Does any subclass exist" is a whole-module question. The markers
exist so the question never has to be asked — a declaration is readable from a single file, which
makes the check correct even across a module boundary and into a consumer's subclass. The one case
provable in a single file — `protected` in a `final` class — is already covered by PMD's stock
`AvoidProtectedMethodInFinalClassNotExtending`, which stays correct where it fires: nothing can
override in a `final` class, and no out-of-package subclass can exist there for a seam to reach.

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

### UseStaticImports

Reports a static member — method or field — reached through its declaring type. The import carries
the owner; the code should not repeat it on every use.

> **If you enable `category/java/codestyle.xml`, you must exclude `TooManyStaticImports`.** It caps
> static imports at four by default, and this rule only ever adds them. No configuration satisfies
> both. This is the one rule here that fights a stock rule rather than composing with it.
>
> ```xml
> <rule ref="category/java/codestyle.xml">
>     <exclude name="TooManyStaticImports"/>
> </rule>
> ```

> **Error Prone's `BadImport` needs nothing from you.** It rejects static imports of a handful of
> names this rule would otherwise demand, `copyOf` among them, which would leave you with one report
> asking for the import and one refusing it. `copyOf` is excluded here for that reason — and because
> it is uninformative anyway — so the two tools agree without a suppression on either side.

**The threshold is a floor, not a ceiling.** The rule only ever says "import this". It never reports
an import as unnecessary and never stops you importing a shorter name by hand, so anything below the
floor is simply your call, and a report you disagree with is a `@SuppressWarnings` away.

```java
Mockito.doReturn(true)             // reported: 8 characters
Collections.unmodifiableList(xs)   // reported: self-describing, so not excluded
Collectors.toList()                // reported
AccessType.WRITE                   // reported: fields count too

Math.max(a, b)                     // not reported: 3 characters, under the floor
Optional.empty()                   // not reported: empty what?
Duration.ofSeconds(3)              // not reported: of[A-Z] prefix
Registry.INSTANCE                  // not reported: instance of what?
List.copyOf(xs)                    // not reported: a copy of what, into what?
Example.class.getName()            // not reported: a class literal cannot be imported
```

Short names are left alone because the short static members of the JDK are overwhelmingly the
ambiguous ones — `of`, `get`, `min`, `max`, `now`, `abs`, `sum`.

**Ambiguity is handled structurally, not by the exclusion list.** If a file uses both
`Arrays.toString` and `Objects.toString`, neither is reported and you may import one, the other, or
neither. If it uses only one, the bare name is unambiguous *in that file* and the import line names
the owner. A name already bound in the file by a method, field, parameter or local variable is left
alone too, since the import would be shadowed.

The exclusion list is therefore only about *uninformative* names — factory-shaped members where the
member name says what it produces but not of what:

```
exact:   value  values  valueOf  from  empty  create  builder  parse
         now  between  copyOf  getInstance  newInstance  INSTANCE
prefix:  of…  from…   at a camelCase boundary, so ofSeconds is excluded and offer is not
```

Self-describing members such as `unmodifiableList`, `toList` and `groupingBy` are deliberately
absent, and are reported. The list is fixed — suppress at the site for your own factory methods.

Violations are reported **once per member per file**, not once per occurrence, because one import
fixes them all. A first run over an existing codebase therefore reports roughly the number of import
lines you need to add.

### UseTypeImports

Reports a fully-qualified type name used in code where an import would let the simple name stand.

This does **not** overlap with PMD's stock `UnnecessaryFullyQualifiedName`, which sounds like it
covers the same ground and does not. That rule fires only when the simple name is *already* in
scope, and its fix is to drop the qualifier:

```
simple name already in scope   →  UnnecessaryFullyQualifiedName (stock)   "drop the qualifier"
simple name not yet in scope   →  UseTypeImports (this artifact)          "add an import"
```

The two partition the space, so enabling both never produces two reports for one name.

```java
private java.util.List<String> names;      // reported: add an import
private java.time.Duration timeout;        // reported

private List<String> names;                // not reported: imported
java.lang.String name = "joke";            // not reported: java.lang, the stock rule's job
private com.example.Helper helper;         // not reported: same package
```

If two types in one file want the same simple name, only one can be imported — the rule reports
neither and leaves the choice to you. A qualified nested type such as `java.util.Map.Entry` is
reported without prescribing which of the two valid fixes to apply. Reported once per name per file.

Note that when PMD cannot resolve a qualifier, both import rules stay silent rather than guess, so a
clean run is not by itself proof of compliance.

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

Not every `static` method enters the chain. A **named constructor** — a `static` method returning its
own declaring type or a directly declared interface — is exempt, so it exits at the first step rather
than being reported by it and pushed toward an instance method it was never meant to become:

```java
static Cost finite(int amount) { … }                     // exits here: a named constructor
private static void process(…)                           // continues down the chain
```

`UseStaticImports` and `UseTypeImports` sit outside that chain: they report independently and each
violation is one import line.

## Build

```
./gradlew check
```

Runs the unit tests, the integration tests at the PMD compile floor, Spotless, PMD, Error Prone with
NullAway, and mutation testing at 100% mutation, coverage and test strength.

### This project runs its own rules on itself

`rules/build.gradle` puts `project(':rules')` on the `pmd` configuration and the convention plugin
sets `ruleSets = ['rulesets/java/joke-strict.xml']`, so `pmdMain` and `pmdTest` analyse this
repository with the artifact this repository builds — through the same published resource a consumer
references, resolved off the analysis classpath the same way. There is no ruleset file in this
repository; the composition under test is the shipped one. It is exactly the wiring the
[Use it](#use-it) section describes, and it means a new rule has to leave this repository clean as
part of the change that adds it.

The consequence is that **a broken rule breaks the build that produces it**, and the repair is to
edit the rule that is currently failing. To build past it:

```
./gradlew check -x pmdMain -x pmdTest
```

### How the supported PMD range is covered

Two signals, at the two ends of the range:

| signal | PMD version | comes from | ruleset | code analysed |
|---|---|---|---|---|
| `integrationTest` | 7.0.0, the compile floor | the `dependencies` platform | both shipped rulesets | synthetic fixtures |
| `pmdMain` / `pmdTest` | 7.26.0 | the `pmd-dist` coordinate in the convention plugin | `joke-strict.xml` | this repository's real source |

A rule compiled against API absent from the floor fails `integrationTest`. A rule or ruleset broken
by a newer PMD fails `pmdMain`, against real source rather than fixtures. Versions between the two
ends are not exercised, which is a deliberate trade: a compile-floor break shows at the floor and an
API removal shows at the ceiling, so the interior was carrying no weight.

`pmdMain` therefore says nothing about the 7.0.0 floor on its own — `integrationTest` owns that — and
the `pmd-dist` version must stay at or above the floor `rulesets/java/joke-strict.xml` declares,
since `pmdMain` resolves that ruleset.

**Adopting a newer PMD** is one coordinate: raise `net.sourceforge.pmd:pmd-dist` in
`buildSrc/src/main/groovy/conventions.gradle` and run `./gradlew check`. There is no version list to
extend and no guard to satisfy.

### Rule test data stays in XML

The `pmd-test` descriptors under `rules/src/test/resources` deliberately contain violating code, and
the examples in `category/java/joke.xml` do too. Both are invisible to `pmdMain` and `pmdTest` only
because they are XML. Do not move rule fixtures into `.java` files — the build would flag its own
test data, and excluding the fixture path to fix it would silently exclude whatever moved there
next.

## License

[Apache License 2.0](LICENSE)
