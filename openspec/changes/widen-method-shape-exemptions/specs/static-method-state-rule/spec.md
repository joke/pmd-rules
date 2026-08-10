## MODIFIED Requirements

### Requirement: StaticMethodsModifyStaticState reports static methods that do not write static state
The artifact SHALL provide a rule named `StaticMethodsModifyStaticState` that reports a `static`
method unless it writes a private static field, its declaring type is a utility class, or it is a
named constructor.

The rule SHALL be implemented in Java against the PMD 7 Java AST.

The rule SHALL NOT declare a `minimumLanguageVersion`: `static` exists in every Java version.

The rule's rationale SHALL be stated as an invariant about meaning rather than as a claim about
mockability. Mockito's inline mock maker and Spock's `SpyStatic` both mock static methods, so a
mockability argument is false and would discredit the rule. The invariant is that `static` on a
method means exactly one thing — the method writes class-level state — so that the modifier carries
information for a reader.

#### Scenario: A static helper is reported
- **WHEN** a class with instance methods declares `static String format(int n) { return "" + n; }`
- **THEN** the rule reports a violation on that method

#### Scenario: An instance method is not reported
- **WHEN** a class declares `String format(int n) { return "" + n; }`
- **THEN** the rule reports no violation

### Requirement: Named constructors are exempt
The rule SHALL NOT report a `static` method whose declared return type names its own declaring type,
or names an interface that the declaring type **directly declares** in its `implements` clause. Such
a method is a constructor with a name: a test double over it could only return what the constructor
it wraps already returns, so there is nothing to intercept and no seam is lost.

The comparison SHALL be made on the AST by **simple name**, without type resolution. This matches the
approach taken by `UseVisibleForTestingAnnotation` and for the same reason: resolution requires an
`auxclasspath` that consumers frequently do not configure, and a misconfigured one would make the
rule silently pass rather than fail visibly.

The consequence SHALL be documented: a factory returning a same-named type from a different package
is exempted too. A missed report that review can catch is preferred to silent under-reporting on
every misconfigured `auxclasspath`.

A return type that is a **superclass** of the declaring type, or an interface inherited transitively
rather than directly declared, SHALL NOT be exempt — that shape is a factory for something else,
which is a helper.

#### Scenario: A static factory returning the enclosing type is not reported
- **WHEN** a class with instance methods declares `static Foo of(int x) { return new Foo(x); }`
- **THEN** the rule reports no violation

#### Scenario: A static factory returning a directly implemented interface is not reported
- **WHEN** `class FooImpl implements Foo` declares `static Foo of(int x) { return new FooImpl(x); }`
- **THEN** the rule reports no violation

#### Scenario: A static factory returning an unrelated type is reported
- **WHEN** a class with instance methods declares `static Bar of(int x) { return new Bar(x); }`
- **THEN** the rule reports a violation

#### Scenario: A static factory returning a superclass is reported
- **WHEN** `class Foo extends Base` declares `static Base of(int x) { return new Foo(x); }`
- **THEN** the rule reports a violation, because only the declaring type and its directly declared
  interfaces are exempt

#### Scenario: A void static method is reported
- **WHEN** a class with instance methods declares `static void configure(int x) { }`
- **THEN** the rule reports a violation, because `void` names no type

### Requirement: Utility classes are exempt
The rule SHALL NOT report any static method declared in a utility class. A type is a utility class
when it carries a **utility-class marker annotation**, or when it declares no instance methods and
declares no `public` or `protected` constructor.

The marker check SHALL be evaluated **first**, short-circuiting the structural test. Lombok's
`@UtilityClass` privatises the constructor and makes every member static during annotation
processing, so the source PMD reads declares instance-looking methods and no constructor at all —
which defeats both halves of the structural test on a type that is, once compiled, exactly the shape
the exemption describes.

Marker annotations SHALL be matched by simple name and held as a set, seeded with `UtilityClass`.
Matching by name SHALL NOT introduce a dependency on Lombok: nothing is imported, and a project
without Lombok never matches the name.

A class that declares no constructor at all SHALL be evaluated on its implicit constructor, which
takes the class's own access. A `public class` with no declared constructor is therefore not a
utility class, which agrees with the stock `UseUtilityClass` rule already enabled in `.pmd.xml`.

Instance *fields* SHALL NOT disqualify a utility class; only instance methods do.

Only methods declared directly on the type SHALL be considered. Inherited methods and methods of
nested types SHALL NOT be.

#### Scenario: A Lombok utility class is exempt
- **WHEN** a `public class` carries `@UtilityClass` and declares methods without the `static` keyword
- **THEN** the rule reports no violation, because the marker is checked before the structural test

#### Scenario: The marker is matched without Lombok present
- **WHEN** a type carries an annotation whose simple name is `UtilityClass` from any package
- **THEN** the rule reports no violation on its static methods

#### Scenario: A sealed utility class is exempt
- **WHEN** a class declares only static methods and a `private` constructor
- **THEN** the rule reports no violation

#### Scenario: A public class with an implicit constructor is not a utility class
- **WHEN** a `public class` declares only static methods and no constructor
- **THEN** the rule reports a violation on each static method

#### Scenario: A package-private class with an implicit constructor is a utility class
- **WHEN** a package-private class declares only static methods and no constructor
- **THEN** the rule reports no violation

#### Scenario: One instance method disqualifies the type
- **WHEN** a class with a `private` constructor declares one instance method alongside static methods
- **THEN** the rule reports a violation on each static method

#### Scenario: An interface with only static methods is exempt
- **WHEN** an interface declares only static methods
- **THEN** the rule reports no violation

#### Scenario: An interface with default methods is not exempt
- **WHEN** an interface declares a static method and a default method
- **THEN** the rule reports a violation on the static method

#### Scenario: Nested types are evaluated independently
- **WHEN** a static nested utility class sits inside a class with instance methods
- **THEN** the nested class's static methods are not reported
