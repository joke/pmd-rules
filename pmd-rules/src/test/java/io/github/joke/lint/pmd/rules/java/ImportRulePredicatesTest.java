package io.github.joke.lint.pmd.rules.java;

import static net.sourceforge.pmd.lang.document.Chars.wrap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.Set;
import net.sourceforge.pmd.lang.java.ast.ASTClassType;
import net.sourceforge.pmd.lang.java.ast.ASTTypeExpression;
import org.jetbrains.annotations.VisibleForTesting;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Covers the decisions the {@code pmd-test} fixtures cannot reach.
 *
 * <p>Both import rules guard against inputs that valid Java never produces — a qualifier with no
 * class type, a name with no dot, a prefix exactly as long as the member. Those guards are correct
 * and unreachable through parsed source, so a fixture can neither exercise them nor kill their
 * mutants. Calling the predicates directly is what the package-private seam is for.
 */
@Tag("unit")
class ImportRulePredicatesTest {

    private final UseStaticImports staticImports = new UseStaticImports();
    private final UseTypeImports typeImports = new UseTypeImports();

    @Test
    void aMemberWithNoRecordedOwnerIsNotUnambiguous() {
        assertThat(staticImports.isUnambiguous("absent", Map.of(), Set.of())).isFalse();
    }

    @Test
    void aMemberWithOneOwnerIsUnambiguous() {
        assertThat(staticImports.isUnambiguous("sqrt", Map.of("sqrt", Set.of("Math")), Set.of()))
                .isTrue();
    }

    @Test
    void aQualifierThatIsNotATypeExpressionHasNoTypeName() {
        assertThat(staticImports.qualifyingTypeName(null)).isNull();
    }

    @Test
    void aTypeExpressionWithoutAClassTypeHasNoTypeName() {
        final var qualifier = mock(ASTTypeExpression.class);
        doReturn(null).when(qualifier).firstChild(ASTClassType.class);

        assertThat(staticImports.qualifyingTypeName(qualifier)).isNull();
    }

    @Test
    void aPrefixAsLongAsTheMemberIsNotAFactoryName() {
        assertThat(staticImports.startsFactoryName("of", "of")).isFalse();
    }

    @Test
    void aPrefixFollowedByAnUppercaseLetterIsAFactoryName() {
        assertThat(staticImports.startsFactoryName("ofSeconds", "of")).isTrue();
    }

    @Test
    void aPrefixFollowedByALowercaseLetterIsNotAFactoryName() {
        assertThat(staticImports.startsFactoryName("offer", "of")).isFalse();
    }

    @Test
    void aThreeCharacterMemberIsUnderTheFloor() {
        assertThat(staticImports.isLongEnough("max")).isFalse();
    }

    @Test
    void aFourCharacterMemberClearsTheFloor() {
        assertThat(staticImports.isLongEnough("sqrt")).isTrue();
    }

    @Test
    void aTypeWithNoRecordedNameIsNotUnambiguous() {
        assertThat(typeImports.isUnambiguous("a.B", Map.of())).isFalse();
    }

    @Test
    void anUnqualifiedNameHasAnEmptyQualifier() {
        assertThat(typeImports.qualifierOf("Foo")).isEmpty();
    }

    @Test
    void aQualifiedNameSplitsAtTheLastDot() {
        assertThat(typeImports.qualifierOf("java.util.List")).isEqualTo("java.util");
    }

    @Test
    void aSimpleNameIsTheSegmentAfterTheLastDot() {
        assertThat(typeImports.simpleNameOf("java.util.Map.Entry")).isEqualTo("Entry");
    }

    @Test
    void typeArgumentsAreTrimmedFromAQualifiedName() {
        assertThat(typeImports.qualifiedName(classTypeWithText("java.util.List<String>")))
                .isEqualTo("java.util.List");
    }

    @Test
    void surroundingWhitespaceIsTrimmedFromAQualifiedName() {
        assertThat(typeImports.qualifiedName(classTypeWithText("  java.util.List  ")))
                .isEqualTo("java.util.List");
    }

    @Test
    void aTypeInTheCurrentPackageIsAlreadyInScope() {
        assertThat(typeImports.isAlreadyInScope("com.example.Thing", Set.of(), "com.example"))
                .isTrue();
    }

    @Test
    void aTypeInAnotherPackageIsNotAlreadyInScope() {
        assertThat(typeImports.isAlreadyInScope("com.other.Thing", Set.of(), "com.example"))
                .isFalse();
    }

    @VisibleForTesting
    ASTClassType classTypeWithText(final String text) {
        final var type = mock(ASTClassType.class);
        doReturn(wrap(text)).when(type).getText();
        return type;
    }
}
