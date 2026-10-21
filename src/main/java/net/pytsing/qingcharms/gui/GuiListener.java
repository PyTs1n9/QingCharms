package net.pytsing.qingcharms.gui;

import net.pytsing.qingcharms.QingCharmsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class GuiListener implements Listener {

    private final QingCharmsPlugin plugin;

    public GuiListener(QingCharmsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)
                || event.getClickedInventory() == null) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (plugin.getGuiManager().isGuiOpen(player)
                && event.getClickedInventory().getHolder() instanceof Player) {
            if (isEquipClick(event.getClick())
                    && (event.getCursor() == null
                        || event.getCursor().getType() == org.bukkit.Material.AIR)
                    && plugin.getGuiManager().autoEquip(player, event.getSlot(),
                            event.getCurrentItem(), event.getClick() == ClickType.SHIFT_RIGHT)) {
                event.setCancelled(true);
            }
            return;
        }

        if (!(event.getClickedInventory().getHolder() instanceof CharmGuiHolder)) {
            return;
        }

        event.setCancelled(true);
        CharmGuiHolder holder = (CharmGuiHolder) event.getClickedInventory().getHolder();
        GuiSlot slot = holder.getDefinition().getSlot(event.getSlot());
        if (slot != null) {
            plugin.getGuiManager().handleSlot(
                    player, slot, event.getClick(), event.getCursor());
        }
    }

    private boolean isEquipClick(ClickType click) {
        return click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player
                && event.getInventory() != null
                && event.getInventory().getHolder() instanceof CharmGuiHolder) {
            plugin.getGuiManager().clearSelection((Player) event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory() == null
                || !(event.getInventory().getHolder() instanceof CharmGuiHolder)) {
            return;
        }
        event.setCancelled(true);
    }
}
