package net.pytsing.qingcharms.build;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BuildConditionTest {

    @Test
    public void supportsAllConfiguredOperators() {
        assertTrue(new BuildCondition("火", 3, "lt").matches(2));
        assertTrue(new BuildCondition("火", 3, "gt").matches(4));
        assertTrue(new BuildCondition("火", 3, "eq").matches(3));
        assertTrue(new BuildCondition("火", 3, "gteq").matches(3));
        assertTrue(new BuildCondition("火", 3, "lteq").matches(3));
        assertFalse(new BuildCondition("火", 3, "gt").matches(3));
    }

    @Test
    public void allConditionsMustMatch() {
        BuildRule build = new BuildRule("flame-water", "焱水", Arrays.asList(
                new BuildCondition("火", 3, "gteq"),
                new BuildCondition("水", 1, "eq")),
                Collections.singletonList("暴击率: +30%"));

        assertTrue(build.matches(new TagSnapshot(counts(3, 1)),
                Collections.<String>emptySet()));
        assertFalse(build.matches(new TagSnapshot(counts(3, 2)),
                Collections.<String>emptySet()));
    }

    private static java.util.Map<String, Integer> counts(int fire, int water) {
        java.util.Map<String, Integer> result = new java.util.LinkedHashMap<String, Integer>();
        result.put("火", Integer.valueOf(fire));
        result.put("水", Integer.valueOf(water));
        return result;
    }
}
