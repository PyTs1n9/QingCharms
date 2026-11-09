package net.pytsing.qingcharms.build;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;

/** Validates the complete builds.yml candidate before configuration commit. */
public final class BuildConfigValidator {

    private BuildConfigValidator() {
    }

    public static String validate(ConfigurationSection root) {
        if (root == null) {
            return null;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }
            if (id.trim().isEmpty()) {
                return "build id cannot be empty";
            }
            List<Map<?, ?>> conditions = section.getMapList("condition");
            if (conditions.isEmpty()) {
                return "build '" + id + "' condition cannot be empty";
            }
            for (Map<?, ?> condition : conditions) {
                Object tag = condition.containsKey("tags")
                        ? condition.get("tags") : condition.get("tag");
                Object count = condition.get("count");
                Object operator = condition.get("operator");
                if (!(tag instanceof String) || !(count instanceof Number)
                        || !(operator instanceof String)) {
                    return "build '" + id
                            + "' conditions require tags, numeric count and operator";
                }
                try {
                    new BuildCondition((String) tag,
                            ((Number) count).intValue(), (String) operator);
                } catch (IllegalArgumentException exception) {
                    return "build '" + id + "' has invalid condition: "
                            + exception.getMessage();
                }
            }
            if (section.getStringList("attribute").isEmpty()) {
                return "build '" + id + "' attribute cannot be empty";
            }
        }
        return null;
    }
}
