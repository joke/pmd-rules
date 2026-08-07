package io.github.joke.pmd.rules.java;

import static net.sourceforge.pmd.lang.java.ast.ModifierOwner.Visibility.V_PRIVATE;
import static net.sourceforge.pmd.lang.java.ast.ModifierOwner.Visibility.V_PROTECTED;

import java.util.Set;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.Annotatable;
import net.sourceforge.pmd.lang.java.ast.ModifierOwner.Visibility;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Reports a method declared {@code private} or {@code protected}. The only legal visibilities are
 * {@code public} and package-private.
 *
 * <p>A {@code private} method cannot be reached from a test, so it can only be exercised through
 * whatever public method calls it — a failure deep in the chain surfaces as a failure of the caller,
 * and it cannot be stubbed when the caller is what you meant to test.
 *
 * <p>{@code protected} is reachable but is the wrong seam. It widens the API to every subclass in
 * every consumer's codebase, where package-private widens it only to the package the test lives in.
 * Package-private also collides with nothing: PMD's own {@code
 * AvoidProtectedMethodInFinalClassNotExtending} and {@code AvoidProtectedFieldInFinalClass} fire on
 * {@code protected} alone, so choosing it would have put this rule in opposition to two stock rules
 * and to {@code ClassWithOnlyPrivateConstructorsShouldBeFinal}, which pushes classes toward final.
 *
 * <p>Exactly two legal forms, with no "package-private is sometimes acceptable" middle ground, is
 * the point. The rule exists to remove discretion, and a third permitted form hands it back.
 *
 * <p>A genuine extension point is a {@code @SuppressWarnings} away. That is the intended escape and
 * not a failure of the rule — see the class comment on the cross-module check this rule does not
 * attempt, in the change's design notes.
 */
public class AvoidPrivateAndProtectedMethods extends AbstractJavaRulechainRule {

    private static final Set<String> VISIBILITY_NOT_CHOSEN_HERE = Set.of("Override");

    private static final Set<Visibility> REPORTED = Set.of(V_PRIVATE, V_PROTECTED);

    public AvoidPrivateAndProtectedMethods() {
        super(ASTMethodDeclaration.class);
    }

    @Override
    public Object visit(final ASTMethodDeclaration node, final Object data) {
        reportIfHidden(node, data);
        return data;
    }

    @VisibleForTesting
    void reportIfHidden(final ASTMethodDeclaration node, final Object data) {
        if (isHidden(node)) {
            asCtx(data).addViolation(node);
        }
    }

    /**
     * Constructors are out of scope by construction — this rule visits method declarations only.
     * You do not spy a constructor, and a private one is required by both the utility-class
     * exception in {@link StaticMethodsModifyStaticState} and PMD's {@code UseUtilityClass}.
     */
    @VisibleForTesting
    boolean isHidden(final ASTMethodDeclaration node) {
        return REPORTED.contains(node.getVisibility()) && !isVisibilityFixedElsewhere(node);
    }

    /**
     * A {@code static} method is left to {@link StaticMethodsModifyStaticState}, which reports it
     * first. The rules cascade rather than pile up: dropping {@code static} surfaces the visibility
     * violation on the next run, so one method yields one violation with one obvious fix instead of
     * two reports on one line.
     *
     * <p>An overriding method's visibility is not its author's to choose — Java forbids narrowing
     * it, and a framework superclass declaring a {@code protected} hook requires a {@code protected}
     * override.
     */
    @VisibleForTesting
    boolean isVisibilityFixedElsewhere(final ASTMethodDeclaration node) {
        return node.isStatic() || hasAnyAnnotation(node, VISIBILITY_NOT_CHOSEN_HERE);
    }

    @VisibleForTesting
    boolean hasAnyAnnotation(final Annotatable node, final Set<String> simpleNames) {
        return node.getDeclaredAnnotations().any(annotation -> simpleNames.contains(annotation.getSimpleName()));
    }
}
