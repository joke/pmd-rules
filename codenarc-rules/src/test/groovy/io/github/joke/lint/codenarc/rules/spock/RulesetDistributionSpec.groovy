package io.github.joke.lint.codenarc.rules.spock

import org.codenarc.ruleregistry.RuleRegistryInitializer
import org.codenarc.ruleset.RuleSetUtil
import spock.lang.Specification
import spock.lang.Tag

/**
 * Loads the shipped rulesets the way a consuming Gradle build does - by classpath reference off the
 * codenarc configuration - so that a missing or misnamed resource fails here rather than in the
 * first consumer's build.
 *
 * RuleSetUtil rather than CodeNarc's own test harness, for the reason the PMD module gives for
 * avoiding pmd-test in its distribution tests: the harness is itself versioned, and using it would
 * confound a distribution failure with a harness failure.
 */
@Tag('integration')
class RulesetDistributionSpec extends Specification {

    /**
     * Bare rule names resolve through the rule registry, which CodeNarcRunner populates on a real
     * run and which nothing populates when RuleSetUtil is driven directly. Without this the strict
     * ruleset fails to load with "No such rule named [...]", which would look like a distribution
     * fault rather than an uninitialised registry.
     */
    def setupSpec() {
        new RuleRegistryInitializer().initializeRuleRegistry()
    }

    def 'the convenience ruleset resolves from the classpath'() {
        expect:
        RuleSetUtil.loadRuleSetFile('rulesets/groovy/joke.groovy').rules*.name == ['AvoidUnrollAnnotation']
    }

    def 'the strict ruleset resolves from the classpath'() {
        expect:
        RuleSetUtil.loadRuleSetFile('rulesets/groovy/joke-strict.groovy').rules.size() == 113
    }

    def 'the strict ruleset carries the stock composition'() {
        expect:
        RuleSetUtil.loadRuleSetFile('rulesets/groovy/joke-strict.groovy').rules*.name
                .containsAll(['UnnecessarySemicolon', 'ElseBlockBraces', 'UnusedVariable'])
    }
}
