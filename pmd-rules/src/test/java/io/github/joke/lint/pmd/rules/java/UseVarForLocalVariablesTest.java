package io.github.joke.lint.pmd.rules.java;

import net.sourceforge.pmd.test.SimpleAggregatorTst;
import org.junit.jupiter.api.Tag;

@Tag("unit")
class UseVarForLocalVariablesTest extends SimpleAggregatorTst {

    @Override
    protected void setUp() {
        addRule("category/java/joke.xml", "UseVarForLocalVariables");
    }
}
