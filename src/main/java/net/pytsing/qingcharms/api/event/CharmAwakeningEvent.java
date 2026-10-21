package net.pytsing.qingcharms.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/**
 * Fired before a charm awakening state changes.
 *
 * @since 0.1.0
 */
public final class CharmAwakeningEvent extends CharmGrowthEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public CharmAwakeningEvent(Player player, String uniqueId, String charmId,
                               boolean oldAwakened, boolean newAwakened,
                               Collection<ItemStack> consumedResources) {
        super(player, uniqueId, charmId, oldAwakened, newAwakened,
                consumedResources);
    }

    public CharmAwakeningEvent(Player player, String uniqueId, String charmId,
                               Object oldState, Object newState,
                               Collection<ItemStack> consumedResources) {
        super(player, uniqueId, charmId, oldState, newState, consumedResources);
    }

    public boolean wasAwakened() {
        return Boolean.TRUE.equals(getOldState());
    }

    public boolean isAwakened() {
        return Boolean.TRUE.equals(getNewState());
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}