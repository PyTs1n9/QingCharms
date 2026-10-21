package net.pytsing.qingcharms.api.event;

import net.pytsing.qingcharms.build.BuildRule;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Set;

/**
 * Notifies plugins when a build rule or bond becomes active.
 *
 * @since 0.1.0
 */
public final class CharmRuleActivateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String ruleId;
    private final Set<String> buildTags;
    private final BuildRule rule;

    public CharmRuleActivateEvent(Player player, BuildRule rule) {
        this(player, rule == null ? "" : rule.getId(),
                rule == null ? null : rule.getTags(), rule);
    }

    public CharmRuleActivateEvent(Player player, String ruleId,
                                  Set<String> buildTags) {
        this(player, ruleId, buildTags, null);
    }

    private CharmRuleActivateEvent(Player player, String ruleId,
                                   Set<String> buildTags, BuildRule rule) {
        this.player = player;
        this.ruleId = ruleId == null ? "" : ruleId;
        this.buildTags = EventSets.copy(buildTags);
        this.rule = rule;
    }

    public Player getPlayer() {
        return player;
    }

    public String getRuleId() {
        return ruleId;
    }

    public Set<String> getBuildTags() {
        return buildTags;
    }

    public BuildRule getRule() {
        return rule;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}