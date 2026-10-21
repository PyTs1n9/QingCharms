package net.pytsing.qingcharms.api;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Immutable equipment hand-off to integrations such as ILS.
 *
 * QingCharms preserves complete ItemStacks, including Lore, and may append a
 * non-equipped virtual build-effect item for integrations; it never interprets
 * Lore or calculates combat attributes.
 */
public final class EquipmentSnapshot {

    private final UUID ownerId;
    private final long dataVersion;
    private final List<ItemStack> items;

    public EquipmentSnapshot(UUID ownerId, long dataVersion,
                             Collection<ItemStack> equipment) {
        this.ownerId = ownerId;
        this.dataVersion = dataVersion;

        List<ItemStack> itemCopies = new ArrayList<ItemStack>();
        if (equipment != null) {
            for (ItemStack item : equipment) {
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    itemCopies.add(item.clone());
                }
            }
        }
        this.items = Collections.unmodifiableList(itemCopies);
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public long getDataVersion() {
        return dataVersion;
    }

    /**
     * Returns full item copies so integrations can read the original Lore.
     */
    public List<ItemStack> getItems() {
        List<ItemStack> copies = new ArrayList<ItemStack>(items.size());
        for (ItemStack item : items) {
            copies.add(item.clone());
        }
        return Collections.unmodifiableList(copies);
    }
}
