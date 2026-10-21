package net.pytsing.qingcharms.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Shared cancellable payload for charm progression changes.
 *
 * <p>Subclasses must be fired before the old state is replaced and before
 * consumed resources are removed.</p>
 */
public abstract class CharmGrowthEvent extends Event implements Cancellable {

    private final Player player;
    private final String uniqueId;
    private final String charmId;
    private final Object oldState;
    private final Object newState;
    private final List<ItemStack> consumedResources;
    private boolean cancelled;

    protected CharmGrowthEvent(Player player, String uniqueId, String charmId,
                               Object oldState, Object newState,
                               Collection<ItemStack> consumedResources) {
        this.player = player;
        this.uniqueId = uniqueId == null ? "" : uniqueId;
        this.charmId = charmId == null ? "" : charmId;
        this.oldState = oldState;
        this.newState = newState;
        List<ItemStack> resources = new ArrayList<ItemStack>();
        if (consumedResources != null) {
            for (ItemStack resource : consumedResources) {
                if (resource != null) {
                    resources.add(resource.clone());
                }
            }
        }
        this.consumedResources = Collections.unmodifiableList(resources);
    }

    public Player getPlayer() {
        return player;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public String getItemUniqueId() {
        return uniqueId;
    }

    public String getCharmId() {
        return charmId;
    }

    public Object getOldState() {
        return oldState;
    }

    public Object getNewState() {
        return newState;
    }

    public List<ItemStack> getConsumedResources() {
        List<ItemStack> copies = new ArrayList<ItemStack>(consumedResources.size());
        for (ItemStack resource : consumedResources) {
            copies.add(resource.clone());
        }
        return Collections.unmodifiableList(copies);
    }

    public List<ItemStack> getResourcesConsumed() {
        return getConsumedResources();
    }

    public boolean isCancellable() {
        return true;
    }

    @Override
    public final boolean isCancelled() {
        return cancelled;
    }

    @Override
    public final void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}