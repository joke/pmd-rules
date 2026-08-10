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
 * Reports a method declared {@code private}, or declared {@code protected} without a marker stating
 * why. The legal visibilities are {@code public}, package-private, and a {@code protected} whose
 * intent is declared.
 *
 * <p>A {@code private} method cannot be reached from a test, so it can only be exercised through
 * whatever public method calls it — a failure deep in the chain surfaces as a failure of the caller,
 * and it cannot be stubbed when the caller is what you meant to test.
 *
 * <p>{@code protected} is reachable but is usually the wrong seam. It widens the API to every
 * subclass in every consumer's codebase, where package-private widens it only to the package the
 * test lives in. It is therefore a stated exception rather than an available alternative: the rule
 * exists to remove discretion, and what a marker hands back is a choice between two <em>documented</em>
 * intents, not a choice about whether to declare one. The count of undeclared legal forms stays at
 * zero.
 *
 * <p>The marker is necessary because package-private is not always a compliant rewrite. A {@code
 * protected} member on a published abstract base whose subclasses live in other packages is
 * unreachable if narrowed, so demanding package-private there demands a rewrite that does not
 * compile.
 *
 * <p>Permitting a marked {@code protected} puts this rule in opposition to nothing. PMD's own
 * {@code AvoidProtectedMethodInFinalClassNotExtending} and {@code AvoidProtectedFieldInFinalClass}
 * fire on {@code protected} in a {@code final} class and remain correct where they fire: nothing
 * can override in a {@code final} class, so {@code OverrideOnly} is meaningless there, and no
 * out-of-package subclass can exist, so a test seam has no reason to widen past package-private.
 *
 * <p>Anything the markers do not cover is a {@code @SuppressWarnings} away. That is the intended
 * escape and not a failure of the rule — see the class comment on the cross-module check this rule
 * does not attempt, in the change's design notes.
 */
public class AvoidPrivateAndProtectedMethods extends AbstractJavaRulechainRule {

    private static final Set<String> VISIBILITY_NOT_CHOSEN_HERE = Set.of("Override");

    /**
     * Markers that make {@code protected} a stated intent rather than a default: {@code
     * OverrideOnly} says <em>implementors override this</em>, {@code VisibleForTesting} says
     * <em>this was widened for a test</em>.
     *
     * <p>One entry covers both spellings of the nested JetBrains annotation. PMD's simple name is
     * the last identifier and never contains a dot, so {@code @ApiStatus.OverrideOnly} written
     * through its outer type and {@code @OverrideOnly} imported directly are both observed as
     * {@code OverrideOnly}. Test data covers both spellings anyway, because that is a property of
     * the AST rather than of this rule and nothing here would notice if it changed.
     *
     * <p>Hardcoded rather than exposed as a property. Letting each consumer choose which
     * annotations legitimise {@code protected} reintroduces exactly the per-project drift the rule
     * exists to prevent; {@code @SuppressWarnings} remains the escape for anything else.
     *
     * <p>These excuse {@code protected} only. No marker makes a {@code private} method reachable
     * from a test, so none of them excuses one.
     */
    private static final Set<String> PROTECTED_INTENT_MARKERS = Set.of("OverrideOnly", "VisibleForTesting");

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
        return REPORTED.contains(node.getVisibility())
                && !isVisibilityFixedElsewhere(node)
                && !isDeclaredProtectedIntent(node);
    }

    /**
     * A {@code protected} method that carries a marker has stated which of the two meanings it
     * intends, so it is not reported. An unmarked one still is: the modifier can never be a default
     * that slips through, which is what keeps the count of <em>undeclared</em> legal forms at zero.
     *
     * <p>The visibility test is not redundant with {@link #isHidden}'s. It is what stops a marker
     * from excusing a {@code private} method — no annotation makes one reachable from a test.
     */
    @VisibleForTesting
    boolean isDeclaredProtectedIntent(final ASTMethodDeclaration node) {
        return node.getVisibility() == V_PROTECTED && hasAnyAnnotation(node, PROTECTED_INTENT_MARKERS);
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
