package net.pytsing.qingcharms.api.event;

import net.pytsing.qingcharms.build.BuildSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Set;

/**
 * Notifies plugins after the current build tags or active rules change.
 *
 * @since 0.1.0
 */
public final class CharmBuildChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final BuildSnapshot previousBuild;
    private final BuildSnapshot newBuild;
    private final Set<String> previousTags;
    private final Set<String> currentTags;

    public CharmBuildChangeEvent(Player player, BuildSnapshot previousBuild,
                                BuildSnapshot newBuild) {
        this.player = player;
        this.previousBuild = previousBuild;
        this.newBuild = newBuild;
        this.previousTags = EventSets.copy(previousBuild == null
                ? null : previousBuild.getTags().getBuildCounts().keySet());
        this.currentTags = EventSets.copy(newBuild == null
                ? null : newBuild.getTags().getBuildCounts().keySet());
    }

    public CharmBuildChangeEvent(Player player, Set<String> previousTags,
                                Set<String> currentTags) {
        this.player = player;
        this.previousBuild = null;
        this.newBuild = null;
        this.previousTags = EventSets.copy(previousTags);
        this.currentTags = EventSets.copy(currentTags);
    }

    public Player getPlayer() {
        return player;
    }

    public BuildSnapshot getPreviousBuild() {
        return previousBuild;
    }

    public BuildSnapshot getNewBuild() {
        return newBuild;
    }

    public Set<String> getPreviousTags() {
        return previousTags;
    }

    public Set<String> getCurrentTags() {
        return currentTags;
    }

    public Set<String> getPreviousBuildTags() {
        return previousTags;
    }

    public Set<String> getOldTags() {
        return previousTags;
    }

    public Set<String> getNewTags() {
        return currentTags;
    }

    public Set<String> getCurrentBuildTags() {
        return currentTags;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}