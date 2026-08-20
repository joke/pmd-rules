# Changelog

## [1.0.0](https://github.com/joke/pmd-rules/compare/codenarc-rules-v0.0.0...codenarc-rules-v1.0.0) (2026-08-20)


### ⚠ BREAKING CHANGES

* coordinates move from io.github.joke.pmd:rules to io.github.joke.lint:pmd-rules, and rule classes from io.github.joke.pmd.rules.java to io.github.joke.lint.pmd.rules.java. Ruleset resource paths are unchanged, so consumers edit only the dependency coordinate. The retired coordinate receives one final POM-only publication carrying a relocation to the new one.

### Features

* publish PMD and CodeNarc rules as separate artifacts ([1a16999](https://github.com/joke/pmd-rules/commit/1a16999228d7a5eb16f0853caa6dd283a1b0aa27))
