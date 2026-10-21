package net.pytsing.qingcharms.util;

import org.bukkit.Sound;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves Bukkit Sound enum names that were renamed after Minecraft 1.8.
 */
public final class SoundUtils {

    private static final Map<String, String> SOUND_ALIASES;

    static {
        Map<String, String> aliases = new HashMap<String, String>();
        addAlias(aliases, "LEVEL_UP", "ENTITY_PLAYER_LEVELUP");
        addAlias(aliases, "CLICK", "UI_BUTTON_CLICK");
        addAlias(aliases, "ANVIL_LAND", "BLOCK_ANVIL_LAND");
        addAlias(aliases, "WITHER_SPAWN", "ENTITY_WITHER_SPAWN");
        addAlias(aliases, "ENDERDRAGON_GROWL", "ENTITY_ENDERDRAGON_GROWL");
        addAlias(aliases, "VILLAGER_NO", "ENTITY_VILLAGER_NO");
        SOUND_ALIASES = Collections.unmodifiableMap(aliases);
    }

    private SoundUtils() {
    }

    public static Sound parse(String value) {
        if (value == null || value.trim().isEmpty()
                || "none".equalsIgnoreCase(value.trim())) {
            return null;
        }

        String name = normalize(value);
        Sound sound = valueOf(name);
        if (sound != null) {
            return sound;
        }

        String alias = SOUND_ALIASES.get(name);
        return alias == null ? null : valueOf(alias);
    }

    static String normalize(String value) {
        return value.trim().toUpperCase(Locale.US)
                .replace('-', '_').replace(' ', '_');
    }

    static String aliasOf(String value) {
        return SOUND_ALIASES.get(normalize(value));
    }

    private static Sound valueOf(String name) {
        try {
            return Sound.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void addAlias(Map<String, String> aliases,
                                 String legacyName, String modernName) {
        aliases.put(legacyName, modernName);
        aliases.put(modernName, legacyName);
    }
}
