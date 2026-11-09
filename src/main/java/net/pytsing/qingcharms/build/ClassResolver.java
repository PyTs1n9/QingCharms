package net.pytsing.qingcharms.build;

import net.pytsing.qingcharms.config.ConfigManager;
import net.pytsing.qingcharms.model.CharmDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves playable classes exclusively through configured permission nodes.
 */
public final class ClassResolver {

    private final ConfigManager configManager;
    private final Map<String, ClassDefinition> classes =
            new LinkedHashMap<String, ClassDefinition>();

    public ClassResolver(ConfigManager configManager) {
        this.configManager = configManager;
        reload();
    }

    public void reload() {
        classes.clear();
        ConfigurationSection root = configManager.getBuildSection("classes");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            String name = section.getString("name",
                    section.getString("display-name", id));
            ClassDefinition definition = new ClassDefinition(id, name,
                    section.getString("permission", ""),
                    section.getStringList("titles"),
                    section.getStringList("tags"),
                    section.getStringList("recommended-tags"));
            if (!definition.getId().isEmpty()) {
                classes.put(definition.getId(), definition);
            }
        }
    }

    public Collection<ClassDefinition> getClasses() {
        return Collections.unmodifiableList(
                new ArrayList<ClassDefinition>(classes.values()));
    }

    public ClassDefinition getClassDefinition(String id) {
        return id == null
                ? null : classes.get(id.trim().toLowerCase(Locale.US));
    }

    public Set<String> resolve(Player player) {
        if (player == null) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<String>();
        for (ClassDefinition definition : classes.values()) {
            String permission = definition.getPermission();
            if (!permission.isEmpty() && player.hasPermission(permission)) {
                result.add(definition.getId());
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public boolean canEquip(Player player, CharmDefinition charm) {
        if (charm == null || charm.getClasses().isEmpty()) {
            return true;
        }
        for (String required : charm.getClasses()) {
            ClassDefinition definition = getClassDefinition(required);
            if (definition != null && !definition.getPermission().isEmpty()
                    && player != null
                    && player.hasPermission(definition.getPermission())) {
                return true;
            }
        }
        return false;
    }

    public List<String> getRequiredClassNames(Collection<String> classIds) {
        List<String> result = new ArrayList<String>();
        if (classIds == null) {
            return result;
        }
        for (String classId : classIds) {
            ClassDefinition definition = getClassDefinition(classId);
            result.add(definition == null ? classId : definition.getDisplayName());
        }
        return result;
    }

    public Set<String> recommendedTags(Player player) {
        Set<String> result = new LinkedHashSet<String>();
        for (String classId : resolve(player)) {
            ClassDefinition definition = classes.get(classId);
            if (definition != null) {
                result.addAll(definition.getRecommendedTags());
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
