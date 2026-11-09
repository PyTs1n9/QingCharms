package net.pytsing.qingcharms.build;

import net.pytsing.qingcharms.QingCharmsPlugin;
import net.pytsing.qingcharms.api.event.CharmBuildChangeEvent;
import net.pytsing.qingcharms.api.event.CharmRuleActivateEvent;
import net.pytsing.qingcharms.api.event.CharmRuleDeactivateEvent;
import net.pytsing.qingcharms.model.CharmDefinition;
import net.pytsing.qingcharms.model.PlayerData;
import net.pytsing.qingcharms.util.EventUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class BuildResolver {

    private final QingCharmsPlugin plugin;
    private final ClassResolver classResolver;
    private final TagResolver tagResolver;
    private final RuleResolver ruleResolver;
    private final LoreCompiler loreCompiler;
    private final Map<UUID, BuildSnapshot> lastSnapshots =
            new HashMap<UUID, BuildSnapshot>();

    public BuildResolver(QingCharmsPlugin plugin) {
        this.plugin = plugin;
        this.classResolver = new ClassResolver(plugin.getConfigManager());
        this.tagResolver = new TagResolver();
        this.ruleResolver = new RuleResolver(plugin, plugin.getConfigManager(), tagResolver);
        this.loreCompiler = new LoreCompiler();
    }

    public void reload() {
        classResolver.reload();
        ruleResolver.reload();
        if (plugin.getConfigManager().getClassesConfiguration()
                .isConfigurationSection("rules")) {
            plugin.getLogger().warning("classes.yml contains legacy 'rules'; they are "
                    + "ignored. Move build conditions and attribute lines to builds.yml.");
        }
        Set<UUID> online = new LinkedHashSet<UUID>();
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
            refresh(player, plugin.getDataManager().loadPlayer(player.getUniqueId()),
                    null, true);
        }
        for (UUID playerId : new ArrayList<UUID>(lastSnapshots.keySet())) {
            if (!online.contains(playerId)) {
                lastSnapshots.remove(playerId);
            }
        }
    }

    public BuildResolution resolve(Player player, Map<String, ItemStack> equipment) {
        Set<String> activeClasses = classResolver.resolve(player);
        Map<String, CharmDefinition> definitions =
                new LinkedHashMap<String, CharmDefinition>();
        if (equipment != null) {
            for (Map.Entry<String, ItemStack> entry : equipment.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                Optional<CharmDefinition> charm =
                        plugin.getCharmManager().getCharmByItem(entry.getValue());
                if (charm.isPresent()) {
                    definitions.put(entry.getKey(), charm.get());
                }
            }
        }
        List<ClassDefinition> classDefinitions = new ArrayList<ClassDefinition>();
        for (String classId : activeClasses) {
            ClassDefinition definition = classResolver.getClassDefinition(classId);
            if (definition != null) {
                classDefinitions.add(definition);
            }
        }
        return ruleResolver.resolve(player == null ? null : player.getUniqueId(),
                definitions, activeClasses, classDefinitions);
    }

    public BuildResolution resolveEquipped(Player player, PlayerData playerData) {
        return resolve(player, getEquipment(playerData));
    }

    public ItemStack createLoreItem(Player player, PlayerData playerData) {
        return loreCompiler.createLoreItem(playerData, resolveEquipped(player, playerData));
    }

    public boolean canEquip(Player player, CharmDefinition charm) {
        return classResolver.canEquip(player, charm);
    }

    public EquipmentRequirementResult validateEquipment(
            Player player, ItemStack item, String slotId) {
        Optional<CharmDefinition> charm = plugin.getCharmManager().getCharmByItem(item);
        return EquipmentRequirementValidator.validate(player, item, slotId,
                charm.orElse(null), classResolver);
    }

    public EquipmentRequirementResult validateUsage(
            Player player, CharmDefinition charm) {
        return EquipmentRequirementValidator.validateUsage(
                player, charm, classResolver);
    }

    public Set<String> getRecommendedTags(Player player) {
        return classResolver.recommendedTags(player);
    }

    public Set<String> getActiveClassIds(Player player) {
        return classResolver.resolve(player);
    }

    /**
     * Returns the configured display names of the active classes.
     *
     * <p>Names come from {@code classes.<id>.name} and keep their colour codes;
     * {@code MessageUtils} translates {@code &} codes after substitution.</p>
     */
    public List<String> getActiveClassNames(Player player) {
        return classResolver.getRequiredClassNames(classResolver.resolve(player));
    }

    public void initialize(Player player, PlayerData playerData) {
        BuildResolution resolution = resolveEquipped(player, playerData);
        loreCompiler.compile(playerData, resolution);
        lastSnapshots.put(player.getUniqueId(), resolution.getSnapshot());
        plugin.getGuiManager().invalidateEquipmentSnapshot(player.getUniqueId());
    }

    public BuildResolution refresh(Player player, PlayerData playerData,
                                   BuildResolution previous, boolean notify) {
        EventUtils.requireMainThread();
        BuildResolution current = resolveEquipped(player, playerData);
        loreCompiler.compile(playerData, current);
        BuildSnapshot previousSnapshot = previous == null
                ? lastSnapshots.get(player.getUniqueId()) : previous.getSnapshot();
        BuildSnapshot currentSnapshot = current.getSnapshot();
        if (notify && previousSnapshot != null) {
            if (!previousSnapshot.getTags().getBuildCounts().equals(
                    currentSnapshot.getTags().getBuildCounts())) {
                EventUtils.call(new CharmBuildChangeEvent(
                        player, previousSnapshot, currentSnapshot));
            }

            Set<String> previousRules = ruleIds(previousSnapshot.getActiveRules());
            Set<String> currentRules = ruleIds(currentSnapshot.getActiveRules());
            for (BuildRule rule : currentSnapshot.getActiveRules()) {
                if (!previousRules.contains(rule.getId())) {
                    EventUtils.call(new CharmRuleActivateEvent(player, rule));
                }
            }
            for (BuildRule rule : previousSnapshot.getActiveRules()) {
                if (!currentRules.contains(rule.getId())) {
                    EventUtils.call(new CharmRuleDeactivateEvent(player, rule));
                }
            }
        }
        lastSnapshots.put(player.getUniqueId(), currentSnapshot);
        plugin.getDataManager().savePlayer(playerData);
        plugin.getGuiManager().invalidateEquipmentSnapshot(player.getUniqueId());
        return current;
    }

    public void invalidate(UUID playerId) {
        if (playerId != null) {
            lastSnapshots.remove(playerId);
        }
    }

    private Map<String, ItemStack> getEquipment(PlayerData playerData) {
        Map<String, ItemStack> result = new LinkedHashMap<String, ItemStack>();
        if (playerData == null) {
            return result;
        }
        for (String slotId : playerData.getEquippedSlotIds()) {
            ItemStack item = playerData.getEquipped(slotId);
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                result.put(slotId, item);
            }
        }
        return result;
    }

    private Set<String> ruleIds(List<BuildRule> rules) {
        Set<String> result = new LinkedHashSet<String>();
        for (BuildRule rule : rules) {
            result.add(rule.getId());
        }
        return result;
    }
}
