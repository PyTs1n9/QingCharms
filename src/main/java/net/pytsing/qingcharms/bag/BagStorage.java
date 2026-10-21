package net.pytsing.qingcharms.bag;

import net.pytsing.qingcharms.api.ItemUniqueId;
import net.pytsing.qingcharms.model.CharmRarity;
import net.pytsing.qingcharms.util.YamlUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * YAML persistence for dedicated charm bag instances.
 */
public final class BagStorage {

    private final JavaPlugin plugin;
    private final File folder;

    public BagStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "charm-bags");
    }

    public synchronized List<CharmBagEntry> load(UUID ownerId) {
        if (ownerId == null) {
            return new ArrayList<CharmBagEntry>();
        }
        File file = fileFor(ownerId);
        YamlUtils.LoadResult result = YamlUtils.loadChecked(file, plugin.getLogger());
        if (result.isFailed()) {
            throw new IllegalStateException("Could not read charm bag: " + file.getName());
        }
        if (result.isMissing()) {
            return new ArrayList<CharmBagEntry>();
        }

        YamlConfiguration yaml = result.getConfiguration();
        ConfigurationSection items = yaml.getConfigurationSection("items");
        if (items == null) {
            return new ArrayList<CharmBagEntry>();
        }

        List<CharmBagEntry> entries = new ArrayList<CharmBagEntry>();
        Set<String> ids = new LinkedHashSet<String>();
        long fallbackOrder = 0L;
        for (String key : items.getKeys(false)) {
            ConfigurationSection section = items.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            ItemStack item = ItemUniqueId.withoutInstanceIdentityLore(section.getItemStack("item"));
            String uniqueId = normalizeId(section.getString("unique-id", key));
            if (item == null || uniqueId.isEmpty() || !ids.add(uniqueId)) {
                plugin.getLogger().warning("Ignoring invalid charm bag entry in "
                        + file.getName() + ": " + key);
                continue;
            }
            entries.add(new CharmBagEntry(uniqueId, item,
                    section.getString("name", ""),
                    section.getString("category", "其他"),
                    section.getString("type", ""),
                    section.getInt("level", 0),
                    parseRarity(section.getString("rarity", "COMMON")),
                    section.getString("source-description", ""),
                    section.getLong("order", ++fallbackOrder),
                    section.getBoolean("favorite", false),
                    section.getBoolean("locked", false),
                    section.getBoolean("pinned", false)));
        }
        return entries;
    }

    public synchronized boolean save(UUID ownerId, List<CharmBagEntry> entries) {
        if (ownerId == null) {
            return false;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        ConfigurationSection items = yaml.createSection("items");
        long fallbackOrder = 0L;
        for (CharmBagEntry entry : entries == null
                ? Collections.<CharmBagEntry>emptyList() : entries) {
            if (entry == null || !entry.hasItem()) {
                continue;
            }
            String key = normalizeId(entry.getUniqueId());
            if (key.isEmpty() || items.isConfigurationSection(key)) {
                plugin.getLogger().warning("Skipping duplicate charm bag ID: " + key);
                continue;
            }
            ConfigurationSection section = items.createSection(key);
            section.set("unique-id", entry.getUniqueId());
            section.set("item", ItemUniqueId.withoutInstanceIdentityLore(entry.getItem()));
            section.set("name", entry.getName());
            section.set("category", entry.getCategory());
            section.set("type", entry.getType());
            section.set("level", entry.getLevel());
            section.set("rarity", entry.getRarity().name());
            section.set("source-description", entry.getSourceDescription());
            section.set("order",
                    entry.getCreatedOrder() > 0L ? entry.getCreatedOrder() : ++fallbackOrder);
            section.set("favorite", entry.isFavorite());
            section.set("locked", entry.isLocked());
            section.set("pinned", entry.isPinned());
        }

        try {
            YamlUtils.save(fileFor(ownerId), yaml);
            return true;
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().severe("Could not save charm bag: " + exception.getMessage());
            return false;
        }
    }

    private File fileFor(UUID ownerId) {
        return new File(folder, ownerId.toString().toLowerCase(Locale.US) + ".yml");
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static CharmRarity parseRarity(String value) {
        try {
            return CharmRarity.valueOf(value.trim().toUpperCase(Locale.US));
        } catch (IllegalArgumentException exception) {
            return CharmRarity.COMMON;
        }
    }
}
