package io.github.joke.lint.pmd.rules.java;

import net.sourceforge.pmd.test.SimpleAggregatorTst;
import org.junit.jupiter.api.Tag;

@Tag("unit")
class AvoidPrivateAndProtectedMethodsTest extends SimpleAggregatorTst {

    @Override
    protected void setUp() {
        addRule("category/java/joke.xml", "AvoidPrivateAndProtectedMethods");
    }
}
