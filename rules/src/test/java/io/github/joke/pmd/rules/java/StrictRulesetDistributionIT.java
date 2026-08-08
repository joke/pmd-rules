package io.github.joke.pmd.rules.java;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.rule.Rule;
import net.sourceforge.pmd.lang.rule.RuleSet;
import org.jetbrains.annotations.VisibleForTesting;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@code rulesets/java/joke-strict.xml}, the one shipped resource that names rules this
 * artifact does not define. Loading it is the whole point: a stock rule PMD renamed or removed is a
 * ruleset-load failure, and this test is where that surfaces — on a build here rather than in a
 * consumer's analysis.
 *
 * <p>Separate from {@link RulesetDistributionIT} so that a failure names which resource broke: the
 * two carry different PMD floors, and one class covering both would not say which one the build was
 * complaining about.
 *
 * <p>Runs at whatever PMD version the test classpath resolves — the 7.0.0 compile floor — rather
 * than only at the floor this ruleset declares. That it loads there at all is incidental and is not
 * a support commitment: the declared floor is 7.26.0 and stands on its own. This class does not skip
 * itself based on the running version, because an assumption that stops holding degrades to a
 * skipped test, which a build log does not distinguish from a test that never ran.
 *
 * <p>Deliberately does not use {@code pmd-test}, for the reason given on {@link
 * RulesetDistributionIT}.
 */
@Tag("integration")
class StrictRulesetDistributionIT {

    private static final String STRICT = "rulesets/java/joke-strict.xml";
    private static final String CATEGORY = "category/java/joke.xml";

    @Test
    void theStrictRulesetResolvesEveryRuleItNames() {
        try (var pmd = PmdAnalysis.create(new PMDConfiguration())) {
            assertThat(ruleNames(pmd.newRuleSetLoader().loadFromResource(STRICT)))
                    .isNotEmpty();
        }
    }

    @Test
    void theStrictRulesetContainsEveryRuleThisArtifactDefines() {
        try (var pmd = PmdAnalysis.create(new PMDConfiguration())) {
            final var strict = pmd.newRuleSetLoader().loadFromResource(STRICT);
            final var category = pmd.newRuleSetLoader().loadFromResource(CATEGORY);

            assertThat(ruleNames(strict)).containsAll(ruleNames(category));
        }
    }

    @Test
    void theStrictRulesetContainsRulesThisArtifactDoesNotDefine() {
        try (var pmd = PmdAnalysis.create(new PMDConfiguration())) {
            final var strict = ruleNames(pmd.newRuleSetLoader().loadFromResource(STRICT));
            final var category = ruleNames(pmd.newRuleSetLoader().loadFromResource(CATEGORY));

            assertThat(strict).hasSizeGreaterThan(category.size());
        }
    }

    @Test
    void theStaticImportCapIsExcluded() {
        try (var pmd = PmdAnalysis.create(new PMDConfiguration())) {
            assertThat(ruleNames(pmd.newRuleSetLoader().loadFromResource(STRICT)))
                    .contains("UseStaticImports")
                    .doesNotContain("TooManyStaticImports");
        }
    }

    @Test
    void theCognitiveComplexityOverrideSurvivesTheComposition() {
        try (var pmd = PmdAnalysis.create(new PMDConfiguration())) {
            assertThat(intProperty(
                            pmd.newRuleSetLoader().loadFromResource(STRICT), "CognitiveComplexity", "reportLevel"))
                    .isEqualTo(5);
        }
    }

    @Test
    void theNPathComplexityOverrideSurvivesTheComposition() {
        try (var pmd = PmdAnalysis.create(new PMDConfiguration())) {
            assertThat(intProperty(pmd.newRuleSetLoader().loadFromResource(STRICT), "NPathComplexity", "reportLevel"))
                    .isEqualTo(5);
        }
    }

    @Test
    void theNestedIfDepthOverrideSurvivesTheComposition() {
        try (var pmd = PmdAnalysis.create(new PMDConfiguration())) {
            assertThat(intProperty(
                            pmd.newRuleSetLoader().loadFromResource(STRICT),
                            "AvoidDeeplyNestedIfStmts",
                            "problemDepth"))
                    .isEqualTo(2);
        }
    }

    @VisibleForTesting
    int intProperty(final RuleSet ruleset, final String ruleName, final String propertyName) {
        final var rule = ruleset.getRules().stream()
                .filter(candidate -> ruleName.equals(candidate.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(ruleName + " is not in " + STRICT));
        final var descriptor = rule.getPropertyDescriptor(propertyName);
        if (descriptor == null) {
            throw new AssertionError(ruleName + " has no property " + propertyName);
        }
        return (Integer) rule.getProperty(descriptor);
    }

    @VisibleForTesting
    List<String> ruleNames(final RuleSet ruleset) {
        return ruleset.getRules().stream().map(Rule::getName).collect(toList());
    }
}
