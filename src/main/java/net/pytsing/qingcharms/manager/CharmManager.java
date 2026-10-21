package net.pytsing.qingcharms.manager;

import net.pytsing.qingcharms.QingCharmsPlugin;
import net.pytsing.qingcharms.api.CharmAPI;
import net.pytsing.qingcharms.api.EquipmentSnapshot;
import net.pytsing.qingcharms.api.ItemUniqueId;
import net.pytsing.qingcharms.api.event.CharmEquipEvent;
import net.pytsing.qingcharms.api.event.CharmReplaceEvent;
import net.pytsing.qingcharms.api.event.CharmTriggerNotifyEvent;
import net.pytsing.qingcharms.api.event.CharmUnequipEvent;
import net.pytsing.qingcharms.build.BuildResolution;
import net.pytsing.qingcharms.config.ConfigManager;
import net.pytsing.qingcharms.gui.GuiDefinition;
import net.pytsing.qingcharms.gui.GuiSlot;
import net.pytsing.qingcharms.model.CharmDefinition;
import net.pytsing.qingcharms.model.ItemIdentity;
import net.pytsing.qingcharms.model.CharmItemSignature;
import net.pytsing.qingcharms.model.CharmRarity;
import net.pytsing.qingcharms.model.CharmType;
import net.pytsing.qingcharms.model.PlayerData;
import net.pytsing.qingcharms.util.EventUtils;
import net.pytsing.qingcharms.util.InventoryUtils;
import net.pytsing.qingcharms.util.MessageUtils;
import net.pytsing.qingcharms.trigger.TriggerMessageService;
import net.pytsing.qingcharms.util.PathUtils;
import net.pytsing.qingcharms.util.YamlUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CharmManager implements CharmAPI {

    private static final int MAX_QC_ITEM_DEPTH = 8;
    private static final int MAX_QC_ITEM_FILES = 1000;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CharmItemAuth itemAuth;
    private volatile CharmRuntime runtime = CharmRuntime.empty();
    private CharmRuntimeBuilder building;
    private Map<File, YamlConfiguration> preparedDocuments =
            Collections.<File, YamlConfiguration>emptyMap();

    public CharmManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemAuth = new CharmItemAuth(plugin, configManager);
    }

    public void load() {
        PreparedReload prepared = prepareReload((YamlConfiguration) plugin.getConfig());
        if (prepared.isFailed()) {
            plugin.getLogger().severe("Could not load charm definitions: " + prepared.getError());
            return;
        }
        ReloadResult result = buildReload(prepared);
        if (result.isFailed()) {
            plugin.getLogger().severe("Could not build charm definitions: " + result.getError());
            return;
        }
        commitReload(result);
        plugin.getLogger().info("Loaded " + runtime.charms.size() + " charm(s).");
    }

    private void loadConfigCharms(ConfigurationSection section) {
        if (section == null) {
            plugin.getLogger().warning("No charms section found in config.yml.");
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection charmSection = section.getConfigurationSection(id);
            if (charmSection == null || !charmSection.getBoolean("enabled", false)) {
                continue;
            }

            try {
                ItemStack item = loadItem(id, charmSection);
                if (item == null || item.getType() == Material.AIR) {
                    plugin.getLogger().warning("Charm '" + id + "' has no valid item.");
                    continue;
                }
                registerCharm(id, item, charmSection, false);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Failed to load charm '" + id + "': "
                        + exception.getMessage());
            }
        }
    }

    private void loadQcItemFiles(ConfigurationSection settings) {
        File folder = getQcItemsFolder(settings);
        if (folder == null) {
            return;
        }
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create qcItems folder: " + folder.getAbsolutePath());
            return;
        }

        List<File> files = new ArrayList<File>();
        collectYamlFiles(folder, files, 0, new HashSet<String>());
        Collections.sort(files);
        for (File file : files) {
            String fileName = file.getName();
            int extension = fileName.lastIndexOf('.');
            String id = extension > 0 ? fileName.substring(0, extension) : fileName;
            if (id.trim().isEmpty()) {
                continue;
            }

            try {
                YamlConfiguration yaml = preparedDocuments.get(file);
                if (yaml == null) {
                    YamlUtils.LoadResult result = YamlUtils.loadChecked(file, plugin.getLogger());
                    if (result.isFailed()) {
                        continue;
                    }
                    yaml = result.getConfiguration();
                }
                ItemStack item = yaml.getItemStack("Item");
                if (item == null) {
                    item = yaml.getItemStack("item");
                }
                if (item == null || item.getType() == Material.AIR) {
                    plugin.getLogger().warning("Ignoring qcItems file without Item: "
                            + file.getAbsolutePath());
                    continue;
                }

                ConfigurationSection metadata = yaml.getConfigurationSection("charm");
                if (metadata == null) {
                    metadata = yaml;
                }
                if (!metadata.getBoolean("enabled", true)) {
                    continue;
                }
                registerCharm(id, item, metadata, true);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Failed to load qcItems charm '" + id + "': "
                        + exception.getMessage());
            }
        }
    }

    private void collectYamlFiles(File folder, List<File> files, int depth, Set<String> visited) {
        if (depth > MAX_QC_ITEM_DEPTH) {
            plugin.getLogger().warning("Skipping qcItems directory deeper than "
                    + MAX_QC_ITEM_DEPTH + ": " + folder.getAbsolutePath());
            return;
        }
        try {
            if (!visited.add(folder.getCanonicalPath())) {
                return;
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not resolve qcItems directory: "
                    + folder.getAbsolutePath());
            return;
        }
        File[] children = folder.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                if (!java.nio.file.Files.isSymbolicLink(child.toPath())) {
                    collectYamlFiles(child, files, depth + 1, visited);
                }
            } else if (child.isFile()) {
                if (java.nio.file.Files.isSymbolicLink(child.toPath())) {
                    continue;
                }
                String name = child.getName().toLowerCase(Locale.US);
                if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                    if (files.size() >= MAX_QC_ITEM_FILES) {
                        plugin.getLogger().warning("Ignoring qcItems files after the limit of "
                                + MAX_QC_ITEM_FILES + ".");
                        return;
                    }
                    files.add(child);
                }
            }
        }
    }

    public File getQcItemsFolder() {
        return getQcItemsFolder(plugin.getConfig().getConfigurationSection("settings"));
    }

    public File findQcItemFile(String charmId) {
        if (charmId == null || charmId.trim().isEmpty()) {
            return null;
        }
        File folder = getQcItemsFolder();
        if (folder == null) {
            return null;
        }
        List<File> files = new ArrayList<File>();
        collectYamlFiles(folder, files, 0, new HashSet<String>());
        Collections.sort(files);
        String expected = charmId.trim().toLowerCase(Locale.US);
        for (File file : files) {
            String name = file.getName();
            int extension = name.lastIndexOf('.');
            String id = extension > 0 ? name.substring(0, extension) : name;
            if (expected.equals(id.toLowerCase(Locale.US))) {
                return file;
            }
        }
        return null;
    }

    private File getQcItemsFolder(ConfigurationSection settings) {
        String configured = settings == null
                ? "qcItems" : settings.getString("qc-items-folder", "qcItems");
        return resolveDataPath(configured, "qcItems");
    }

    private void registerCharm(String id, ItemStack item, ConfigurationSection section, boolean fromQcItems) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        // A null display name means "do not compare names". Fabricating "&7<id>"
        // here used to make every item without a display name impossible to
        // match, including copies produced by /qc give.
        String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName() : null;
        String displayName = section.getString("display-name", itemName);
        List<String> lore = section.getStringList("lore");
        if (lore.isEmpty() && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            lore = item.getItemMeta().getLore();
        }


        CharmRarity rarity = parseRarity(section.getString("rarity", "COMMON"));
        String typeValue = section.getString("type", "NONE").trim();
        if (typeValue.isEmpty()) {
            typeValue = "NONE";
        }
        if ("NONE".equalsIgnoreCase(typeValue)) {
            String inferred = CharmTypeInference.infer(item, building.typeSlots);
            if (!inferred.isEmpty()) {
                plugin.getLogger().info("Charm '" + id + "' has no type; inferred '"
                        + inferred + "' from its Lore.");
                typeValue = inferred;
            }
        }
        CharmType type = CharmType.parse(typeValue);
        String equipLore = section.getString("equip-lore");
        String slot = typeValue;
        String permission = section.getString("permission",
                "qingcharms.charm." + id.toLowerCase(Locale.US));
        int data = resolveData(section, -1);
        if (data < 0) {
            data = resolveData(section.getConfigurationSection("item"), -1);
        }
        if (fromQcItems) {
            // The Item stack in a qcItems file already describes its own variant
            // (glass/wool colour, etc.). Letting a separate 'data' field override
            // it used to make the charm unmatchable against the very items
            // /qc give hands out, so the Item stack always wins here.
            int templateData = item.getType().getMaxDurability() > 0
                    ? -1 : item.getDurability();
            if (data >= 0 && data != templateData) {
                plugin.getLogger().warning("Charm '" + id + "' sets data " + data
                        + " but its Item uses "
                        + (templateData < 0 ? "no data" : "data " + templateData)
                        + "; the Item value is used. Remove 'data' from the charm"
                        + " section to silence this warning.");
            }
            data = templateData;
        }

        ConfigurationSection requirements =
                section.getConfigurationSection("requirements");
        int requiredLevel = requirements != null
                && requirements.contains("player-level")
                ? requirements.getInt("player-level", 0)
                : section.getInt("required-level", section.getInt("min-level", 0));
        requiredLevel = Math.max(0, requiredLevel);
        // Both spellings are supported and combined: a file that keeps classes
        // at the charm level (next to material/type) and only writes
        // requirements.player-level used to lose its class restriction
        // entirely.
        List<String> requiredClasses = new ArrayList<String>(section.getStringList("classes"));
        if (requirements != null) {
            for (String classId : requirements.getStringList("classes")) {
                if (!containsIgnoreCase(requiredClasses, classId)) {
                    requiredClasses.add(classId);
                }
            }
        }
        if (section.contains("tags")) {
            plugin.getLogger().warning("Charm '" + id + "' sets 'tags', which is no longer "
                    + "used: build tags come from classes.<id>.tags only. Remove the line.");
        }
        String key = id.toLowerCase(Locale.US);
        if (building.charms.containsKey(key)) {
            plugin.getLogger().warning("Duplicate charm ID '" + id
                    + "'; keeping the first definition.");
            return;
        }
        if (fromQcItems) {
            building.qcItemCharmIds.add(key);
        }
        CharmDefinition definition = new CharmDefinition(id, item.getType(), displayName, lore,
                withEquipLore(item, equipLore), type, equipLore,
                slot, null, (short) data, rarity, permission,
                requiredClasses, requiredLevel,
                section.getString("category", typeValue),
                section.getString("source-description", ""),
                section.getInt("level", 0));
        building.charms.put(key, definition);
        if (fromQcItems) {
            if ("NONE".equalsIgnoreCase(typeValue)) {
                plugin.getLogger().warning("Charm '" + id + "' has no equip type and cannot be equipped. "
                        + "Set 'charm.type' in its qcItems file.");
            } else if (!building.typeSlots.isEmpty()
                    && !CharmTypeInference.containsType(building.typeSlots, typeValue)) {
                plugin.getLogger().warning("Charm '" + id + "' uses type '" + typeValue
                        + "' but no equip slot in gui.yml uses that type; it cannot be equipped.");
            }
        }
    }

    /**
     * Slot types declared by equip/unequip slots in gui.yml, in declaration order.
     * Used to infer a charm type from its Lore when the file does not define one.
     */
    private List<String> resolveEquipSlotTypes(ConfigurationSection guiSection) {
        List<String> types = new ArrayList<String>();
        GuiDefinition definition = GuiDefinition.fromConfig(guiSection);
        if (definition == null) {
            return types;
        }
        for (GuiSlot slot : definition.getSlots()) {
            if (slot.requiresType() && slot.hasUsableType()
                    && !CharmTypeInference.containsType(types, slot.getType())) {
                types.add(slot.getType());
            }
        }
        return types;
    }

    public List<String> getEquipSlotTypes() {
        return runtime.equipSlotTypes;
    }

    private ItemStack loadItem(String id, ConfigurationSection section) {
        String itemFile = section.getString("item-file");
        if (itemFile != null && !itemFile.trim().isEmpty()) {
            File file = resolveItemFile(itemFile.trim());
            if (file == null) {
                return null;
            }
            if (!file.isFile()) {
                plugin.getLogger().warning("Charm '" + id + "' item-file does not exist: "
                        + file.getAbsolutePath());
                return null;
            }

            YamlConfiguration yaml = preparedDocuments.get(file);
            if (yaml == null) {
                YamlUtils.LoadResult result = YamlUtils.loadChecked(file, plugin.getLogger());
                if (result.isFailed()) {
                    return null;
                }
                yaml = result.getConfiguration();
            }
            ItemStack item = yaml.getItemStack("Item");
            if (item == null) {
                item = yaml.getItemStack("item");
            }
            int configuredData = resolveData(section, -1);
            if (item != null && configuredData >= 0) {
                item.setDurability((short) configuredData);
            }
            return withEquipLore(item, section.getString("equip-lore"));
        }

        ConfigurationSection itemSection = section.getConfigurationSection("item");
        if (itemSection == null) {
            itemSection = section.getConfigurationSection("meta");
            if (itemSection == null) {
                itemSection = section;
            }
        }

        String materialName = itemSection.getString("material", null);
        if (materialName == null || materialName.trim().isEmpty()) {
            materialName = itemSection.getString("type", null);
        }
        if (materialName == null || materialName.trim().isEmpty()) {
            materialName = section.getString("material", "");
        }
        Material material = parseMaterial(materialName);
        if (material == null) {
            return null;
        }

        int configuredData = resolveData(itemSection,
                resolveData(section, -1));
        int colonData = parseMaterialData(materialName);
        int data = configuredData >= 0 ? configuredData : colonData;
        if (materialName.indexOf(':') >= 0 && data < 0) {
            return null;
        }

        int amount = itemSection.getInt("amount", section.getInt("amount", 1));
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        if (data >= 0) {
            item.setDurability((short) data);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displayName = firstString(itemSection, section,
                    "display-name", "displayName");
            if (displayName != null) {
                meta.setDisplayName(MessageUtils.color(displayName));
            }

            List<String> lore = new ArrayList<String>(
                    firstStringList(itemSection, section, "lore"));
            String equipLore = firstString(itemSection, section, "equip-lore");
            if (equipLore != null && !equipLore.trim().isEmpty()
                    && !containsLoreLine(lore, equipLore)) {
                lore.add(equipLore);
            }
            if (!lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<String>();
                for (String line : lore) {
                    coloredLore.add(MessageUtils.color(line));
                }
                meta.setLore(coloredLore);
            }

            boolean unbreakable = itemSection.getBoolean("unbreakable",
                    itemSection.getBoolean("Unbreakable",
                            section.getBoolean("unbreakable",
                                    section.getBoolean("Unbreakable", false))));
            if (unbreakable) {
                setUnbreakable(meta);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean containsLoreLine(List<String> lore, String required) {
        String normalizedRequired = MessageUtils.color(required)
                .replace('\u00a7', '&').trim();
        for (String line : lore) {
            if (normalizedRequired.equals(MessageUtils.color(
                    ItemUniqueId.stripHiddenData(line))
                    .replace('\u00a7', '&').trim())) {
                return true;
            }
        }
        return false;
    }

    private ItemStack withEquipLore(ItemStack item, String equipLore) {
        if (item == null || item.getType() == Material.AIR
                || equipLore == null || equipLore.trim().isEmpty()) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        List<String> lore = meta.hasLore()
                ? new ArrayList<String>(meta.getLore())
                : new ArrayList<String>();
        if (!containsLoreLine(lore, equipLore)) {
            lore.add(MessageUtils.color(equipLore));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private File resolveItemFile(String path) {
        return resolveDataPath(path, null);
    }

    private File resolveDataPath(String path, String fallback) {
        String value = path == null || path.trim().isEmpty() ? fallback : path.trim();
        try {
            return PathUtils.resolveWithin(plugin.getDataFolder(), value);
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().warning("Ignoring unsafe path '" + value + "': "
                    + exception.getMessage());
            return null;
        }
    }

    private Material parseMaterial(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.US);
        int colon = normalized.indexOf(':');
        if (colon >= 0) {
            normalized = normalized.substring(0, colon);
        }
        if (normalized.isEmpty()) {
            return null;
        }

        try {
            Material material = Material.getMaterial(Integer.parseInt(normalized));
            if (material != null) {
                return material;
            }
        } catch (NumberFormatException ignored) {
            // Not a numeric material ID; fall back to Bukkit material names.
        }
        return Material.matchMaterial(normalized);
    }

    private String[] splitMaterialValue(String value) {
        String normalized = value == null ? "" : value.trim();
        int colon = normalized.indexOf(':');
        if (colon < 0) {
            return new String[] { normalized, null };
        }
        return new String[] {
                normalized.substring(0, colon).trim(),
                normalized.substring(colon + 1).trim()
        };
    }

    private int parseMaterialData(String value) {
        String[] parts = splitMaterialValue(value);
        if (parts[1] == null) {
            return -1;
        }
        try {
            return normalizeData(Integer.parseInt(parts[1]));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean containsIgnoreCase(List<String> values, String candidate) {
        if (values == null || candidate == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && value.trim().equalsIgnoreCase(candidate.trim())) {
                return true;
            }
        }
        return false;
    }

    private int resolveData(ConfigurationSection section, int fallback) {
        if (section == null) {
            return fallback;
        }
        if (section.contains("data")) {
            return normalizeData(section.getInt("data", fallback));
        }
        if (section.contains("id_data")) {
            return normalizeData(section.getInt("id_data", fallback));
        }
        if (section.contains("durability")) {
            return normalizeData(section.getInt("durability", fallback));
        }
        return fallback;
    }

    private int normalizeData(int value) {
        return value == -1 || value >= 0 && value <= Short.MAX_VALUE
                ? value : -1;
    }

    private String firstString(ConfigurationSection primary, ConfigurationSection fallback,
                               String... keys) {
        for (String key : keys) {
            String value = primary.getString(key);
            if (value != null) {
                return value;
            }
            value = fallback.getString(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private List<String> firstStringList(ConfigurationSection primary,
                                         ConfigurationSection fallback,
                                         String... keys) {
        for (String key : keys) {
            List<String> value = primary.getStringList(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
            value = fallback.getStringList(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return Collections.emptyList();
    }

    private void setUnbreakable(ItemMeta meta) {
        try {
            java.lang.reflect.Method method =
                    meta.getClass().getMethod("setUnbreakable", boolean.class);
            method.invoke(meta, Boolean.TRUE);
        } catch (Exception ignored) {
            // Older Spigot versions do not expose unbreakable metadata.
        }
    }

    private CharmRarity parseRarity(String value) {
        try {
            return CharmRarity.valueOf(value.toUpperCase(Locale.US));
        } catch (IllegalArgumentException exception) {
            return CharmRarity.COMMON;
        }
    }

    public void unload() {
        runtime = CharmRuntime.empty();
    }

    @Override
    public Optional<CharmDefinition> getCharm(String id) {
        return id == null
                ? Optional.<CharmDefinition>empty()
                : Optional.ofNullable(runtime.charms.get(id.trim().toLowerCase(Locale.US)));
    }

    @Override
    @Deprecated
    public Optional<CharmDefinition> getCharmByMaterial(Material material) {
        if (material == null) {
            return Optional.empty();
        }
        List<CharmDefinition> matches = runtime.lookup.byMaterial.get(material);
        return matches == null || matches.size() != 1
                ? Optional.<CharmDefinition>empty()
                : Optional.of(matches.get(0));
    }

    public boolean isQcItemCharm(String charmId) {
        return charmId != null
                && runtime.qcItemCharmIds.contains(charmId.trim().toLowerCase(Locale.US));
    }

    @Override
    public Optional<CharmDefinition> getCharmByItem(ItemStack item) {
        if (!itemAuth.isAuthentic(item)) {
            return Optional.empty();
        }
        CharmItemSignature signature = CharmItemSignature.from(item);
        String authenticatedId = itemAuth.getCharmId(item);
        // A signed token is authoritative. Resolve it first so a charm can never
        // be hidden by another definition that happens to share its appearance
        // (same material/data/name/lore) and sorts earlier in the lookup index.
        if (!authenticatedId.isEmpty()) {
            Optional<CharmDefinition> signed = getCharm(authenticatedId);
            if (signed.isPresent() && signed.get().matches(signature)) {
                return signed;
            }
        }
        Optional<CharmDefinition> match = findCharmByAppearance(signature);
        if (!match.isPresent()
                || (!authenticatedId.isEmpty()
                && !authenticatedId.equals(match.get().getId()))) {
            return Optional.empty();
        }
        return match;
    }

    public ItemStack createCharmInstance(CharmDefinition charm) {
        return charm == null ? null
                : itemAuth.issue(charm.getItemTemplate(), charm.getId());
    }

    private Optional<CharmDefinition> findCharmByAppearance(CharmItemSignature candidate) {
        if (candidate == null) {
            return Optional.empty();
        }
        return runtime.lookup.find(candidate);
    }


    @Override
    public Collection<CharmDefinition> getCharms() {
        return Collections.unmodifiableList(new ArrayList<CharmDefinition>(runtime.charms.values()));
    }

    @Override
    public boolean isCharm(ItemStack item) {
        return getCharmByItem(item).isPresent();
    }

    @Override
    public String getUniqueId(ItemStack item) {
        return ItemUniqueId.from(item);
    }

    @Override
    public ItemStack getEquippedItem(Player player, String slotId) {
        if (player == null || slotId == null || slotId.trim().isEmpty()) {
            return null;
        }
        return ((QingCharmsPlugin) plugin).getDataManager()
                .loadPlayer(player.getUniqueId()).getEquipped(slotId);
    }

    @Override
    public List<ItemStack> getEquippedCharms(Player player) {
        if (player == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<ItemStack>(
                ((QingCharmsPlugin) plugin).getDataManager()
                        .loadPlayer(player.getUniqueId()).getEquippedItems()));
    }

    @Override
    public Set<String> getOwnedCharmIds(Player player) {
        if (player == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<String>(
                ((QingCharmsPlugin) plugin).getDataManager()
                        .loadPlayer(player.getUniqueId()).getCharmIds()));
    }

    public PreparedReload prepareReload(YamlConfiguration candidate) {
        return prepareReload(candidate, configManager.getGuiConfiguration());
    }

    public PreparedReload prepareReload(YamlConfiguration candidate,
                                        YamlConfiguration guiCandidate) {
        if (candidate == null) {
            return PreparedReload.failed("candidate configuration is null");
        }
        if (candidate.contains("charms")
                && candidate.getConfigurationSection("charms") == null) {
            return PreparedReload.failed("invalid charms section");
        }

        Map<File, YamlConfiguration> documents =
                new LinkedHashMap<File, YamlConfiguration>();
        File folder = getQcItemsFolder(candidate.getConfigurationSection("settings"));
        if (folder == null) {
            return PreparedReload.failed("invalid qcItems folder");
        }
        if (!folder.exists() && !folder.mkdirs()) {
            return PreparedReload.failed("could not create qcItems folder");
        }

        List<File> files = new ArrayList<File>();
        collectYamlFiles(folder, files, 0, new HashSet<String>());
        Collections.sort(files);
        for (File file : files) {
            YamlUtils.LoadResult result = YamlUtils.loadChecked(file, plugin.getLogger());
            if (result.isFailed()) {
                return PreparedReload.failed("could not parse " + file.getName());
            }
            documents.put(file, result.getConfiguration());
        }

        ConfigurationSection charms = candidate.getConfigurationSection("charms");
        if (charms != null) {
            for (String id : charms.getKeys(false)) {
                ConfigurationSection charm = charms.getConfigurationSection(id);
                if (charm == null || !charm.getBoolean("enabled", false)) {
                    continue;
                }
                String itemFile = charm.getString("item-file");
                if (itemFile == null || itemFile.trim().isEmpty()) {
                    continue;
                }
                File file = resolveItemFile(itemFile.trim());
                if (file == null || !file.isFile()) {
                    return PreparedReload.failed("missing item-file for charm " + id);
                }
                YamlUtils.LoadResult result = YamlUtils.loadChecked(file, plugin.getLogger());
                if (result.isFailed()) {
                    return PreparedReload.failed("could not parse item-file for charm " + id);
                }
                documents.put(file, result.getConfiguration());
            }
        }
        return PreparedReload.ready(candidate, documents,
                resolveEquipSlotTypes(guiCandidate == null ? null
                        : guiCandidate.getConfigurationSection("gui")));
    }

    public ReloadResult buildReload(PreparedReload prepared) {
        if (prepared == null || prepared.isFailed()) {
            return ReloadResult.failed(prepared == null
                    ? "prepared reload is null" : prepared.getError());
        }
        Map<File, YamlConfiguration> previousDocuments = preparedDocuments;
        CharmRuntimeBuilder previousBuilder = building;
        preparedDocuments = prepared.documents;
        building = new CharmRuntimeBuilder();
        building.typeSlots.addAll(prepared.getTypeSlots());
        try {
            loadConfigCharms(prepared.getConfiguration().getConfigurationSection("charms"));
            loadQcItemFiles(prepared.getConfiguration().getConfigurationSection("settings"));
            return ReloadResult.ready(building.build());
        } catch (RuntimeException exception) {
            return ReloadResult.failed("could not build charm definitions: "
                    + exception.getMessage());
        } finally {
            preparedDocuments = previousDocuments;
            building = previousBuilder;
        }
    }

    public boolean commitReload(ReloadResult result) {
        if (result == null || result.isFailed()) {
            return false;
        }
        runtime = result.runtime;
        return true;
    }

    public static final class PreparedReload {

        private final YamlConfiguration configuration;
        private final Map<File, YamlConfiguration> documents;
        private final List<String> typeSlots;
        private final String error;

        private PreparedReload(YamlConfiguration configuration,
                               Map<File, YamlConfiguration> documents,
                               List<String> typeSlots,
                               String error) {
            this.configuration = configuration;
            this.documents = documents == null
                    ? Collections.<File, YamlConfiguration>emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<File, YamlConfiguration>(documents));
            this.typeSlots = typeSlots == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(typeSlots));
            this.error = error;
        }

        private static PreparedReload ready(YamlConfiguration configuration,
                                            Map<File, YamlConfiguration> documents,
                                            List<String> typeSlots) {
            return new PreparedReload(configuration, documents, typeSlots, null);
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

        public List<String> getTypeSlots() {
            return typeSlots;
        }
    }

    public static final class ReloadResult {

        private final CharmRuntime runtime;
        private final String error;

        private ReloadResult(CharmRuntime runtime, String error) {
            this.runtime = runtime;
            this.error = error;
        }

        private static ReloadResult ready(CharmRuntime runtime) {
            return new ReloadResult(runtime, null);
        }

        private static ReloadResult failed(String error) {
            return new ReloadResult(null, error);
        }

        public boolean isFailed() {
            return error != null;
        }

        public String getError() {
            return error;
        }
    }

    private static final class CharmRuntime {

        private final Map<String, CharmDefinition> charms;
        private final Set<String> qcItemCharmIds;
        private final LookupIndex lookup;
        private final List<String> equipSlotTypes;

        private CharmRuntime(Map<String, CharmDefinition> charms,
                             Set<String> qcItemCharmIds,
                             LookupIndex lookup,
                             List<String> equipSlotTypes) {
            this.charms = charms;
            this.qcItemCharmIds = qcItemCharmIds;
            this.lookup = lookup;
            this.equipSlotTypes = equipSlotTypes;
        }

        private static CharmRuntime empty() {
            return new CharmRuntime(Collections.<String, CharmDefinition>emptyMap(),
                    Collections.<String>emptySet(), LookupIndex.empty(),
                    Collections.<String>emptyList());
        }
    }

    private static final class CharmRuntimeBuilder {

        private final Map<String, CharmDefinition> charms =
                new LinkedHashMap<String, CharmDefinition>();
        private final Set<String> qcItemCharmIds = new LinkedHashSet<String>();
        private final List<String> typeSlots = new ArrayList<String>();

        private CharmRuntime build() {
            return new CharmRuntime(Collections.unmodifiableMap(
                    new LinkedHashMap<String, CharmDefinition>(charms)),
                    Collections.unmodifiableSet(new LinkedHashSet<String>(qcItemCharmIds)),
                    LookupIndex.build(charms.values()),
                    Collections.unmodifiableList(new ArrayList<String>(typeSlots)));
        }
    }

    private static final class ItemKey {

        private final Material material;
        private final short durability;

        private ItemKey(Material material, short durability) {
            this.material = material;
            this.durability = durability;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ItemKey)) {
                return false;
            }
            ItemKey that = (ItemKey) other;
            return material == that.material && durability == that.durability;
        }

        @Override
        public int hashCode() {
            return 31 * material.hashCode() + durability;
        }
    }

    private static final class LookupIndex {

        private final Map<ItemKey, List<CharmDefinition>> exact;
        private final Map<Material, List<CharmDefinition>> wildcard;
        private final Map<Material, List<CharmDefinition>> byMaterial;

        private LookupIndex(Map<ItemKey, List<CharmDefinition>> exact,
                            Map<Material, List<CharmDefinition>> wildcard,
                            Map<Material, List<CharmDefinition>> byMaterial) {
            this.exact = exact;
            this.wildcard = wildcard;
            this.byMaterial = byMaterial;
        }

        private static LookupIndex empty() {
            return new LookupIndex(Collections.<ItemKey, List<CharmDefinition>>emptyMap(),
                    Collections.<Material, List<CharmDefinition>>emptyMap(),
                    Collections.<Material, List<CharmDefinition>>emptyMap());
        }

        private static LookupIndex build(Collection<CharmDefinition> definitions) {
            List<CharmDefinition> sorted = new ArrayList<CharmDefinition>(definitions);
            Collections.sort(sorted, new Comparator<CharmDefinition>() {
                @Override
                public int compare(CharmDefinition left, CharmDefinition right) {
                    return left.getId().compareTo(right.getId());
                }
            });

            Map<ItemKey, List<CharmDefinition>> exact =
                    new LinkedHashMap<ItemKey, List<CharmDefinition>>();
            Map<Material, List<CharmDefinition>> wildcard =
                    new LinkedHashMap<Material, List<CharmDefinition>>();
            Map<Material, List<CharmDefinition>> byMaterial =
                    new LinkedHashMap<Material, List<CharmDefinition>>();

            for (CharmDefinition charm : sorted) {
                if (charm.getData() < 0) {
                    add(wildcard, charm.getMaterial(), charm);
                } else {
                    add(exact, new ItemKey(charm.getMaterial(), (short) charm.getData()), charm);
                }
                add(byMaterial, charm.getMaterial(), charm);
            }
            return new LookupIndex(unmodifiable(exact), unmodifiable(wildcard),
                    unmodifiable(byMaterial));
        }

        private static <K> void add(Map<K, List<CharmDefinition>> index, K key,
                                    CharmDefinition charm) {
            List<CharmDefinition> values = index.get(key);
            if (values == null) {
                values = new ArrayList<CharmDefinition>();
                index.put(key, values);
            }
            values.add(charm);
        }

        private static <K> Map<K, List<CharmDefinition>> unmodifiable(
                Map<K, List<CharmDefinition>> source) {
            Map<K, List<CharmDefinition>> copy =
                    new LinkedHashMap<K, List<CharmDefinition>>();
            for (Map.Entry<K, List<CharmDefinition>> entry : source.entrySet()) {
                copy.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }

        private Optional<CharmDefinition> find(CharmItemSignature candidate) {
            Optional<CharmDefinition> match = find(exact.get(new ItemKey(
                    candidate.getMaterial(), candidate.getDurability())), candidate);
            return match.isPresent()
                    ? match
                    : find(wildcard.get(candidate.getMaterial()), candidate);
        }

        private Optional<CharmDefinition> find(List<CharmDefinition> candidates,
                                               CharmItemSignature candidate) {
            if (candidates != null) {
                for (CharmDefinition charm : candidates) {
                    if (charm.matches(candidate)) {
                        return Optional.of(charm);
                    }
                }
            }
            return Optional.empty();
        }
    }

    @Override
    public EquipmentSnapshot getEquipmentSnapshot(Player player) {
        return ((QingCharmsPlugin) plugin).getGuiManager().getEquipmentSnapshot(player);
    }

    @Override
    public BuildResolution resolveBuild(Player player) {
        QingCharmsPlugin qingCharms = (QingCharmsPlugin) plugin;
        return qingCharms.getBuildResolver().resolveEquipped(player,
                qingCharms.getDataManager().loadPlayer(player.getUniqueId()));
    }

    @Override
    public Set<String> getActiveClasses(Player player) {
        return ((QingCharmsPlugin) plugin).getBuildResolver().getActiveClassIds(player);
    }

    @Override
    public Set<String> getRecommendedTags(Player player) {
        return ((QingCharmsPlugin) plugin).getBuildResolver().getRecommendedTags(player);
    }

    @Override
    public boolean equip(Player player, ItemStack item, String slotId) {
        EventUtils.requireMainThread();
        if (player == null || slotId == null || slotId.trim().isEmpty()) {
            return false;
        }
        Optional<CharmDefinition> optionalCharm = getCharmByItem(item);
        if (!optionalCharm.isPresent()) {
            return false;
        }

        String normalizedSlotId = slotId.trim();
        QingCharmsPlugin qingCharms = (QingCharmsPlugin) plugin;
        if (!qingCharms.getBuildResolver()
                .validateEquipment(player, item, normalizedSlotId).isAllowed()) {
            return false;
        }

        Inventory inventory = player.getInventory();
        int sourceIndex = findSourceIndex(inventory, item);
        if (sourceIndex < 0) {
            return false;
        }

        PlayerData playerData = qingCharms.getDataManager()
                .loadPlayer(player.getUniqueId());
        ItemStack previous = playerData.getEquipped(normalizedSlotId);
        boolean replacing = isItem(previous);
        if (replacing && previous.getAmount() <= 0) {
            return false;
        }
        if (replacing && !InventoryUtils.canAddAfterConsuming(
                inventory, previous, sourceIndex)) {
            return false;
        }

        String itemKey = "equipped:" + normalizedSlotId;
        String uniqueId = qingCharms.getProgressionService()
                .resolveSourceUniqueId(playerData, item, sourceIndex, itemKey);
        boolean consumesWholeSource = item.getAmount() <= 1;
        ItemStack newItem = item.clone();
        newItem.setAmount(1);
        CharmDefinition charm = optionalCharm.get();
        boolean allowed = replacing
                ? EventUtils.callCancellable(new CharmReplaceEvent(player, uniqueId,
                        charm.getId(), normalizedSlotId, previous, newItem))
                : EventUtils.callCancellable(new CharmEquipEvent(player, uniqueId,
                        charm.getId(), normalizedSlotId, null, newItem));
        if (!allowed) {
            return false;
        }

        ItemStack[] inventoryBefore = InventoryUtils.snapshot(inventory);
        Map<String, ItemStack> dataBefore = equipmentMap(playerData);
        Map<String, ItemIdentity> identityBefore = playerData.getItemIdentities();
        BuildResolution buildBefore =
                qingCharms.getBuildResolver().resolveEquipped(player, playerData);
        try {
            consumeSource(inventory, sourceIndex);
            if (replacing) {
                addOrRestore(inventory, inventoryBefore, previous.clone());
                rememberInventoryIdentity(inventory, playerData,
                        "equipped:" + normalizedSlotId, previous);
            }
            ItemStack bound = qingCharms.getProgressionService().bind(
                    playerData, player, newItem, itemKey, uniqueId);
            bound.setAmount(1);
            playerData.setEquipped(normalizedSlotId, bound);
            if (consumesWholeSource) {
                playerData.removeItemIdentity("inventory:" + sourceIndex);
            }
            qingCharms.getBuildResolver().refresh(
                    player, playerData, buildBefore, true);
            qingCharms.getDataManager().savePlayer(playerData);
            qingCharms.getGuiManager().invalidateEquipmentSnapshot(player.getUniqueId());
            return true;
        } catch (RuntimeException exception) {
            InventoryUtils.restore(inventory, inventoryBefore);
            restoreEquipment(playerData, dataBefore, identityBefore);
            plugin.getLogger().warning("Could not equip charm: " + exception.getMessage());
            return false;
        }
    }

    @Override
    public boolean unequip(Player player, String slotId) {
        EventUtils.requireMainThread();
        if (player == null || slotId == null || slotId.trim().isEmpty()) {
            return false;
        }

        QingCharmsPlugin qingCharms = (QingCharmsPlugin) plugin;
        PlayerData playerData = qingCharms.getDataManager()
                .loadPlayer(player.getUniqueId());
        String normalizedSlotId = slotId.trim();
        ItemStack oldItem = playerData.getEquipped(normalizedSlotId);
        if (!isItem(oldItem) || oldItem.getAmount() <= 0) {
            return false;
        }

        Inventory inventory = player.getInventory();
        if (!InventoryUtils.canAdd(inventory, oldItem)) {
            return false;
        }
        String storedId = playerData.getItemUniqueId(
                "equipped:" + normalizedSlotId, oldItem);
        String uniqueId = storedId.isEmpty() ? getUniqueId(oldItem) : storedId;
        Optional<CharmDefinition> charm = getCharmByItem(oldItem);
        boolean allowed = EventUtils.callCancellable(new CharmUnequipEvent(
                player, uniqueId,
                charm.isPresent() ? charm.get().getId() : "",
                normalizedSlotId, oldItem, null));
        if (!allowed) {
            return false;
        }

        ItemStack[] inventoryBefore = InventoryUtils.snapshot(inventory);
        Map<String, ItemStack> dataBefore = equipmentMap(playerData);
        Map<String, ItemIdentity> identityBefore = playerData.getItemIdentities();
        BuildResolution buildBefore =
                qingCharms.getBuildResolver().resolveEquipped(player, playerData);
        try {
            addOrRestore(inventory, inventoryBefore, oldItem.clone());
            rememberInventoryIdentity(inventory, playerData,
                    "equipped:" + normalizedSlotId, oldItem);
            playerData.removeEquipped(normalizedSlotId);
            qingCharms.getBuildResolver().refresh(
                    player, playerData, buildBefore, true);
            qingCharms.getDataManager().savePlayer(playerData);
            qingCharms.getGuiManager().invalidateEquipmentSnapshot(player.getUniqueId());
            return true;
        } catch (RuntimeException exception) {
            InventoryUtils.restore(inventory, inventoryBefore);
            restoreEquipment(playerData, dataBefore, identityBefore);
            plugin.getLogger().warning("Could not unequip charm: " + exception.getMessage());
            return false;
        }
    }

    @Override
    public boolean notifyTrigger(Player player, String charmId, String triggerType) {
        EventUtils.requireMainThread();
        if (player == null || triggerType == null || triggerType.trim().isEmpty()) {
            return false;
        }
        Optional<CharmDefinition> charm = getCharm(charmId);
        if (!charm.isPresent()) {
            return false;
        }


        TriggerMessageService messages =
                ((QingCharmsPlugin) plugin).getTriggerMessageService();
        if (!messages.isEligible(player, charm.get(), triggerType)) {
            return false;
        }
        if (!EventUtils.callCancellable(new CharmTriggerNotifyEvent(
                player, charm.get().getId(), triggerType.trim()))) {
            return false;
        }
        return messages.show(player, charm.get(), triggerType);
    }
    private int findSourceIndex(Inventory inventory, ItemStack expected) {
        if (inventory == null || !isItem(expected)) {
            return -1;
        }
        ItemStack[] contents = inventory.getContents();
        for (int index = 0; index < contents.length; index++) {
            ItemStack candidate = contents[index];
            if (isItem(candidate) && candidate.getAmount() > 0
                    && candidate.isSimilar(expected)) {
                return index;
            }
        }
        return -1;
    }

    private void consumeSource(Inventory inventory, int sourceIndex) {
        ItemStack source = inventory.getItem(sourceIndex);
        if (!isItem(source) || source.getAmount() <= 0) {
            throw new IllegalStateException("source item disappeared");
        }
        if (source.getAmount() > 1) {
            source.setAmount(source.getAmount() - 1);
            inventory.setItem(sourceIndex, source);
        } else {
            inventory.setItem(sourceIndex, null);
        }
    }

    private void rememberInventoryIdentity(Inventory inventory, PlayerData data,
                                           String fromKey, ItemStack item) {
        String uniqueId = data.getItemUniqueId(fromKey, item);
        if (uniqueId.isEmpty()) {
            return;
        }
        int inventorySlot = findSourceIndex(inventory, item);
        if (inventorySlot >= 0) {
            data.setItemIdentity("inventory:" + inventorySlot, uniqueId, item);
        }
    }

    private void addOrRestore(Inventory inventory, ItemStack[] before, ItemStack item) {
        Map<Integer, ItemStack> leftovers = inventory.addItem(item);
        if (!leftovers.isEmpty()) {
            InventoryUtils.restore(inventory, before);
            throw new IllegalStateException("inventory is full");
        }
    }

    private Map<String, ItemStack> equipmentMap(PlayerData playerData) {
        Map<String, ItemStack> result = new LinkedHashMap<String, ItemStack>();
        for (String slotId : playerData.getEquippedSlotIds()) {
            result.put(slotId, playerData.getEquipped(slotId));
        }
        return result;
    }

    private void restoreEquipment(PlayerData playerData, Map<String, ItemStack> previous,
                                  Map<String, ItemIdentity> identities) {
        playerData.restoreEquipmentState(previous, identities);
    }
    private static boolean isItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR;
    }

}
















