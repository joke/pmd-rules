package io.github.joke.pmd.rules.java;

import static java.lang.Character.isUpperCase;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sourceforge.pmd.lang.java.ast.ASTClassType;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTFieldAccess;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTTypeExpression;
import net.sourceforge.pmd.lang.java.ast.ASTVariableId;
import net.sourceforge.pmd.lang.java.ast.JavaNode;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

/**
 * Reports a static member — method or field — reached through its declaring type where a static
 * import should carry the owner instead.
 *
 * <p>The rule never resolves whether a member is static, because Java already guarantees it: an
 * instance member cannot be reached through a type, so a type-qualified member is necessarily
 * static. PMD takes the type-ness of the qualifier from the import declaration rather than the class
 * file, so this works without an {@code auxclasspath}. Where PMD cannot disambiguate a qualifier the
 * rule under-reports, which is the safe direction.
 *
 * <p><strong>The length threshold is a floor, not a ceiling.</strong> The rule never reports a static
 * import as unnecessary and never stops anyone importing a shorter name by hand — it only ever says
 * "import this". That is what makes the boundary cheap: {@code BigDecimal.ONE} is not demanded while
 * {@code ZERO} is, and a codebase wanting both simply imports both.
 *
 * <p>Ambiguity between owners is handled structurally rather than by the exclusion list. If two types
 * in one file contribute the same simple name, neither is reported and the developer imports one,
 * the other, or neither. The exclusion list is therefore only about <em>uninformative</em> names —
 * factory-shaped members where the class supplied the type the member name omits.
 *
 * <p>Visits the compilation unit rather than individual nodes because reporting is per file: one
 * import fixes every occurrence, so a violation per occurrence would state a count wildly out of
 * proportion to the work. Per-file state cannot live in the rule instance, which PMD reuses across
 * files within a thread.
 */
public class UseStaticImports extends AbstractJavaRulechainRule {

    /** Below this, the qualifier carries more than it costs — {@code of}, {@code min}, {@code now}. */
    private static final int SHORTEST_IMPORTABLE_NAME = 4;

    /**
     * Names where the member says what it produces but not of what, leaving the class as the only
     * thing that carried the type. Ambiguity is not the concern here — conflict detection covers
     * that — so self-describing members such as {@code unmodifiableList} or {@code toList} are
     * absent on purpose.
     *
     * <p>{@code copyOf} qualifies on those grounds alone — a copy of what, into what, is carried by
     * {@code List}, {@code Set} or {@code Arrays} and never by the member name. Error Prone's
     * {@code BadImport} check independently refuses to let anyone statically import it, so the entry
     * also spares consumers of both tools a pair of reports no single edit satisfies.
     */
    private static final Set<String> UNINFORMATIVE_NAMES = Set.of(
            "value",
            "values",
            "valueOf",
            "from",
            "empty",
            "create",
            "builder",
            "parse",
            "now",
            "between",
            "copyOf",
            "getInstance",
            "newInstance",
            "INSTANCE");

    /** Matched at a camelCase boundary, so {@code ofSeconds} matches and {@code offer} does not. */
    private static final List<String> FACTORY_PREFIXES = List.of("of", "from");

    public UseStaticImports() {
        super(ASTCompilationUnit.class);
    }

    @Override
    public Object visit(final ASTCompilationUnit node, final Object data) {
        reportImportableMembers(node, data);
        return data;
    }

    @VisibleForTesting
    void reportImportableMembers(final ASTCompilationUnit unit, final Object data) {
        final var ownersByMember = new LinkedHashMap<String, Set<String>>();
        final var firstUseByMember = new LinkedHashMap<String, JavaNode>();
        collectQualifiedAccesses(unit, ownersByMember, firstUseByMember);
        reportUnambiguousMembers(unit, ownersByMember, firstUseByMember, data);
    }

