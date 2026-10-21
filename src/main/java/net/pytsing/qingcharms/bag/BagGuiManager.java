package net.pytsing.qingcharms.bag;

import net.pytsing.qingcharms.QingCharmsPlugin;
import net.pytsing.qingcharms.util.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders and handles the dedicated charm bag GUI. Equipment GUI behavior is
 * intentionally not shared with this class.
 */
public final class BagGuiManager {

    private final QingCharmsPlugin plugin;
    private final CharmBagManager bagManager;

    public BagGuiManager(QingCharmsPlugin plugin, CharmBagManager bagManager) {
        this.plugin = plugin;
        this.bagManager = bagManager;
    }

    public boolean open(Player player) {
        return open(player, new CharmBagGuiHolder(
                bagManager.getDefaultSort(), BagCategoryFilter.ALL, 0,
                player == null ? null : player.getUniqueId(), false));
    }

    public boolean openFor(Player admin, Player target) {
        if (admin == null || target == null) {
            return false;
        }
        return open(admin, new CharmBagGuiHolder(
                bagManager.getDefaultSort(), BagCategoryFilter.ALL, 0,
                target.getUniqueId(), true));
    }

    public boolean open(Player viewer, CharmBagGuiHolder holder) {
        if (viewer == null || holder == null || !bagManager.isEnabled()) {
            return false;
        }
        if (holder.isAdminView()) {
            if (!viewer.hasPermission("qingcharms.command.admin")) {
                MessageUtils.send(viewer, "command.open.no-permission");
                return false;
            }
        } else {
            String permission = bagManager.getPermission();
            if (!permission.isEmpty() && !viewer.hasPermission(permission)) {
                MessageUtils.send(viewer, "charm-bag.no-permission");
                return false;
            }
        }

        try {
            Player owner = owner(viewer, holder);
            render(viewer, owner, holder);
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not open charm bag: " + exception.getMessage());
            MessageUtils.send(viewer, "charm-bag.storage-error");
            return false;
        }
    }

