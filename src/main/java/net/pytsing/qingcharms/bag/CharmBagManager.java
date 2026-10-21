package net.pytsing.qingcharms.bag;

import net.pytsing.qingcharms.QingCharmsPlugin;
import net.pytsing.qingcharms.api.ItemUniqueId;
import net.pytsing.qingcharms.model.CharmDefinition;
import net.pytsing.qingcharms.model.PlayerData;
import net.pytsing.qingcharms.util.InventoryUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates charm bag state and transactional item movement. Bag GUI details
 * stay in BagGuiManager; storage stays in BagStorage.
 */
public final class CharmBagManager {

    public enum DepositResult {
        SUCCESS,
        NOT_CHARM,
        FULL,
        STORAGE_ERROR
    }

    public enum WithdrawResult {
        SUCCESS,
        NOT_FOUND,
        NO_SPACE,
        STORAGE_ERROR
    }

    private final QingCharmsPlugin plugin;
    private final BagStorage storage;
    private final CharmMetadataManager metadataManager;
    private final BagSorter sorter;
    private final BagCategoryFilter categoryFilter;
    private final Map<UUID, List<CharmBagEntry>> bags =
            new HashMap<UUID, List<CharmBagEntry>>();
    private final Map<UUID, Boolean> storageErrors = new HashMap<UUID, Boolean>();

    private boolean enabled;
    private int capacity;
    private int rows;
    private int pageSize;
    private String title;
    private String permission;
    private BagSortMode defaultSort;

    public CharmBagManager(QingCharmsPlugin plugin) {
        this.plugin = plugin;
        this.storage = new BagStorage(plugin);
        this.metadataManager = new CharmMetadataManager(plugin);
        this.sorter = new BagSorter();
        this.categoryFilter = new BagCategoryFilter();
        reload();
    }

