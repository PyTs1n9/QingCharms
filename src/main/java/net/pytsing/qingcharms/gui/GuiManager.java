package net.pytsing.qingcharms.gui;

import net.pytsing.qingcharms.QingCharmsPlugin;
import net.pytsing.qingcharms.api.EquipmentSnapshot;
import net.pytsing.qingcharms.feedback.CharmFeedbackManager;
import net.pytsing.qingcharms.api.event.CharmEquipEvent;
import net.pytsing.qingcharms.api.event.CharmReplaceEvent;
import net.pytsing.qingcharms.api.event.CharmUnequipEvent;
import net.pytsing.qingcharms.build.BuildResolution;
import net.pytsing.qingcharms.build.EquipmentRequirementResult;
import net.pytsing.qingcharms.model.CharmDefinition;
import net.pytsing.qingcharms.model.ItemIdentity;
import net.pytsing.qingcharms.model.CharmItemSignature;
import net.pytsing.qingcharms.model.PlayerData;
import net.pytsing.qingcharms.util.EventUtils;
import net.pytsing.qingcharms.util.InventoryUtils;
import net.pytsing.qingcharms.util.MessageUtils;
import net.pytsing.qingcharms.util.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class GuiManager {

    private static final int CURSOR_SOURCE_INDEX = -2;

    private final QingCharmsPlugin plugin;
    private final CharmFeedbackManager feedbackManager;
    private final Map<UUID, SelectedCharm> selectedSlots = new HashMap<UUID, SelectedCharm>();
    private final Map<UUID, EquipmentSnapshot> equipmentSnapshots =
            new HashMap<UUID, EquipmentSnapshot>();
    private GuiDefinition definition;

    public GuiManager(QingCharmsPlugin plugin) {
        this.plugin = plugin;
        this.feedbackManager = new CharmFeedbackManager(plugin);
        load();
    }

    public void load() {
        feedbackManager.reload();
        selectedSlots.clear();
        equipmentSnapshots.clear();
        definition = GuiDefinition.fromConfig(
                plugin.getConfigManager().getGuiSection("gui"), plugin.getLogger());
        if (definition == null) {
            definition = new GuiDefinition(null, 3, "qingcharms.gui", null);
        }
    }

    public GuiDefinition getDefinition() {
        return definition;
    }

    public boolean isGuiOpen(Player player) {
        return player != null && player.getOpenInventory() != null
                && player.getOpenInventory().getTopInventory() != null
                && player.getOpenInventory().getTopInventory().getHolder()
                        instanceof CharmGuiHolder;
    }

    public boolean open(Player player) {
        if (player == null || definition == null) {
            return false;
        }
        String permission = definition.getPermission();
        if (!permission.isEmpty() && !player.hasPermission(permission)) {
            MessageUtils.send(player, "gui.no-permission");
            return false;
        }

        PlayerData playerData = plugin.getDataManager().loadPlayer(player.getUniqueId());
        CharmGuiHolder holder = new CharmGuiHolder(definition);
        String rawTitle = definition.getTitle();
        if (rawTitle.length() > 32) {
            rawTitle = rawTitle.substring(0, 32);
        }
        Inventory inventory = Bukkit.createInventory(
                holder, definition.getRows() * 9, MessageUtils.color(rawTitle));
        holder.setInventory(inventory);
        for (GuiSlot slot : definition.getSlots()) {
            ItemStack displayItem = slot.createItem();
            if (isEquipmentSlot(slot)) {
                ItemStack equipped = playerData.getEquipped(slot.getType());
                if (equipped != null && equipped.getType() != Material.AIR) {
                    displayItem = equipped;
                }
            }
            inventory.setItem(slot.getSlot(), displayItem);
        }
        player.openInventory(inventory);
        return true;
    }

    public boolean selectItem(Player player, int inventorySlot, ItemStack item) {
        if (player == null || inventorySlot < 0 || inventorySlot >= 36) {
            return false;
        }

        CharmItemSignature signature = CharmItemSignature.from(item);
        Optional<CharmDefinition> charm = plugin.getCharmManager().getCharmByItem(item);
        if (!charm.isPresent()) {
            return false;
        }

        selectedSlots.put(player.getUniqueId(),
                new SelectedCharm(inventorySlot, charm.get(), item, signature));
        MessageUtils.send(player, "gui.selected",
                charm.get().getTypeValue(),
                signature.getNormalizedName() == null
                        ? item.getType().name() : signature.getNormalizedName());
        return true;
    }

    public boolean autoEquip(Player player, int inventorySlot, ItemStack item,
                             boolean replaceOccupied) {
        if (player == null || inventorySlot < 0 || inventorySlot >= 36) {
            return false;
        }

        CharmItemSignature signature = CharmItemSignature.from(item);
        Optional<CharmDefinition> charm = plugin.getCharmManager().getCharmByItem(item);
        if (!charm.isPresent()) {
            return false;
        }

        String type = charm.get().getTypeValue();
        for (GuiSlot slot : definition.getSlots()) {
            if (!"equip".equals(slot.getAction())
                    || !type.equalsIgnoreCase(slot.getType())) {
                continue;
            }
            if (!hasSlotPermission(player, slot)) {
                return true;
            }
            requestEquip(player, slot, inventorySlot, item, replaceOccupied);
            return true;
        }

        MessageUtils.send(player, "gui.equip.wrong-slot", type);
        return true;
    }

    public void clearSelection(Player player) {
        if (player != null) {
            selectedSlots.remove(player.getUniqueId());
        }
    }

    public void handleSlot(Player player, GuiSlot slot, ClickType click) {
        handleSlot(player, slot, click, null);
    }

    public void handleSlot(Player player, GuiSlot slot, ClickType click,
                           ItemStack cursorItem) {
        if (player == null || slot == null || definition == null) {
            return;
        }
        if (!hasSlotPermission(player, slot)) {
            return;
        }

        String action = slot.getAction();
        if ("equip".equals(action)) {
            boolean rightClick = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
            if (!rightClick) {
                PlayerData playerData =
                        plugin.getDataManager().loadPlayer(player.getUniqueId());
                if (hasEquippedItem(playerData, slot.getType())) {
                    unequip(player, slot);
                }
                return;
            }
            if (isCharmItem(cursorItem)) {
                requestEquip(player, slot, CURSOR_SOURCE_INDEX, cursorItem.clone(),
                        click == ClickType.SHIFT_RIGHT);
            } else {
                MessageUtils.send(player, "gui.equip.use-right-click");
            }
        } else if ("unequip".equals(action)) {
            unequip(player, slot);
        } else if ("refresh".equals(action) || "reopen".equals(action)) {
            reopen(player);
        } else if ("close".equals(action)) {
            player.closeInventory();
        }
    }

    public void refreshOpen() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isGuiOpen(player)) {
                reopen(player);
            }
        }
    }

    public EquipmentSnapshot getEquipmentSnapshot(Player player) {
        if (player == null) {
            return new EquipmentSnapshot(null, 0L, Collections.<ItemStack>emptyList());
        }
        UUID playerId = player.getUniqueId();
        PlayerData playerData = plugin.getDataManager().loadPlayer(playerId);
        long dataVersion = playerData.getEquipmentDataVersion();
        EquipmentSnapshot cached = equipmentSnapshots.get(playerId);
        if (cached != null && cached.getDataVersion() == dataVersion) {
            return cached;
        }
        List<ItemStack> snapshotItems = new ArrayList<ItemStack>(playerData.getEquippedItems());
        ItemStack buildLore = plugin.getBuildResolver().createLoreItem(player, playerData);
        if (buildLore != null) {
            snapshotItems.add(buildLore);
        }
        EquipmentSnapshot snapshot = new EquipmentSnapshot(
                playerId, dataVersion, snapshotItems);
        equipmentSnapshots.put(playerId, snapshot);
        return snapshot;
    }

    public void invalidateEquipmentSnapshot(UUID playerId) {
        if (playerId != null) {
            equipmentSnapshots.remove(playerId);
        }
    }

    public List<ItemStack> getEquippedCharmItems(Player player) {
        if (player == null) {
            return new ArrayList<ItemStack>();
        }
        PlayerData playerData = plugin.getDataManager().loadPlayer(player.getUniqueId());
        return new ArrayList<ItemStack>(playerData.getEquippedItems());
    }

    private void requestEquip(Player player, GuiSlot slot, int sourceIndex,
                              ItemStack source, boolean replaceOccupied) {
        PlayerData playerData = plugin.getDataManager().loadPlayer(player.getUniqueId());
        boolean occupied = hasEquippedItem(playerData, slot.getType());
        if (replaceOccupied && !occupied) {
            MessageUtils.send(player, "gui.equip.nothing-to-replace");
            return;
        }
        if (!replaceOccupied && occupied) {
            MessageUtils.send(player, "gui.equip.slot-full");
            return;
        }
        equip(player, slot, sourceIndex, source, replaceOccupied);
    }

    private void equip(Player player, GuiSlot slot, int sourceIndex,
                       ItemStack source, boolean replaceOccupied) {
        String slotId = slot.getType();
        if (!slot.hasUsableType()) {
            MessageUtils.send(player, "gui.equip.no-type");
            return;
        }
        if (sourceIndex != CURSOR_SOURCE_INDEX && sourceIndex < 0) {
            MessageUtils.send(player, "gui.equip.no-selection");
            return;
        }

        ItemStack currentSource = sourceIndex == CURSOR_SOURCE_INDEX
                ? player.getItemOnCursor() : player.getInventory().getItem(sourceIndex);
        if (currentSource == null || currentSource.getType() == Material.AIR
                || currentSource.getAmount() <= 0) {
            selectedSlots.remove(player.getUniqueId());
            MessageUtils.send(player, "gui.equip.no-selection");
            return;
        }

        Optional<CharmDefinition> optionalCharm =
                plugin.getCharmManager().getCharmByItem(source);
        if (!optionalCharm.isPresent()) {
            MessageUtils.send(player, "gui.equip.not-charm");
            return;
        }
        CharmDefinition charm = optionalCharm.get();
        EquipmentRequirementResult requirement =
                plugin.getBuildResolver().validateEquipment(player, source, slotId);
        if (!requirement.isAllowed()) {
            MessageUtils.send(player, requirement.getMessageKey(),
                    requirement.getMessageArguments());
            feedbackManager.play(player, charm, source, "failure");
            return;
        }

        PlayerData playerData = plugin.getDataManager().loadPlayer(player.getUniqueId());
        if (!replaceOccupied && hasEquippedItem(playerData, slotId)) {
            MessageUtils.send(player, "gui.equip.slot-full");
            return;
        }
        BuildResolution before = plugin.getBuildResolver().resolveEquipped(player, playerData);
        Map<String, ItemStack> equipment = equipmentMap(playerData);
        ItemStack previous = equipment.put(slotId, source.clone());
        Map<String, ItemStack> replaced = new LinkedHashMap<String, ItemStack>();

        String itemKey = "equipped:" + slotId;
        String uniqueId = plugin.getProgressionService()
                .resolveSourceUniqueId(playerData, source, sourceIndex, itemKey);
        boolean consumesWholeSource = currentSource.getAmount() <= 1;
        if (!fireEquipEvents(player, charm, source, uniqueId, slotId, previous, replaced)) {
            feedbackManager.play(player, charm, source, "failure");
            return;
        }

        ItemStack[] inventoryBefore = InventoryUtils.snapshot(player.getInventory());
        ItemStack cursorBefore = cloneItem(player.getItemOnCursor());
        Map<String, ItemStack> dataBefore = equipmentMap(playerData);
        Map<String, ItemIdentity> identityBefore = playerData.getItemIdentities();
        try {
            consumeSource(player, sourceIndex, currentSource);

            if (previous != null && previous.getType() != Material.AIR) {
                addOrRollback(player, inventoryBefore, previous.clone());
                rememberInventoryIdentity(player, playerData, "equipped:" + slotId, previous);
            }
            for (Map.Entry<String, ItemStack> entry : replaced.entrySet()) {
                addOrRollback(player, inventoryBefore, entry.getValue().clone());
                rememberInventoryIdentity(player, playerData,
                        "equipped:" + entry.getKey(), entry.getValue());
            }

            ItemStack equipped = plugin.getProgressionService()
                    .bind(playerData, player, source.clone(), itemKey, uniqueId);
            equipped.setAmount(1);
            playerData.setEquipped(slotId, equipped);
            if (consumesWholeSource && sourceIndex >= 0) {
                playerData.removeItemIdentity("inventory:" + sourceIndex);
            }
            for (String replacedSlot : replaced.keySet()) {
                playerData.removeEquipped(replacedSlot);
            }

            plugin.getBuildResolver().refresh(player, playerData, before, true);
            selectedSlots.remove(player.getUniqueId());
            invalidateEquipmentSnapshot(player.getUniqueId());
            feedbackManager.play(player, charm, source,
                    previous != null && previous.getType() != Material.AIR
                            || !replaced.isEmpty() ? "replace" : "equip");
            reopen(player);
        } catch (RuntimeException exception) {
            InventoryUtils.restore(player.getInventory(), inventoryBefore);
            player.setItemOnCursor(cursorBefore);
            restoreEquipment(playerData, dataBefore, identityBefore);
            selectedSlots.remove(player.getUniqueId());
            plugin.getLogger().warning("Could not equip charm: " + exception.getMessage());
            MessageUtils.send(player, "gui.equip.no-space");
            feedbackManager.play(player, charm, source, "failure");
        }
    }

    private boolean fireEquipEvents(Player player, CharmDefinition charm, ItemStack source,
                                    String uniqueId, String slotId, ItemStack previous,
                                    Map<String, ItemStack> replaced) {
        ItemStack newItem = source.clone();
        newItem.setAmount(1);
        boolean replacing = previous != null && previous.getType() != Material.AIR;
        boolean allowed = replacing
                ? EventUtils.callCancellable(new CharmReplaceEvent(player, uniqueId,
                        charm.getId(), slotId, previous, newItem))
                : EventUtils.callCancellable(new CharmEquipEvent(player, uniqueId,
                        charm.getId(), slotId, null, newItem));
        if (!allowed) {
            return false;
        }
        for (Map.Entry<String, ItemStack> entry : replaced.entrySet()) {
            if (fireUnequipEvent(player, player.getUniqueId(), entry.getValue(),
                    entry.getKey())) {
                return false;
            }
        }
        return true;
    }

    private boolean fireUnequipEvent(Player player, java.util.UUID playerId,
                                     ItemStack item, String slotId) {
        Optional<CharmDefinition> charm = plugin.getCharmManager().getCharmByItem(item);
        ItemStack removed = item.clone();
        removed.setAmount(1);
        PlayerData data = plugin.getDataManager().loadPlayer(playerId);
        String storedId = data.getItemUniqueId("equipped:" + slotId, removed);
        String uniqueId = storedId.isEmpty()
                ? plugin.getCharmManager().getUniqueId(removed) : storedId;
        CharmUnequipEvent event = new CharmUnequipEvent(player, uniqueId,
                charm.isPresent() ? charm.get().getId() : "",
                slotId, removed, null);
        return !EventUtils.callCancellable(event);
    }

    private void unequip(Player player, GuiSlot slot) {
        String slotId = slot.getType();
        if (!slot.hasUsableType()) {
            MessageUtils.send(player, "gui.unequip.no-type");
            return;
        }

        PlayerData playerData = plugin.getDataManager().loadPlayer(player.getUniqueId());
        ItemStack item = playerData.getEquipped(slotId);
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return;
        }
        CharmDefinition feedbackCharm =
                plugin.getCharmManager().getCharmByItem(item).orElse(null);
        if (!InventoryUtils.canAdd(player.getInventory(), item)) {
            MessageUtils.send(player, "gui.unequip.no-space");
            feedbackManager.play(player, feedbackCharm, item, "failure");
            return;
        }
        if (fireUnequipEvent(player, player.getUniqueId(), item, slotId)) {
            feedbackManager.play(player, feedbackCharm, item, "failure");
            return;
        }

        BuildResolution before = plugin.getBuildResolver().resolveEquipped(player, playerData);
        ItemStack[] inventoryBefore = InventoryUtils.snapshot(player.getInventory());
        Map<String, ItemStack> dataBefore = equipmentMap(playerData);
        Map<String, ItemIdentity> identityBefore = playerData.getItemIdentities();
        try {
            addOrRollback(player, inventoryBefore, item.clone());
            rememberInventoryIdentity(player, playerData, "equipped:" + slotId, item);
            playerData.removeEquipped(slotId);
            plugin.getBuildResolver().refresh(player, playerData, before, true);
            selectedSlots.remove(player.getUniqueId());
            invalidateEquipmentSnapshot(player.getUniqueId());
            feedbackManager.play(player, feedbackCharm, item, "unequip");
            reopen(player);
        } catch (RuntimeException exception) {
            InventoryUtils.restore(player.getInventory(), inventoryBefore);
            restoreEquipment(playerData, dataBefore, identityBefore);
            plugin.getLogger().warning("Could not unequip charm: " + exception.getMessage());
            MessageUtils.send(player, "gui.unequip.no-space");
            feedbackManager.play(player, feedbackCharm, item, "failure");
        }
    }

    private void rememberInventoryIdentity(Player player, PlayerData data,
                                      String fromKey, ItemStack item) {
        String uniqueId = data.getItemUniqueId(fromKey, item);
        if (uniqueId.isEmpty()) {
            return;
        }
        int inventorySlot = findInventoryItem(player, item);
        if (inventorySlot >= 0) {
            data.setItemIdentity("inventory:" + inventorySlot, uniqueId, item);
        }
    }

    private static void consumeSource(Player player, int sourceIndex, ItemStack source) {
        if (source.getAmount() > 1) {
            source.setAmount(source.getAmount() - 1);
        } else {
            source = new ItemStack(Material.AIR);
        }
        if (sourceIndex == CURSOR_SOURCE_INDEX) {
            player.setItemOnCursor(source);
        } else {
            player.getInventory().setItem(sourceIndex, source);
        }
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? new ItemStack(Material.AIR) : item.clone();
    }

    private boolean isCharmItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR
                && plugin.getCharmManager().getCharmByItem(item).isPresent();
    }

    private void addOrRollback(Player player, ItemStack[] inventoryBefore, ItemStack item) {
        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            InventoryUtils.restore(player.getInventory(), inventoryBefore);
            throw new IllegalStateException("inventory is full");
        }
    }

    private Map<String, ItemStack> equipmentMap(PlayerData playerData) {
        Map<String, ItemStack> result = new LinkedHashMap<String, ItemStack>();
        for (String slotId : playerData.getEquippedSlotIds()) {
            ItemStack item = playerData.getEquipped(slotId);
            if (item != null && item.getType() != Material.AIR) {
                result.put(slotId, item);
            }
        }
        return result;
    }

    private void restoreEquipment(PlayerData playerData, Map<String, ItemStack> previous,
                                  Map<String, ItemIdentity> identities) {
        playerData.restoreEquipmentState(previous, identities);
    }


    public boolean equipFromApi(Player player, ItemStack item, String slotId) {
        if (player == null || item == null || slotId == null) {
            return false;
        }
        GuiSlot target = findEquipmentSlot(slotId);
        if (target == null) {
            return false;
        }
        int sourceIndex = findInventoryItem(player, item);
        if (sourceIndex < 0) {
            return false;
        }
        PlayerData data = plugin.getDataManager().loadPlayer(player.getUniqueId());
        long before = data.getEquipmentDataVersion();
        equip(player, target, sourceIndex, item, false);
        return data.getEquipmentDataVersion() != before;
    }

    public boolean unequipFromApi(Player player, String slotId) {
        if (player == null || slotId == null) {
            return false;
        }
        GuiSlot target = findEquipmentSlot(slotId);
        if (target == null) {
            return false;
        }
        PlayerData data = plugin.getDataManager().loadPlayer(player.getUniqueId());
        long before = data.getEquipmentDataVersion();
        unequip(player, target);
        return data.getEquipmentDataVersion() != before;
    }

    private GuiSlot findEquipmentSlot(String slotId) {
        for (GuiSlot slot : definition.getSlots()) {
            if (isEquipmentSlot(slot) && slot.getType() != null
                    && slot.getType().equalsIgnoreCase(slotId.trim())) {
                return slot;
            }
        }
        return null;
    }

    private int findInventoryItem(Player player, ItemStack expected) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int index = 0; index < contents.length && index < 36; index++) {
            ItemStack item = contents[index];
            if (item != null && item.getType() != Material.AIR && item.isSimilar(expected)) {
                return index;
            }
        }
        return -1;
    }
    private boolean hasSlotPermission(Player player, GuiSlot slot) {
        String permission = slot.getPermission();
        if (permission.isEmpty() || player.hasPermission(permission)) {
            return true;
        }
        MessageUtils.send(player, "gui.slot-no-permission");
        return false;
    }

    private boolean hasEquippedItem(PlayerData playerData, String slotId) {
        if (playerData == null || slotId == null) {
            return false;
        }
        ItemStack item = playerData.getEquipped(slotId);
        return item != null && item.getType() != Material.AIR;
    }

    private boolean hasSelection(Player player) {
        return player != null && selectedSlots.containsKey(player.getUniqueId());
    }

    private ItemStack getSelectedSource(Player player, String type) {
        SelectedCharm selection = selectedSlots.get(player.getUniqueId());
        if (selection == null) {
            return null;
        }

        ItemStack item = player.getInventory().getItem(selection.getInventorySlot());
        if (item == null || item.getType() == Material.AIR
                || !item.equals(selection.getItem())) {
            selectedSlots.remove(player.getUniqueId());
            return null;
        }
        if (!type.equalsIgnoreCase(selection.getCharm().getTypeValue())
                || !selection.getSignature().containsSearchLore(type)) {
            return null;
        }
        return item;
    }

    private boolean isEquipmentSlot(GuiSlot slot) {
        String action = slot.getAction();
        return "equip".equals(action) || "unequip".equals(action);
    }

    private static final class SelectedCharm {

        private final int inventorySlot;
        private final CharmDefinition charm;
        private final ItemStack item;
        private final CharmItemSignature signature;

        private SelectedCharm(int inventorySlot, CharmDefinition charm,
                              ItemStack item, CharmItemSignature signature) {
            this.inventorySlot = inventorySlot;
            this.charm = charm;
            this.item = item.clone();
            this.signature = signature;
        }

        private int getInventorySlot() {
            return inventorySlot;
        }

        private CharmDefinition getCharm() {
            return charm;
        }

        private ItemStack getItem() {
            return item;
        }

        private CharmItemSignature getSignature() {
            return signature;
        }
    }

    private void reopen(Player player) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                open(player);
            }
        });
    }
}














