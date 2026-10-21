package net.pytsing.qingcharms.bag;

import net.pytsing.qingcharms.api.ItemUniqueId;
import net.pytsing.qingcharms.model.CharmRarity;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * One stored charm instance. The unique ID is instance identity and is never
 * inferred from item content, so identical items do not share bag identity.
 */
public final class CharmBagEntry {

    private final String uniqueId;
    private final ItemStack item;
    private final String name;
    private final String category;
    private final String type;
    private final int level;
    private final CharmRarity rarity;
    private final String sourceDescription;
    private final long createdOrder;
    private boolean favorite;
    private boolean locked;
    private boolean pinned;

    public CharmBagEntry(String uniqueId, ItemStack item, String name,
                         String category, String type, int level,
                         CharmRarity rarity, String sourceDescription,
                         long createdOrder, boolean favorite,
                         boolean locked, boolean pinned) {
        this.uniqueId = uniqueId;
        this.item = item == null || item.getType() == Material.AIR
                ? null : ItemUniqueId.withUniqueId(item, uniqueId);
        this.name = name == null ? "" : name;
        this.category = category == null || category.trim().isEmpty()
                ? "其他" : category.trim();
        this.type = type == null ? "" : type;
        this.level = Math.max(0, level);
        this.rarity = rarity == null ? CharmRarity.COMMON : rarity;
        this.sourceDescription = sourceDescription == null
                ? "" : sourceDescription.trim();
        this.createdOrder = createdOrder;
        this.favorite = favorite;
        this.locked = locked;
        this.pinned = pinned;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public ItemStack getItem() {
        return item == null ? null : item.clone();
    }

    public boolean hasItem() {
        return item != null && item.getType() != Material.AIR;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getType() {
        return type;
    }

    public int getLevel() {
        return level;
    }

    public CharmRarity getRarity() {
        return rarity;
    }

    public String getSourceDescription() {
        return sourceDescription;
    }

    public long getCreatedOrder() {
        return createdOrder;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public CharmBagEntry copy() {
        return new CharmBagEntry(uniqueId, item, name, category, type, level,
                rarity, sourceDescription, createdOrder, favorite, locked, pinned);
    }
}
