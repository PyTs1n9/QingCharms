package net.pytsing.qingcharms.listener;

import net.pytsing.qingcharms.QingCharmsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public final class CharmListener implements Listener {

    private final QingCharmsPlugin plugin;

    public CharmListener(QingCharmsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getBuildResolver().initialize(event.getPlayer(),
                plugin.getDataManager().loadPlayer(event.getPlayer().getUniqueId()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getTriggerMessageService().clear(event.getPlayer().getUniqueId());
        plugin.getGuiManager().invalidateEquipmentSnapshot(event.getPlayer().getUniqueId());
        plugin.getBuildResolver().invalidate(event.getPlayer().getUniqueId());
        plugin.getDataManager().savePlayer(event.getPlayer().getUniqueId());
        plugin.getDataManager().unload(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)
                && isCharmItem(event.getItem())) {
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton();
            ItemStack item = hotbarSlot < 0 ? null
                    : event.getWhoClicked().getInventory().getItem(hotbarSlot);
            if (isCharmItem(item)) {
                event.setCancelled(true);
            }
            return;
        }

        if (isArmorSlot(event) && isCharmItem(event.getCursor())) {
            event.setCancelled(true);
            return;
        }

        if ((event.getClick() == ClickType.SHIFT_LEFT
                || event.getClick() == ClickType.SHIFT_RIGHT)
                && isCharmItem(event.getCurrentItem())
                && isPlayerInventoryView(event) && !isArmorSlot(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player) || event.getInventory() == null
                || !(event.getInventory().getHolder() instanceof Player)) {
            return;
        }
        for (Integer rawSlot : event.getNewItems().keySet()) {
            if (!isArmorSlot(rawSlot)) {
                continue;
            }
            ItemStack item = event.getNewItems().get(rawSlot);
            if (isCharmItem(item)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean isCharmItem(ItemStack item) {
        return item != null && item.getType() != org.bukkit.Material.AIR
                && plugin.getCharmManager().getCharmByItem(item).isPresent();
    }

    private boolean isPlayerInventoryView(InventoryClickEvent event) {
        return event.getView() != null
                && event.getView().getTopInventory() != null
                && event.getView().getTopInventory().getHolder() instanceof Player;
    }

    private boolean isArmorSlot(InventoryClickEvent event) {
        return event.getClickedInventory() != null
                && event.getClickedInventory().getHolder() instanceof Player
                && event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR;
    }

    private boolean isArmorSlot(int rawSlot) {
        return isPlayerArmorRawSlot(rawSlot);
    }

    private boolean isPlayerArmorRawSlot(int rawSlot) {
        return rawSlot >= 36 && rawSlot <= 39;
    }
}







