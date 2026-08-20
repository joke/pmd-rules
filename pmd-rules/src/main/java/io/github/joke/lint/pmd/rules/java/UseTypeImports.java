package io.github.joke.lint.pmd.rules.java;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.sourceforge.pmd.lang.java.ast.ASTClassType;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTImportDeclaration;
import net.sourceforge.pmd.lang.java.ast.JavaNode;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Reports a fully-qualified type name used in code where an import would let the simple name stand.
 *
 * <p>Splits cleanly with PMD's stock {@code UnnecessaryFullyQualifiedName}, which sounds like it
 * already does this and does not. That rule fires only when the simple name is <em>already</em> in
 * scope — its message is "already in scope" and its fix is "drop the qualifier". Write {@code
 * java.util.List} with no import and it says nothing, because the fix there is to <em>add</em> one.
 *
 * <pre>
 * simple name already in scope   → UnnecessaryFullyQualifiedName (stock)   "drop the qualifier"
 * simple name not yet in scope   → UseTypeImports (this rule)              "add an import"
 * </pre>
 *
 * <p>The two therefore partition the space, and a consumer enabling both never gets two reports for
 * one qualified name. This rule stays silent on anything reachable through {@code java.lang}, the
 * current package, or an existing import.
 *
 * <p>Reports once per qualified name per file, because one import fixes every occurrence. Visits the
 * compilation unit for the same reason {@link UseStaticImports} does: per-file state cannot live in
 * the rule instance, which PMD reuses across files within a thread.
 */
public class UseTypeImports extends AbstractJavaRulechainRule {

    private static final String JAVA_LANG = "java.lang";

    public UseTypeImports() {
        super(ASTCompilationUnit.class);
    }

    @Override
    public Object visit(final ASTCompilationUnit node, final Object data) {
        reportImportableTypes(node, data);
        return data;
    }

    @VisibleForTesting
    void reportImportableTypes(final ASTCompilationUnit unit, final Object data) {
        final var namesBySimpleName = new LinkedHashMap<String, Set<String>>();
        final var firstUseByName = new LinkedHashMap<String, JavaNode>();
        collectQualifiedTypes(unit, namesBySimpleName, firstUseByName);
        reportUnambiguousTypes(unit, namesBySimpleName, firstUseByName, data);
    }

    /**
     * Import and package declarations never appear here: PMD keeps their names as plain strings
     * rather than class types, so no explicit exemption exists — one would be a branch no test could
     * take. A qualifier segment such as the {@code util} of {@code java.util.List} is skipped
     * because its parent is the class type it qualifies.
     */
    @VisibleForTesting
    void collectQualifiedTypes(
            final ASTCompilationUnit unit,
            final Map<String, Set<String>> namesBySimpleName,
            final Map<String, JavaNode> firstUseByName) {
        for (final var type : unit.descendants(ASTClassType.class)) {
            recordQualifiedType(type, namesBySimpleName, firstUseByName);
        }
    }

    @VisibleForTesting
    void recordQualifiedType(
            final ASTClassType type,
            final Map<String, Set<String>> namesBySimpleName,
            final Map<String, JavaNode> firstUseByName) {
        if (isQualifiedUse(type)) {
            final var name = qualifiedName(type);
            namesBySimpleName
                    .computeIfAbsent(type.getSimpleName(), unused -> new HashSet<>())
                    .add(name);
            firstUseByName.putIfAbsent(name, type);
        }
    }

    /**
     * Only the outermost node of a reference is considered — for {@code java.util.Map.Entry} the
     * {@code Map} node is the qualifier of the {@code Entry} node, and reporting both would be one
     * violation too many.
     */
    @VisibleForTesting
    boolean isQualifiedUse(final ASTClassType type) {
        return !(type.getParent() instanceof ASTClassType) && isRootFullyQualified(type);
    }

    /**
     * PMD marks fully-qualified on the innermost segment: {@code java.util.List} is one node with the
     * package absorbed and no qualifier at all, while {@code java.util.Map.Entry} is an {@code Entry}
     * node qualified by a fully-qualified {@code Map}. Walking to the root covers both.
     */
    @VisibleForTesting
    boolean isRootFullyQualified(final ASTClassType type) {
        final var qualifier = type.getQualifier();
        return qualifier == null ? type.isFullyQualified() : isRootFullyQualified(qualifier);
    }

    /**
     * Taken from the source text rather than rebuilt from the qualifier chain, which does not exist
     * for a simple qualified name, and without type resolution, which would need an
     * {@code auxclasspath} consumers often do not configure. Type arguments are trimmed, so
     * {@code java.util.List<String>} yields {@code java.util.List}.
     */
    @VisibleForTesting
    String qualifiedName(final ASTClassType type) {
        return type.getText().toString().replaceAll("<[\\s\\S]*", "").trim();
    }

    @VisibleForTesting
    void reportUnambiguousTypes(
            final ASTCompilationUnit unit,
            final Map<String, Set<String>> namesBySimpleName,
            final Map<String, JavaNode> firstUseByName,
            final Object data) {
        final var imported = importedSimpleNames(unit);
        final var currentPackage = unit.getPackageName();
        for (final var use : firstUseByName.entrySet()) {
            reportIfImportable(use.getKey(), use.getValue(), namesBySimpleName, imported, currentPackage, data);
        }
    }

    @VisibleForTesting
    void reportIfImportable(
            final String name,
            final JavaNode use,
            final Map<String, Set<String>> namesBySimpleName,
            final Set<String> imported,
            final String currentPackage,
            final Object data) {
        if (isImportable(name, namesBySimpleName, imported, currentPackage)) {
            asCtx(data).addViolation(use, name);
        }
    }

    @VisibleForTesting
    boolean isImportable(
            final String name,
            final Map<String, Set<String>> namesBySimpleName,
            final Set<String> imported,
            final String currentPackage) {
        return !isAlreadyInScope(name, imported, currentPackage) && isUnambiguous(name, namesBySimpleName);
    }

    /**
     * An existing import of the same simple name settles it either way: bound to this type, the
     * qualifier is merely redundant and belongs to the stock rule; bound to a different type, only
     * one of them can be imported and the developer chooses.
     */
    @VisibleForTesting
    boolean isAlreadyInScope(final String name, final Set<String> imported, final String currentPackage) {
        final var enclosing = qualifierOf(name);
        return imported.contains(simpleNameOf(name)) || JAVA_LANG.equals(enclosing) || enclosing.equals(currentPackage);
    }

    @VisibleForTesting
    boolean isUnambiguous(final String name, final Map<String, Set<String>> namesBySimpleName) {
        return namesBySimpleName.getOrDefault(simpleNameOf(name), Set.of()).size() == 1;
    }

    @VisibleForTesting
    String simpleNameOf(final String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    /**
     * For a nested type such as {@code java.util.Map.Entry} this is {@code java.util.Map} rather
     * than a package, which is what we want: it matches neither {@code java.lang} nor the current
     * package, so the type is reported and the developer picks which of the two valid imports to add.
     */
    @VisibleForTesting
    String qualifierOf(final String name) {
        return name.substring(0, Math.max(name.lastIndexOf('.'), 0));
    }

    @VisibleForTesting
    Set<String> importedSimpleNames(final ASTCompilationUnit unit) {
        final var names = new HashSet<String>();
        unit.descendants(ASTImportDeclaration.class)
                .filter(declaration -> !declaration.isStatic())
                .forEach(declaration -> names.add(declaration.getImportedSimpleName()));
        return names;
    }
}
