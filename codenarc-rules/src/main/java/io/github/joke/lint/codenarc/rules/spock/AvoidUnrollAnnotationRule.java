package io.github.joke.lint.codenarc.rules.spock;

import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codenarc.rule.AbstractAstVisitor;
import org.codenarc.rule.AbstractAstVisitorRule;
import org.codenarc.rule.AstVisitor;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Reports {@code @Unroll} on a specification class or a feature method.
 *
 * <p>Spock 2 unrolls every data-driven feature by default, so the annotation adds nothing to what
 * the runner already does. Left in place it reads as though it were switching a behaviour on, which
 * sends a reader looking for the un-annotated features that supposedly behave differently.
 *
 * <p>{@code PMD.DataClass} is suppressed because CodeNarc's rule contract mandates the shape the
 * rule reports: {@link org.codenarc.rule.AbstractRule} declares {@code name} and {@code priority} as
 * abstract read-write properties, because a ruleset configures a rule by setting them. Four of this
 * class's methods are therefore accessors it cannot decline to have, and every rule class this
 * artifact ever ships will carry the same four. The suppression states that once, per class, rather
 * than being answered by excluding the rule from the ruleset this project publishes to consumers.
 */
@SuppressWarnings("PMD.DataClass")
public class AvoidUnrollAnnotationRule extends AbstractAstVisitorRule {

    private static final String RULE_NAME = "AvoidUnrollAnnotation";
    private static final int DEFAULT_PRIORITY = 2;

    private String name = RULE_NAME;
    private int priority = DEFAULT_PRIORITY;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(final int priority) {
        this.priority = priority;
    }

    @Override
    public Class<? extends AstVisitor> getAstVisitorClass() {
        return AvoidUnrollAnnotationAstVisitor.class;
    }

    /**
     * Neither visit method calls {@code super}. {@code visitClassEx} and {@code visitMethodEx} are
     * empty hooks on {@link AbstractAstVisitor} — the traversal itself is driven by the {@code
     * final} {@code visitClass} and {@code visitMethod} that call them — so a {@code super} call
     * would be a statement no test could ever distinguish the absence of.
     */
    public static class AvoidUnrollAnnotationAstVisitor extends AbstractAstVisitor<AvoidUnrollAnnotationRule> {

        private static final String UNROLL = "Unroll";
        private static final String UNROLL_QUALIFIED = "spock.lang.Unroll";
        private static final String MESSAGE = "Spock 2 unrolls by default, so @Unroll adds nothing.";

        @Override
        public void visitClassEx(final ClassNode node) {
            reportUnroll(node);
        }

        @Override
        public void visitMethodEx(final MethodNode node) {
            reportUnroll(node);
        }

        @VisibleForTesting
        void reportUnroll(final AnnotatedNode node) {
            node.getAnnotations().stream().filter(this::isUnroll).forEach(this::report);
        }

        /**
         * Matches on the name as written, because resolving the annotation would need a compile
         * classpath and CodeNarc analyses source without one. Both the simple and the qualified form
         * are accepted since either compiles.
         */
        @VisibleForTesting
        boolean isUnroll(final AnnotationNode annotation) {
            final var declared = annotation.getClassNode().getName();
            return UNROLL.equals(declared) || UNROLL_QUALIFIED.equals(declared);
        }

        @VisibleForTesting
        void report(final AnnotationNode annotation) {
            addViolation(annotation, MESSAGE);
        }
    }
}
