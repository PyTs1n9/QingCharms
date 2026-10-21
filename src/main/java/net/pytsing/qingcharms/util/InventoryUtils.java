package net.pytsing.qingcharms.util;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class InventoryUtils {

    private InventoryUtils() {
    }

    public static boolean canAdd(Inventory inventory, ItemStack item) {
        return canAdd(inventory == null ? null : inventory.getContents(),
                item, inventory == null ? 0 : inventory.getMaxStackSize());
    }

    public static boolean canAddAfterConsuming(Inventory inventory, ItemStack item,
                                                int sourceSlot) {
        if (inventory == null || !isItem(item)) {
            return false;
        }
        ItemStack[] contents = inventory.getContents();
        if (contents == null || sourceSlot < 0 || sourceSlot >= contents.length) {
            return false;
        }
        ItemStack[] slots = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            slots[index] = contents[index] == null ? null : contents[index].clone();
        }
        ItemStack source = slots[sourceSlot];
        if (!isItem(source)) {
            return false;
        }
        if (source.getAmount() <= 1) {
            slots[sourceSlot] = null;
        } else {
            source = source.clone();
            source.setAmount(source.getAmount() - 1);
            slots[sourceSlot] = source;
        }
        return canAdd(slots, item, inventory.getMaxStackSize());
    }

    public static ItemStack[] snapshot(Inventory inventory) {
        if (inventory == null) {
            return new ItemStack[0];
        }
        ItemStack[] contents = inventory.getContents();
        ItemStack[] snapshot = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            snapshot[index] = contents[index] == null ? null : contents[index].clone();
        }
        return snapshot;
    }

    public static void restore(Inventory inventory, ItemStack[] snapshot) {
        if (inventory == null || snapshot == null) {
            return;
        }
        ItemStack[] contents = new ItemStack[snapshot.length];
        for (int index = 0; index < snapshot.length; index++) {
            contents[index] = snapshot[index] == null ? null : snapshot[index].clone();
        }
        inventory.setContents(contents);
    }

    private static boolean canAdd(ItemStack[] slots, ItemStack item, int inventoryMaxStack) {
        if (slots == null || !isItem(item) || item.getAmount() <= 0) {
            return false;
        }
        int maxStackSize = Math.min(item.getMaxStackSize(), inventoryMaxStack);
        if (maxStackSize <= 0) {
            return false;
        }

        int remaining = item.getAmount();
        for (ItemStack slot : slots) {
            if (remaining <= 0) {
                return true;
            }
            if (!isItem(slot)) {
                remaining -= maxStackSize;
            } else if (slot.isSimilar(item)) {
                int slotAmount = Math.min(slot.getAmount(), maxStackSize);
                remaining -= Math.max(0, maxStackSize - slotAmount);
            }
        }
        return remaining <= 0;
    }

    private static boolean isItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR;
    }
}
