package net.pytsing.qingcharms.build;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public final class BuildConfigValidatorTest {

    @Test
    public void rejectsAnyMalformedEnabledBuildBeforeCommit() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("builds.good.condition", java.util.Collections.singletonList(
                condition("火", 2, "gteq")));
        yaml.set("builds.good.attribute", java.util.Collections.singletonList("攻击力: +1"));
        yaml.set("builds.bad.condition", java.util.Collections.singletonList(
                condition("水", 1, "approximately")));
        yaml.set("builds.bad.attribute", java.util.Collections.singletonList("攻击力: +1"));

        assertNotNull(BuildConfigValidator.validate(
                yaml.getConfigurationSection("builds")));
    }

    @Test
    public void acceptsValidConditionsAndEmptyDefaultConfiguration() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("builds.flame.condition", java.util.Collections.singletonList(
                condition("火", 2, "gteq")));
        yaml.set("builds.flame.attribute", java.util.Collections.singletonList("攻击力: +1"));

        assertNull(BuildConfigValidator.validate(yaml.getConfigurationSection("builds")));
        assertNull(BuildConfigValidator.validate(null));
    }

    private static java.util.Map<String, Object> condition(String tag, int count,
                                                            String operator) {
        java.util.Map<String, Object> condition = new java.util.LinkedHashMap<String, Object>();
        condition.put("tags", tag);
        condition.put("count", Integer.valueOf(count));
        condition.put("operator", operator);
        return condition;
    }
}
