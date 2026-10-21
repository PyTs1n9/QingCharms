package net.pytsing.qingcharms.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/**
 * Fired before a charm enhancement level changes.
 *
 * @since 0.1.0
 */
public final class CharmEnhanceEvent extends CharmGrowthEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public CharmEnhanceEvent(Player player, String uniqueId, String charmId,
                             int oldLevel, int newLevel,
                             Collection<ItemStack> consumedResources) {
        super(player, uniqueId, charmId, oldLevel, newLevel, consumedResources);
    }

    public CharmEnhanceEvent(Player player, String uniqueId, String charmId,
                             Object oldState, Object newState,
                             Collection<ItemStack> consumedResources) {
        super(player, uniqueId, charmId, oldState, newState, consumedResources);
    }

    public int getOldEnhanceLevel() {
        return asInt(getOldState());
    }

    public int getNewEnhanceLevel() {
        return asInt(getNewState());
    }

    private static int asInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}