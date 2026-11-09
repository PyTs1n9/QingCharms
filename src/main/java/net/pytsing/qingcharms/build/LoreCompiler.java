package net.pytsing.qingcharms.build;

import net.pytsing.qingcharms.model.PlayerData;
import net.pytsing.qingcharms.util.MessageUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Removes legacy dynamic Lore from equipped items. Build effects are emitted as
 * a separate virtual snapshot item so they never appear on or move between
 * player equipment.
 */
public final class LoreCompiler {

    private static final String LEGACY_MANAGED_SUFFIX =
            "\u00a70\u00a71\u00a70\u00a71\u00a70";

    public boolean compile(PlayerData playerData, BuildResolution resolution) {
        if (playerData == null) {
            return false;
        }
        boolean changed = false;
        for (String slotId : new ArrayList<String>(playerData.getEquippedSlotIds())) {
            ItemStack item = playerData.getEquipped(slotId);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            List<String> cleaned = stripLegacyManagedLore(item);
            if (!sameLore(item, cleaned)) {
                setLore(item, cleaned);
                playerData.setEquipped(slotId, item);
                changed = true;
            }
        }
        return changed;
    }

    public ItemStack createLoreItem(PlayerData playerData, BuildResolution resolution) {
        if (playerData == null || resolution == null) {
            return null;
        }
        List<String> lore = new ArrayList<String>();
        for (BuildRule rule : resolution.getLoreRules()) {
            for (String line : rule.getAttributes()) {
                String normalized = normalize(line);
                if (!normalized.isEmpty()) {
                    lore.add(MessageUtils.color(line));
                }
            }
        }
        if (lore.isEmpty()) {
            return null;
        }

        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private List<String> stripLegacyManagedLore(ItemStack item) {
        List<String> result = new ArrayList<String>();
        if (!item.hasItemMeta()) {
            return result;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return result;
        }
        for (String line : meta.getLore()) {
            if (line == null || line.endsWith(LEGACY_MANAGED_SUFFIX)) {
                continue;
            }
            result.add(line);
        }
        return result;
    }

    private boolean sameLore(ItemStack item, List<String> expected) {
        List<String> actual = new ArrayList<String>();
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasLore()) {
                actual.addAll(meta.getLore());
            }
        }
        return actual.equals(expected);
    }

    private void setLore(ItemStack item, List<String> lore) {
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta == null) {
            return;
        }
        meta.setLore(lore.isEmpty() ? null : new ArrayList<String>(lore));
        item.setItemMeta(meta);
    }

    private static String normalize(String value) {
        return ChatColor.stripColor(MessageUtils.color(value == null ? "" : value)).trim();
    }
}
