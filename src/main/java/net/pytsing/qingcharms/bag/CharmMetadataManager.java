package net.pytsing.qingcharms.bag;

import net.pytsing.qingcharms.model.CharmDefinition;
import net.pytsing.qingcharms.model.CharmRarity;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads display-only charm metadata. It never interprets combat Lore values.
 */
public final class CharmMetadataManager {

    private static final Pattern LEVEL = Pattern.compile(
            "(?:等级|level)\\s*[:：]?\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE);

    private final JavaPlugin plugin;

    public CharmMetadataManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public CharmBagEntry createEntry(String uniqueId, CharmDefinition charm,
                                     ItemStack item, long createdOrder) {
        ConfigurationSection configured = charm == null ? null
                : plugin.getConfig().getConfigurationSection(
                        "charms." + charm.getId());
        String name = displayName(item, charm);
        String category = first(charm == null ? null : charm.getCategory(),
                configured == null ? null
                        : configured.getString("category"),
                charm == null || charm.getTypeValue() == null
                        ? "" : charm.getTypeValue(),
                "其他");
        String type = charm == null ? "" : charm.getTypeValue();
        int level = charm == null ? 0 : charm.getDisplayLevel();
        if (level <= 0) {
            level = levelFromLore(item);
        }
        CharmRarity rarity = charm == null ? CharmRarity.COMMON : charm.getRarity();
        String source = first(charm == null ? null : charm.getSourceDescription(),
                configured == null ? null
                        : configured.getString("source-description"),
                sourceFromLore(item),
                "");
        return new CharmBagEntry(uniqueId, item, name, category, type,
                level, rarity, source, createdOrder, false, false, false);
    }

    private static String displayName(ItemStack item, CharmDefinition charm) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                return meta.getDisplayName();
            }
        }
        return charm == null ? "" : charm.getDisplayName();
    }

    private static int levelFromLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return 0;
        }
        for (String line : meta.getLore()) {
            Matcher matcher = LEVEL.matcher(ChatColor.stripColor(line == null ? "" : line));
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static String sourceFromLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return "";
        }
        String prefixes = "获取来源|来源|source";
        for (String line : meta.getLore()) {
            String plain = ChatColor.stripColor(line == null ? "" : line).trim();
            Matcher matcher = Pattern.compile("^(?:" + prefixes + ")\\s*[:：]\\s*(.+)$",
                    Pattern.CASE_INSENSITIVE).matcher(plain);
            if (matcher.matches()) {
                return matcher.group(1).trim();
            }
        }
        return "";
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}