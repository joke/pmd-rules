package io.github.joke.lint.pmd.rules.java;

import net.sourceforge.pmd.lang.java.ast.ASTLambdaExpression;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Reports a lambda whose body is a block.
 *
 * <p>A lambda block body is anonymous by construction. It cannot be called from a test, it cannot be
 * stubbed by a caller that wants to be tested in isolation, and its branches are reachable only
 * through whatever pipeline encloses it. Logic belongs in something with a name.
 *
 * <p>The rule does <strong>not</strong> require a method reference, and saying so is the most
 * important part of its documentation — the intuitive reading would make it close to unusable, since
 * a lambda closing over a local variable cannot become a method reference at all. Any non-block body
 * satisfies it, so {@code items.forEach(item -> process(item, context))} is compliant and the fix is
 * mechanical in every case. PMD's stock {@code LambdaCanBeMethodReference} then drives a bare
 * delegation the rest of the way; the two compose rather than collide.
 *
 * <p>An empty block is exempt. {@code () -> { }} has nothing to extract, so a violation on it could
 * not be acted on, and an unfixable violation is worse than a missed one.
 *
 * <p>Logic inside an <em>expression</em> body — a ternary, a long chain — is deliberately out of
 * scope. Block-versus-expression is a syntactic proxy for "logic hiding in an anonymous place": a
 * good proxy and a cheap one to determine, and the start rather than the whole answer.
 */
public class AvoidLambdaBlockBodies extends AbstractJavaRulechainRule {

    public AvoidLambdaBlockBodies() {
        super(ASTLambdaExpression.class);
    }

    @Override
    public Object visit(final ASTLambdaExpression node, final Object data) {
        reportIfBlockBody(node, data);
        return data;
    }

    @VisibleForTesting
    void reportIfBlockBody(final ASTLambdaExpression node, final Object data) {
        if (hasExtractableBlockBody(node)) {
            asCtx(data).addViolation(node);
        }
    }

    /**
     * Tests {@code getBlockBody() != null} rather than {@code isBlockBody()}, which says the same
     * thing: the accessor returns null exactly when the body is an expression. PMD declares it
     * {@code @Nullable}, so with NullAway the {@code isBlockBody()} form needs a null check as well
     * — and that check could never be false, leaving a branch no test could reach and a mutant
     * nothing could kill.
     *
     * <p>A comment is not a statement, so a block holding nothing but a comment is empty here and
     * goes unreported. That is right: there is still nothing to extract.
     */
    @VisibleForTesting
    boolean hasExtractableBlockBody(final ASTLambdaExpression node) {
        final var block = node.getBlockBody();
        return block != null && !block.isEmpty();
    }
}
