package net.pytsing.qingcharms.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/**
 * Generic cancellable progression change event.
 *
 * @since 0.1.0
 */
public final class CharmProgressionChangeEvent extends CharmGrowthEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public CharmProgressionChangeEvent(Player player, String uniqueId,
                                       String charmId, Object oldState,
                                       Object newState,
                                       Collection<ItemStack> consumedResources) {
        super(player, uniqueId, charmId, oldState, newState, consumedResources);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}