    public void reload() {
        ConfigurationSection root =
                plugin.getConfigManager().getGuiSection("charm-bag");
        enabled = root == null || root.getBoolean("enabled", true);
        rows = Math.max(2, Math.min(6, root == null ? 6 : root.getInt("rows", 6)));
        pageSize = Math.max(1, Math.min((rows - 1) * 9,
                root == null ? 45 : root.getInt("page-size", 45)));
        capacity = Math.max(pageSize, Math.min(10000,
                root == null ? 180 : root.getInt("capacity", 180)));
        title = root == null ? "&8青玉护符背包"
                : root.getString("title", "&8青玉护符背包");
        permission = root == null ? "qingcharms.bag"
                : root.getString("permission", "qingcharms.bag");
        ConfigurationSection sort = root == null ? null : root.getConfigurationSection("sort");
        defaultSort = BagSortMode.parse(sort == null ? null : sort.getString("default"),
                BagSortMode.RARITY);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getRows() {
        return rows;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getTitle() {
        return title;
    }

    public String getPermission() {
        return permission;
    }

    public BagSortMode getDefaultSort() {
        return defaultSort;
    }

    public BagSorter getSorter() {
        return sorter;
    }

    public BagCategoryFilter getCategoryFilter() {
        return categoryFilter;
    }

    public synchronized List<CharmBagEntry> getEntries(Player player) {
        if (player == null) {
            return new ArrayList<CharmBagEntry>();
        }
        List<CharmBagEntry> entries = bags.get(player.getUniqueId());
        if (entries != null) {
            return copy(entries);
        }
        if (Boolean.TRUE.equals(storageErrors.get(player.getUniqueId()))) {
            throw new IllegalStateException("charm bag storage is unavailable");
        }
        try {
            entries = storage.load(player.getUniqueId());
            bags.put(player.getUniqueId(), entries);
            return copy(entries);
        } catch (RuntimeException exception) {
            storageErrors.put(player.getUniqueId(), Boolean.TRUE);
            throw exception;
        }
    }

    public DepositResult deposit(Player bagOwner, ItemStack item, int sourceSlot) {
        return deposit(bagOwner, bagOwner, item, sourceSlot);
    }

    public DepositResult deposit(Player bagOwner, Player donor,
                                 ItemStack item, int sourceSlot) {
        return deposit(bagOwner, donor, item, sourceSlot, true);
    }

    public DepositResult depositFromCursor(Player bagOwner, Player donor,
                                           ItemStack item) {
        return deposit(bagOwner, donor, item, -1, false);
    }

    private DepositResult deposit(Player bagOwner, Player donor, ItemStack item,
                                  int sourceSlot, boolean consumeInventorySource) {
        if (bagOwner == null || donor == null || item == null
                || item.getType() == Material.AIR) {
            return DepositResult.NOT_CHARM;
        }
        Optional<CharmDefinition> charm = plugin.getCharmManager().getCharmByItem(item);
        if (!charm.isPresent()) {
            return DepositResult.NOT_CHARM;
        }

        List<CharmBagEntry> current;
        try {
            current = getEntries(bagOwner);
        } catch (RuntimeException exception) {
            return DepositResult.STORAGE_ERROR;
        }
        if (current.size() >= capacity) {
            return DepositResult.FULL;
        }

        ItemStack single = item.clone();
        single.setAmount(1);
        Inventory inventory = donor.getInventory();
        int sourceIndex = -1;
        String uniqueId = "";
        if (consumeInventorySource) {
            sourceIndex = resolveSourceIndex(inventory, single, sourceSlot);
            if (sourceIndex < 0) {
                return DepositResult.NOT_CHARM;
            }
            PlayerData playerData = playerData(donor);
            uniqueId = playerData == null ? "" :
                    playerData.getItemUniqueId("inventory:" + sourceIndex, single);
        } else {
            ItemStack cursor = donor.getItemOnCursor();
            if (cursor == null || cursor.getType() == Material.AIR
                    || !cursor.isSimilar(single)) {
                return DepositResult.NOT_CHARM;
            }
        }
        if (uniqueId.isEmpty()) {
            uniqueId = ItemUniqueId.explicitFrom(single);
        }
        if (uniqueId.isEmpty()) {
            uniqueId = UUID.randomUUID().toString();
        }

        CharmBagEntry entry = metadataManager.createEntry(
                uniqueId, charm.get(), single.clone(),
                createdOrder(current));
        List<CharmBagEntry> candidate = copy(current);
        candidate.add(entry);
        if (!storage.save(bagOwner.getUniqueId(), candidate)) {
            return DepositResult.STORAGE_ERROR;
        }

        if (!consumeInventorySource) {
            try {
                ItemStack cursor = donor.getItemOnCursor();
                if (cursor == null || cursor.getType() == Material.AIR
                        || !cursor.isSimilar(single)) {
                    throw new IllegalStateException("cursor charm is no longer available");
                }
                if (cursor.getAmount() <= 1) {
                    donor.setItemOnCursor(null);
                } else {
                    cursor.setAmount(cursor.getAmount() - 1);
                    donor.setItemOnCursor(cursor);
                }
                bags.put(bagOwner.getUniqueId(), candidate);
                storageErrors.remove(bagOwner.getUniqueId());
                return DepositResult.SUCCESS;
            } catch (RuntimeException exception) {
                storage.save(bagOwner.getUniqueId(), current);
                plugin.getLogger().warning("Could not deposit cursor charm: "
                        + exception.getMessage());
                return DepositResult.STORAGE_ERROR;
            }
        }

        ItemStack[] before = InventoryUtils.snapshot(inventory);
        try {
            ItemStack source = inventory.getItem(sourceIndex);
            if (source == null || !source.isSimilar(single)) {
                throw new IllegalStateException("source charm is no longer available");
            }
            boolean removeSource = source.getAmount() <= 1;
            if (removeSource) {
                inventory.setItem(sourceIndex, null);
            } else {
                source.setAmount(source.getAmount() - 1);
                inventory.setItem(sourceIndex, source);
            }
            PlayerData playerData = playerData(donor);
            if (playerData != null && removeSource) {
                playerData.removeItemIdentity("inventory:" + sourceIndex);
            }
            bags.put(bagOwner.getUniqueId(), candidate);
            storageErrors.remove(bagOwner.getUniqueId());
            return DepositResult.SUCCESS;
        } catch (RuntimeException exception) {
            InventoryUtils.restore(inventory, before);
            storage.save(bagOwner.getUniqueId(), current);
            plugin.getLogger().warning("Could not deposit charm: " + exception.getMessage());
            return DepositResult.STORAGE_ERROR;
        }
    }

    public WithdrawResult withdraw(Player bagOwner, String uniqueId) {
        return withdraw(bagOwner, bagOwner, uniqueId);
    }

    public WithdrawResult withdraw(Player bagOwner, Player receiver, String uniqueId) {
        if (bagOwner == null || receiver == null) {
            return WithdrawResult.STORAGE_ERROR;
        }
        List<CharmBagEntry> current;
        try {
            current = getEntries(bagOwner);
        } catch (RuntimeException exception) {
            return WithdrawResult.STORAGE_ERROR;
        }
        CharmBagEntry entry = find(current, uniqueId);
        if (entry == null) {
            return WithdrawResult.NOT_FOUND;
        }
        ItemStack item = entry.getItem();
        Inventory inventory = receiver.getInventory();
        if (!InventoryUtils.canAdd(inventory, item)) {
            return WithdrawResult.NO_SPACE;
        }

        List<CharmBagEntry> candidate = copy(current);
        remove(candidate, uniqueId);
        if (!storage.save(bagOwner.getUniqueId(), candidate)) {
            return WithdrawResult.STORAGE_ERROR;
        }

        ItemStack[] before = InventoryUtils.snapshot(inventory);
        try {
            Map<Integer, ItemStack> leftovers = inventory.addItem(item);
            if (!leftovers.isEmpty()) {
                InventoryUtils.restore(inventory, before);
                storage.save(bagOwner.getUniqueId(), current);
                return WithdrawResult.NO_SPACE;
            }
            int inventorySlot = resolveSourceIndex(inventory, item, -1);
            PlayerData playerData = playerData(receiver);
            if (playerData != null && inventorySlot >= 0) {
                playerData.setItemIdentity("inventory:" + inventorySlot,
                        entry.getUniqueId(), item);
            }
            bags.put(bagOwner.getUniqueId(), candidate);
            return WithdrawResult.SUCCESS;
        } catch (RuntimeException exception) {
            InventoryUtils.restore(inventory, before);
            storage.save(bagOwner.getUniqueId(), current);
            plugin.getLogger().warning("Could not withdraw charm: " + exception.getMessage());
            return WithdrawResult.STORAGE_ERROR;
        }
    }

    public synchronized void save(Player player) {
        if (player != null) {
            save(player.getUniqueId());
        }
    }

    public synchronized void save(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        List<CharmBagEntry> entries = bags.get(ownerId);
        if (entries != null && !storage.save(ownerId, entries)) {
            storageErrors.put(ownerId, Boolean.TRUE);
        }
    }

    public synchronized void unload(Player player) {
        if (player == null) {
            return;
        }
        save(player);
        bags.remove(player.getUniqueId());
        storageErrors.remove(player.getUniqueId());
    }

    public synchronized void saveAll() {
        for (Map.Entry<UUID, List<CharmBagEntry>> entry : bags.entrySet()) {
            if (!storage.save(entry.getKey(), entry.getValue())) {
                storageErrors.put(entry.getKey(), Boolean.TRUE);
            }
        }
    }

    private static List<CharmBagEntry> copy(List<CharmBagEntry> entries) {
        List<CharmBagEntry> result = new ArrayList<CharmBagEntry>(
                entries == null ? 0 : entries.size());
        if (entries != null) {
            for (CharmBagEntry entry : entries) {
                if (entry != null && entry.hasItem()) {
                    result.add(entry.copy());
                }
            }
        }
        return result;
    }

    private static CharmBagEntry find(List<CharmBagEntry> entries, String uniqueId) {
        if (entries == null || uniqueId == null) {
            return null;
        }
        for (CharmBagEntry entry : entries) {
            if (uniqueId.equals(entry.getUniqueId())) {
                return entry;
            }
        }
        return null;
    }

    private static void remove(List<CharmBagEntry> entries, String uniqueId) {
        Iterator<CharmBagEntry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            if (uniqueId.equals(iterator.next().getUniqueId())) {
                iterator.remove();
                return;
            }
        }
    }

    private static long createdOrder(List<CharmBagEntry> entries) {
        long maximum = 0L;
        for (CharmBagEntry entry : entries) {
            maximum = Math.max(maximum, entry.getCreatedOrder());
        }
        return maximum + 1L;
    }

    private PlayerData playerData(Player player) {
        return plugin instanceof QingCharmsPlugin
                ? ((QingCharmsPlugin) plugin).getDataManager().loadPlayer(player.getUniqueId())
                : null;
    }

    private static int resolveSourceIndex(Inventory inventory, ItemStack expected,
                                         int preferredSlot) {
        if (inventory == null || expected == null) {
            return -1;
        }
        ItemStack preferred = preferredSlot >= 0
                ? inventory.getItem(preferredSlot) : null;
        if (preferred != null && preferred.isSimilar(expected)) {
            return preferredSlot;
        }
        return findIndexSimilar(inventory, expected);
    }

    private static ItemStack findSimilar(Inventory inventory, ItemStack expected) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getAmount() > 0 && item.isSimilar(expected)) {
                return item;
            }
        }
        return null;
    }

    private static int indexOf(Inventory inventory, ItemStack expected) {
        ItemStack[] contents = inventory.getContents();
        for (int index = 0; index < contents.length; index++) {
            if (contents[index] == expected) {
                return index;
            }
        }
        return findIndexSimilar(inventory, expected);
    }

    private static int findIndexSimilar(Inventory inventory, ItemStack expected) {
        ItemStack[] contents = inventory.getContents();
        for (int index = 0; index < contents.length; index++) {
            if (contents[index] != null && contents[index].isSimilar(expected)) {
                return index;
            }
        }
        return -1;
    }
}
