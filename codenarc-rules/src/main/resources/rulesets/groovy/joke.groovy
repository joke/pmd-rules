ruleset {

    description '''
        Every rule this artifact defines, as a single reference for consumers who want all of them.

        This ruleset references only classes this artifact ships. It deliberately names no CodeNarc
        stock rule and no CodeNarc stock ruleset: a stock name resolves against the consumer's
        CodeNarc version, and a name CodeNarc has since renamed or removed is a hard ruleset-load
        failure. Composing this with CodeNarc's own rulesets is the consumer's business.

        Rules are referenced by class rather than by name. CodeNarc resolves bare names through
        codenarc-base-rules.properties, whose filename is hardcoded inside CodeNarc, so a
        third-party jar has no way to register a name for its own rules.

        The rules are Spock-focused: CodeNarc's stock catalogue carries only two Spock rules, and
        neither covers the conventions this artifact exists to enforce.
    '''

    rule(io.github.joke.lint.codenarc.rules.spock.AvoidUnrollAnnotationRule)

}
