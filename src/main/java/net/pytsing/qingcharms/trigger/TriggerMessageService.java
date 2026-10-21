package net.pytsing.qingcharms.trigger;

import net.pytsing.qingcharms.model.CharmDefinition;
import org.bukkit.Bukkit;
import net.pytsing.qingcharms.util.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Displays short charm trigger messages with tick-based throttling.
 *
 * <p>This service never decides whether an effect triggers and never executes
 * combat logic. Integrations call it after their own effect has triggered.</p>
 */
public final class TriggerMessageService {

    private static final int MAX_TRACKED_TRIGGERS = 4096;

    private final JavaPlugin plugin;
    private final AtomicLong currentTick = new AtomicLong();
    private final Map<TriggerKey, Long> lastShownTick =
            new ConcurrentHashMap<TriggerKey, Long>();
    private final Map<String, TriggerMessageSettings> charmOverrides =
            new HashMap<String, TriggerMessageSettings>();
    private TriggerMessageSettings defaults;
    private BukkitTask ticker;

    public TriggerMessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public synchronized void start() {
        if (ticker != null) {
            return;
        }
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                currentTick.incrementAndGet();
            }
        }, 1L, 1L);
    }

    public synchronized void shutdown() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        lastShownTick.clear();
    }

    public void reload() {
        charmOverrides.clear();
        ConfigurationSection root =
                plugin.getConfig().getConfigurationSection("trigger-message");
        defaults = TriggerMessageSettings.from(
                TriggerMessageSettings.defaults(), root);

        ConfigurationSection charms = plugin.getConfig().getConfigurationSection("charms");
        if (charms != null) {
            for (String id : charms.getKeys(false)) {
                ConfigurationSection charm = charms.getConfigurationSection(id);
                if (charm == null) {
                    continue;
                }
                ConfigurationSection section =
                        charm.getConfigurationSection("trigger-message");
                if (section != null) {
                    charmOverrides.put(normalize(id),
                            TriggerMessageSettings.from(defaults, section));
                }
            }
        }
    }

    /**
     * Returns whether a message is currently allowed without consuming the
     * throttle window.
     */
    public boolean isEligible(Player player, CharmDefinition charm, String triggerType) {
        if (player == null || charm == null || triggerType == null
                || triggerType.trim().isEmpty()) {
            return false;
        }
        TriggerMessageSettings settings = resolve(charm);
        if (!settings.isEnabled() || settings.getFormat().trim().isEmpty()) {
            return false;
        }
        TriggerKey key = key(player, charm, triggerType);
        Long previous = lastShownTick.get(key);
        return previous == null
                || currentTick.get() - previous.longValue() >= settings.getCooldownTicks();
    }

    /**
     * Shows and records one throttled message. Returns true only when shown.
     */
    public boolean show(Player player, CharmDefinition charm, String triggerType) {
        if (!isEligible(player, charm, triggerType)) {
            return false;
        }
        TriggerMessageSettings settings = resolve(charm);
        TriggerKey key = key(player, charm, triggerType);
        long tick = currentTick.get();
        Long previous = lastShownTick.get(key);
        if (previous != null
                && tick - previous.longValue() < settings.getCooldownTicks()) {
            return false;
        }
        if (lastShownTick.size() >= MAX_TRACKED_TRIGGERS) {
            lastShownTick.clear();
        }
        lastShownTick.put(key, Long.valueOf(tick));

        String charmName = charm.getDisplayName();
        if (charmName == null || charmName.trim().isEmpty()) {
            charmName = charm.getId();
        }
        String message = settings.getFormat()
                .replace("{name}", charmName)
                .replace("{charm}", charmName)
                .replace("{id}", charm.getId())
                .replace("{type}", charm.getTypeValue() == null ? "" : charm.getTypeValue())
                .replace("{trigger}", triggerType.trim());
        player.sendMessage(MessageUtils.color(message));
        return true;
    }

    public void clear(UUID playerId) {
        if (playerId == null) {
            return;
        }
        for (TriggerKey key : lastShownTick.keySet()) {
            if (playerId.equals(key.getPlayerId())) {
                lastShownTick.remove(key);
            }
        }
    }

    private TriggerMessageSettings resolve(CharmDefinition charm) {
        TriggerMessageSettings item = charmOverrides.get(normalize(charm.getId()));
        return item == null ? defaults : item;
    }

    private static TriggerKey key(Player player, CharmDefinition charm,
                                  String triggerType) {
        return new TriggerKey(player.getUniqueId(),
                normalize(charm.getId()), normalize(triggerType));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static final class TriggerKey {

        private final UUID playerId;
        private final String charmId;
        private final String triggerType;

        private TriggerKey(UUID playerId, String charmId, String triggerType) {
            this.playerId = playerId;
            this.charmId = charmId;
            this.triggerType = triggerType;
        }

        private UUID getPlayerId() {
            return playerId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TriggerKey)) {
                return false;
            }
            TriggerKey that = (TriggerKey) other;
            return playerId.equals(that.playerId)
                    && charmId.equals(that.charmId)
                    && triggerType.equals(that.triggerType);
        }

        @Override
        public int hashCode() {
            int result = playerId.hashCode();
            result = 31 * result + charmId.hashCode();
            result = 31 * result + triggerType.hashCode();
            return result;
        }
    }

    private static final class TriggerMessageSettings {

        private final boolean enabled;
        private final int cooldownTicks;
        private final String format;

        private TriggerMessageSettings(boolean enabled, int cooldownTicks,
                                       String format) {
            this.enabled = enabled;
            this.cooldownTicks = Math.max(0, cooldownTicks);
            this.format = format == null ? "" : format;
        }

        private static TriggerMessageSettings defaults() {
            return new TriggerMessageSettings(true, 5,
                    "&7[护符] &f{name}已触发");
        }

        private static TriggerMessageSettings from(TriggerMessageSettings base,
                                                   ConfigurationSection section) {
            if (section == null) {
                return base;
            }
            return new TriggerMessageSettings(
                    section.getBoolean("enabled", base.isEnabled()),
                    section.getInt("cooldown-ticks", base.getCooldownTicks()),
                    section.getString("format", base.getFormat()));
        }

        private boolean isEnabled() {
            return enabled;
        }

        private int getCooldownTicks() {
            return cooldownTicks;
        }

        private String getFormat() {
            return format;
        }
    }
}
