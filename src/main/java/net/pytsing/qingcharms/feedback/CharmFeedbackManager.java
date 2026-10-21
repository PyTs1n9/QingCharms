package net.pytsing.qingcharms.feedback;

import net.pytsing.qingcharms.model.CharmDefinition;
import net.pytsing.qingcharms.util.MessageUtils;
import net.pytsing.qingcharms.util.SoundUtils;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays cosmetic equipment feedback without participating in equipment state,
 * persistence, Lore parsing, attribute calculation, or combat resolution.
 */
public final class CharmFeedbackManager {

    private static final int MAX_PARTICLE_COUNT = 1024;
    private static final double MAX_PARTICLE_RADIUS = 32.0D;

    private final JavaPlugin plugin;
    private final Map<String, FeedbackProfile> defaults =
            new HashMap<String, FeedbackProfile>();
    private final Map<String, Map<String, FeedbackProfile>> charmOverrides =
            new HashMap<String, Map<String, FeedbackProfile>>();
    private boolean playFailures;

    public CharmFeedbackManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        defaults.clear();
        charmOverrides.clear();
        playFailures = false;

        ConfigurationSection root =
                plugin.getConfig().getConfigurationSection("feedback");
        if (root == null) {
            loadBuiltInDefaults();
            return;
        }

        playFailures = root.getBoolean("play-failures", false);
        defaults.put("equip", FeedbackProfile.from(
                FeedbackProfile.equipDefaults(), root.getConfigurationSection("equip")));
        defaults.put("replace", FeedbackProfile.from(
                defaults.get("equip"), root.getConfigurationSection("replace")));
        defaults.put("unequip", FeedbackProfile.from(
                FeedbackProfile.unequipDefaults(), root.getConfigurationSection("unequip")));
        defaults.put("failure", FeedbackProfile.from(
                FeedbackProfile.failureDefaults(), root.getConfigurationSection("failure")));

