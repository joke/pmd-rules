package io.github.joke.lint.pmd.rules.java;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import net.sourceforge.pmd.lang.java.ast.ASTAnonymousClassDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTLambdaExpression;
import net.sourceforge.pmd.lang.java.ast.ASTLocalVariableDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.rule.Rule;
import net.sourceforge.pmd.reporting.RuleContext;
import org.jetbrains.annotations.VisibleForTesting;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Covers each rule's {@code visit} in isolation from the decision it delegates to, which the {@code
 * pmd-test} fixtures cannot do: they drive a rule end to end, so they exercise {@code visit} and its
 * predicate together and cannot tell a fault in one from a fault in the other.
 *
 * <p>These are the tests the old shape made impossible. While {@code isRewritableAsVar} was {@code
 * private static} there was no way to stub it, no way to reach {@code visit} without a parsed AST,
 * and therefore no way to kill the mutants on {@code visit} — which is why that method carried a
 * {@code @DoNotMutate} justified by the absence of PMD's parsing helpers. Nothing about PMD changed;
 * the predicate became reachable, and the exemption stopped being needed.
 *
 * <p>Each rule also has a case asserting the returned value. PMD discards a rulechain visitor's
 * return, so nothing observable depends on it — which is exactly why it has to be asserted here
 * rather than exempted from mutation.
 */
@Tag("unit")
class VisitDelegationTest {

    private final ASTLocalVariableDeclaration declaration = mock(ASTLocalVariableDeclaration.class);
    private final ASTMethodDeclaration method = mock(ASTMethodDeclaration.class);
    private final ASTLambdaExpression lambda = mock(ASTLambdaExpression.class);
    private final ASTAnonymousClassDeclaration anonymousClass = mock(ASTAnonymousClassDeclaration.class);
    private final ASTCompilationUnit unit = mock(ASTCompilationUnit.class);

    @Test
    void useVarForLocalVariablesReportsARewritableDeclaration() {
        final var rule = spy(new UseVarForLocalVariables());
        final var context = contextFor(rule);
        doReturn(true).when(rule).isRewritableAsVar(declaration);

        rule.visit(declaration, context);

        verify(context).addViolation(declaration);
    }

    @Test
    void useVarForLocalVariablesReportsNothingOtherwise() {
        final var rule = spy(new UseVarForLocalVariables());
        final var context = contextFor(rule);
        doReturn(false).when(rule).isRewritableAsVar(declaration);

        rule.visit(declaration, context);

        verify(context, never()).addViolation(declaration);
    }

    @Test
    void useVarForLocalVariablesReturnsTheDataItWasGiven() {
        final var rule = spy(new UseVarForLocalVariables());
        final var context = contextFor(rule);
        doReturn(false).when(rule).isRewritableAsVar(declaration);

        assertThat(rule.visit(declaration, context)).isSameAs(context);
    }

    @Test
    void staticMethodsModifyStaticStateReportsAnUnjustifiedStatic() {
        final var rule = spy(new StaticMethodsModifyStaticState());
        final var context = contextFor(rule);
        doReturn(true).when(rule).isUnjustifiedStatic(method);

        rule.visit(method, context);

        verify(context).addViolation(method);
    }

    @Test
    void staticMethodsModifyStaticStateReportsNothingOtherwise() {
        final var rule = spy(new StaticMethodsModifyStaticState());
        final var context = contextFor(rule);
        doReturn(false).when(rule).isUnjustifiedStatic(method);

        rule.visit(method, context);

        verify(context, never()).addViolation(method);
    }

    @Test
    void staticMethodsModifyStaticStateReturnsTheDataItWasGiven() {
        final var rule = spy(new StaticMethodsModifyStaticState());
        final var context = contextFor(rule);
        doReturn(false).when(rule).isUnjustifiedStatic(method);

        assertThat(rule.visit(method, context)).isSameAs(context);
    }

    @Test
    void avoidPrivateAndProtectedMethodsReportsAHiddenMethod() {
        final var rule = spy(new AvoidPrivateAndProtectedMethods());
        final var context = contextFor(rule);
        doReturn(true).when(rule).isHidden(method);

        rule.visit(method, context);

        verify(context).addViolation(method);
    }

    @Test
    void avoidPrivateAndProtectedMethodsReportsNothingOtherwise() {
        final var rule = spy(new AvoidPrivateAndProtectedMethods());
        final var context = contextFor(rule);
        doReturn(false).when(rule).isHidden(method);

        rule.visit(method, context);

        verify(context, never()).addViolation(method);
    }

    @Test
    void avoidPrivateAndProtectedMethodsReturnsTheDataItWasGiven() {
        final var rule = spy(new AvoidPrivateAndProtectedMethods());
        final var context = contextFor(rule);
        doReturn(false).when(rule).isHidden(method);

        assertThat(rule.visit(method, context)).isSameAs(context);
    }

