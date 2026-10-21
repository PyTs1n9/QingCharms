package net.pytsing.qingcharms.config;

import net.pytsing.qingcharms.util.YamlUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConfigManager {

    private final JavaPlugin plugin;
    private final File configFile;
    private final File guiFile;
    private final File classesFile;
    private YamlConfiguration guiConfiguration = new YamlConfiguration();
    private YamlConfiguration classesConfiguration = new YamlConfiguration();
    private boolean saveBlocked = true;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.guiFile = new File(plugin.getDataFolder(), "gui.yml");
        this.classesFile = new File(plugin.getDataFolder(), "classes.yml");
    }

    public void load() {
        ensureFiles();
        PreparedReload prepared = prepareReload();
        if (prepared.isFailed()) {
            return;
        }
        commitReload(prepared);
    }

    public PreparedReload prepareReload() {
        ensureFiles();

        YamlUtils.LoadResult configResult =
                YamlUtils.loadChecked(configFile, plugin.getLogger());
        if (configResult.isFailed()) {
            return PreparedReload.failed("config.yml could not be parsed");
        }
        YamlConfiguration configCandidate = configResult.getConfiguration();
        if (configCandidate.contains("charms")
                && configCandidate.getConfigurationSection("charms") == null) {
            return PreparedReload.failed("invalid charms section");
        }

        YamlUtils.LoadResult guiResult =
                YamlUtils.loadChecked(guiFile, plugin.getLogger());
        if (guiResult.isFailed()) {
            return PreparedReload.failed("gui.yml could not be parsed");
        }
        YamlConfiguration guiCandidate = guiResult.getConfiguration();
        if (guiCandidate.getConfigurationSection("gui") == null) {
            return PreparedReload.failed("invalid or missing gui section in gui.yml");
        }
        if (guiCandidate.contains("charm-bag")
                && guiCandidate.getConfigurationSection("charm-bag") == null) {
            return PreparedReload.failed("invalid charm-bag section in gui.yml");
        }

        YamlUtils.LoadResult classesResult =
                YamlUtils.loadChecked(classesFile, plugin.getLogger());
        if (classesResult.isFailed()) {
            return PreparedReload.failed("classes.yml could not be parsed");
        }
        YamlConfiguration classesCandidate = classesResult.getConfiguration();
        if (classesCandidate.contains("classes")
                && classesCandidate.getConfigurationSection("classes") == null) {
            return PreparedReload.failed("invalid classes section in classes.yml");
        }
        if (classesCandidate.contains("rules")
                && classesCandidate.getConfigurationSection("rules") == null) {
            return PreparedReload.failed("invalid rules section in classes.yml");
        }

        return PreparedReload.ready(configCandidate, guiCandidate, classesCandidate);
    }

    public boolean commitReload(PreparedReload prepared) {
        if (prepared == null || prepared.isFailed()) {
            return false;
        }
        try {
            plugin.getConfig().loadFromString(prepared.getConfiguration().saveToString());
            if (plugin.getConfig().contains("charms")
                    && plugin.getConfig().getConfigurationSection("charms") == null) {
                saveBlocked = true;
                return false;
            }

            YamlConfiguration nextGui = new YamlConfiguration();
            nextGui.loadFromString(prepared.getGuiConfiguration().saveToString());
            guiConfiguration = nextGui;

            YamlConfiguration nextClasses = new YamlConfiguration();
            nextClasses.loadFromString(prepared.getClassesConfiguration().saveToString());
            classesConfiguration = nextClasses;
            saveBlocked = false;
            return true;
        } catch (Exception exception) {
            saveBlocked = true;
            plugin.getLogger().severe("Could not apply configuration: " + exception.getMessage());
            return false;
        }
    }

    public void reload() {
        PreparedReload prepared = prepareReload();
        if (prepared.isFailed()) {
            saveBlocked = true;
            plugin.getLogger().severe(
                    "Configuration could not be loaded; keeping the existing configuration.");
            return;
        }
        commitReload(prepared);
    }

    public void save() {
        if (saveBlocked) {
            plugin.getLogger().severe(
                    "Skipping config.yml save because the existing configuration is invalid.");
            return;
        }
        try {
            YamlUtils.save(configFile, (YamlConfiguration) plugin.getConfig());
        } catch (IOException exception) {
            saveBlocked = true;
            plugin.getLogger().severe("Could not save config.yml as UTF-8; saving is disabled: "
                    + exception.getMessage());
        }
    }

    public boolean isSaveBlocked() {
        return saveBlocked;
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public ConfigurationSection getSection(String path) {
        return getConfig().getConfigurationSection(path);
    }

    public ConfigurationSection getGuiSection(String path) {
        return guiConfiguration.getConfigurationSection(path);
    }

    /** Current gui.yml snapshot, used to resolve slot types during charm reload. */
    public YamlConfiguration getGuiConfiguration() {
        return guiConfiguration;
    }

    /** Current classes.yml snapshot, holding the {@code classes} and {@code rules} sections. */
    public YamlConfiguration getClassesConfiguration() {
        return classesConfiguration;
    }

    public File getClassesFile() {
        return classesFile;
    }

    /** Section inside classes.yml, e.g. {@code classes} or {@code rules}. */
    public ConfigurationSection getBuildSection(String path) {
        return classesConfiguration.getConfigurationSection(path);
    }

    public String getString(String path, String fallback) {
        return getConfig().getString(path, fallback);
    }

    public boolean getBoolean(String path, boolean fallback) {
        return getConfig().getBoolean(path, fallback);
    }

    public List<String> getStringList(String path) {
        List<String> configured = getConfig().getStringList(path);
        return configured == null ? Collections.<String>emptyList() : new ArrayList<String>(configured);
    }

    private void ensureFiles() {
        ensureDefaultFile(configFile, "config.yml");
        migrateLegacyGuiSections();
        ensureDefaultFile(guiFile, "gui.yml");
        migrateLegacyBuildSections();
        ensureDefaultFile(classesFile, "classes.yml");
    }

    private void ensureDefaultFile(File file, String resource) {
        if (file.exists()) {
            return;
        }
        try {
            YamlUtils.copyResource(plugin, resource, file, false);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not create " + resource + ": " + exception.getMessage());
        }
    }

    private void migrateLegacyGuiSections() {
        if (!configFile.exists()) {
            return;
        }

        YamlUtils.LoadResult legacyResult = YamlUtils.loadChecked(configFile, plugin.getLogger());
        if (legacyResult.isFailed()) {
            return;
        }

        YamlConfiguration legacy = legacyResult.getConfiguration();
        boolean hasGui = legacy.isConfigurationSection("gui");
        boolean hasBag = legacy.isConfigurationSection("charm-bag");
        if (!hasGui && !hasBag && guiFile.exists()) {
            return;
        }

        YamlConfiguration migrated = new YamlConfiguration();
        if (guiFile.exists()) {
            YamlUtils.LoadResult guiResult = YamlUtils.loadChecked(guiFile, plugin.getLogger());
            if (guiResult.isFailed()) {
                plugin.getLogger().severe(
                        "Keeping legacy GUI settings in config.yml because gui.yml is invalid.");
                return;
            }
            migrated = guiResult.getConfiguration();
        }

        boolean guiChanged = false;
        if (hasGui && !migrated.isConfigurationSection("gui")) {
            migrated.set("gui", legacy.get("gui"));
            guiChanged = true;
        }
        if (hasBag && !migrated.isConfigurationSection("charm-bag")) {
            migrated.set("charm-bag", legacy.get("charm-bag"));
            guiChanged = true;
        }

        try {
            if (guiChanged) {
                YamlUtils.save(guiFile, migrated);
                plugin.getLogger().info("Migrated GUI settings from config.yml to gui.yml.");
            }

            boolean configChanged = false;
            if (hasGui && migrated.isConfigurationSection("gui")) {
                legacy.set("gui", null);
                configChanged = true;
            }
            if (hasBag && migrated.isConfigurationSection("charm-bag")) {
                legacy.set("charm-bag", null);
                configChanged = true;
            }
            if (configChanged) {
                YamlUtils.save(configFile, legacy);
                plugin.getLogger().info("Removed legacy GUI settings from config.yml.");
            }
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not migrate GUI settings to gui.yml: "
                    + exception.getMessage());
        }
    }

    /**
     * Moves legacy {@code classes} / {@code rules} sections from config.yml into classes.yml.
     *
     * <p>Existing classes.yml content always wins: the legacy section is only copied when the
     * target file does not already define it. Either way the section is stripped from
     * config.yml so the two files cannot drift apart.</p>
     */
    private void migrateLegacyBuildSections() {
        if (!configFile.exists()) {
            return;
        }

        YamlUtils.LoadResult legacyResult = YamlUtils.loadChecked(configFile, plugin.getLogger());
        if (legacyResult.isFailed()) {
            return;
        }

        YamlConfiguration legacy = legacyResult.getConfiguration();
        boolean hasClasses = legacy.isConfigurationSection("classes");
        boolean hasRules = legacy.isConfigurationSection("rules");
        if (!hasClasses && !hasRules) {
            return;
        }

        YamlConfiguration migrated;
        boolean targetExists = classesFile.exists();
        if (targetExists) {
            YamlUtils.LoadResult existing = YamlUtils.loadChecked(classesFile, plugin.getLogger());
            if (existing.isFailed()) {
                plugin.getLogger().severe(
                        "Keeping legacy classes/rules in config.yml because classes.yml is invalid.");
                return;
            }
            migrated = existing.getConfiguration();
        } else {
            YamlUtils.LoadResult defaults =
                    YamlUtils.loadResource(plugin, "classes.yml", plugin.getLogger());
            migrated = defaults.isOk()
                    ? defaults.getConfiguration() : new YamlConfiguration();
        }

        boolean buildChanged = false;
        // An existing classes.yml always wins; otherwise the legacy section replaces the template.
        if (hasClasses && (!targetExists || !migrated.isConfigurationSection("classes"))) {
            migrated.set("classes", legacy.get("classes"));
            buildChanged = true;
        }
        if (hasRules && (!targetExists || !migrated.isConfigurationSection("rules"))) {
            migrated.set("rules", legacy.get("rules"));
            buildChanged = true;
        }

        try {
            if (buildChanged) {
                YamlUtils.save(classesFile, migrated);
                plugin.getLogger().info("Migrated classes/rules from config.yml to classes.yml.");
            }

            boolean configChanged = false;
            if (hasClasses && migrated.isConfigurationSection("classes")) {
                legacy.set("classes", null);
                configChanged = true;
            }
            if (hasRules && migrated.isConfigurationSection("rules")) {
                legacy.set("rules", null);
                configChanged = true;
            }
            if (configChanged) {
                YamlUtils.save(configFile, legacy);
                plugin.getLogger().info("Removed classes/rules from config.yml.");
            }
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not migrate classes/rules to classes.yml: "
                    + exception.getMessage());
        }
    }

    public static final class PreparedReload {

        private final YamlConfiguration configuration;
        private final YamlConfiguration guiConfiguration;
        private final YamlConfiguration classesConfiguration;
        private final String error;

        private PreparedReload(YamlConfiguration configuration,
                               YamlConfiguration guiConfiguration,
                               YamlConfiguration classesConfiguration,
                               String error) {
            this.configuration = configuration;
            this.guiConfiguration = guiConfiguration;
            this.classesConfiguration = classesConfiguration;
            this.error = error;
        }

        private static PreparedReload ready(YamlConfiguration configuration,
                                            YamlConfiguration guiConfiguration,
                                            YamlConfiguration classesConfiguration) {
            return new PreparedReload(configuration, guiConfiguration, classesConfiguration, null);
        }

        private static PreparedReload failed(String error) {
            return new PreparedReload(null, null, null, error);
        }

        public boolean isFailed() {
            return error != null;
        }

        public String getError() {
            return error;
        }

        public YamlConfiguration getConfiguration() {
            return configuration;
        }

        public YamlConfiguration getGuiConfiguration() {
            return guiConfiguration;
        }

        public YamlConfiguration getClassesConfiguration() {
            return classesConfiguration;
        }
    }
}