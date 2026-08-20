package io.github.joke.lint.pmd.rules.java;

import static net.sourceforge.pmd.lang.java.ast.ModifierOwner.Visibility.V_PACKAGE;

import java.util.Set;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.Annotatable;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Reports a package-private method that does not carry {@code @VisibleForTesting}.
 *
 * <p>{@link AvoidPrivateAndProtectedMethods} makes package-private the canonical form for an
 * internal method. This rule makes the widened visibility read as a deliberate test seam rather than
 * a forgotten modifier — which is the only reason the wider visibility was acceptable.
 *
 * <p>The annotation is matched by <strong>simple name</strong>, so any of the four common
 * declarations works: JetBrains, Guava, AndroidX and Elastic all ship one and all are markers.
 * Matching the name also avoids PMD type resolution, which needs an {@code auxclasspath} consumers
 * frequently do not configure and which would make the rule silently pass when misconfigured.
 *
 * <p>Only methods are in scope. Fields, constructors and nested classes are not.
 *
 * <p>Note that a package-private method is stubbable only from a test in the same package
 * <em>and</em> the same classloader — true for a standard Gradle layout, false under JPMS with a
 * sealed module.
 */
public class UseVisibleForTestingAnnotation extends AbstractJavaRulechainRule {

    /**
     * {@code Override} because an overriding method's visibility is fixed by its supertype and is
     * therefore not a seam its author chose.
     *
     * <p>The rest because JUnit 5 test and lifecycle methods are conventionally package-private —
     * PMD's own {@code JUnitJupiterTestShouldBePackagePrivate} requires it — and annotating them
     * {@code @VisibleForTesting} would be nonsense. The set mirrors the defaults of PMD's {@code
     * CommentDefaultAccessModifier}, which treats the same annotations as evidence that a
     * package-private member is intentional.
     */
    private static final Set<String> INTENTIONALLY_PACKAGE_PRIVATE = Set.of(
            "VisibleForTesting",
            "Override",
            "Test",
            "ParameterizedTest",
            "RepeatedTest",
            "TestFactory",
            "TestTemplate",
            "BeforeAll",
            "BeforeEach",
            "AfterAll",
            "AfterEach");

    public UseVisibleForTestingAnnotation() {
        super(ASTMethodDeclaration.class);
    }

    @Override
    public Object visit(final ASTMethodDeclaration node, final Object data) {
        reportIfUnmarkedSeam(node, data);
        return data;
    }

    @VisibleForTesting
    void reportIfUnmarkedSeam(final ASTMethodDeclaration node, final Object data) {
        if (isUnmarkedSeam(node)) {
            asCtx(data).addViolation(node);
        }
    }

    @VisibleForTesting
    boolean isUnmarkedSeam(final ASTMethodDeclaration node) {
        return node.getVisibility() == V_PACKAGE && !hasAnyAnnotation(node, INTENTIONALLY_PACKAGE_PRIVATE);
    }

    @VisibleForTesting
    boolean hasAnyAnnotation(final Annotatable node, final Set<String> simpleNames) {
        return node.getDeclaredAnnotations().any(annotation -> simpleNames.contains(annotation.getSimpleName()));
    }
}
