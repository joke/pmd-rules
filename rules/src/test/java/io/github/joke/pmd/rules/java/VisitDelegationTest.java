package io.github.joke.pmd.rules.java;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import net.sourceforge.pmd.lang.java.ast.ASTLocalVariableDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.rule.Rule;
import net.sourceforge.pmd.reporting.RuleContext;
import org.jetbrains.annotations.VisibleForTesting;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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

    @Test
    void useVarForLocalVariablesReportsARewritableDeclaration() {
        final var rule = spy(new UseVarForLocalVariables());
        final var context = contextFor(rule);
        Mockito.doReturn(true).when(rule).isRewritableAsVar(declaration);

        rule.visit(declaration, context);

        verify(context).addViolation(declaration);
    }

    @Test
    void useVarForLocalVariablesReportsNothingOtherwise() {
        final var rule = spy(new UseVarForLocalVariables());
        final var context = contextFor(rule);
        Mockito.doReturn(false).when(rule).isRewritableAsVar(declaration);

        rule.visit(declaration, context);

        verify(context, Mockito.never()).addViolation(declaration);
    }

    @Test
    void useVarForLocalVariablesReturnsTheDataItWasGiven() {
        final var rule = spy(new UseVarForLocalVariables());
        final var context = contextFor(rule);
        Mockito.doReturn(false).when(rule).isRewritableAsVar(declaration);

        assertThat(rule.visit(declaration, context)).isSameAs(context);
    }

    @Test
    void staticMethodsModifyStaticStateReportsAnUnjustifiedStatic() {
        final var rule = spy(new StaticMethodsModifyStaticState());
        final var context = contextFor(rule);
        Mockito.doReturn(true).when(rule).isUnjustifiedStatic(method);

        rule.visit(method, context);

        verify(context).addViolation(method);
    }

    @Test
    void staticMethodsModifyStaticStateReportsNothingOtherwise() {
        final var rule = spy(new StaticMethodsModifyStaticState());
        final var context = contextFor(rule);
        Mockito.doReturn(false).when(rule).isUnjustifiedStatic(method);

        rule.visit(method, context);

        verify(context, Mockito.never()).addViolation(method);
    }

    @Test
    void staticMethodsModifyStaticStateReturnsTheDataItWasGiven() {
        final var rule = spy(new StaticMethodsModifyStaticState());
        final var context = contextFor(rule);
        Mockito.doReturn(false).when(rule).isUnjustifiedStatic(method);

        assertThat(rule.visit(method, context)).isSameAs(context);
    }

    @Test
    void avoidPrivateAndProtectedMethodsReportsAHiddenMethod() {
        final var rule = spy(new AvoidPrivateAndProtectedMethods());
        final var context = contextFor(rule);
        Mockito.doReturn(true).when(rule).isHidden(method);

        rule.visit(method, context);

        verify(context).addViolation(method);
    }

    @Test
    void avoidPrivateAndProtectedMethodsReportsNothingOtherwise() {
        final var rule = spy(new AvoidPrivateAndProtectedMethods());
        final var context = contextFor(rule);
        Mockito.doReturn(false).when(rule).isHidden(method);

        rule.visit(method, context);

        verify(context, Mockito.never()).addViolation(method);
    }

    @Test
    void avoidPrivateAndProtectedMethodsReturnsTheDataItWasGiven() {
        final var rule = spy(new AvoidPrivateAndProtectedMethods());
        final var context = contextFor(rule);
        Mockito.doReturn(false).when(rule).isHidden(method);

        assertThat(rule.visit(method, context)).isSameAs(context);
    }

    @Test
    void useVisibleForTestingAnnotationReportsAnUnmarkedSeam() {
        final var rule = spy(new UseVisibleForTestingAnnotation());
        final var context = contextFor(rule);
        Mockito.doReturn(true).when(rule).isUnmarkedSeam(method);

        rule.visit(method, context);

        verify(context).addViolation(method);
    }

    @Test
    void useVisibleForTestingAnnotationReportsNothingOtherwise() {
        final var rule = spy(new UseVisibleForTestingAnnotation());
        final var context = contextFor(rule);
        Mockito.doReturn(false).when(rule).isUnmarkedSeam(method);

        rule.visit(method, context);

        verify(context, Mockito.never()).addViolation(method);
    }

    @Test
    void useVisibleForTestingAnnotationReturnsTheDataItWasGiven() {
        final var rule = spy(new UseVisibleForTestingAnnotation());
        final var context = contextFor(rule);
        Mockito.doReturn(false).when(rule).isUnmarkedSeam(method);

        assertThat(rule.visit(method, context)).isSameAs(context);
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