    @VisibleForTesting
    void collectQualifiedAccesses(
            final ASTCompilationUnit unit,
            final Map<String, Set<String>> ownersByMember,
            final Map<String, JavaNode> firstUseByMember) {
        for (final var call : unit.descendants(ASTMethodCall.class)) {
            recordAccess(call.getQualifier(), call.getMethodName(), call, ownersByMember, firstUseByMember);
        }
        for (final var access : unit.descendants(ASTFieldAccess.class)) {
            recordAccess(access.getQualifier(), access.getName(), access, ownersByMember, firstUseByMember);
        }
    }

    @VisibleForTesting
    void recordAccess(
            final @Nullable JavaNode qualifier,
            final String member,
            final JavaNode use,
            final Map<String, Set<String>> ownersByMember,
            final Map<String, JavaNode> firstUseByMember) {
        final var owner = qualifyingTypeName(qualifier);
        if (owner != null) {
            ownersByMember.computeIfAbsent(member, unused -> new HashSet<>()).add(owner);
            firstUseByMember.putIfAbsent(member, use);
        }
    }

    /**
     * A class literal never reaches here: {@code Foo.class} parses as its own node rather than a
     * field access, so no explicit exemption exists — one would be a branch no test could take.
     */
    @VisibleForTesting
    @Nullable
    String qualifyingTypeName(final @Nullable JavaNode qualifier) {
        if (!(qualifier instanceof ASTTypeExpression)) {
            return null;
        }
        final var type = ((ASTTypeExpression) qualifier).firstChild(ASTClassType.class);
        return type == null ? null : type.getSimpleName();
    }

    @VisibleForTesting
    void reportUnambiguousMembers(
            final ASTCompilationUnit unit,
            final Map<String, Set<String>> ownersByMember,
            final Map<String, JavaNode> firstUseByMember,
            final Object data) {
        final var declared = declaredNames(unit);
        for (final var use : firstUseByMember.entrySet()) {
            reportIfImportable(use.getKey(), use.getValue(), ownersByMember, declared, data);
        }
    }

    @VisibleForTesting
    void reportIfImportable(
            final String member,
            final JavaNode use,
            final Map<String, Set<String>> ownersByMember,
            final Set<String> declared,
            final Object data) {
        if (isImportable(member, ownersByMember, declared)) {
            asCtx(data).addViolation(use, member);
        }
    }

    @VisibleForTesting
    boolean isImportable(
            final String member, final Map<String, Set<String>> ownersByMember, final Set<String> declared) {
        return isLongEnough(member) && !isUninformative(member) && isUnambiguous(member, ownersByMember, declared);
    }

    @VisibleForTesting
    boolean isLongEnough(final String member) {
        return member.length() >= SHORTEST_IMPORTABLE_NAME;
    }

    /**
     * Java permits at most one single static import of a given simple name, so two owners means the
     * developer must choose — the rule reports neither. A name already bound in the file by a method,
     * field, parameter or local variable would be shadowed by the import, so it is left alone too.
     */
    @VisibleForTesting
    boolean isUnambiguous(
            final String member, final Map<String, Set<String>> ownersByMember, final Set<String> declared) {
        return ownersByMember.getOrDefault(member, Set.of()).size() == 1 && !declared.contains(member);
    }

    @VisibleForTesting
    boolean isUninformative(final String member) {
        return UNINFORMATIVE_NAMES.contains(member) || hasFactoryPrefix(member);
    }

    @VisibleForTesting
    boolean hasFactoryPrefix(final String member) {
        return FACTORY_PREFIXES.stream().anyMatch(prefix -> startsFactoryName(member, prefix));
    }

    @VisibleForTesting
    boolean startsFactoryName(final String member, final String prefix) {
        return member.length() > prefix.length()
                && member.startsWith(prefix)
                && isUpperCase(member.charAt(prefix.length()));
    }

    @VisibleForTesting
    Set<String> declaredNames(final ASTCompilationUnit unit) {
        final var names = new HashSet<String>();
        unit.descendants(ASTMethodDeclaration.class).forEach(method -> names.add(method.getName()));
        unit.descendants(ASTVariableId.class).forEach(variable -> names.add(variable.getName()));
        return names;
    }
}
