package net.pytsing.qingcharms.progression;

import net.pytsing.qingcharms.util.YamlUtils;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ProgressionConfig {

    private final JavaPlugin plugin;
    private final File file;
    private boolean enabled;
    private LevelSettings level;
    private EnhanceSettings enhance;
    private BreakthroughSettings breakthrough;
    private StarSettings star;
    private AwakeningSettings awakening;
    private Map<String, FeedbackSettings> feedback;
    private Map<String, String> loreTemplates;
    private List<String> activationLore;
    private List<String> normalizedActivationLore;

    public ProgressionConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "progression.yml");
        this.level = LevelSettings.disabled();
        this.enhance = EnhanceSettings.disabled();
        this.breakthrough = BreakthroughSettings.disabled();
        this.star = StarSettings.disabled();
        this.awakening = AwakeningSettings.disabled();
        this.feedback = Collections.emptyMap();
        this.loreTemplates = Collections.emptyMap();
        this.activationLore = defaultActivationLore();
        this.normalizedActivationLore = normalizeLore(defaultActivationLore());
        reload();
    }

    public void reload() {
        ensureFile();
        YamlUtils.LoadResult result = YamlUtils.loadChecked(file, plugin.getLogger());
        if (result.isFailed()) {
            enabled = false;
            plugin.getLogger().severe("progression.yml is invalid; progression is disabled.");
            return;
        }
        YamlConfiguration yaml = result.getConfiguration();
        ConfigurationSection root = yaml.getConfigurationSection("progression");
        if (root == null) {
            enabled = false;
            plugin.getLogger().warning("progression.yml has no progression section.");
            return;
        }

        enabled = root.getBoolean("enabled", true);
        try {
            level = LevelSettings.parse(yaml.getConfigurationSection("level"));
            enhance = EnhanceSettings.parse(yaml.getConfigurationSection("enhance"));
            breakthrough = BreakthroughSettings.parse(
                    yaml.getConfigurationSection("breakthrough"));
            star = StarSettings.parse(yaml.getConfigurationSection("star"));
            awakening = AwakeningSettings.parse(
                    yaml.getConfigurationSection("awakening"));
            feedback = parseFeedback(yaml.getConfigurationSection("feedback"));
            loreTemplates = parseTemplates(root.getConfigurationSection("lore"));
            activationLore = root.contains("activation-lore")
                    ? strings(root.get("activation-lore"))
                    : defaultActivationLore();
            normalizedActivationLore = normalizeLore(activationLore);
            validate();
        } catch (IllegalArgumentException exception) {
            enabled = false;
            plugin.getLogger().severe("Invalid progression.yml: " + exception.getMessage());
        }
    }

    private void validate() {
        if (level.maxLevel < 1) {
            throw new IllegalArgumentException("level.max-level must be at least 1");
        }
        if (enhance.max < 0) {
            throw new IllegalArgumentException("enhance.max cannot be negative");
        }
        if (enhance.successRate < 0.0D || enhance.successRate > 1.0D) {
            throw new IllegalArgumentException("enhance.success-rate must be between 0 and 1");
        }
        for (BreakthroughStage stage : breakthrough.stages) {
            if (stage.name.isEmpty()) {
                throw new IllegalArgumentException("breakthrough stage name cannot be empty");
            }
            if (stage.levelCap < 1 || stage.enhanceCap < 0) {
                throw new IllegalArgumentException("invalid caps for breakthrough " + stage.name);
            }
        }
        if (breakthrough.isEnabled() && breakthrough.stages.isEmpty()) {
            throw new IllegalArgumentException("breakthrough requires at least one stage");
        }
        if (star.isEnabled() && star.stages.isEmpty()) {
            throw new IllegalArgumentException("star requires at least one stage");
        }
        for (StarStage stage : star.stages) {
            if (stage.format.isEmpty()) {
                throw new IllegalArgumentException("star stage format cannot be empty");
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LevelSettings getLevel() {
        return level;
    }

    public EnhanceSettings getEnhance() {
        return enhance;
    }

    public BreakthroughSettings getBreakthrough() {
        return breakthrough;
    }

    public StarSettings getStar() {
        return star;
    }

    public AwakeningSettings getAwakening() {
        return awakening;
    }

    public String getLoreTemplate(String type, String fallback) {
        String value = loreTemplates.get(type == null
                ? "" : type.toLowerCase(java.util.Locale.US));
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    public List<String> getActivationLore() {
        return activationLore;
    }

    public boolean hasActivationLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return false;
        }
        List<String> lore = meta.getLore();
        if (lore == null) {
            return false;
        }
        for (String line : lore) {
            if (line != null && isActivationLore(line)) {
                return true;
            }
        }
        return false;
    }

    public boolean isActivationLore(String line) {
        String normalized = normalizeLoreLine(line);
        for (String marker : normalizedActivationLore) {
            if (!marker.isEmpty() && normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    public FeedbackSettings getFeedback(String action) {
        return feedback.get(action == null ? "" : action.toLowerCase(java.util.Locale.US));
    }

    private void ensureFile() {
        if (file.exists()) {
            return;
        }
        try {
            YamlUtils.copyResource(plugin, "progression.yml", file, false);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not create progression.yml: "
                    + exception.getMessage());
        }
    }

    private static Map<String, String> parseTemplates(ConfigurationSection root) {
        Map<String, String> result = new HashMap<String, String>();
        if (root != null) {
            for (String key : root.getKeys(false)) {
                result.put(key.toLowerCase(java.util.Locale.US), root.getString(key, ""));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, FeedbackSettings> parseFeedback(ConfigurationSection root) {
        Map<String, FeedbackSettings> result = new HashMap<String, FeedbackSettings>();
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section != null) {
                    result.put(key.toLowerCase(java.util.Locale.US),
                            FeedbackSettings.parse(section));
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public static final class LevelSettings {

        private final boolean enabled;
        private final int maxLevel;
        private final ResourceCost defaultCost;
        private final Map<Integer, ResourceCost> costs;
        private final Map<Integer, List<String>> unlockLore;

        private LevelSettings(boolean enabled, int maxLevel, ResourceCost defaultCost,
                              Map<Integer, ResourceCost> costs,
                              Map<Integer, List<String>> unlockLore) {
            this.enabled = enabled;
            this.maxLevel = maxLevel;
            this.defaultCost = defaultCost;
            this.costs = costs;
            this.unlockLore = unlockLore;
        }

        private static LevelSettings disabled() {
            return new LevelSettings(false, 1, new ResourceCost(null),
                    Collections.<Integer, ResourceCost>emptyMap(),
                    Collections.<Integer, List<String>>emptyMap());
        }

        private static LevelSettings parse(ConfigurationSection section) {
            if (section == null) {
                return disabled();
            }
            Map<Integer, ResourceCost> costs = new LinkedHashMap<Integer, ResourceCost>();
            ConfigurationSection costSections = section.getConfigurationSection("costs");
            if (costSections != null) {
                for (String key : costSections.getKeys(false)) {
                    ConfigurationSection cost = costSections.getConfigurationSection(key);
                    if (cost != null) {
                        costs.put(Integer.valueOf(parseInt(key)), ResourceCost.from(cost));
                    }
                }
            }
            return new LevelSettings(section.getBoolean("enabled", true),
                    section.getInt("max-level", 10),
                    ResourceCost.from(section.getConfigurationSection("cost")),
                    immutableCosts(costs),
                    parseLore(section.getConfigurationSection("unlock-lore")));
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getMaxLevel() {
            return maxLevel;
        }

        public ResourceCost getCost(int targetLevel) {
            ResourceCost configured = costs.get(Integer.valueOf(targetLevel));
            return configured == null ? defaultCost : configured;
        }

        public List<String> getUnlockLore(int level) {
            List<String> lore = unlockLore.get(Integer.valueOf(level));
            return lore == null ? Collections.<String>emptyList() : lore;
        }
    }

    public static final class EnhanceSettings {

        private final boolean enabled;
        private final int max;
        private final double successRate;
        private final ResourceCost defaultCost;
        private final Map<Integer, ResourceCost> costs;
        private final Map<Integer, List<String>> unlockLore;

        private EnhanceSettings(boolean enabled, int max, double successRate,
                                ResourceCost defaultCost,
                                Map<Integer, ResourceCost> costs,
                                Map<Integer, List<String>> unlockLore) {
            this.enabled = enabled;
            this.max = max;
            this.successRate = successRate;
            this.defaultCost = defaultCost;
            this.costs = costs;
            this.unlockLore = unlockLore;
        }

        private static EnhanceSettings disabled() {
            return new EnhanceSettings(false, 0, 1.0D, new ResourceCost(null),
                    Collections.<Integer, ResourceCost>emptyMap(),
                    Collections.<Integer, List<String>>emptyMap());
        }

        private static EnhanceSettings parse(ConfigurationSection section) {
            if (section == null) {
                return disabled();
            }
            Map<Integer, ResourceCost> costs = new LinkedHashMap<Integer, ResourceCost>();
            ConfigurationSection costSections = section.getConfigurationSection("costs");
            if (costSections != null) {
                for (String key : costSections.getKeys(false)) {
                    ConfigurationSection cost = costSections.getConfigurationSection(key);
                    if (cost != null) {
                        costs.put(Integer.valueOf(parseInt(key)), ResourceCost.from(cost));
                    }
                }
            }
            return new EnhanceSettings(section.getBoolean("enabled", true),
                    section.getInt("max", 10),
                    section.getDouble("success-rate", 1.0D),
                    ResourceCost.from(section.getConfigurationSection("cost")),
                    immutableCosts(costs),
                    parseLore(section.getConfigurationSection("unlock-lore")));
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getMax() {
            return max;
        }

        public double getSuccessRate() {
            return successRate;
        }

        public ResourceCost getCost(int targetEnhance) {
            ResourceCost configured = costs.get(Integer.valueOf(targetEnhance));
            return configured == null ? defaultCost : configured;
        }

        public List<String> getUnlockLore(int enhance) {
            List<String> lore = unlockLore.get(Integer.valueOf(enhance));
            return lore == null ? Collections.<String>emptyList() : lore;
        }
    }

    public static final class BreakthroughSettings {

        private final boolean enabled;
        private final List<BreakthroughStage> stages;

        private BreakthroughSettings(boolean enabled, List<BreakthroughStage> stages) {
            this.enabled = enabled;
            this.stages = Collections.unmodifiableList(new ArrayList<BreakthroughStage>(stages));
        }

        private static BreakthroughSettings disabled() {
            return new BreakthroughSettings(false, Collections.<BreakthroughStage>emptyList());
        }

        private static BreakthroughSettings parse(ConfigurationSection section) {
            if (section == null) {
                return disabled();
            }
            List<BreakthroughStage> stages = new ArrayList<BreakthroughStage>();
            List<Map<?, ?>> configured = section.getMapList("stages");
            for (Map<?, ?> map : configured) {
                stages.add(BreakthroughStage.parse(map));
            }
            return new BreakthroughSettings(section.getBoolean("enabled", true), stages);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public List<BreakthroughStage> getStages() {
            return stages;
        }

        public BreakthroughStage getStage(int index) {
            return index < 0 || index >= stages.size() ? null : stages.get(index);
        }
    }

    public static final class BreakthroughStage {

        private final String name;
        private final int levelCap;
        private final int enhanceCap;
        private final ResourceCost cost;
        private final List<String> unlockLore;
        private final String feedback;

        private BreakthroughStage(String name, int levelCap, int enhanceCap,
                                  ResourceCost cost, List<String> unlockLore,
                                  String feedback) {
            this.name = name;
            this.levelCap = levelCap;
            this.enhanceCap = enhanceCap;
            this.cost = cost;
            this.unlockLore = Collections.unmodifiableList(
                    new ArrayList<String>(unlockLore));
            this.feedback = feedback;
        }

        private static BreakthroughStage parse(Map<?, ?> map) {
            String name = string(map.get("name"), "");
            ResourceCost cost = ResourceCost.fromMap(mapOf(map.get("cost")));
            List<String> lore = strings(map.get("unlock-lore"));
            return new BreakthroughStage(name,
                    integer(map.get("level-cap"), 1),
                    integer(map.get("enhance-cap"), 0),
                    cost, lore,
                    string(map.get("feedback"), "breakthrough"));
        }

        public String getName() {
            return name;
        }

        public int getLevelCap() {
            return levelCap;
        }

        public int getEnhanceCap() {
            return enhanceCap;
        }

        public ResourceCost getCost() {
            return cost;
        }

        public List<String> getUnlockLore() {
            return unlockLore;
        }

        public String getFeedback() {
            return feedback;
        }
    }

    public static final class StarSettings {

        private final boolean enabled;
        private final int max;
        private final List<String> formats;
        private final List<StarStage> stages;

        private StarSettings(boolean enabled, int max, List<String> formats,
                             List<StarStage> stages) {
            this.enabled = enabled;
            this.max = Math.max(0, max);
            this.formats = Collections.unmodifiableList(new ArrayList<String>(formats));
            this.stages = Collections.unmodifiableList(new ArrayList<StarStage>(stages));
        }

        private static StarSettings disabled() {
            return new StarSettings(false, 0, Collections.<String>emptyList(),
                    Collections.<StarStage>emptyList());
        }

        private static StarSettings parse(ConfigurationSection section) {
            if (section == null) {
                return disabled();
            }
            List<StarStage> stages = new ArrayList<StarStage>();
            List<Map<?, ?>> configured = section.getMapList("stages");
            for (Map<?, ?> map : configured) {
                stages.add(StarStage.parse(map));
            }
            return new StarSettings(section.getBoolean("enabled", false),
                    section.getInt("max", stages.size()),
                    section.getStringList("formats"), stages);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getMax() {
            return max;
        }

        public List<String> getFormats() {
            return formats;
        }

        public List<StarStage> getStages() {
            return stages;
        }

        public StarStage getStage(int index) {
            return index < 0 || index >= max || index >= stages.size()
                    ? null : stages.get(index);
        }

        public String format(int star) {
            if (star <= 0) {
                return "";
            }
            int index = star - 1;
            return index < formats.size() ? formats.get(index) : repeat("★", star);
        }
    }

    public static final class StarStage {

        private final String format;
        private final int requiredBreakthrough;
        private final int levelCapBonus;
        private final int enhanceCapBonus;
        private final ResourceCost cost;
        private final String feedback;

        private StarStage(String format, int requiredBreakthrough,
                          int levelCapBonus, int enhanceCapBonus,
                          ResourceCost cost, String feedback) {
            this.format = format;
            this.requiredBreakthrough = requiredBreakthrough;
            this.levelCapBonus = levelCapBonus;
            this.enhanceCapBonus = enhanceCapBonus;
            this.cost = cost;
            this.feedback = feedback;
        }

        private static StarStage parse(Map<?, ?> map) {
            return new StarStage(string(map.get("format"), ""),
                    integer(map.get("required-breakthrough"), 0),
                    integer(map.get("level-cap-bonus"), 0),
                    integer(map.get("enhance-cap-bonus"), 0),
                    ResourceCost.fromMap(asMap(map.get("cost"))),
                    string(map.get("feedback"), "star"));
        }

        public String getFormat() {
            return format;
        }

        public int getRequiredBreakthrough() {
            return requiredBreakthrough;
        }

        public int getLevelCapBonus() {
            return levelCapBonus;
        }

        public int getEnhanceCapBonus() {
            return enhanceCapBonus;
        }

        public ResourceCost getCost() {
            return cost;
        }

        public String getFeedback() {
            return feedback;
        }
    }

    public static final class AwakeningSettings {

        private final boolean enabled;
        private final boolean oneTime;
        private final int requiredLevel;
        private final int requiredEnhance;
        private final int requiredBreakthrough;
        private final int requiredStar;
        private final ResourceCost cost;
        private final String displayName;
        private final List<String> unlockLore;
        private final String feedback;

        private AwakeningSettings(boolean enabled, boolean oneTime, int requiredLevel,
                                  int requiredEnhance, int requiredBreakthrough,
                                  int requiredStar, ResourceCost cost,
                                  String displayName, List<String> unlockLore,
                                  String feedback) {
            this.enabled = enabled;
            this.oneTime = oneTime;
            this.requiredLevel = requiredLevel;
            this.requiredEnhance = requiredEnhance;
            this.requiredBreakthrough = requiredBreakthrough;
            this.requiredStar = requiredStar;
            this.cost = cost;
            this.displayName = displayName;
            this.unlockLore = Collections.unmodifiableList(
                    new ArrayList<String>(unlockLore));
            this.feedback = feedback;
        }

        private static AwakeningSettings disabled() {
            return new AwakeningSettings(false, true, 0, 0, 0, 0,
                    new ResourceCost(null), "", Collections.<String>emptyList(), "awakening");
        }

        private static AwakeningSettings parse(ConfigurationSection section) {
            if (section == null) {
                return disabled();
            }
            ConfigurationSection requirements = section.getConfigurationSection("requirements");
            return new AwakeningSettings(section.getBoolean("enabled", false),
                    section.getBoolean("one-time", true),
                    requirements == null ? 0 : requirements.getInt("level", 0),
                    requirements == null ? 0 : requirements.getInt("enhance", 0),
                    requirements == null ? 0 : requirements.getInt("breakthrough", 0),
                    requirements == null ? 0 : requirements.getInt("star", 0),
                    ResourceCost.from(section.getConfigurationSection("cost")),
                    section.getString("display-name", ""),
                    strings(section.getList("unlock-lore")),
                    section.getString("feedback", "awakening"));
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isOneTime() {
            return oneTime;
        }

        public int getRequiredLevel() {
            return requiredLevel;
        }

        public int getRequiredEnhance() {
            return requiredEnhance;
        }

        public int getRequiredBreakthrough() {
            return requiredBreakthrough;
        }

        public int getRequiredStar() {
            return requiredStar;
        }

        public ResourceCost getCost() {
            return cost;
        }

        public String getDisplayName() {
            return displayName;
        }

        public List<String> getUnlockLore() {
            return unlockLore;
        }

        public String getFeedback() {
            return feedback;
        }
    }

    public static final class FeedbackSettings {

        private final boolean enabled;
        private final String sound;
        private final String particles;
        private final int count;
        private final double radius;
        private final String message;

        private FeedbackSettings(boolean enabled, String sound, String particles,
                                 int count, double radius, String message) {
            this.enabled = enabled;
            this.sound = sound;
            this.particles = particles;
            this.count = count;
            this.radius = radius;
            this.message = message;
        }

        private static FeedbackSettings parse(ConfigurationSection section) {
            return new FeedbackSettings(section.getBoolean("enabled", true),
                    section.getString("sound", ""),
                    section.getString("particles", ""),
                    section.getInt("count", 0),
                    section.getDouble("radius", 0.5D),
                    section.getString("message", ""));
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getSound() {
            return sound;
        }

        public String getParticles() {
            return particles;
        }

        public int getCount() {
            return count;
        }

        public double getRadius() {
            return radius;
        }

        public String getMessage() {
            return message;
        }
    }

    private static Map<Integer, ResourceCost> immutableCosts(Map<Integer, ResourceCost> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<Integer, ResourceCost>(values));
    }

    private static Map<Integer, List<String>> parseLore(ConfigurationSection root) {
        Map<Integer, List<String>> result = new LinkedHashMap<Integer, List<String>>();
        if (root != null) {
            for (String key : root.getKeys(false)) {
                result.put(Integer.valueOf(parseInt(key)), strings(root.getList(key)));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<String> defaultActivationLore() {
        return Collections.singletonList("可成长");
    }

    private static List<String> normalizeLore(List<String> values) {
        List<String> result = new ArrayList<String>();
        for (String value : values) {
            result.add(normalizeLoreLine(value));
        }
        return Collections.unmodifiableList(result);
    }

    private static String normalizeLoreLine(String value) {
        return value == null ? "" : ChatColor.stripColor(
                net.pytsing.qingcharms.util.MessageUtils.color(value))
                .trim().toLowerCase(Locale.US);
    }

    private static List<String> strings(Object value) {
        List<String> result = new ArrayList<String>();
        if (value instanceof List) {
            for (Object element : (List<?>) value) {
                if (element != null && !String.valueOf(element).trim().isEmpty()) {
                    result.add(String.valueOf(element));
                }
            }
        } else if (value != null && !String.valueOf(value).trim().isEmpty()) {
            result.add(String.valueOf(value));
        }
        return result;
    }

    private static Map<?, ?> mapOf(Object value) {
        return value instanceof Map ? (Map<?, ?>) value : Collections.emptyMap();
    }

    private static ConfigurationSection sectionOf(Object value) {
        return value instanceof ConfigurationSection ? (ConfigurationSection) value : null;
    }

    private static int integer(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map ? (Map<?, ?>) value : Collections.<Object, Object>emptyMap();
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid numeric key: " + value);
        }
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}






