package net.pytsing.qingcharms.api.event;

import net.pytsing.qingcharms.api.ItemUniqueId;
import net.pytsing.qingcharms.model.CharmDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

/**
 * Shared payload for equipment events.
 */
public abstract class CharmItemEvent extends Event implements Cancellable {

    private final Player player;
    private final String uniqueId;
    private final String charmId;
    private final String slotId;
    private final ItemStack oldItem;
    private final ItemStack newItem;
    private final CharmDefinition charm;
    private boolean cancelled;

    protected CharmItemEvent(Player player, String uniqueId, String charmId,
                             String slotId, ItemStack oldItem, ItemStack newItem,
                             CharmDefinition charm) {
        this.player = player;
        this.uniqueId = uniqueId == null || uniqueId.isEmpty()
                ? ItemUniqueId.from(newItem == null ? oldItem : newItem) : uniqueId;
        this.charmId = charmId == null
                ? (charm == null ? "" : charm.getId()) : charmId;
        this.slotId = slotId == null ? "" : slotId;
        this.oldItem = copy(oldItem);
        this.newItem = copy(newItem);
        this.charm = charm;
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

    public String getSlotId() {
        return slotId;
    }

    public ItemStack getOldItem() {
        return copy(oldItem);
    }

    public ItemStack getNewItem() {
        return copy(newItem);
    }

    /** Legacy alias for {@link #getOldItem()}. */
    public ItemStack getReplacedItem() {
        return getOldItem();
    }

    /** Legacy alias for {@link #getNewItem()}. */
    public ItemStack getItem() {
        return getNewItem();
    }

    /** Legacy definition accessor when the event was created from a definition. */
    public CharmDefinition getCharm() {
        return charm;
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

    private static ItemStack copy(ItemStack item) {
        return item == null ? null : item.clone();
    }
}