    public void refreshOpen() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getOpenInventory() == null
                    || viewer.getOpenInventory().getTopInventory() == null
                    || !(viewer.getOpenInventory().getTopInventory().getHolder()
                            instanceof CharmBagGuiHolder)) {
                continue;
            }
            CharmBagGuiHolder holder = (CharmBagGuiHolder)
                    viewer.getOpenInventory().getTopInventory().getHolder();
            open(viewer, holder);
        }
    }

    public void handleClick(Player viewer, InventoryClickEvent event) {
        if (viewer == null || event == null || event.getInventory() == null) {
            return;
        }
        CharmBagGuiHolder holder = holder(event);
        if (holder == null || event.getClickedInventory() == null) {
            return;
        }

        Player owner;
        try {
            owner = owner(viewer, holder);
        } catch (RuntimeException exception) {
            MessageUtils.send(viewer, "charm-bag.storage-error");
            return;
        }

        int topSize = event.getInventory().getSize();
        if (event.getRawSlot() >= topSize) {
            if (isSingleDeposit(event)) {
                depositFromInventory(owner, viewer, event.getCurrentItem().clone(),
                        event.getSlot());
                renderNextTick(viewer, holder);
            }
            return;
        }

        int rawSlot = event.getRawSlot();
        int controlStart = (bagManager.getRows() - 1) * 9;
        if (rawSlot >= controlStart) {
            handleControl(viewer, owner, holder, rawSlot - controlStart);
            return;
        }
        if (isCursorDeposit(event)) {
            depositFromCursor(owner, viewer, event.getCursor().clone());
            event.setCursor(viewer.getItemOnCursor());
            renderNextTick(viewer, holder);
            return;
        }

        String uniqueId = holder.getEntryId(rawSlot);
        if (uniqueId != null) {
            handleEntry(viewer, owner, holder, uniqueId, event.getClick());
        } else if (event.getCursor() != null
                && event.getCursor().getType() != Material.AIR) {
            MessageUtils.send(viewer, "charm-bag.not-charm");
        }
    }

    public boolean isSingleDeposit(InventoryClickEvent event) {
        return event != null
                && (event.getClick() == ClickType.SHIFT_LEFT
                    || event.getClick() == ClickType.SHIFT_RIGHT)
                && event.getInventory() != null
                && event.getRawSlot() >= event.getInventory().getSize()
                && event.getCurrentItem() != null
                && event.getCurrentItem().getType() != Material.AIR
                && plugin.getCharmManager().getCharmByItem(event.getCurrentItem()).isPresent();
    }

    private boolean isCursorDeposit(InventoryClickEvent event) {
        if (event == null || (event.getClick() != ClickType.LEFT
                && event.getClick() != ClickType.RIGHT)) {
            return false;
        }
        CharmBagGuiHolder holder = holder(event);
        int controlStart = (bagManager.getRows() - 1) * 9;
        if (holder == null || event.getRawSlot() < 0
                || event.getRawSlot() >= controlStart
                || holder.getEntryId(event.getRawSlot()) != null) {
            return false;
        }
        return event.getCursor() != null
                && event.getCursor().getType() != Material.AIR
                && plugin.getCharmManager().getCharmByItem(event.getCursor()).isPresent();
    }

    private void depositFromInventory(Player owner, Player donor,
                                      ItemStack item, int sourceSlot) {
        sendDepositResult(donor,
                bagManager.deposit(owner, donor, item, sourceSlot));
    }

    private void depositFromCursor(Player owner, Player donor, ItemStack item) {
        sendDepositResult(donor,
                bagManager.depositFromCursor(owner, donor, item));
    }

    private void sendDepositResult(Player viewer, CharmBagManager.DepositResult result) {
        switch (result) {
            case SUCCESS:
                MessageUtils.send(viewer, "charm-bag.deposit.success");
                break;
            case FULL:
                MessageUtils.send(viewer, "charm-bag.deposit.full");
                break;
            case STORAGE_ERROR:
                MessageUtils.send(viewer, "charm-bag.storage-error");
                break;
            case NOT_CHARM:
            default:
                MessageUtils.send(viewer, "charm-bag.not-charm");
                break;
        }
    }

    private void handleControl(Player viewer, Player owner,
                               CharmBagGuiHolder holder, int control) {
        try {
            List<CharmBagEntry> all = bagManager.getEntries(owner);
            List<String> categories = bagManager.getCategoryFilter().categories(all);
            if (control == 0) {
                renderNextTick(viewer, holder.copy(holder.getSortMode(),
                        holder.getCategory(), holder.getPage() - 1));
            } else if (control == 1) {
                renderNextTick(viewer, holder.copy(holder.getSortMode(),
                        bagManager.getCategoryFilter().next(categories, holder.getCategory()),
                        0));
            } else if (control == 2) {
                renderNextTick(viewer, holder.copy(holder.getSortMode().next(),
                        holder.getCategory(), 0));
            } else if (control == 3) {
                renderNextTick(viewer, holder.copy(BagSortMode.NAME,
                        holder.getCategory(), 0));
            } else if (control == 4) {
                renderNextTick(viewer, holder.copy(BagSortMode.LEVEL,
                        holder.getCategory(), 0));
            } else if (control == 5) {
                renderNextTick(viewer, holder.copy(BagSortMode.RARITY,
                        holder.getCategory(), 0));
            } else if (control == 6) {
                renderNextTick(viewer, holder.copy(BagSortMode.TYPE,
                        holder.getCategory(), 0));
            } else if (control == 7) {
                depositAll(owner, viewer);
                renderNextTick(viewer, holder);
            } else if (control == 8) {
                renderNextTick(viewer, holder.copy(holder.getSortMode(),
                        holder.getCategory(), holder.getPage() + 1));
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not update charm bag: " + exception.getMessage());
            MessageUtils.send(viewer, "charm-bag.storage-error");
        }
    }

    private void handleEntry(Player viewer, Player owner, CharmBagGuiHolder holder,
                             String uniqueId, ClickType click) {
        if (click == ClickType.LEFT) {
            withdraw(viewer, owner, holder, uniqueId);
        }
    }

    private void withdraw(Player viewer, Player owner, CharmBagGuiHolder holder,
                          String uniqueId) {
        CharmBagManager.WithdrawResult result =
                bagManager.withdraw(owner, viewer, uniqueId);
        switch (result) {
            case SUCCESS:
                MessageUtils.send(viewer, "charm-bag.withdraw.success");
                renderNextTick(viewer, holder);
                break;
            case NO_SPACE:
                MessageUtils.send(viewer, "charm-bag.withdraw.no-space");
                break;
            case STORAGE_ERROR:
                MessageUtils.send(viewer, "charm-bag.storage-error");
                break;
            case NOT_FOUND:
            default:
                MessageUtils.send(viewer, "charm-bag.not-found");
                break;
        }
    }

    private void depositAll(Player owner, Player donor) {
        List<ItemStack> items = new ArrayList<ItemStack>();
        List<Integer> slots = new ArrayList<Integer>();
        ItemStack[] contents = donor.getInventory().getContents();
        for (int index = 0; index < contents.length; index++) {
            if (contents[index] != null && contents[index].getType() != Material.AIR) {
                items.add(contents[index].clone());
                slots.add(Integer.valueOf(index));
            }
        }
        boolean stored = false;
        boolean failed = false;
        for (int index = 0; index < items.size(); index++) {
            CharmBagManager.DepositResult result = bagManager.deposit(
                    owner, donor, items.get(index), slots.get(index).intValue());
            if (result == CharmBagManager.DepositResult.SUCCESS) {
                stored = true;
            } else if (result != CharmBagManager.DepositResult.NOT_CHARM) {
                failed = true;
            }
        }
        if (stored) {
            MessageUtils.send(donor, "charm-bag.deposit.success");
        }
        if (failed) {
            MessageUtils.send(donor, "charm-bag.deposit.full");
        }
    }

    private void renderNextTick(final Player viewer,
                                final CharmBagGuiHolder holder) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!viewer.isOnline()) {
                    return;
                }
                try {
                    Player owner = owner(viewer, holder);
                    render(viewer, owner, holder);
                    refreshOtherOpen(viewer);
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("Could not refresh charm bag: "
                            + exception.getMessage());
                    MessageUtils.send(viewer, "charm-bag.storage-error");
                }
            }
        });
    }

    private void refreshOtherOpen(Player excludedViewer) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(excludedViewer)
                    || viewer.getOpenInventory() == null
                    || viewer.getOpenInventory().getTopInventory() == null
                    || !(viewer.getOpenInventory().getTopInventory().getHolder()
                            instanceof CharmBagGuiHolder)) {
                continue;
            }
            CharmBagGuiHolder holder = (CharmBagGuiHolder)
                    viewer.getOpenInventory().getTopInventory().getHolder();
            open(viewer, holder);
        }
    }

    private void render(Player viewer, Player owner, CharmBagGuiHolder requested) {
        List<CharmBagEntry> all = bagManager.getEntries(owner);
        List<CharmBagEntry> filtered =
                bagManager.getCategoryFilter().filter(all, requested.getCategory());
        List<CharmBagEntry> sorted =
                bagManager.getSorter().sort(filtered, requested.getSortMode());
        int pageSize = bagManager.getPageSize();
        int pageCount = Math.max(1, (sorted.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requested.getPage(), pageCount - 1));
        CharmBagGuiHolder holder = requested.copy(
                requested.getSortMode(), requested.getCategory(), page);

        int size = bagManager.getRows() * 9;
        String title = bagManager.getTitle();
        if (requested.isAdminView()) {
            title = title + " - " + owner.getName();
        }
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        Inventory inventory = Bukkit.createInventory(holder, size,
                MessageUtils.color(title));
        holder.setInventory(inventory);
        holder.clearEntrySlots();

        int start = page * pageSize;
        int end = Math.min(sorted.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            int slot = index - start;
            CharmBagEntry entry = sorted.get(index);
            holder.putEntrySlot(slot, entry.getUniqueId());
            inventory.setItem(slot, displayItem(entry));
        }

        int control = (bagManager.getRows() - 1) * 9;
        inventory.setItem(control, controlItem(Material.PAPER, "&e上一页",
                "&7当前：" + (page + 1) + "/" + pageCount));
        inventory.setItem(control + 1, controlItem(Material.CHEST,
                "&e分类", "&7当前：" + requested.getCategory()));
        inventory.setItem(control + 2, controlItem(Material.COMPASS,
                "&e排序", "&7当前：" + sortName(requested.getSortMode())));
        inventory.setItem(control + 3, controlItem(Material.NAME_TAG, "&f按名称排序", ""));
        inventory.setItem(control + 4, controlItem(Material.EXP_BOTTLE, "&f按等级排序", ""));
        inventory.setItem(control + 5, controlItem(Material.DIAMOND, "&f按稀有度排序", ""));
        inventory.setItem(control + 6, controlItem(Material.BOOK, "&f按类型排序", ""));
        inventory.setItem(control + 7, controlItem(Material.HOPPER, "&a存入全部护符", ""));
        inventory.setItem(control + 8, controlItem(Material.PAPER, "&e下一页",
                "&7当前：" + (page + 1) + "/" + pageCount));

        viewer.openInventory(inventory);
    }

    private ItemStack displayItem(CharmBagEntry entry) {
        ItemStack item = net.pytsing.qingcharms.api.ItemUniqueId
                .withoutIdentityLore(entry.getItem());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        List<String> lore = meta.hasLore()
                ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
        lore.add("");
        lore.add("&8等级: &7" + entry.getLevel());
        lore.add("&8分类: &7" + entry.getCategory());
        if (!entry.getType().isEmpty()) {
            lore.add("&8类型: &7" + entry.getType());
        }
        if (!entry.getSourceDescription().isEmpty()) {
            lore.add("&8获取来源: &7" + entry.getSourceDescription());
        }

        meta.setLore(color(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack controlItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtils.color(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> lines = new ArrayList<String>();
                lines.add(lore);
                meta.setLore(color(lines));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private Player owner(Player viewer, CharmBagGuiHolder holder) {
        UUID ownerId = holder.getOwnerId() == null
                ? viewer.getUniqueId() : holder.getOwnerId();
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null) {
            throw new IllegalStateException("charm bag owner is offline");
        }
        return owner;
    }

    private static List<String> color(List<String> lines) {
        List<String> result = new ArrayList<String>(lines.size());
        for (String line : lines) {
            result.add(MessageUtils.color(line));
        }
        return result;
    }

    private static CharmBagGuiHolder holder(InventoryClickEvent event) {
        if (event.getInventory() == null
                || !(event.getInventory().getHolder() instanceof CharmBagGuiHolder)) {
            return null;
        }
        return (CharmBagGuiHolder) event.getInventory().getHolder();
    }

    private static String sortName(BagSortMode mode) {
        switch (mode) {
            case NAME:
                return "名称";
            case LEVEL:
                return "等级";
            case TYPE:
                return "类型";
            case RARITY:
            default:
                return "稀有度";
        }
    }
}