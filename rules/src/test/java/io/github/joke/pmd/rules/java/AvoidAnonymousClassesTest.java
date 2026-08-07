package io.github.joke.pmd.rules.java;

import net.sourceforge.pmd.test.SimpleAggregatorTst;
import org.junit.jupiter.api.Tag;

@Tag("unit")
class AvoidAnonymousClassesTest extends SimpleAggregatorTst {

    @Override
    protected void setUp() {
        addRule("category/java/joke.xml", "AvoidAnonymousClasses");
    }
}
