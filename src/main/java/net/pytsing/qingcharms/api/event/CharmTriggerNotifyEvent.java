package net.pytsing.qingcharms.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Message-only trigger notification. This event never executes a skill. */
public final class CharmTriggerNotifyEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String charmId;
    private final String triggerType;
    private final long timestamp;
    private boolean cancelled;

    public CharmTriggerNotifyEvent(Player player, String charmId, String triggerType) {
        this(player, charmId, triggerType, System.currentTimeMillis());
    }

    public CharmTriggerNotifyEvent(Player player, String charmId, String triggerType,
                                   long timestamp) {
        this.player = player;
        this.charmId = charmId == null ? "" : charmId;
        this.triggerType = triggerType == null ? "" : triggerType;
        this.timestamp = timestamp;
    }

    public Player getPlayer() {
        return player;
    }

    public String getCharmId() {
        return charmId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
