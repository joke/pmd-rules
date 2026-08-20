## MODIFIED Requirements

### Requirement: Import and package declarations are out of scope
The rule SHALL NOT report the fully-qualified name inside an import declaration or a package
declaration. Those are where a qualified name belongs.

#### Scenario: An import declaration is not reported
- **WHEN** a file contains `import java.util.List;`
- **THEN** the rule reports no violation

#### Scenario: A package declaration is not reported
- **WHEN** a file contains `package io.github.joke.lint.pmd.rules.java;`
- **THEN** the rule reports no violation
