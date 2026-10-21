package net.pytsing.qingcharms.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Mutating charm operations.
 *
 * <p>All methods must be called from the Bukkit main thread. Cancellable events
 * are fired before equipment data is changed. These methods move or replace
 * final items and Lore but never calculate ItemLoreStats attributes or execute
 * combat skills.</p>
 *
 * @since 0.1.0
 */
public interface CharmOperationAPI {

    /**
     * Equips one copy of {@code item} in {@code slotId}.
     *
     * <p>The matching stack is consumed from the player's inventory when
     * present. A replaced item is returned to the inventory. The operation is
     * rejected when the item cannot be found or the inventory lacks space.</p>
     *
     * @return {@code true} when the equipment data changed
     */
    boolean equip(Player player, ItemStack item, String slotId);

    /**
     * Removes the charm from {@code slotId} and returns its final ItemStack to
     * the player's inventory.
     *
     * @return {@code true} when the equipment data changed
     */
    boolean unequip(Player player, String slotId);

    /**
     * Runs trigger-message throttling and fires
     * {@code CharmTriggerNotifyEvent}.
     *
     * <p>This method does not calculate attributes, trigger effects, or execute
     * skills.</p>
     *
     * @return {@code true} only when the notification was not throttled and the
     * event was not cancelled
     */
    boolean notifyTrigger(Player player, String charmId, String triggerType);
}