    @Test
    void useVisibleForTestingAnnotationReportsAnUnmarkedSeam() {
        final var rule = spy(new UseVisibleForTestingAnnotation());
        final var context = contextFor(rule);
        doReturn(true).when(rule).isUnmarkedSeam(method);

        rule.visit(method, context);

        verify(context).addViolation(method);
    }

    @Test
    void useVisibleForTestingAnnotationReportsNothingOtherwise() {
        final var rule = spy(new UseVisibleForTestingAnnotation());
        final var context = contextFor(rule);
        doReturn(false).when(rule).isUnmarkedSeam(method);

        rule.visit(method, context);

        verify(context, never()).addViolation(method);
    }

    @Test
    void useVisibleForTestingAnnotationReturnsTheDataItWasGiven() {
        final var rule = spy(new UseVisibleForTestingAnnotation());
        final var context = contextFor(rule);
        doReturn(false).when(rule).isUnmarkedSeam(method);

        assertThat(rule.visit(method, context)).isSameAs(context);
    }

    @Test
    void avoidLambdaBlockBodiesReportsABlockBody() {
        final var rule = spy(new AvoidLambdaBlockBodies());
        final var context = contextFor(rule);
        doReturn(true).when(rule).hasExtractableBlockBody(lambda);

        rule.visit(lambda, context);

        verify(context).addViolation(lambda);
    }

    @Test
    void avoidLambdaBlockBodiesReportsNothingOtherwise() {
        final var rule = spy(new AvoidLambdaBlockBodies());
        final var context = contextFor(rule);
        doReturn(false).when(rule).hasExtractableBlockBody(lambda);

        rule.visit(lambda, context);

        verify(context, never()).addViolation(lambda);
    }

    @Test
    void avoidLambdaBlockBodiesReturnsTheDataItWasGiven() {
        final var rule = spy(new AvoidLambdaBlockBodies());
        final var context = contextFor(rule);
        doReturn(false).when(rule).hasExtractableBlockBody(lambda);

        assertThat(rule.visit(lambda, context)).isSameAs(context);
    }

    @Test
    void avoidAnonymousClassesReportsABodyHoldingLogic() {
        final var rule = spy(new AvoidAnonymousClasses());
        final var context = contextFor(rule);
        doReturn(true).when(rule).hasExtractableBody(anonymousClass);

        rule.visit(anonymousClass, context);

        verify(context).addViolation(anonymousClass);
    }

    @Test
    void avoidAnonymousClassesReportsNothingOtherwise() {
        final var rule = spy(new AvoidAnonymousClasses());
        final var context = contextFor(rule);
        doReturn(false).when(rule).hasExtractableBody(anonymousClass);

        rule.visit(anonymousClass, context);

        verify(context, never()).addViolation(anonymousClass);
    }

    @Test
    void avoidAnonymousClassesReturnsTheDataItWasGiven() {
        final var rule = spy(new AvoidAnonymousClasses());
        final var context = contextFor(rule);
        doReturn(false).when(rule).hasExtractableBody(anonymousClass);

        assertThat(rule.visit(anonymousClass, context)).isSameAs(context);
    }

    @Test
    void useStaticImportsDelegatesToItsCollector() {
        final var rule = spy(new UseStaticImports());
        final var context = contextFor(rule);
        doNothing().when(rule).reportImportableMembers(unit, context);

        rule.visit(unit, context);

        verify(rule).reportImportableMembers(unit, context);
    }

    @Test
    void useStaticImportsReturnsTheDataItWasGiven() {
        final var rule = spy(new UseStaticImports());
        final var context = contextFor(rule);
        doNothing().when(rule).reportImportableMembers(unit, context);

        assertThat(rule.visit(unit, context)).isSameAs(context);
    }

    @Test
    void useTypeImportsDelegatesToItsCollector() {
        final var rule = spy(new UseTypeImports());
        final var context = contextFor(rule);
        doNothing().when(rule).reportImportableTypes(unit, context);

        rule.visit(unit, context);

        verify(rule).reportImportableTypes(unit, context);
    }

    @Test
    void useTypeImportsReturnsTheDataItWasGiven() {
        final var rule = spy(new UseTypeImports());
        final var context = contextFor(rule);
        doNothing().when(rule).reportImportableTypes(unit, context);

        assertThat(rule.visit(unit, context)).isSameAs(context);
    }

    /**
     * PMD's {@code asCtx} asserts that the context it is handed belongs to the rule using it, and
     * {@code RuleContext#getRule} is package-private to PMD — so it cannot be named in a {@code
     * doReturn(...).when(...)} from this package. Answering by method name is the way in.
     */
    @VisibleForTesting
    RuleContext contextFor(final Rule rule) {
        return mock(
                RuleContext.class,
                invocation -> "getRule".equals(invocation.getMethod().getName()) ? rule : null);
    }
}
