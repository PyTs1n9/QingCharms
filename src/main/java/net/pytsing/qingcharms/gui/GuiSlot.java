package net.pytsing.qingcharms.gui;

import net.pytsing.qingcharms.util.MessageUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class GuiSlot {

    private final int slot;
    private final int itemId;
    private final short data;
    private final String displayName;
    private final List<String> lore;
    private final String type;
    private final String action;
    private final String permission;

    public GuiSlot(int slot,
                   int itemId,
                   short data,
                   String displayName,
                   List<String> lore,
                   String type,
                   String action,
                   String permission) {
        this.slot = slot;
        this.itemId = itemId;
        this.data = data;
        this.displayName = displayName == null ? "" : displayName;
        this.lore = Collections.unmodifiableList(new ArrayList<String>(
                lore == null ? Collections.<String>emptyList() : lore));
        this.type = type == null || type.trim().isEmpty() ? "NONE" : type.trim();
        this.action = action == null || action.trim().isEmpty()
                ? "none" : action.trim().toLowerCase(Locale.US);
        this.permission = permission == null ? "" : permission;
    }

    public int getSlot() {
        return slot;
    }

    public int getItemId() {
        return itemId;
    }

    public short getData() {
        return data;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return lore;
    }

    public String getType() {
        return type;
    }

    public String getAction() {
        return action;
    }

    /** Equip-style slots need a real type; decorative slots are allowed to be NONE. */
    public boolean requiresType() {
        return "equip".equals(action) || "unequip".equals(action);
    }

    /** True when the configured type cannot identify a charm slot. */
    public boolean hasUsableType() {
        return type != null && !type.trim().isEmpty()
                && !"NONE".equalsIgnoreCase(type.trim());
    }

    public String getPermission() {
        return permission;
    }

    public ItemStack createItem() {
        Material material = Material.getMaterial(itemId);
        if (material == null) {
            material = Material.matchMaterial(String.valueOf(itemId));
        }
        if (material == null) {
            material = Material.PAPER;
        }

        ItemStack item = new ItemStack(material, 1, data);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (!displayName.isEmpty()) {
                meta.setDisplayName(MessageUtils.color(displayName));
            }
            if (!lore.isEmpty()) {
                List<String> colored = new ArrayList<String>();
                for (String line : lore) {
                    colored.add(MessageUtils.color(line));
                }
                meta.setLore(colored);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean matches(ItemStack item) {
        return item != null && item.getTypeId() == itemId
                && item.getDurability() == data;
    }
}
