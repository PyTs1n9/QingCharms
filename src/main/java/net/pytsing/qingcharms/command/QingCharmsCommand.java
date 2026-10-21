package net.pytsing.qingcharms.command;

import net.pytsing.qingcharms.QingCharmsPlugin;
import net.pytsing.qingcharms.api.ItemUniqueId;
import net.pytsing.qingcharms.build.EquipmentRequirementResult;
import net.pytsing.qingcharms.config.ConfigManager;
import net.pytsing.qingcharms.storage.BackupStatus;
import net.pytsing.qingcharms.storage.DataManager;
import net.pytsing.qingcharms.storage.SaveStatus;
import net.pytsing.qingcharms.manager.CharmManager;
import net.pytsing.qingcharms.model.CharmDefinition;
import net.pytsing.qingcharms.model.CharmItemSignature;
import net.pytsing.qingcharms.model.CharmRarity;
import net.pytsing.qingcharms.model.CharmType;
import net.pytsing.qingcharms.util.InventoryUtils;
import net.pytsing.qingcharms.util.ItemStackYaml;
import net.pytsing.qingcharms.util.MessageUtils;
import net.pytsing.qingcharms.util.PermissionUtils;
import net.pytsing.qingcharms.util.YamlUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.text.SimpleDateFormat;

