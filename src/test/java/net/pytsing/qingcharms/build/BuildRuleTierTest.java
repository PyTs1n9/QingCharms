package net.pytsing.qingcharms.build;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * 档位机制：同一 tags 组合下只有 count 最高的规则生效。
 */
public class BuildRuleTierTest {

    private static BuildRule rule(String id, String group, String[] tags, int count) {
        return new BuildRule(id, null, group, Arrays.asList(tags),
                Collections.<String, Integer>emptyMap(),
                Collections.<String>emptyList(), count,
                Collections.singletonList("&f测试: +1%"));
    }

    private static Set<String> ids(List<BuildRule> rules) {
        Set<String> result = new TreeSet<String>();
        for (BuildRule rule : rules) {
            result.add(rule.getId());
        }
        return result;
    }

    @Test
    public void onlyHighestCountStaysActiveForSameTags() {
        List<BuildRule> active = new ArrayList<BuildRule>();
        active.add(rule("holy-bond", null, new String[] { "圣光" }, 2));
        active.add(rule("holy-bond-pro", null, new String[] { "圣光" }, 5));

        List<BuildRule> result = RuleResolver.highestTierOnly(active);

        assertEquals(1, result.size());
        assertEquals("holy-bond-pro", result.get(0).getId());
    }

    @Test
    public void sameCountRulesKeepBoth() {
        List<BuildRule> active = new ArrayList<BuildRule>();
        active.add(rule("holy-def", null, new String[] { "圣光" }, 2));
        active.add(rule("holy-atk", null, new String[] { "圣光" }, 2));

        assertEquals(new TreeSet<String>(Arrays.asList("holy-atk", "holy-def")),
                ids(RuleResolver.highestTierOnly(active)));
    }

    @Test
    public void differentTagSetsDoNotCompete() {
        List<BuildRule> active = new ArrayList<BuildRule>();
        active.add(rule("holy", null, new String[] { "圣光" }, 2));
        active.add(rule("holy-guard", null, new String[] { "圣光", "守护" }, 4));

        assertEquals(2, RuleResolver.highestTierOnly(active).size());
    }

    @Test
    public void tagOrderDoesNotSplitTheTier() {
        BuildRule left = rule("a", null, new String[] { "圣光", "守护" }, 2);
        BuildRule right = rule("b", null, new String[] { "守护", "圣光" }, 3);

        assertEquals(left.getGroupKey(), right.getGroupKey());
    }

    @Test
    public void explicitGroupOverridesTagSignature() {
        List<BuildRule> active = new ArrayList<BuildRule>();
        active.add(rule("low", "圣光档", new String[] { "圣光" }, 2));
        active.add(rule("high", "圣光档", new String[] { "圣光", "守护" }, 6));

        List<BuildRule> result = RuleResolver.highestTierOnly(active);

        assertEquals(1, result.size());
        assertEquals("high", result.get(0).getId());
    }

    @Test
    public void rulesWithoutTagsSkipTiering() {
        BuildRule rule = rule("req-only", null, new String[0], 1);

        assertNull(rule.getGroupKey());
        List<BuildRule> active = new ArrayList<BuildRule>();
        active.add(rule);
        assertEquals(1, RuleResolver.highestTierOnly(active).size());
    }
}
