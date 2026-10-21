package net.pytsing.qingcharms.bag;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CharmBagGuiHolder implements InventoryHolder {

    private final BagSortMode sortMode;
    private final String category;
    private final UUID ownerId;
    private final boolean adminView;
    private int page;
    private Inventory inventory;
    private final Map<Integer, String> entrySlots = new HashMap<Integer, String>();

    public CharmBagGuiHolder(BagSortMode sortMode, String category, int page) {
        this(sortMode, category, page, null, false);
    }

    public CharmBagGuiHolder(BagSortMode sortMode, String category, int page,
                             UUID ownerId, boolean adminView) {
        this.sortMode = sortMode == null ? BagSortMode.RARITY : sortMode;
        this.category = category == null ? BagCategoryFilter.ALL : category;
        this.page = Math.max(0, page);
        this.ownerId = ownerId;
        this.adminView = adminView;
    }

    public CharmBagGuiHolder copy(BagSortMode sortMode, String category, int page) {
        return new CharmBagGuiHolder(sortMode, category, page, ownerId, adminView);
    }

    public BagSortMode getSortMode() {
        return sortMode;
    }

    public String getCategory() {
        return category;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public boolean isAdminView() {
        return adminView;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    public void clearEntrySlots() {
        entrySlots.clear();
    }

    public void putEntrySlot(int slot, String uniqueId) {
        entrySlots.put(Integer.valueOf(slot), uniqueId);
    }

    public String getEntryId(int slot) {
        return entrySlots.get(Integer.valueOf(slot));
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}