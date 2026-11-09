package net.pytsing.qingcharms.build;

import net.pytsing.qingcharms.config.ConfigManager;
import net.pytsing.qingcharms.model.CharmDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Loads and evaluates independent builds.yml definitions. */
public final class RuleResolver {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final TagResolver tagResolver;
    private final List<BuildRule> rules = new ArrayList<BuildRule>();

    public RuleResolver(Plugin plugin, ConfigManager configManager, TagResolver tagResolver) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.tagResolver = tagResolver;
        reload();
    }

    public final void reload() {
        rules.clear();
        ConfigurationSection root = configManager.getBuildSection("builds");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }
            try {
                rules.add(parseBuild(id, section));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring invalid build '" + id + "': "
                        + exception.getMessage());
            }
        }
        Collections.sort(rules, new Comparator<BuildRule>() {
            @Override
            public int compare(BuildRule left, BuildRule right) {
                return left.getId().compareTo(right.getId());
            }
        });
    }

    public Collection<BuildRule> getRules() {
        return Collections.unmodifiableList(new ArrayList<BuildRule>(rules));
    }

    public BuildResolution resolve(UUID owner, Map<String, CharmDefinition> equipment,
                                   Set<String> activeClasses,
                                   Collection<ClassDefinition> ignoredClasses) {
        Set<String> classes = activeClasses == null
                ? Collections.<String>emptySet() : activeClasses;
        TagSnapshot tags = tagResolver.resolve(equipment);
        List<BuildRule> active = new ArrayList<BuildRule>();
        for (BuildRule rule : rules) {
            if (rule.matches(tags, classes)) {
                active.add(rule);
            }
        }
        return new BuildResolution(new BuildSnapshot(owner, classes, tags, active));
    }

    private BuildRule parseBuild(String id, ConfigurationSection section) {
        List<BuildCondition> conditions = new ArrayList<BuildCondition>();
        for (Map<?, ?> condition : section.getMapList("condition")) {
            Object tagValue = condition.containsKey("tags")
                    ? condition.get("tags") : condition.get("tag");
            Object countValue = condition.get("count");
            Object operatorValue = condition.get("operator");
            if (!(tagValue instanceof String) || !(countValue instanceof Number)
                    || !(operatorValue instanceof String)) {
                throw new IllegalArgumentException(
                        "each condition requires tag, numeric count and operator");
            }
            try {
                conditions.add(new BuildCondition((String) tagValue,
                        ((Number) countValue).intValue(), (String) operatorValue));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("invalid condition: "
                        + exception.getMessage());
            }
        }
        if (conditions.isEmpty()) {
            throw new IllegalArgumentException("condition cannot be empty");
        }
        List<String> attributes = section.getStringList("attribute");
        if (attributes.isEmpty()) {
            throw new IllegalArgumentException("attribute cannot be empty");
        }
        return new BuildRule(id, section.getString("name"), conditions, attributes);
    }

    /** Retained as a compatibility helper; new build resolution never tiers rules. */
    static List<BuildRule> highestTierOnly(List<BuildRule> active) {
        Map<String, Integer> bestCount = new LinkedHashMap<String, Integer>();
        for (BuildRule rule : active) {
            String key = rule.getGroupKey();
            if (key == null) {
                continue;
            }
            Integer current = bestCount.get(key);
            if (current == null || rule.getCount() > current.intValue()) {
                bestCount.put(key, Integer.valueOf(rule.getCount()));
            }
        }
        List<BuildRule> result = new ArrayList<BuildRule>();
        for (BuildRule rule : active) {
            String key = rule.getGroupKey();
            Integer best = key == null ? null : bestCount.get(key);
            if (key == null || best != null && rule.getCount() == best.intValue()) {
                result.add(rule);
            }
        }
        return result;
    }
}
