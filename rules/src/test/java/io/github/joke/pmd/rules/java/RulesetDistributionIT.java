package io.github.joke.pmd.rules.java;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.rule.Rule;
import net.sourceforge.pmd.lang.rule.RuleSet;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the artifact the way a consuming Gradle build does: the ruleset is loaded by classpath
 * reference from the jar, not by file path, and analysis runs through the API subset that is stable
 * across all of PMD 7.
 *
 * <p>Deliberately does not use {@code pmd-test}. Its {@code RuleTst} is itself versioned, so a
 * failure here would not distinguish a PMD incompatibility from a harness incompatibility — which is
 * the only thing this test exists to detect. The build runs this class once per supported PMD
 * version.
 */
@Tag("integration")
class RulesetDistributionIT {

    private static final String RULESET = "rulesets/java/joke.xml";
    private static final String CATEGORY = "category/java/joke.xml";

    @Test
    void theConvenienceRulesetSelectsEveryRuleInTheCategory() {
        try (var pmd = PmdAnalysis.create(new PMDConfiguration())) {
            final var ruleset = pmd.newRuleSetLoader().loadFromResource(RULESET);
            final var category = pmd.newRuleSetLoader().loadFromResource(CATEGORY);

            assertThat(ruleNames(ruleset)).isNotEmpty().containsExactlyInAnyOrderElementsOf(ruleNames(category));
        }
    }

    @Test
    void theRulesetShipsTheSeedRule() {
        try (var pmd = PmdAnalysis.create(new PMDConfiguration())) {
            assertThat(ruleNames(pmd.newRuleSetLoader().loadFromResource(RULESET)))
                    .contains("UseVarForLocalVariables");
        }
    }

    @Test
    void aViolatingSourceFileIsReported(@TempDir final Path dir) throws IOException {
        final var report = analyse(dir, "Bad", "class Bad { void m() { String name = \"joke\"; } }");

        assertThat(report.getViolations()).singleElement().satisfies(violation -> assertThat(
                        violation.getRule().getName())
                .isEqualTo("UseVarForLocalVariables"));
    }

    @Test
    void aCompliantSourceFileIsNotReported(@TempDir final Path dir) throws IOException {
        final var report = analyse(dir, "Good", "class Good { void m() { var name = \"joke\"; } }");

        assertThat(report.getViolations()).isEmpty();
    }

    @Test
    void theViolationCarriesTheConfiguredMessage(@TempDir final Path dir) throws IOException {
        final var report = analyse(dir, "Bad", "class Bad { void m() { String name = \"joke\"; } }");

        assertThat(report.getViolations())
                .extracting(RuleViolation::getDescription)
                .containsExactly("Use 'var' instead of an explicit local variable type.");
    }

    private static Report analyse(final Path dir, final String name, final String source) throws IOException {
        final var file = Files.write(dir.resolve(name + ".java"), List.of(source));
        final var configuration = new PMDConfiguration();
        configuration.addInputPath(file);
        try (var pmd = PmdAnalysis.create(configuration)) {
            pmd.addRuleSet(pmd.newRuleSetLoader().loadFromResource(RULESET));
            return pmd.performAnalysisAndCollectReport();
        }
    }

    private static List<String> ruleNames(final RuleSet ruleset) {
        return ruleset.getRules().stream().map(Rule::getName).collect(Collectors.toList());
    }
}
