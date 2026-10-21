package net.pytsing.qingcharms.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class MessageUtils {

    private static FileConfiguration language;

    private MessageUtils() {
    }

    public static void load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "language_zh.yml");
        try {
            YamlUtils.copyResource(plugin, "language_zh.yml", file, false);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not create language_zh.yml: "
                    + exception.getMessage());
        }

        YamlUtils.LoadResult defaults = YamlUtils.loadResource(
                plugin, "language_zh.yml", plugin.getLogger());
        YamlUtils.LoadResult result = YamlUtils.loadChecked(file, plugin.getLogger());
        if (result.isFailed()) {
            plugin.getLogger().severe("language_zh.yml could not be parsed; keeping the existing file unchanged.");
        }

        YamlConfiguration configured = result.getConfiguration();
        configured.addDefaults(defaults.getConfiguration());
        language = configured;
    }

    public static void reload(JavaPlugin plugin) {
        load(plugin);
    }

    public static String get(String key, Object... args) {
        String value = language == null ? null : language.getString(key);
        if (value == null || value.trim().isEmpty()) {
            value = key;
        }
        if (args != null) {
            for (int index = 0; index < args.length; index++) {
                value = value.replace("{" + index + "}", String.valueOf(args[index]));
            }
        }
        return color(value);
    }

    public static void send(CommandSender sender, String key, Object... args) {
        if (sender != null) {
            sender.sendMessage(get(key, args));
        }
    }

    public static void sendHelp(CommandSender sender, String key) {
        if (sender != null && hasMessage(key)) {
            sender.sendMessage(get(key));
        }
    }

    public static void send(Player player, String key, Object... args) {
        if (player != null) {
            player.sendMessage(get(key, args));
        }
    }

    private static boolean hasMessage(String key) {
        String value = language == null ? null : language.getString(key);
        return value != null && !value.trim().isEmpty();
    }

    public static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message == null ? "" : message);
    }
}