public final class QingCharmsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = Collections.unmodifiableList(
            Arrays.asList("help", "list", "reload", "add", "set", "remove", "bag",
                    "on", "off", "give", "export", "gui", "build", "status", "save",
                    "backup", "check"));
    private static final List<String> MODIFY_FIELDS = Collections.unmodifiableList(
            Arrays.asList("material", "data", "rarity", "name", "permission", "enabled",
                    "lore", "type", "equip-lore"));
    private static final List<String> TYPE_NAMES = Collections.unmodifiableList(
            Arrays.asList("NONE", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"));
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");

    private final QingCharmsPlugin plugin;

    public QingCharmsCommand(QingCharmsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ("qingcharmsbag".equalsIgnoreCase(command.getName())) {
            return handleBag(sender);
        }
        if ("qingcharmsgui".equalsIgnoreCase(command.getName())) {
            if (!sender.hasPermission("qingcharms.gui")) {
                MessageUtils.send(sender, "command.gui.no-permission");
                return true;
            }
            if (!(sender instanceof org.bukkit.entity.Player)) {
                MessageUtils.send(sender, "command.gui.player-only");
                return true;
            }
            plugin.getGuiManager().open((org.bukkit.entity.Player) sender);
            return true;
        }
        if (!sender.hasPermission("qingcharms.command")) {
            MessageUtils.send(sender, "command.no-permission");
            return true;
        }

        String subCommand = args.length == 0 ? "help" : args[0].toLowerCase(Locale.US);
        switch (subCommand) {
            case "reload":
                return handleReload(sender);

            case "build":
                return handleBuild(sender);
            case "list":
                return handleList(sender);
            case "add":
                return handleAdd(sender, args);
            case "remove":
            case "delete":
                return handleDelete(sender, args);
            case "set":
            case "modify":
                return handleModify(sender, args);
            case "on":
            case "enable":
                return handleSetEnabled(sender, args, true);
            case "off":
            case "disable":
                return handleSetEnabled(sender, args, false);
            case "give":
                return handleGive(sender, args);
            case "export":
                return handleExport(sender, args);
            case "check":
                return handleCheck(sender);
            case "open":
                return handleOpenPlayerBag(sender, args);
            case "gui":
                return handleGui(sender);
            case "bag":
                return handleBag(sender);
            case "status":
                return handleStatus(sender);
            case "save":
                return handleSave(sender);
            case "backup":
                return handleBackup(sender);
            case "help":
            default:
                handleHelp(sender);
                return true;
        }
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("qingcharms.command.reload")) {
            MessageUtils.send(sender, "command.reload.no-permission");
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                ConfigManager.PreparedReload config =
                        plugin.getConfigManager().prepareReload();
                if (config.isFailed()) {
                    sendReloadResult(sender, false, config.getError());
                    return;
                }

                CharmManager.PreparedReload prepared =
                        plugin.getCharmManager().prepareReload(
                                config.getConfiguration(), config.getGuiConfiguration());
                if (prepared.isFailed()) {
                    sendReloadResult(sender, false, prepared.getError());
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        CharmManager.ReloadResult built =
                                plugin.getCharmManager().buildReload(prepared);
                        if (built.isFailed()) {
                            sendReloadResult(sender, false, built.getError());
                            return;
                        }
                        if (!plugin.getConfigManager().commitReload(config)
                                || !plugin.getCharmManager().commitReload(built)) {
                            sendReloadResult(sender, false, "runtime commit failed");
                            return;
                        }
                
                        MessageUtils.reload(plugin);
                        plugin.getTriggerMessageService().reload();
                        plugin.getBuildResolver().reload();
                        plugin.getProgressionService().reload();
                        plugin.getCharmBagManager().reload();
                        plugin.getBagGuiManager().refreshOpen();
                        plugin.getGuiManager().load();
                        plugin.getGuiManager().refreshOpen();
                        plugin.revalidateEquippedRequirements();
                        for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                            plugin.getProgressionService().refreshEquipment(online);
                        }
                        sendReloadResult(sender, true, null);
                    }
                });
            }
        });
        return true;
    }

    private void sendReloadResult(final CommandSender sender,
                                   final boolean success, final String detail) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (success) {
                    MessageUtils.send(sender, "command.reload.success");
                } else {
                    MessageUtils.send(sender, "command.reload.failed", detail);
                }
            }
        });
    }

    private boolean handleList(CommandSender sender) {
        MessageUtils.send(sender, "command.list.header");
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("charms");
        boolean printed = false;
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                boolean enabled = section.getBoolean("enabled", false);
                if (!enabled) {
                    continue;
                }
                String material = section.getString("material");
                if (material == null || material.trim().isEmpty()) {
                    String itemFile = section.getString("item-file");
                    material = itemFile == null || itemFile.trim().isEmpty() ? "?" : itemFile;
                }
                String rarity = section.getString("rarity", "COMMON");
                String type = section.getString("type", "NONE");
                String data = section.getString("data", section.getString("id_data"));
                if (data != null && !data.trim().isEmpty()) {
                    material = material + ":" + data.trim();
                }
                MessageUtils.send(sender, "command.list.entry", id, material,
                        rarity, type, MessageUtils.get(enabled
                                ? "command.list.entry-enabled"
                                : "command.list.entry-disabled"));
                ConfigurationSection attributes = section.getConfigurationSection("attributes");
                if (attributes != null && !attributes.getKeys(false).isEmpty()) {
                    MessageUtils.send(sender, "command.list.attributes",
                            String.join(", ", attributes.getKeys(false)));
                }
                printed = true;
            }
        }

        for (net.pytsing.qingcharms.model.CharmDefinition charm
                : plugin.getCharmManager().getCharms()) {
            ConfigurationSection configuredSection = root == null
                    ? null : root.getConfigurationSection(charm.getId());
            if (configuredSection != null && configuredSection.getBoolean("enabled", false)) {
                continue;
            }
            MessageUtils.send(sender, "command.list.entry", charm.getId(),
                    charm.getMaterial().name()
                            + (charm.getData() >= 0 ? ":" + charm.getData() : ""),
                    charm.getRarity().name(), charm.getTypeValue(),
                    MessageUtils.get("command.list.qc-items"));
            printed = true;
        }

        if (!printed) {
            MessageUtils.send(sender, "command.list.empty");
        }
        return true;
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("qingcharms.command.admin")) {
            MessageUtils.send(sender, "command.admin.no-permission");
            return true;
        }
        if (args.length < 3) {
            MessageUtils.send(sender, "command.add.usage");
            return true;
        }

        String id = args[1].toLowerCase(Locale.US);
        if (!ID_PATTERN.matcher(id).matches()) {
            MessageUtils.send(sender, "command.add.invalid-id");
            return true;
        }

        ConfigurationSection root = getConfigSection(sender);
        if (root == null) {
            return true;
        }
        if (root.contains(id)) {
            MessageUtils.send(sender, "command.add.already-exists", id);
            return true;
        }

        String materialValue = args[2].trim();
        Material material = parseMaterial(materialValue);
        if (material == null) {
            MessageUtils.send(sender, "command.add.invalid-material", materialValue);
            return true;
        }

        String[] materialParts = splitMaterialValue(materialValue);
        Integer data = materialParts[1] == null ? null : parseData(materialParts[1]);
        if (materialParts[1] != null && data == null) {
            MessageUtils.send(sender, "command.add.invalid-data",
                    materialParts[1], Short.MAX_VALUE);
            return true;
        }

        String typeName = "NONE";
        String rarityName = "COMMON";
        String displayName = "&7" + id;
        if (args.length >= 4) {
            int cursor = 3;
            if (cursor < args.length && !isValidRarity(args[cursor])) {
                typeName = args[cursor];
                cursor++;
            }
            if (cursor < args.length && isValidRarity(args[cursor])) {
                rarityName = args[cursor];
                cursor++;
            }
            if (cursor < args.length) {
                displayName = join(args, cursor);
            }
        }

        if (typeName == null || typeName.trim().isEmpty()) {
            typeName = "NONE";
        }
        typeName = typeName.trim();
        CharmRarity rarity = parseRarity(rarityName);
        if (rarity == null) {
            MessageUtils.send(sender, "command.add.invalid-rarity", rarityName);
            return true;
        }

        ConfigurationSection section = root.createSection(id);
        section.set("enabled", true);
        section.set("material", materialParts[0]);
        if (data != null) {
            section.set("data", data);
        }
        section.set("display-name", displayName);
        section.set("lore", Arrays.asList("&7青玉护符 · " + id));
        section.set("type", typeName);
        if (!"NONE".equalsIgnoreCase(typeName)) {
            section.set("lore", Arrays.asList("&7" + typeName + "护符",
                    "&8[护符槽位: " + typeName + "]"));
            section.set("equip-lore", "&8[护符槽位: " + typeName + "]");
        } else {
            section.set("equip-lore", "");
        }
        section.set("rarity", rarity.name());
        section.set("permission", "qingcharms.charm." + id);

        saveAndReload(sender, "command.add.success", id);
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("qingcharms.command.admin")) {
            MessageUtils.send(sender, "command.admin.no-permission");
            return true;
        }
        if (args.length != 2) {
            MessageUtils.send(sender, "command.delete.usage");
            return true;
        }

        ConfigurationSection root = getConfigSection(sender);
        if (root == null) {
            return true;
        }
        String id = args[1].toLowerCase(Locale.US);
        if (!root.contains(id)) {
            MessageUtils.send(sender, "command.not-found", id);
            return true;
        }

        root.set(id, null);
        saveAndReload(sender, "command.delete.success", id);
        return true;
    }

    private boolean handleModify(CommandSender sender, String[] args) {
        if (!sender.hasPermission("qingcharms.command.admin")) {
            MessageUtils.send(sender, "command.admin.no-permission");
            return true;
        }
        if (args.length < 4) {
            MessageUtils.send(sender, "command.modify.usage");
            MessageUtils.send(sender, "command.modify.fields");
            return true;
        }

        String id = args[1].toLowerCase(Locale.US);
        CharmTarget target = findCharmTarget(id);
        if (target == null) {
            MessageUtils.send(sender, "command.not-found", id);
            return true;
        }

        ConfigurationSection section = target.getSection();
        String field = args[2].toLowerCase(Locale.US);
        if ("material".equals(field)) {
            String materialValue = args[3].trim();
            Material material = parseMaterial(materialValue);
            if (material == null) {
                MessageUtils.send(sender, "command.modify.invalid-material",
                        materialValue);
                return true;
            }
            String[] materialParts = splitMaterialValue(materialValue);
            Integer data = materialParts[1] == null ? null : parseData(materialParts[1]);
            if (materialParts[1] != null && data == null) {
                MessageUtils.send(sender, "command.modify.invalid-data",
                        materialParts[1], Short.MAX_VALUE);
                return true;
            }
            section.set("material", materialParts[0]);
            if (data != null) {
                section.set("data", data);
            }
            target.updateItemMaterial(material, data);
        } else if ("data".equals(field)) {
            if ("clear".equalsIgnoreCase(args[3]) && args.length == 4) {
                section.set("data", null);
            } else {
                Integer data = parseData(args[3]);
                if (data == null) {
                    MessageUtils.send(sender, "command.modify.invalid-data-clear",
                            args[3], Short.MAX_VALUE);
                    return true;
                }
                section.set("data", data);
                target.updateItemData(data);
            }
        } else if ("type".equals(field)) {
            if ("clear".equalsIgnoreCase(args[3]) && args.length == 4) {
                section.set("type", "NONE");
            } else {
                String type = join(args, 3).trim();
                if (type.isEmpty()) {
                    MessageUtils.send(sender, "command.modify.invalid-type", args[3]);
                    return true;
                }
                section.set("type", type);
            }
        } else if ("equip-lore".equals(field)) {
            String previous = section.getString("equip-lore");
            String equipLore = "clear".equalsIgnoreCase(args[3]) && args.length == 4
                    ? "" : join(args, 3);
            section.set("equip-lore", equipLore);
            target.updateItemEquipLore(previous, equipLore);
        } else if ("rarity".equals(field)) {
            CharmRarity rarity = parseRarity(args[3].toUpperCase(Locale.US));
            if (rarity == null) {
                MessageUtils.send(sender, "command.modify.invalid-rarity", args[3]);
                return true;
            }
            section.set("rarity", rarity.name());
        } else if ("name".equals(field) || "display-name".equals(field)) {
            String displayName = join(args, 3);
            section.set("display-name", displayName);
            target.updateItemName(displayName);
        } else if ("permission".equals(field)) {
            section.set("permission", args[3]);
        } else if ("enabled".equals(field)) {
            Boolean enabled = parseBoolean(args[3]);
            if (enabled == null) {
                MessageUtils.send(sender, "command.modify.invalid-enabled");
                return true;
            }
            section.set("enabled", enabled);
        } else if ("lore".equals(field)) {
            String action = args[3].toLowerCase(Locale.US);
            List<String> lore = target.getEditableLore(section);
            if ("clear".equals(action)) {
                lore.clear();
            } else if ("set".equals(action)) {
                if (args.length < 5) {
                    MessageUtils.send(sender, "command.modify.lore-set-usage");
                    return true;
                }
                lore.clear();
                lore.add(join(args, 4));
            } else if ("add".equals(action)) {
                if (args.length < 5) {
                    MessageUtils.send(sender, "command.modify.lore-add-usage");
                    return true;
                }
                lore.add(join(args, 4));
            } else {
                MessageUtils.send(sender, "command.modify.invalid-lore-action");
                return true;
            }
            section.set("lore", lore);
            target.updateItemLore(lore);
        } else {
            MessageUtils.send(sender, "command.modify.unknown-field", field);
            MessageUtils.send(sender, "command.modify.fields");
            return true;
        }

        if (!saveCharmTarget(sender, target)) {
            return true;
        }
        plugin.getCharmManager().load();
        MessageUtils.send(sender, "command.modify.success", id);
        return true;
    }
    private boolean handleSetEnabled(CommandSender sender, String[] args, boolean enabled) {
        if (!sender.hasPermission("qingcharms.command.admin")) {
            MessageUtils.send(sender, "command.admin.no-permission");
            return true;
        }
        if (args.length != 2) {
            MessageUtils.send(sender, "command.toggle.usage",
                    enabled ? "enable" : "disable");
            return true;
        }

        ConfigurationSection root = getConfigSection(sender);
        if (root == null) {
            return true;
        }
        String id = args[1].toLowerCase(Locale.US);
        ConfigurationSection section = root.getConfigurationSection(id);
        if (section == null) {
            MessageUtils.send(sender, "command.not-found", id);
            return true;
        }

        section.set("enabled", enabled);
        saveAndReload(sender, enabled
                ? "command.toggle.enabled" : "command.toggle.disabled", id);
        return true;
    }

    private boolean handleExport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("qingcharms.command.admin")) {
            MessageUtils.send(sender, "command.admin.no-permission");
            return true;
        }
        if (!(sender instanceof org.bukkit.entity.Player)) {
            MessageUtils.send(sender, "command.export.player-only");
            return true;
        }
        if (args.length < 2 || args.length > 4) {
            MessageUtils.send(sender, "command.export.usage");
            return true;
        }

        String id = args[1].toLowerCase(Locale.US);
        if (!ID_PATTERN.matcher(id).matches()) {
            MessageUtils.send(sender, "command.export.invalid-id");
            return true;
        }

        boolean overwrite = false;
        String requestedType = null;
        for (int index = 2; index < args.length; index++) {
            String token = args[index] == null ? "" : args[index].trim();
            if ("-f".equalsIgnoreCase(token) || "force".equalsIgnoreCase(token)) {
                overwrite = true;
            } else if (requestedType == null && !token.isEmpty()) {
                requestedType = token;
            } else {
                MessageUtils.send(sender, "command.export.usage");
                return true;
            }
        }

        org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
        org.bukkit.inventory.ItemStack item = player.getItemInHand();
        if (item == null || item.getType() == Material.AIR) {
            MessageUtils.send(sender, "command.export.empty-hand");
            return true;
        }

        File folder = plugin.getCharmManager().getQcItemsFolder();
        if (folder == null) {
            MessageUtils.send(sender, "command.export.failed");
            return true;
        }
        File file = new File(folder, id + ".yml");
        boolean existed = file.exists();
        if (existed && !overwrite) {
            MessageUtils.send(sender, "command.export.already-exists", id);
            return true;
        }

        // Templates must not carry the previous instance signature, otherwise the
        // hidden ID/token would be baked into every /qc give copy.
        org.bukkit.inventory.ItemStack template =
                net.pytsing.qingcharms.api.ItemUniqueId.withoutIdentityLore(item.clone());
        String type = requestedType == null
                ? net.pytsing.qingcharms.manager.CharmTypeInference.infer(
                        template, plugin.getCharmManager().getEquipSlotTypes())
                : requestedType;

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Item", ItemStackYaml.serializeForExport(template));
        yaml.set("charm.enabled", true);
        if (type != null && !type.trim().isEmpty() && !"NONE".equalsIgnoreCase(type.trim())) {
            yaml.set("charm.type", type.trim());
        }
        yaml.set("charm.permission", "qingcharms.charm." + id);
        try {
            YamlUtils.save(file, yaml);
        } catch (java.io.IOException exception) {
            plugin.getLogger().severe("Could not export held item to "
                    + file.getAbsolutePath() + ": " + exception.getMessage());
            MessageUtils.send(sender, "command.export.failed");
            return true;
        }

        plugin.getCharmManager().load();
        MessageUtils.send(sender, existed
                ? "command.export.overwritten" : "command.export.success", id,
                file.getAbsolutePath());

        boolean usableType = type != null && !type.trim().isEmpty()
                && !"NONE".equalsIgnoreCase(type.trim());
        if (!usableType) {
            MessageUtils.send(sender, "command.export.no-type", id);
        } else {
            MessageUtils.send(sender, "command.export.type", type.trim());
            if (!net.pytsing.qingcharms.manager.CharmTypeInference
                    .loreContainsType(template, type.trim())) {
                MessageUtils.send(sender, "command.export.type-not-in-lore", type.trim());
            }
        }

        signHeldItem(player, id, template);
        return true;
    }

    /**
     * Explains why the held item is or is not recognized as a charm. Intended
     * for admins debugging qcItems files: it prints what the plugin sees
     * (material/data/name), whether the item is a signed instance, the matched
     * charm, or a field-by-field comparison against every charm of the same
     * material when nothing matches.
     */
    private boolean handleCheck(CommandSender sender) {
        if (!(sender instanceof org.bukkit.entity.Player)) {
            MessageUtils.send(sender, "command.check.player-only");
            return true;
        }
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
        ItemStack item = player.getItemInHand();
        if (item == null || item.getType() == Material.AIR) {
            MessageUtils.send(sender, "command.check.empty-hand");
            return true;
        }

        CharmItemSignature signature = CharmItemSignature.from(item);
        String name = signature.getNormalizedName();
        MessageUtils.send(sender, "command.check.header",
                item.getType().name() + ":" + item.getDurability(),
                name == null || name.isEmpty() ? "-" : name);
        MessageUtils.send(sender, ItemUniqueId.identityTokenFrom(item).isEmpty()
                ? "command.check.unsigned" : "command.check.signed");

        Optional<CharmDefinition> match = plugin.getCharmManager().getCharmByItem(item);
        if (match.isPresent()) {
            CharmDefinition charm = match.get();
            MessageUtils.send(sender, "command.check.matched",
                    charm.getId(), charm.getTypeValue());
            EquipmentRequirementResult requirement = plugin.getBuildResolver()
                    .validateEquipment(player, item, charm.getTypeValue());
            if (requirement.isAllowed()) {
                MessageUtils.send(sender, "command.check.passed");
            } else {
                MessageUtils.send(sender, "command.check.blocked",
                        MessageUtils.get(requirement.getMessageKey(),
                                requirement.getMessageArguments()));
            }
            return true;
        }

        MessageUtils.send(sender, "command.check.unmatched");
        boolean printed = false;
        for (CharmDefinition charm : plugin.getCharmManager().getCharms()) {
            if (charm.getMaterial() != item.getType()) {
                continue;
            }
            MessageUtils.send(sender, "command.check.candidate", charm.getId(),
                    dataMark(charm, signature),
                    mark(nameMatches(charm, signature)),
                    mark(loreMatches(charm, signature)));
            printed = true;
        }
        if (!printed) {
            MessageUtils.send(sender, "command.check.no-candidates");
        }
        return true;
    }

    private static String mark(boolean matched) {
        return MessageUtils.get(matched ? "command.check.ok" : "command.check.bad");
    }

    private static String dataMark(CharmDefinition charm, CharmItemSignature signature) {
        if (charm.getData() < 0) {
            return MessageUtils.get("command.check.data-any");
        }
        boolean matched = charm.getData() == signature.getDurability();
        return MessageUtils.get(matched ? "command.check.data-ok" : "command.check.data-bad",
                charm.getData(), String.valueOf(signature.getDurability()));
    }

    private static boolean nameMatches(CharmDefinition charm, CharmItemSignature signature) {
        String expected = charm.getNormalizedDisplayName();
        return expected == null || expected.equals(signature.getNormalizedName());
    }

    private static boolean loreMatches(CharmDefinition charm, CharmItemSignature signature) {
        if (charm.getLore().isEmpty()) {
            return true;
        }
        Set<String> required = new LinkedHashSet<String>();
        for (String line : charm.getLore()) {
            required.add(CharmItemSignature.normalizeMatch(line));
        }
        return signature.getNormalizedLore().containsAll(required);
    }

    /**
     * Re-issues the exported charm as a server-signed instance in the player's
     * hand, so an item built with /lore becomes recognizable and equippable
     * without an extra /qc give round trip.
     */
    private void signHeldItem(org.bukkit.entity.Player player, String id,
                              org.bukkit.inventory.ItemStack template) {
        net.pytsing.qingcharms.model.CharmDefinition charm =
                plugin.getCharmManager().getCharm(id).orElse(null);
        if (charm == null) {
            MessageUtils.send(player, "command.export.sign-missing", id);
            return;
        }
        if (!charm.matches(template)) {
            MessageUtils.send(player, "command.export.sign-mismatch", id);
            return;
        }

        org.bukkit.inventory.ItemStack signed =
                plugin.getCharmManager().createCharmInstance(charm);
        if (signed == null || signed.getType() == Material.AIR) {
            MessageUtils.send(player, "command.export.sign-missing", id);
            return;
        }

        org.bukkit.inventory.ItemStack current = player.getItemInHand();
        int amount = current == null ? 1 : current.getAmount();
        if (amount <= 1) {
            player.setItemInHand(signed);
        } else {
            org.bukkit.inventory.ItemStack rest = current.clone();
            rest.setAmount(amount - 1);
            player.setItemInHand(rest);
            java.util.Map<Integer, org.bukkit.inventory.ItemStack> leftovers =
                    player.getInventory().addItem(signed);
            if (!leftovers.isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), signed);
            }
        }
        player.updateInventory();
        MessageUtils.send(player, "command.export.signed");
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("qingcharms.command.give")) {
            MessageUtils.send(sender, "command.give.no-permission");
            return true;
        }
        if (args.length < 2 || args.length > 3) {
            MessageUtils.send(sender, "command.give.usage");
            return true;
        }

        net.pytsing.qingcharms.model.CharmDefinition charm =
                plugin.getCharmManager().getCharm(args[1]).orElse(null);
        if (charm == null) {
            MessageUtils.send(sender, "command.not-found", args[1]);
            return true;
        }
        org.bukkit.entity.Player target = args.length == 3
                ? org.bukkit.Bukkit.getPlayerExact(args[2])
                : (sender instanceof org.bukkit.entity.Player
                        ? (org.bukkit.entity.Player) sender : null);
        if (target == null) {
            MessageUtils.send(sender, "command.give.player-offline");
            return true;
        }
        if (!PermissionUtils.hasCharmPermission(target, charm.getPermission())) {
            MessageUtils.send(sender, "command.give.target-no-permission",
                    target.getName());
            return true;
        }
        org.bukkit.inventory.ItemStack item =
                plugin.getCharmManager().createCharmInstance(charm);
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            MessageUtils.send(sender, "command.give.no-item-template");
            return true;
        }
        if (!InventoryUtils.canAdd(target.getInventory(), item)) {
            MessageUtils.send(sender, "command.give.no-space", target.getName());
            return true;
        }

        org.bukkit.inventory.ItemStack[] before =
                InventoryUtils.snapshot(target.getInventory());
        try {
            java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> leftovers =
                    target.getInventory().addItem(item.clone());
            if (!leftovers.isEmpty()) {
                InventoryUtils.restore(target.getInventory(), before);
                MessageUtils.send(sender, "command.give.no-space", target.getName());
                return true;
            }
        } catch (RuntimeException exception) {
            InventoryUtils.restore(target.getInventory(), before);
            plugin.getLogger().warning("Could not give charm item: " + exception.getMessage());
            MessageUtils.send(sender, "command.give.no-space", target.getName());
            return true;
        }

        MessageUtils.send(sender, "command.give.success",
                target.getName(), charm.getId());
        return true;
    }

    private boolean handleGui(CommandSender sender) {
        if (!sender.hasPermission("qingcharms.gui")) {
            MessageUtils.send(sender, "command.gui.no-permission");
            return true;
        }
        if (!(sender instanceof org.bukkit.entity.Player)) {
            MessageUtils.send(sender, "command.gui.player-only");
            return true;
        }
        plugin.getGuiManager().open((org.bukkit.entity.Player) sender);
        return true;
    }

    private boolean handleBag(CommandSender sender) {
        if (!sender.hasPermission("qingcharms.bag")) {
            MessageUtils.send(sender, "charm-bag.no-permission");
            return true;
        }
        if (!(sender instanceof org.bukkit.entity.Player)) {
            MessageUtils.send(sender, "command.gui.player-only");
            return true;
        }
        plugin.getBagGuiManager().open((org.bukkit.entity.Player) sender);
        return true;
    }

    private boolean handleOpenPlayerBag(CommandSender sender, String[] args) {
        if (!sender.hasPermission("qingcharms.command.admin")) {
            MessageUtils.send(sender, "command.open.no-permission");
            return true;
        }
        if (!(sender instanceof org.bukkit.entity.Player)) {
            MessageUtils.send(sender, "command.open.player-only");
            return true;
        }
        if (args.length != 2) {
            MessageUtils.send(sender, "command.open.usage");
            return true;
        }

        org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            MessageUtils.send(sender, "command.open.player-offline", args[1]);
            return true;
        }
        if (plugin.getBagGuiManager().openFor(
                (org.bukkit.entity.Player) sender, target)) {
            MessageUtils.send(sender, "command.open.success", target.getName());
        }
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        if (!sender.hasPermission("qingcharms.command.status")) {
            MessageUtils.send(sender, "command.status.no-permission");
            return true;
        }

        DataManager data = plugin.getDataManager();
        SaveStatus save = data.getLastSaveStatus();
        BackupStatus backup = data.getLastBackupStatus();
        MessageUtils.send(sender, "command.status.header",
                plugin.getDescription().getVersion());
        MessageUtils.send(sender, "command.status.charms",
                plugin.getAPI().getCharms().size());
        MessageUtils.send(sender, "command.status.players",
                data.getCacheSize(), data.getDirtyCount());
        MessageUtils.send(sender, "command.status.last-save",
                formatTime(save.getTimestamp()), formatDuration(save),
                save.getTimestamp() <= 0L ? "未执行" : save.isSuccessful() ? "成功" : "失败", save.getMessage());
        MessageUtils.send(sender, "command.status.last-backup",
                formatTime(backup.getTimestamp()), formatDuration(backup),
                backup.getTimestamp() <= 0L ? "未执行" : backup.isSuccessful() ? "成功" : "失败", backup.getMessage());
        MessageUtils.send(sender, "command.status.read-only",
                data.isReadOnly() ? "是" : "否");
        MessageUtils.send(sender, "command.status.storage",
                formatBytes(data.getStoredYamlBytes()), data.getBackupCount(),
                formatBytes(data.getDiskFreeBytes()));
        return true;
    }

    private boolean handleSave(CommandSender sender) {
        if (!sender.hasPermission("qingcharms.command.save")) {
            MessageUtils.send(sender, "command.save.no-permission");
            return true;
        }
        if (plugin.getDataManager().isReadOnly()) {
            MessageUtils.send(sender, "command.save.read-only");
            return true;
        }

        plugin.getDataManager().saveAllNow();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                final SaveStatus status = plugin.getDataManager().flushPending();
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (status.isSuccessful()) {
                            MessageUtils.send(sender, "command.save.success",
                                    status.getPlayerCount(), formatDuration(status));
                        } else {
                            MessageUtils.send(sender, "command.save.failed", status.getMessage());
                        }
                    }
                });
            }
        });
        return true;
    }

    private boolean handleBackup(CommandSender sender) {
        if (!sender.hasPermission("qingcharms.command.backup")) {
            MessageUtils.send(sender, "command.backup.no-permission");
            return true;
        }
        if (plugin.getDataManager().isReadOnly()) {
            MessageUtils.send(sender, "command.backup.read-only");
            return true;
        }

        plugin.getDataManager().saveAllNow();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                final SaveStatus save = plugin.getDataManager().flushPending();
                final BackupStatus backup = save.isSuccessful()
                        ? plugin.getDataManager().backupNow()
                        : new BackupStatus(System.currentTimeMillis(), 0L, false,
                                null, "保存失败，已取消备份");
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (backup.isSuccessful()) {
                            MessageUtils.send(sender, "command.backup.success",
                                    backup.getFile() == null
                                            ? backup.getMessage()
                                            : backup.getFile().getName(),
                                    formatDuration(backup),
                                    plugin.getDataManager().getBackupCount());
                        } else {
                            MessageUtils.send(sender, "command.backup.failed", backup.getMessage());
                        }
                    }
                });
            }
        });
        return true;
    }

    private static String formatTime(long timestamp) {
        if (timestamp <= 0L) {
            return "从未";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(timestamp));
    }

    private static String formatDuration(SaveStatus status) {
        return formatMillis(status.getDurationMillis());
    }

    private static String formatDuration(BackupStatus status) {
        return formatMillis(status.getDurationMillis());
    }

    private static String formatMillis(double millis) {
        return String.format(Locale.US, "%.2f ms", millis);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = { "KB", "MB", "GB", "TB" };
        int unit = -1;
        do {
            value /= 1024.0D;
            unit++;
        } while (value >= 1024.0D && unit < units.length - 1);
        return String.format(Locale.US, "%.2f %s", value, units[unit]);
    }
    private boolean handleBuild(CommandSender sender) {
        if (!(sender instanceof org.bukkit.entity.Player)) {
            MessageUtils.send(sender, "command.gui.player-only");
            return true;
        }
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
        java.util.List<String> classNames = plugin.getBuildResolver().getActiveClassNames(player);
        java.util.Set<String> tags = plugin.getAPI().getRecommendedTags(player);
        MessageUtils.send(sender, "command.build.header");
        MessageUtils.send(sender, "command.build.classes",
                classNames.isEmpty() ? MessageUtils.get("command.build.none")
                        : String.join(", ", classNames));
        MessageUtils.send(sender, "command.build.tags",
                tags.isEmpty() ? MessageUtils.get("command.build.none")
                        : String.join(", ", tags));
        net.pytsing.qingcharms.build.BuildResolution resolution =
                plugin.getAPI().resolveBuild(player);
        java.util.List<String> ruleNames = new ArrayList<String>();
        for (net.pytsing.qingcharms.build.BuildRule rule
                : resolution.getSnapshot().getActiveRules()) {
            ruleNames.add(rule.getName());
        }
        MessageUtils.send(sender, "command.build.rules",
                ruleNames.isEmpty() ? MessageUtils.get("command.build.none")
                        : String.join(", ", ruleNames));
        return true;
    }

    private void handleHelp(CommandSender sender) {
        MessageUtils.sendHelp(sender, "command.help.header");
        MessageUtils.sendHelp(sender, "command.help.author");
        MessageUtils.sendHelp(sender, "command.help.help");
        MessageUtils.sendHelp(sender, "command.help.list");
        MessageUtils.sendHelp(sender, "command.help.reload");
        MessageUtils.sendHelp(sender, "command.help.status");
        MessageUtils.sendHelp(sender, "command.help.gui");
        MessageUtils.sendHelp(sender, "command.help.bag");
        if (sender.hasPermission("qingcharms.command.admin")) {
            MessageUtils.sendHelp(sender, "command.help.open");
        }
        MessageUtils.sendHelp(sender, "command.help.build");
        MessageUtils.sendHelp(sender, "command.help.check");

        if (sender.hasPermission("qingcharms.command.admin")) {
            MessageUtils.sendHelp(sender, "command.help.add");
            MessageUtils.sendHelp(sender, "command.help.delete");
            MessageUtils.sendHelp(sender, "command.help.modify");
            MessageUtils.sendHelp(sender, "command.help.toggle");
            MessageUtils.sendHelp(sender, "command.help.give");
            MessageUtils.sendHelp(sender, "command.help.export");
            MessageUtils.sendHelp(sender, "command.help.save");
            MessageUtils.sendHelp(sender, "command.help.backup");
            MessageUtils.sendHelp(sender, "command.help.types");
            MessageUtils.sendHelp(sender, "command.help.aliases");
        }
    }

    private ConfigurationSection getConfigSection(CommandSender sender) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("charms");
        if (root == null) {
            root = plugin.getConfig().createSection("charms");
        }
        return root;
    }

    private void saveAndReload(CommandSender sender, String messageKey, Object... args) {
        plugin.getConfigManager().save();
        plugin.getCharmManager().load();
        MessageUtils.send(sender, messageKey, args);
    }

    private CharmTarget findCharmTarget(String id) {
        String key = id.toLowerCase(Locale.US);
        if (plugin.getCharmManager().isQcItemCharm(key)) {
            CharmTarget target = loadQcItemTarget(key);
            if (target != null) {
                return target;
            }
        }

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("charms");
        ConfigurationSection section = root == null ? null : root.getConfigurationSection(key);
        if (section != null) {
            return CharmTarget.config(section);
        }
        return loadQcItemTarget(key);
    }

    private CharmTarget loadQcItemTarget(String id) {
        File file = plugin.getCharmManager().findQcItemFile(id);
        if (file == null) {
            return null;
        }
        YamlUtils.LoadResult result = YamlUtils.loadChecked(file, plugin.getLogger());
        if (result.isFailed()) {
            return null;
        }
        try {
            return CharmTarget.qcItem(file, result.getConfiguration());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not read qcItems charm '"
                    + id + "': " + exception.getMessage());
            return null;
        }
    }

    private boolean saveCharmTarget(CommandSender sender, CharmTarget target) {
        try {
            target.save(plugin.getConfigManager());
        } catch (java.io.IOException | RuntimeException exception) {
            plugin.getLogger().severe("Could not save charm definition: "
                    + exception.getMessage());
            MessageUtils.send(sender, "command.modify.save-failed");
            return false;
        }
        return true;
    }

    private static final class CharmTarget {

        private final ConfigurationSection section;
        private final File file;
        private final YamlConfiguration yaml;
        private final String itemKey;
        private final org.bukkit.inventory.ItemStack item;
        private boolean itemChanged;

        private CharmTarget(ConfigurationSection section, File file,
                            YamlConfiguration yaml, String itemKey,
                            org.bukkit.inventory.ItemStack item) {
            this.section = section;
            this.file = file;
            this.yaml = yaml;
            this.itemKey = itemKey;
            this.item = item;
        }

        private static CharmTarget config(ConfigurationSection section) {
            return new CharmTarget(section, null, null, null, null);
        }

        private static CharmTarget qcItem(File file, YamlConfiguration yaml) {
            String itemKey = yaml.contains("Item") ? "Item"
                    : yaml.contains("item") ? "item" : null;
            if (itemKey == null) {
                throw new IllegalStateException("missing Item");
            }
            org.bukkit.inventory.ItemStack item = yaml.getItemStack(itemKey);
            if (item == null || item.getType() == Material.AIR) {
                throw new IllegalStateException("invalid Item");
            }
            ConfigurationSection metadata = yaml.getConfigurationSection("charm");
            return new CharmTarget(metadata == null ? yaml : metadata,
                    file, yaml, itemKey, item);
        }

        private ConfigurationSection getSection() {
            return section;
        }

        private List<String> getEditableLore(ConfigurationSection currentSection) {
            if (currentSection.contains("lore")) {
                return new ArrayList<String>(currentSection.getStringList("lore"));
            }
            return getItemLore();
        }

        private List<String> getItemLore() {
            if (item == null || !item.hasItemMeta()) {
                return new ArrayList<String>();
            }
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            return meta != null && meta.hasLore()
                    ? new ArrayList<String>(meta.getLore())
                    : new ArrayList<String>();
        }

        private void updateItemMaterial(Material material, Integer data) {
            if (item == null) {
                return;
            }
            item.setType(material);
            if (data != null && data >= 0) {
                item.setDurability(data.shortValue());
            }
            itemChanged = true;
        }

        private void updateItemData(int data) {
            if (item != null && data >= 0) {
                item.setDurability((short) data);
                itemChanged = true;
            }
        }

        private void updateItemName(String displayName) {
            if (item == null) {
                return;
            }
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return;
            }
            meta.setDisplayName(MessageUtils.color(displayName));
            item.setItemMeta(meta);
            itemChanged = true;
        }

        private void updateItemLore(List<String> lore) {
            if (item == null) {
                return;
            }
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return;
            }
            List<String> updated = new ArrayList<String>();
            for (String line : lore) {
                updated.add(MessageUtils.color(line));
            }
            String equipLore = section.getString("equip-lore");
            if (equipLore != null && !equipLore.trim().isEmpty()
                    && !containsLore(updated, equipLore)) {
                updated.add(MessageUtils.color(equipLore));
            }
            meta.setLore(updated);
            item.setItemMeta(meta);
            itemChanged = true;
        }

        private void updateItemEquipLore(String previous, String equipLore) {
            if (item == null) {
                return;
            }
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return;
            }
            List<String> updated = getItemLore();
            if (previous != null && !previous.trim().isEmpty()) {
                for (int index = updated.size() - 1; index >= 0; index--) {
                    if (sameLoreLine(updated.get(index), previous)) {
                        updated.remove(index);
                    }
                }
            }
            if (equipLore != null && !equipLore.trim().isEmpty()
                    && !containsLore(updated, equipLore)) {
                updated.add(MessageUtils.color(equipLore));
            }
            meta.setLore(updated);
            item.setItemMeta(meta);
            itemChanged = true;
        }

        private static boolean containsLore(List<String> lore, String expected) {
            for (String line : lore) {
                if (sameLoreLine(line, expected)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean sameLoreLine(String left, String right) {
            return MessageUtils.color(left).replace('\u00a7', '&').trim()
                    .equals(MessageUtils.color(right).replace('\u00a7', '&').trim());
        }

        private void save(ConfigManager configManager) throws java.io.IOException {
            if (file == null) {
                configManager.save();
                return;
            }
            if (itemChanged) {
                yaml.set(itemKey, item);
            }
            YamlUtils.save(file, yaml);
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

    private Integer parseData(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if ("none".equalsIgnoreCase(normalized) || "any".equalsIgnoreCase(normalized)) {
            return -1;
        }
        try {
            int data = Integer.parseInt(normalized);
            return data == -1 || data >= 0 && data <= Short.MAX_VALUE
                    ? Integer.valueOf(data) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private CharmRarity parseRarity(String value) {
        try {
            return CharmRarity.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isValidRarity(String value) {
        return parseRarity(value.toUpperCase(Locale.US)) != null;
    }

    private Boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private String join(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if ("qingcharmsbag".equalsIgnoreCase(command.getName())
                || "qingcharmsgui".equalsIgnoreCase(command.getName())
                || !sender.hasPermission("qingcharms.command")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterStartsWith(permittedSubCommands(sender), args[0]);
        }

        String subCommand = normalizeSubCommand(args[0]);
        if (!canSuggestSubCommand(sender, subCommand)) {
            return Collections.emptyList();
        }
        if ("add".equals(subCommand) && args.length == 3) {
            return filterStartsWith(materialNames(), args[2]);
        }
        if ("add".equals(subCommand) && args.length == 4) {
            return filterStartsWith(TYPE_NAMES, args[3]);
        }
        if ("give".equals(subCommand) && args.length == 2) {
            return filterStartsWith(enabledCharmIds(), args[1]);
        }
        if ("open".equals(subCommand) && args.length == 2
                && sender.hasPermission("qingcharms.command.admin")) {
            return filterStartsWith(onlinePlayerNames(), args[1]);
        }
        if ("export".equals(subCommand) && args.length == 2) {
            return Collections.emptyList();
        }
        if ("export".equals(subCommand) && args.length == 3) {
            List<String> suggestions = new ArrayList<String>(
                    plugin.getCharmManager().getEquipSlotTypes());
            suggestions.add("-f");
            return filterStartsWith(suggestions, args[2]);
        }
        if ("export".equals(subCommand) && args.length == 4) {
            return filterStartsWith(Arrays.asList("-f"), args[3]);
        }
        if (("remove".equals(subCommand)
                || "on".equals(subCommand) || "off".equals(subCommand))
                && args.length == 2) {
            return filterStartsWith(configuredCharmIds(), args[1]);
        }
        if ("set".equals(subCommand) && args.length == 2) {
            return filterStartsWith(modifiableCharmIds(), args[1]);
        }
        if ("set".equals(subCommand) && args.length == 3) {
            return filterStartsWith(MODIFY_FIELDS, args[2]);
        }
        if ("set".equals(subCommand) && args.length == 4
                && "type".equalsIgnoreCase(args[2])) {
            return filterStartsWith(TYPE_NAMES, args[3]);
        }
        return Collections.emptyList();
    }

    private List<String> permittedSubCommands(CommandSender sender) {
        List<String> commands = new ArrayList<String>();
        for (String subCommand : SUB_COMMANDS) {
            if (canSuggestSubCommand(sender, subCommand)) {
                commands.add(subCommand);
            }
        }
        return commands;
    }

    private boolean canSuggestSubCommand(CommandSender sender, String subCommand) {
        if ("reload".equals(subCommand)) {
            return sender.hasPermission("qingcharms.command.reload");
        }
        if ("status".equals(subCommand)) {
            return sender.hasPermission("qingcharms.command.status");
        }
        if ("save".equals(subCommand)) {
            return sender.hasPermission("qingcharms.command.save");
        }
        if ("backup".equals(subCommand)) {
            return sender.hasPermission("qingcharms.command.backup");
        }
        if ("add".equals(subCommand) || "remove".equals(subCommand)
                || "set".equals(subCommand) || "on".equals(subCommand)
                || "off".equals(subCommand) || "export".equals(subCommand)) {
            return sender.hasPermission("qingcharms.command.admin");
        }
        if ("give".equals(subCommand)) {
            return sender.hasPermission("qingcharms.command.give");
        }
        if ("open".equals(subCommand)) {
            return sender.hasPermission("qingcharms.command.admin");
        }
        return true;
    }

    private String normalizeSubCommand(String value) {
        String subCommand = value == null ? "" : value.toLowerCase(Locale.US);
        if ("delete".equals(subCommand)) {
            return "remove";
        }
        if ("modify".equals(subCommand)) {
            return "set";
        }
        if ("enable".equals(subCommand)) {
            return "on";
        }
        if ("disable".equals(subCommand)) {
            return "off";
        }
        return subCommand;
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<String>();
        for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> modifiableCharmIds() {
        List<String> ids = configuredCharmIds();
        for (net.pytsing.qingcharms.model.CharmDefinition charm
                : plugin.getCharmManager().getCharms()) {
            if (!containsIgnoreCase(ids, charm.getId())) {
                ids.add(charm.getId());
            }
        }
        return ids;
    }
    private List<String> configuredCharmIds() {
        List<String> ids = new ArrayList<String>();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("charms");
        if (root != null) {
            ids.addAll(root.getKeys(false));
        }
        return ids;
    }

    private List<String> enabledCharmIds() {
        List<String> ids = new ArrayList<String>();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("charms");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section != null && section.getBoolean("enabled", false)) {
                    ids.add(id);
                }
            }
        }
        for (net.pytsing.qingcharms.model.CharmDefinition charm
                : plugin.getCharmManager().getCharms()) {
            if (!containsIgnoreCase(ids, charm.getId())) {
                ids.add(charm.getId());
            }
        }
        return ids;
    }

    private boolean containsIgnoreCase(List<String> values, String expected) {
        for (String value : values) {
            if (value.equalsIgnoreCase(expected)) {
                return true;
            }
        }
        return false;
    }

    private List<String> materialNames() {
        List<String> names = new ArrayList<String>();
        for (Material material : Material.values()) {
            names.add(material.name());
        }
        return names;
    }

    private List<String> filterStartsWith(List<String> source, String input) {
        String normalized = input.toLowerCase(Locale.US);
        List<String> matches = new ArrayList<String>();
        for (String value : source) {
            if (value.toLowerCase(Locale.US).startsWith(normalized)) {
                matches.add(value);
            }
        }
        return matches;
    }
}



















