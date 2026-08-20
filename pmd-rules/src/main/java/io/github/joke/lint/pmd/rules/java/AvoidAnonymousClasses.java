package io.github.joke.lint.pmd.rules.java;

import net.sourceforge.pmd.lang.java.ast.ASTAnonymousClassDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTEnumConstant;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Reports an anonymous class declaration whose body is not empty.
 *
 * <p>An anonymous class body holds logic with no name: nothing can instantiate it, nothing can stub
 * it. It is the less testable of the two anonymous forms — unlike a lambda it can declare several
 * methods and carry its own fields. The compliant form is a named class, whose methods are reachable
 * through the type.
 *
 * <p>This rule and {@link AvoidLambdaBlockBodies} ship together because each is the other's bypass.
 * A ban on lambda block bodies alone is escaped in one edit by rewriting the lambda as an anonymous
 * class, and every other rule in this artifact waves that through: it is not static, its method is
 * {@code public} and {@code @Override}, and it is not a lambda.
 *
 * <p>Two exemptions, both because the alternative would be a violation nobody can act on:
 *
 * <ul>
 *   <li>An <strong>empty body</strong>, where the body is the mechanism rather than a place logic
 *       hides. {@code new TypeToken<List<String>>() {}} exists precisely to create an anonymous
 *       subclass carrying a generic signature, and no rewrite preserves that.
 *   <li>An <strong>enum constant body</strong>. PMD represents one as an anonymous class — it is the
 *       node {@link ASTEnumConstant#getAnonymousClass()} returns — so without this the rule would
 *       report the strategy enum, which has no anonymous-free rewrite that keeps the enum. This is
 *       the exemption most easily missed, because nothing in the source text says "anonymous class".
 * </ul>
 */
public class AvoidAnonymousClasses extends AbstractJavaRulechainRule {

    public AvoidAnonymousClasses() {
        super(ASTAnonymousClassDeclaration.class);
    }

    @Override
    public Object visit(final ASTAnonymousClassDeclaration node, final Object data) {
        reportIfBodyHoldsLogic(node, data);
        return data;
    }

    @VisibleForTesting
    void reportIfBodyHoldsLogic(final ASTAnonymousClassDeclaration node, final Object data) {
        if (hasExtractableBody(node)) {
            asCtx(data).addViolation(node);
        }
    }

    @VisibleForTesting
    boolean hasExtractableBody(final ASTAnonymousClassDeclaration node) {
        return !isEnumConstantBody(node) && !node.getBody().isEmpty();
    }

    /**
     * Keyed on the parent rather than on the body's shape, so an anonymous class declared inside a
     * method <em>of</em> an enum is still reported — only the constant's own body is exempt.
     */
    @VisibleForTesting
    boolean isEnumConstantBody(final ASTAnonymousClassDeclaration node) {
        return node.getParent() instanceof ASTEnumConstant;
    }
}
