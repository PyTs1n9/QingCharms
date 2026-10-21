package net.pytsing.qingcharms.api.event;

import net.pytsing.qingcharms.model.CharmDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired before one equipped charm replaces another in the same slot.
 *
 * @since 0.1.0
 */
public final class CharmReplaceEvent extends CharmItemEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public CharmReplaceEvent(Player player, String uniqueId, String charmId,
                             String slotId, ItemStack oldItem, ItemStack newItem) {
        super(player, uniqueId, charmId, slotId, oldItem, newItem, null);
    }

    public CharmReplaceEvent(Player player, CharmDefinition charm, ItemStack item,
                             String slotId, ItemStack replacedItem) {
        super(player, null, charm == null ? null : charm.getId(), slotId,
                replacedItem, item, charm);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}