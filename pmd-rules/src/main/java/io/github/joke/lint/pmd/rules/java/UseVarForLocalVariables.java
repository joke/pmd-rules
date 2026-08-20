package io.github.joke.lint.pmd.rules.java;

import java.util.List;
import net.sourceforge.pmd.lang.java.ast.ASTArrayInitializer;
import net.sourceforge.pmd.lang.java.ast.ASTExpression;
import net.sourceforge.pmd.lang.java.ast.ASTForeachStatement;
import net.sourceforge.pmd.lang.java.ast.ASTLambdaExpression;
import net.sourceforge.pmd.lang.java.ast.ASTLocalVariableDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodReference;
import net.sourceforge.pmd.lang.java.ast.ASTNullLiteral;
import net.sourceforge.pmd.lang.java.ast.ASTVariableDeclarator;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

/**
 * Reports local variable declarations written with an explicit type where {@code var} would compile
 * and preserve the declared type.
 *
 * <p>Targeting {@link ASTLocalVariableDeclaration} rather than the variable ids is what keeps the
 * rule honest about scope: fields sit under an {@code ASTFieldDeclaration}, and method, constructor,
 * catch and lambda parameters have no local variable declaration ancestor at all, so none of them is
 * ever visited.
 */
public class UseVarForLocalVariables extends AbstractJavaRulechainRule {

    private static final int ONE_DECLARATOR = 1;

    /** Initializers that carry no type of their own for {@code var} to infer. */
    private static final List<Class<? extends ASTExpression>> UNINFERABLE_INITIALIZERS = List.of(
            ASTNullLiteral.class, ASTArrayInitializer.class, ASTLambdaExpression.class, ASTMethodReference.class);

    public UseVarForLocalVariables() {
        super(ASTLocalVariableDeclaration.class);
    }

    /**
     * Holds no logic of its own: every decision lives in {@link #isRewritableAsVar}, which a test
     * stubs on a spy to reach both branches here without parsing anything.
     */
    @Override
    public Object visit(final ASTLocalVariableDeclaration node, final Object data) {
        reportIfRewritableAsVar(node, data);
        return data;
    }

    @VisibleForTesting
    void reportIfRewritableAsVar(final ASTLocalVariableDeclaration node, final Object data) {
        if (isRewritableAsVar(node)) {
            asCtx(data).addViolation(node);
        }
    }

    @VisibleForTesting
    boolean isRewritableAsVar(final ASTLocalVariableDeclaration node) {
        if (node.isTypeInferred()) {
            return false;
        }
        // `int a = 1, b = 2;` cannot be rewritten: var forbids multiple declarators.
        final var declarators = node.children(ASTVariableDeclarator.class).toList();
        return declarators.size() == ONE_DECLARATOR && declaresAnInferableType(node, declarators.get(0));
    }

    @VisibleForTesting
    boolean declaresAnInferableType(final ASTLocalVariableDeclaration node, final ASTVariableDeclarator declarator) {
        // The variable of an enhanced for loop has no initializer of its own — the loop assigns it,
        // and `for (var s : names)` is legal — so it is decided before the initializer is examined.
        return node.getParent() instanceof ASTForeachStatement || isInferableInitializer(declarator.getInitializer());
    }

    /**
     * An initializer lets {@code var} infer the declared type unless it is absent, is the
     * {@code null} literal, or is one of the poly expressions that has no type without a target
     * type to give it one.
     */
    @VisibleForTesting
    boolean isInferableInitializer(final @Nullable ASTExpression initializer) {
        return initializer != null && UNINFERABLE_INITIALIZERS.stream().noneMatch(type -> type.isInstance(initializer));
    }
}
