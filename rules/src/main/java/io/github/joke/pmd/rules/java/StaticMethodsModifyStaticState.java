package io.github.joke.pmd.rules.java;

import java.lang.reflect.Modifier;
import java.util.Set;
import net.sourceforge.pmd.lang.java.ast.ASTAssignableExpr.ASTNamedReferenceExpr;
import net.sourceforge.pmd.lang.java.ast.ASTAssignableExpr.AccessType;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import net.sourceforge.pmd.lang.java.ast.Annotatable;
import net.sourceforge.pmd.lang.java.ast.ModifierOwner.Visibility;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.lang.java.symbols.JFieldSymbol;
import net.sourceforge.pmd.lang.java.symbols.JVariableSymbol;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

/**
 * Reports a {@code static} method that neither writes private static state nor belongs to a utility
 * class.
 *
 * <p>The point is what {@code static} tells a reader. Left alone, the modifier means any of five
 * things — helper, factory, constant accessor, entry point, class-state mutator — and therefore
 * means nothing. Under this rule it means one thing: the method writes class-level state, so the
 * reader knows to go find the field.
 *
 * <p>This is deliberately <em>not</em> an argument about mocking. Mockito's inline mock maker and
 * Spock's {@code SpyStatic} both mock static methods, so a mockability rationale would be false and
 * would discredit the rule the first time someone checked it.
 *
 * <p>A read is not a write: a static method that only returns a private static field is an accessor
 * whose encapsulation was already fake, and the field should have been public. A mutating call such
 * as {@code REGISTRY.put(k, v)} is not a write either, because no static analysis can tell {@code
 * put} from {@code size} — which means a {@code private static final} field can never justify a
 * static method, since a final field is only ever assigned in its initializer.
 */
public class StaticMethodsModifyStaticState extends AbstractJavaRulechainRule {

    /**
     * Annotations whose methods JUnit 5 requires to be static outside the {@code PER_CLASS}
     * lifecycle. A violation on one of these would have no compliant rewrite, and an unfixable
     * violation is worse than a missed one.
     *
     * <p>{@code @MethodSource} providers are deliberately absent: the annotation sits on the test
     * method and names its provider in a string, so nothing distinguishes a provider from any other
     * static method. Those suppress at the site.
     */
    private static final Set<String> STATIC_REQUIRED_BY_FRAMEWORK = Set.of("BeforeAll", "AfterAll");

    public StaticMethodsModifyStaticState() {
        super(ASTMethodDeclaration.class);
    }

    @Override
    public Object visit(final ASTMethodDeclaration node, final Object data) {
        reportIfUnjustifiedStatic(node, data);
        return data;
    }

    @VisibleForTesting
    void reportIfUnjustifiedStatic(final ASTMethodDeclaration node, final Object data) {
        if (isUnjustifiedStatic(node)) {
            asCtx(data).addViolation(node);
        }
    }

    @VisibleForTesting
    boolean isUnjustifiedStatic(final ASTMethodDeclaration node) {
        return node.isStatic() && !isJustified(node);
    }

    @VisibleForTesting
    boolean isJustified(final ASTMethodDeclaration node) {
        return isRequiredStaticByPlatform(node) || declaredInUtilityClass(node) || writesPrivateStaticField(node);
    }

    @VisibleForTesting
    boolean isRequiredStaticByPlatform(final ASTMethodDeclaration node) {
        return node.isMainMethod() || hasAnyAnnotation(node, STATIC_REQUIRED_BY_FRAMEWORK);
    }

    @VisibleForTesting
    boolean hasAnyAnnotation(final Annotatable node, final Set<String> simpleNames) {
        return node.getDeclaredAnnotations().any(annotation -> simpleNames.contains(annotation.getSimpleName()));
    }

    @VisibleForTesting
    boolean declaredInUtilityClass(final ASTMethodDeclaration node) {
        final var type = node.getEnclosingType();
        return declaresNoInstanceMethod(type) && declaresNoAccessibleConstructor(type);
    }

    /**
     * Only methods declared directly on the type count. Inherited methods and the methods of nested
     * types are somebody else's declaration, and instance <em>fields</em> never disqualify a type —
     * a field cannot be called, so it is not the thing an instance would exist to offer.
     */
    @VisibleForTesting
    boolean declaresNoInstanceMethod(final ASTTypeDeclaration type) {
        return type.getDeclarations(ASTMethodDeclaration.class).all(ASTMethodDeclaration::isStatic);
    }

    @VisibleForTesting
    boolean declaresNoAccessibleConstructor(final ASTTypeDeclaration type) {
        final var constructors = type.getDeclarations(ASTConstructorDeclaration.class);
        return constructors.isEmpty()
                ? hasInaccessibleImplicitConstructor(type)
                : constructors.none(this::isAccessible);
    }

    /**
     * An undeclared constructor takes the type's own access — so a {@code public class} with no
     * declared constructor is not a utility class, which agrees with PMD's own {@code
     * UseUtilityClass}. Two types do not follow that rule: an interface has no constructor at all,
     * and an enum's implicit constructor is always private.
     */
    @VisibleForTesting
    boolean hasInaccessibleImplicitConstructor(final ASTTypeDeclaration type) {
        return type.isInterface() || type.isEnum() || !type.getVisibility().isAtLeast(Visibility.V_PROTECTED);
    }

    @VisibleForTesting
    boolean isAccessible(final ASTConstructorDeclaration constructor) {
        return constructor.getVisibility().isAtLeast(Visibility.V_PROTECTED);
    }

    /**
     * Descendants of the whole declaration rather than of its body, because a body is absent on a
     * {@code static native} method and a null check there would be a branch no test could ever take
     * the other way. Nothing outside a body can be an assignment target, so the wider search finds
     * exactly the same writes.
     */
    @VisibleForTesting
    boolean writesPrivateStaticField(final ASTMethodDeclaration node) {
        return node.descendants().filterIs(ASTNamedReferenceExpr.class).any(this::isWriteToPrivateStaticField);
    }

    @VisibleForTesting
    boolean isWriteToPrivateStaticField(final ASTNamedReferenceExpr access) {
        return access.getAccessType() == AccessType.WRITE && isPrivateStaticField(access.getReferencedSym());
    }

    /**
     * No check that the field is declared in the same top-level type: Java's access rules already
     * guarantee it, since a private field is unreachable from anywhere else.
     */
    @VisibleForTesting
    boolean isPrivateStaticField(final @Nullable JVariableSymbol symbol) {
        if (!(symbol instanceof JFieldSymbol)) {
            return false;
        }
        final var field = (JFieldSymbol) symbol;
        return field.isStatic() && Modifier.isPrivate(field.getModifiers());
    }
}
