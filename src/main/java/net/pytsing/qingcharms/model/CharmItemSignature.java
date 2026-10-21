package net.pytsing.qingcharms.model;

import net.pytsing.qingcharms.api.ItemUniqueId;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class CharmItemSignature {

    private final Material material;
    private final short durability;
    private final String normalizedName;
    private final Set<String> normalizedLore;
    private final Set<String> searchableLore;

    private CharmItemSignature(Material material, short durability,
                               String normalizedName, Set<String> normalizedLore,
                               Set<String> searchableLore) {
        this.material = material;
        this.durability = durability;
        this.normalizedName = normalizedName;
        this.normalizedLore = Collections.unmodifiableSet(normalizedLore);
        this.searchableLore = Collections.unmodifiableSet(searchableLore);
    }

    public static CharmItemSignature from(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        String name = meta != null && meta.hasDisplayName()
                ? meta.getDisplayName() : null;
        Set<String> normalizedLore = new LinkedHashSet<String>();
        Set<String> searchableLore = new LinkedHashSet<String>();
        if (meta != null && meta.hasLore()) {
            for (String line : meta.getLore()) {
                normalizedLore.add(normalizeMatch(line));
                searchableLore.add(normalizeSearch(line));
            }
        }
        return new CharmItemSignature(item.getType(), item.getDurability(),
                normalizeMatch(name), normalizedLore, searchableLore);
    }

    public Material getMaterial() {
        return material;
    }

    public short getDurability() {
        return durability;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public Set<String> getNormalizedLore() {
        return normalizedLore;
    }

    public boolean containsSearchLore(String expected) {
        String normalizedExpected = normalizeSearch(expected);
        if (normalizedExpected.isEmpty() || "none".equals(normalizedExpected)) {
            return true;
        }
        for (String line : searchableLore) {
            if (line.contains(normalizedExpected)) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeMatch(String value) {
        return value == null ? null
                : ItemUniqueId.stripHiddenData(value).replace('\u00a7', '&').trim();
    }

    public static String normalizeSearch(String value) {
        return value == null
                ? ""
                : ChatColor.stripColor(ItemUniqueId.stripHiddenData(value))
                        .trim().toLowerCase(Locale.US);
    }
}
