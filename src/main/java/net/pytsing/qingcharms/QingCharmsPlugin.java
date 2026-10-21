package net.pytsing.qingcharms;

import net.pytsing.qingcharms.api.CharmAPI;
import net.pytsing.qingcharms.api.CharmOperationAPI;
import net.pytsing.qingcharms.api.CharmQueryAPI;
import net.pytsing.qingcharms.build.BuildResolver;
import net.pytsing.qingcharms.build.EquipmentRequirementResult;
import net.pytsing.qingcharms.command.ProgressionCommand;
import net.pytsing.qingcharms.command.QingCharmsCommand;
import net.pytsing.qingcharms.config.ConfigManager;
import net.pytsing.qingcharms.gui.GuiListener;
import net.pytsing.qingcharms.gui.GuiManager;
import net.pytsing.qingcharms.listener.CharmListener;
import net.pytsing.qingcharms.listener.ProgressionListener;
import net.pytsing.qingcharms.progression.ProgressionService;
import net.pytsing.qingcharms.trigger.TriggerMessageService;
import net.pytsing.qingcharms.manager.CharmManager;
import net.pytsing.qingcharms.model.PlayerData;
import net.pytsing.qingcharms.storage.DataManager;
import net.pytsing.qingcharms.storage.YamlDataManager;
import net.pytsing.qingcharms.util.InventoryUtils;
import net.pytsing.qingcharms.util.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class QingCharmsPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private DataManager dataManager;
    private CharmManager charmManager;
    private BuildResolver buildResolver;
    private ProgressionService progressionService;
    private TriggerMessageService triggerMessageService;
    private GuiManager guiManager;
    private BukkitTask buildRefreshTask;
    private BukkitTask progressionWearTask;

    @Override
    public void onEnable() {
        try {
            configManager = new ConfigManager(this);
            configManager.load();
            MessageUtils.load(this);

            dataManager = new YamlDataManager(this);
            dataManager.load();

            triggerMessageService = new TriggerMessageService(this);
            triggerMessageService.start();
            charmManager = new CharmManager(this, configManager);
            charmManager.load();

            buildResolver = new BuildResolver(this);
            progressionService = new ProgressionService(this);
            guiManager = new GuiManager(this);

            QingCharmsCommand command = new QingCharmsCommand(this);
            getCommand("qingcharms").setExecutor(command);
            getCommand("qingcharms").setTabCompleter(command);
            getCommand("qingcharmsgui").setExecutor(command);
            ProgressionCommand progressionCommand = new ProgressionCommand(this);
            getCommand("qingcharmsgrowth").setExecutor(progressionCommand);
            getCommand("qingcharmsgrowth").setTabCompleter(progressionCommand);

            Bukkit.getPluginManager().registerEvents(new CharmListener(this), this);
            Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);
            Bukkit.getPluginManager().registerEvents(new ProgressionListener(this), this);

            Bukkit.getServicesManager().register(CharmQueryAPI.class, getAPI(),
                    this, ServicePriority.Normal);
            Bukkit.getServicesManager().register(CharmOperationAPI.class, getAPI(),
                    this, ServicePriority.Normal);
            Bukkit.getServicesManager().register(CharmAPI.class, getAPI(),
                    this, ServicePriority.Normal);

            progressionWearTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
                @Override
                public void run() {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        progressionService.tickWear(player);
                    }
                }
            }, 20L, 20L);
            buildRefreshTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
                @Override
                public void run() {
                    refreshOnlineBuilds();
                }
            }, 100L, 100L);

            getLogger().info("QingCharms " + getDescription().getVersion() + " enabled.");
        } catch (Exception exception) {
            getLogger().log(java.util.logging.Level.SEVERE, "QingCharms startup error", exception);
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        if (buildRefreshTask != null) {
            buildRefreshTask.cancel();
        }
        if (progressionWearTask != null) {
            progressionWearTask.cancel();
        }
        if (triggerMessageService != null) {
            triggerMessageService.shutdown();
        }
        if (charmManager != null) {
            charmManager.unload();
        }
        Bukkit.getServicesManager().unregisterAll(this);
        if (dataManager != null) {
            dataManager.shutdown();
            dataManager.unloadAll();
        }
        getLogger().info("QingCharms has been disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public CharmManager getCharmManager() {
        return charmManager;
    }

    public BuildResolver getBuildResolver() {
        return buildResolver;
    }

    public TriggerMessageService getTriggerMessageService() {
        return triggerMessageService;
    }

    public ProgressionService getProgressionService() {
        return progressionService;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    /**
     * Re-checks equipped items after configuration reload. Invalid items are
     * returned to inventory. If the inventory is full, the item remains equipped until inventory space is available.
     */
    public void revalidateEquippedRequirements() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = dataManager.loadPlayer(player.getUniqueId());
            boolean changed = false;
            for (String slotId : new ArrayList<String>(data.getEquippedSlotIds())) {
                ItemStack item = data.getEquipped(slotId);
                if (item == null || item.getType() == org.bukkit.Material.AIR) {
                    data.removeEquipped(slotId);
                    changed = true;
                    continue;
                }
                EquipmentRequirementResult result =
                        buildResolver.validateEquipment(player, item, slotId);
                if (result.isAllowed()) {
                    continue;
                }

                org.bukkit.inventory.ItemStack[] inventoryBefore =
                        InventoryUtils.snapshot(player.getInventory());
                boolean returnedToInventory = false;
                try {
                    java.util.Map<Integer, ItemStack> leftovers =
                            player.getInventory().addItem(item.clone());
                    returnedToInventory = leftovers == null || leftovers.isEmpty();
                    if (!returnedToInventory) {
                        InventoryUtils.restore(player.getInventory(), inventoryBefore);
                    }
                } catch (RuntimeException exception) {
                    InventoryUtils.restore(player.getInventory(), inventoryBefore);
                    getLogger().warning("Could not return invalid equipped item for "
                            + player.getName() + ": " + exception.getMessage());
                }
                if (!returnedToInventory) {
                    MessageUtils.send(player, result.getMessageKey(),
                            result.getMessageArguments());
                    continue;
                }
                data.removeEquipped(slotId);
                changed = true;
                MessageUtils.send(player, result.getMessageKey(),
                        result.getMessageArguments());
            }
            if (changed) {
                buildResolver.refresh(player, data, null, false);
                dataManager.savePlayer(data);
                guiManager.invalidateEquipmentSnapshot(player.getUniqueId());
            }
        }
    }

    private void refreshOnlineBuilds() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                PlayerData data = dataManager.loadPlayer(player.getUniqueId());
                buildResolver.refresh(player, data, null, true);
            } catch (RuntimeException exception) {
                getLogger().warning("Could not refresh build for " + player.getName()
                        + ": " + exception.getMessage());
            }
        }
    }

    /**
     * @deprecated Use {@link CharmAPI#getEquipmentSnapshot(Player)}.
     */
    @Deprecated
    public List<ItemStack> getEquippedCharmItems(Player player) {
        return player == null || getAPI() == null
                ? Collections.<ItemStack>emptyList()
                : getAPI().getEquipmentSnapshot(player).getItems();
    }

    public CharmAPI getAPI() {
        return charmManager;
    }
}