        ConfigurationSection charms = root.getConfigurationSection("charms");
        if (charms != null) {
            for (String id : charms.getKeys(false)) {
                ConfigurationSection charm = charms.getConfigurationSection(id);
                if (charm == null) {
                    continue;
                }
                Map<String, FeedbackProfile> profiles =
                        new HashMap<String, FeedbackProfile>();
                profiles.put("equip", FeedbackProfile.from(
                        defaults.get("equip"), charm.getConfigurationSection("equip")));
                profiles.put("replace", FeedbackProfile.from(
                        defaults.get("replace"), charm.getConfigurationSection("replace")));
                profiles.put("unequip", FeedbackProfile.from(
                        defaults.get("unequip"), charm.getConfigurationSection("unequip")));
                profiles.put("failure", FeedbackProfile.from(
                        defaults.get("failure"), charm.getConfigurationSection("failure")));
                charmOverrides.put(normalize(id), Collections.unmodifiableMap(profiles));
            }
        }
    }

    public void play(Player player, CharmDefinition charm, String event) {
        play(player, charm, null, event);
    }

    /**
     * Plays feedback for a specific item. When the item carries a display name
     * it is preferred over the charm definition's configured name, so renamed
     * or in-game modified charms are reported by what the player actually sees.
     */
    public void play(Player player, CharmDefinition charm, ItemStack item, String event) {
        if (player == null || event == null || event.trim().isEmpty()) {
            return;
        }
        String normalizedEvent = normalize(event);
        if ("failure".equals(normalizedEvent) && !playFailures) {
            return;
        }

        FeedbackProfile profile = resolve(charm, normalizedEvent);
        if (profile == null || !profile.isEnabled()) {
            return;
        }

        // Feedback is cosmetic and must never interrupt equipment operations.
        try {
            playSound(player, profile);
            playParticles(player, profile);
            sendMessage(player, charm, item, normalizedEvent, profile);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not play charm feedback '"
                    + normalizedEvent + "': " + exception.getMessage());
        }
    }

    private FeedbackProfile resolve(CharmDefinition charm, String event) {
        if (charm != null) {
            Map<String, FeedbackProfile> profiles =
                    charmOverrides.get(normalize(charm.getId()));
            if (profiles != null) {
                FeedbackProfile itemProfile = profiles.get(event);
                if (itemProfile != null) {
                    return itemProfile;
                }
            }
        }
        return defaults.get(event);
    }

    private void playSound(Player player, FeedbackProfile profile) {
        if (profile.getSound() != null) {
            player.playSound(player.getLocation(), profile.getSound(),
                    (float) profile.getVolume(), (float) profile.getPitch());
        }
    }

    private void playParticles(Player player, FeedbackProfile profile) {
        if (profile.getParticle() == null || profile.getCount() <= 0) {
            return;
        }
        Location center = player.getLocation().clone().add(0.0D, 1.0D, 0.0D);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < profile.getCount(); index++) {
            Location location = center.clone().add(
                    randomOffset(random, profile.getRadius()),
                    randomOffset(random, profile.getRadius()),
                    randomOffset(random, profile.getRadius()));
            player.getWorld().playEffect(location, profile.getParticle(), 0);
        }
    }

    private void sendMessage(Player player, CharmDefinition charm, ItemStack item,
                             String event, FeedbackProfile profile) {
        String message = profile.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        String charmName = displayName(charm, item);
        String charmId = charm == null ? "" : charm.getId();
        String charmType = charm == null ? "" : charm.getTypeValue();
        player.sendMessage(MessageUtils.color(message
                .replace("{charm}", charmName)
                .replace("{name}", charmName)
                .replace("{id}", charmId)
                .replace("{type}", charmType)
                .replace("{event}", event)));
    }

    /**
     * Resolves the name shown in feedback messages: the item's own display name
     * first (what the player actually sees in the GUI and inventory), then the
     * charm definition's display name, then the item template's display name,
     * and only then the raw charm ID.
     */
    private static String displayName(CharmDefinition charm, ItemStack item) {
        String itemName = itemDisplayName(item);
        if (!itemName.isEmpty()) {
            return itemName;
        }
        if (charm == null) {
            return "";
        }
        String charmName = charm.getDisplayName();
        if (charmName != null && !charmName.trim().isEmpty()
                && !charmName.trim().equalsIgnoreCase(charm.getId())) {
            return charmName;
        }
        String templateName = itemDisplayName(charm.getItemTemplate());
        if (!templateName.isEmpty()) {
            return templateName;
        }
        return charmName == null ? "" : charmName;
    }

    private static String itemDisplayName(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return "";
        }
        String name = meta.getDisplayName();
        return name == null ? "" : name.trim();
    }

    private static double randomOffset(ThreadLocalRandom random, double radius) {
        return radius <= 0.0D ? 0.0D : (random.nextDouble() * 2.0D - 1.0D) * radius;
    }

    private void loadBuiltInDefaults() {
        defaults.put("equip", FeedbackProfile.equipDefaults());
        defaults.put("replace", FeedbackProfile.from(
                FeedbackProfile.equipDefaults(), null));
        defaults.put("unequip", FeedbackProfile.unequipDefaults());
        defaults.put("failure", FeedbackProfile.failureDefaults());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static final class FeedbackProfile {

        private final boolean enabled;
        private final Sound sound;
        private final double volume;
        private final double pitch;
        private final Effect particle;
        private final int count;
        private final double radius;
        private final String message;

        private FeedbackProfile(boolean enabled, Sound sound, double volume,
                                double pitch, Effect particle, int count,
                                double radius, String message) {
            this.enabled = enabled;
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
            this.particle = particle;
            this.count = count;
            this.radius = radius;
            this.message = message;
        }

        private static FeedbackProfile from(FeedbackProfile base,
                                            ConfigurationSection section) {
            boolean enabled = section == null
                    ? base.isEnabled() : section.getBoolean("enabled", base.isEnabled());
            Sound sound = section != null && section.contains("sound")
                    ? SoundUtils.parse(section.getString("sound")) : base.getSound();
            double volume = section == null ? base.getVolume()
                    : clamp(section.getDouble("volume", base.getVolume()), 0.0D, 4.0D);
            double pitch = section == null ? base.getPitch()
                    : clamp(section.getDouble("pitch", base.getPitch()), 0.0D, 2.0D);
            Effect particle = section != null && section.contains("particles")
                    ? parseEffect(section.getString("particles")) : base.getParticle();
            int count = section == null ? base.getCount()
                    : (int) clamp(section.getInt("count", base.getCount()),
                    0, MAX_PARTICLE_COUNT);
            double radius = section == null ? base.getRadius()
                    : clamp(section.getDouble("radius", base.getRadius()),
                    0.0D, MAX_PARTICLE_RADIUS);
            String message = section != null && section.contains("message")
                    ? section.getString("message", "") : base.getMessage();
            return new FeedbackProfile(enabled, sound, volume, pitch,
                    particle, count, radius, message);
        }

        private static FeedbackProfile equipDefaults() {
            return new FeedbackProfile(true, Sound.LEVEL_UP, 1.0D, 1.0D,
                    Effect.FLAME, 12, 0.4D, "&a已装备 {charm}");
        }

        private static FeedbackProfile unequipDefaults() {
            return new FeedbackProfile(true, Sound.CLICK, 1.0D, 1.0D,
                    Effect.SMOKE, 8, 0.4D, "&7已卸下 {charm}");
        }

        private static FeedbackProfile failureDefaults() {
            return new FeedbackProfile(false, null, 1.0D, 1.0D,
                    null, 0, 0.0D, "");
        }

        private boolean isEnabled() {
            return enabled;
        }

        private Sound getSound() {
            return sound;
        }

        private double getVolume() {
            return volume;
        }

        private double getPitch() {
            return pitch;
        }

        private Effect getParticle() {
            return particle;
        }

        private int getCount() {
            return count;
        }

        private double getRadius() {
            return radius;
        }

        private String getMessage() {
            return message;
        }


        private static Effect parseEffect(String value) {
            if (value == null || value.trim().isEmpty()
                    || "none".equalsIgnoreCase(value.trim())) {
                return null;
            }
            try {
                return Effect.valueOf(normalizeEnum(value));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        private static String normalizeEnum(String value) {
            return value.trim().toUpperCase(Locale.US)
                    .replace('-', '_').replace(' ', '_');
        }

        private static double clamp(double value, double minimum, double maximum) {
            return Math.max(minimum, Math.min(maximum, value));
        }
    }
}

