package net.pytsing.qingcharms.bag;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class BagGuiListener implements Listener {

    private final BagGuiManager guiManager;
    private final CharmBagManager bagManager;

    public BagGuiListener(BagGuiManager guiManager, CharmBagManager bagManager) {
        this.guiManager = guiManager;
        this.bagManager = bagManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() == null
                || !(event.getInventory().getHolder() instanceof CharmBagGuiHolder)
                || !(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        int topSize = event.getInventory().getSize();
        boolean clickedTop = event.getRawSlot() >= 0 && event.getRawSlot() < topSize;
        if (clickedTop) {
            event.setCancelled(true);
            guiManager.handleClick(player, event);
            return;
        }

        // Double-click can collect similar items from both inventories and must
        // never pull display items out of the custom bag.
        if (event.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }
        if (guiManager.isSingleDeposit(event)) {
            event.setCancelled(true);
            guiManager.handleClick(player, event);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory() != null
                && event.getInventory().getHolder() instanceof CharmBagGuiHolder
                && event.getPlayer() instanceof Player) {
            CharmBagGuiHolder holder = (CharmBagGuiHolder) event.getInventory().getHolder();
            bagManager.save(holder.getOwnerId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory() == null
                || !(event.getInventory().getHolder() instanceof CharmBagGuiHolder)) {
            return;
        }

        int topSize = event.getInventory().getSize();
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot != null && rawSlot.intValue() < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
