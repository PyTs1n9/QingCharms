package net.pytsing.qingcharms.progression;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProgressionRecord {

    private static final Pattern LEVEL = Pattern.compile(
            "(?:等级|level)\\s*[:：]?\\s*(\\d+)(?:\\s*/\\s*(\\d+))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ENHANCE = Pattern.compile(
            "(?:强化|enhance|enhancement)\\s*[:：]?\\s*\\+?(\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BREAKTHROUGH = Pattern.compile(
            "(?:突破|breakthrough)\\s*[:：]?\\s*(.+?)阶段?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STAR = Pattern.compile(
            "(?:星级|star)\\s*[:：]?\\s*(.+)",
            Pattern.CASE_INSENSITIVE);

    private final String uniqueId;
    private int level;
    private int enhance;
    private int breakthrough;
    private int star;
    private boolean awakened;
    private long kills;
    private long wearSeconds;
    private String baseName;
    private List<String> managedLore = new ArrayList<String>();

    public ProgressionRecord(String uniqueId) {
        this.uniqueId = uniqueId == null ? "" : uniqueId.trim();
        this.level = 1;
    }

    public ProgressionRecord(String uniqueId, int level, int enhance,
                             int breakthrough, int star, boolean awakened,
                             long kills, long wearSeconds, String baseName) {
        this.uniqueId = uniqueId == null ? "" : uniqueId.trim();
        this.level = Math.max(1, level);
        this.enhance = Math.max(0, enhance);
        this.breakthrough = Math.max(0, breakthrough);
        this.star = Math.max(0, star);
        this.awakened = awakened;
        this.kills = Math.max(0L, kills);
        this.wearSeconds = Math.max(0L, wearSeconds);
        this.baseName = baseName == null ? "" : baseName;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    public int getEnhance() {
        return enhance;
    }

    public void setEnhance(int enhance) {
        this.enhance = Math.max(0, enhance);
    }

    public int getBreakthrough() {
        return breakthrough;
    }

    public void setBreakthrough(int breakthrough) {
        this.breakthrough = Math.max(0, breakthrough);
    }

    public int getStar() {
        return star;
    }

    public void setStar(int star) {
        this.star = Math.max(0, star);
    }

    public boolean isAwakened() {
        return awakened;
    }

    public void setAwakened(boolean awakened) {
        this.awakened = awakened;
    }

    public long getKills() {
        return kills;
    }

    public void setKills(long kills) { this.kills = Math.max(0L, kills); }
    public void addKills(long amount) {
        kills = Math.max(0L, kills + Math.max(0L, amount));
    }

    public long getWearSeconds() {
        return wearSeconds;
    }

    public void setWearSeconds(long wearSeconds) { this.wearSeconds = Math.max(0L, wearSeconds); }
    public void addWearSeconds(long amount) {
        wearSeconds = Math.max(0L, wearSeconds + Math.max(0L, amount));
    }

    public String getBaseName() {
        return baseName;
    }

    public List<String> getManagedLore() {
        return new ArrayList<String>(managedLore);
    }

    public void setManagedLore(List<String> lines) {
        managedLore = lines == null
                ? new ArrayList<String>() : new ArrayList<String>(lines);
    }

    public void setBaseName(String baseName) {
        this.baseName = baseName == null ? "" : baseName;
    }

    public ProgressionRecord copy() {
        ProgressionRecord copy = new ProgressionRecord(uniqueId, level, enhance, breakthrough, star,
                awakened, kills, wearSeconds, baseName);
        copy.setManagedLore(managedLore);
        return copy;
    }

    public static ProgressionRecord fromItem(String uniqueId, ItemStack item) {
        ProgressionRecord record = new ProgressionRecord(uniqueId);
        if (item == null) {
            return record;
        }
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                record.baseName = meta.getDisplayName();
            }
            if (meta != null && meta.hasLore()) {
                for (String line : meta.getLore()) {
                    String plain = ChatColor.stripColor(line == null ? "" : line).trim();
                    Matcher matcher = LEVEL.matcher(plain);
                    if (matcher.find()) {
                        record.level = parseInt(matcher.group(1), record.level);
                    }
                    matcher = ENHANCE.matcher(plain);
                    if (matcher.find()) {
                        record.enhance = parseInt(matcher.group(1), record.enhance);
                    }
                    matcher = BREAKTHROUGH.matcher(plain);
                    if (matcher.find()) {
                        record.breakthrough = parseStage(matcher.group(1));
                    }
                    matcher = STAR.matcher(plain);
                    if (matcher.find()) {
                        record.star = Math.max(0, matcher.group(1).trim().length());
                    }
                    if (plain.toLowerCase(Locale.US).contains("awakened")
                            || plain.contains("觉醒")) {
                        record.awakened = true;
                    }
                }
            }
        }
        return record;
    }

    public void save(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        section.set("unique-id", uniqueId);
        section.set("level", Integer.valueOf(level));
        section.set("enhance", Integer.valueOf(enhance));
        section.set("breakthrough", Integer.valueOf(breakthrough));
        section.set("star", Integer.valueOf(star));
        section.set("awakened", Boolean.valueOf(awakened));
        section.set("kills", Long.valueOf(kills));
        section.set("wear-seconds", Long.valueOf(wearSeconds));
        section.set("base-name", baseName);
        section.set("managed-lore", managedLore);
    }

    public static ProgressionRecord load(String uniqueId, ConfigurationSection section) {
        ProgressionRecord record = new ProgressionRecord(uniqueId);
        if (section == null) {
            return record;
        }
        record.level = Math.max(1, section.getInt("level", 1));
        record.enhance = Math.max(0, section.getInt("enhance", 0));
        record.breakthrough = Math.max(0, section.getInt("breakthrough", 0));
        record.star = Math.max(0, section.getInt("star", 0));
        record.awakened = section.getBoolean("awakened", false);
        record.kills = Math.max(0L, section.getLong("kills", 0L));
        record.wearSeconds = Math.max(0L, section.getLong("wear-seconds", 0L));
        record.baseName = section.getString("base-name", "");
        record.setManagedLore(section.getStringList("managed-lore"));
        return record;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int parseStage(String value) {
        String normalized = ChatColor.stripColor(value == null ? "" : value)
                .trim().toUpperCase(Locale.US);
        if (normalized.matches("\\d+")) {
            return Integer.parseInt(normalized);
        }
        int stage = 0;
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (Character.isLetterOrDigit(character) || Character.getType(character) == Character.OTHER_SYMBOL) {
                stage++;
            }
        }
        return Math.max(0, stage);
    }
}


