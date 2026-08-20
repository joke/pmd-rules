package io.github.joke.lint.codenarc.rules.spock

import org.codenarc.rule.Rule
import org.codenarc.source.SourceString
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class AvoidUnrollAnnotationRuleSpec extends Specification {

    Rule rule = new AvoidUnrollAnnotationRule()

    def 'the rule is named and prioritised'() {
        expect:
        rule.name == 'AvoidUnrollAnnotation'
        rule.priority == 2
    }

    def 'the name and priority are settable, as CodeNarc ruleset configuration requires'() {
        when:
        rule.name = 'Renamed'
        rule.priority = 3

        then:
        rule.name == 'Renamed'
        rule.priority == 3
    }

    def 'an annotated feature method is reported'() {
        expect:
        violationsIn('''
            class ExampleSpec {
                @Unroll
                def 'a feature'() { }
            }
        ''')*.message == ['Spock 2 unrolls by default, so @Unroll adds nothing.']
    }

    def 'a fully qualified annotation is reported'() {
        expect:
        violationsIn('''
            class ExampleSpec {
                @spock.lang.Unroll
                def 'a feature'() { }
            }
        ''').size() == 1
    }

    def 'an annotated specification class is reported'() {
        expect:
        violationsIn('''
            @Unroll
            class ExampleSpec {
                def 'a feature'() { }
            }
        ''').size() == 1
    }

    def 'an unannotated specification is not reported'() {
        expect:
        violationsIn('''
            class ExampleSpec {
                def 'a feature'() { }
            }
        ''').empty
    }

    def 'an unrelated annotation is not reported'() {
        expect:
        violationsIn('''
            class ExampleSpec {
                @Override
                def 'a feature'() { }
            }
        ''').empty
    }

    def 'an annotation whose simple name merely ends in Unroll is not reported'() {
        expect:
        violationsIn('''
            class ExampleSpec {
                @NotUnroll
                def 'a feature'() { }
            }
        ''').empty
    }

    def 'both a class and a method annotation are reported'() {
        expect:
        violationsIn('''
            @Unroll
            class ExampleSpec {
                @Unroll
                def 'a feature'() { }
            }
        ''').size() == 2
    }

    private List violationsIn(String source) {
        rule.applyTo(new SourceString(source))
    }
}
