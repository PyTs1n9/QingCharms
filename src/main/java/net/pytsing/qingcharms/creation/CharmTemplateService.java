package net.pytsing.qingcharms.creation;

import net.pytsing.qingcharms.api.ItemUniqueId;
import net.pytsing.qingcharms.manager.CharmManager;
import net.pytsing.qingcharms.manager.CharmTypeInference;
import net.pytsing.qingcharms.model.CharmDefinition;
import net.pytsing.qingcharms.util.InventoryUtils;
import net.pytsing.qingcharms.util.ItemStackYaml;
import net.pytsing.qingcharms.util.YamlUtils;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Main-thread template export: validate, persist, deliver, then publish. */
public final class CharmTemplateService {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");
    private final Store store;
    private final FileAccess files;

    public CharmTemplateService(final CharmManager manager) {
        this(new Store() {
            public File getFolder() { return manager.getQcItemsFolder(); }
            public File findFile(String id) {
                List<File> matches = manager.findQcItemFiles(id);
                if (matches.size() > 1) { throw new DuplicateTemplateException(); }
                return matches.isEmpty() ? null : matches.get(0);
            }
            public Collection<String> getSlotTypes() { return manager.getEquipSlotTypes(); }
            public void invalidatePreparedReloads() { manager.invalidatePreparedReloads(); }
            public ItemStack createInstance(CharmDefinition charm) {
                return manager.createCharmInstance(charm);
            }
            public Update prepare(String id, ItemStack template) {
                final CharmManager.PreparedQcItemUpdate update =
                        manager.prepareQcItemUpdate(id, template);
                return new Update() {
                    public boolean isFailed() { return update.isFailed(); }
                    public String getError() { return update.getError(); }
                    public CharmDefinition getCharm() { return update.getCharm(); }
                    public boolean commit() { return manager.commitQcItemUpdate(update); }
                };
            }
        }, new DiskFiles());
    }

    CharmTemplateService(Store store, FileAccess files) {
        this.store = store;
        this.files = files;
    }

    public Result export(Player player, String requestedId, boolean overwrite) {
        String id = requestedId == null ? "" : requestedId.toLowerCase(Locale.US);
        if (!ID_PATTERN.matcher(id).matches()) {
            return result(Status.INVALID_ID, id, null, null, null);
        }
        ItemStack held = player == null ? null : player.getItemInHand();
        if (!isItem(held)) {
            return result(Status.EMPTY_HAND, id, null, null, null);
        }

        File target = null;
        byte[] original = null;
        ItemStack[] before = null;
        boolean fileAttempted = false;
        boolean reloadsInvalidated = false;
        boolean inventoryAttempted = false;
        Status failure = Status.INVALID_TEMPLATE;
        try {
            File folder = store.getFolder();
            if (folder == null) {
                return result(Status.WRITE_FAILED, id, null, null, "Invalid qcItems folder");
            }
            File existing = store.findFile(id);
            target = existing == null ? new File(folder, id + ".yml") : existing;
            if (Files.isSymbolicLink(target.toPath())
                    || (target.exists() && !target.isFile())) {
                return result(Status.WRITE_FAILED, id, target, null, "Not a regular template file");
            }
            boolean existed = target.exists();
            ItemStack template = ItemUniqueId.withoutIdentityLore(held.clone());
            template.setAmount(1);
            List<String> lore = template.hasItemMeta() && template.getItemMeta().hasLore()
                    ? template.getItemMeta().getLore() : Collections.<String>emptyList();
            List<String> types = CharmTypeInference.findLoreTypeMatches(lore, store.getSlotTypes());
            if (types.size() != 1) {
                return result(types.isEmpty() ? Status.MISSING_TYPE : Status.AMBIGUOUS_TYPE,
                        id, target, types, null);
            }
            Update update = store.prepare(id, template);
            if (update == null || update.isFailed() || update.getCharm() == null) {
                return result(prepareFailure(update), id, target, types,
                        update == null ? "No template candidate" : update.getError());
            }
            if (existed && !overwrite) {
                return result(Status.ALREADY_EXISTS, id, target, types, null);
            }
            ItemStack signed = store.createInstance(update.getCharm());
            if (!isItem(signed)) {
                return result(Status.INVALID_TEMPLATE, id, target, types, "Could not sign template");
            }
            signed.setAmount(1);
            PlayerInventory inventory = player.getInventory();
            before = InventoryUtils.snapshot(inventory);
            ItemStack[] delivery = planDelivery(before, inventory.getHeldItemSlot(),
                    signed, inventory.getMaxStackSize());
            if (delivery == null) {
                return result(Status.NO_SPACE, id, target, types, null);
            }

            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("Item", ItemStackYaml.serializeForExport(template));
            yaml.set("charm.enabled", true);
            yaml.set("charm.permission", "qingcharms.charm." + id);
            failure = Status.WRITE_FAILED;
            original = existed ? files.read(target) : null;
            store.invalidatePreparedReloads();
            reloadsInvalidated = true;
            fileAttempted = true;
            files.save(target, yaml);

            failure = Status.LOAD_FAILED;
            YamlConfiguration restored = files.load(target);
            ItemStack restoredItem = restored == null ? null : restored.getItemStack("Item");
            if (!isItem(restoredItem) || restoredItem.getAmount() != 1
                    || !template.isSimilar(restoredItem)) {
                throw new IOException("Exported Item did not survive YAML round trip");
            }

            failure = Status.DELIVERY_FAILED;
            inventoryAttempted = true;
            inventory.setContents(delivery);
            failure = Status.STALE_RUNTIME;
            if (!update.commit()) {
                throw new IllegalStateException("Charm definitions changed during export");
            }
            return result(Status.SUCCESS, id, target, types, existed ? "overwritten" : "created");
        } catch (DuplicateTemplateException exception) {
            return result(Status.DUPLICATE_ID, id, target, null,
                    "Multiple qcItems files share this ID");
        } catch (IOException | RuntimeException exception) {
            String detail = exception.toString();
            boolean recoveryFailed = false;
            if (inventoryAttempted) {
                try {
                    InventoryUtils.restore(player.getInventory(), before);
                } catch (RuntimeException recovery) {
                    recoveryFailed = true;
                    detail += "; inventory recovery: " + recovery;
                }
            }
            if (fileAttempted) {
                try {
                    files.restore(target, original);
                } catch (IOException | RuntimeException recovery) {
                    recoveryFailed = true;
                    detail += "; file recovery: " + recovery;
                }
            }
            return result(recoveryFailed ? Status.RECOVERY_FAILED : failure,
                    id, target, null, detail);
        } finally {
            // A reload may have read the temporary on-disk version before rollback.
            // Invalidate it even when the runtime definition never changed.
            if (reloadsInvalidated) { store.invalidatePreparedReloads(); }
        }
    }

    private static Status prepareFailure(Update update) {
        String error = update == null ? "" : update.getError();
        if ("config-conflict".equals(error)) { return Status.CONFIG_CONFLICT; }
        if ("missing-type".equals(error)) { return Status.MISSING_TYPE; }
        if ("multiple-types".equals(error)) { return Status.AMBIGUOUS_TYPE; }
        return Status.INVALID_TEMPLATE;
    }

    /** Plan using storage slots only; armor/offhand may appear in getContents(). */
    static ItemStack[] planDelivery(ItemStack[] before, int heldSlot,
                                   ItemStack signed, int inventoryMaxStack) {
        int storageSize = before == null ? 0 : Math.min(36, before.length);
        if (heldSlot < 0 || heldSlot >= Math.min(9, storageSize)
                || !isItem(before[heldSlot]) || !isItem(signed)) {
            return null;
        }
        ItemStack[] planned = new ItemStack[before.length];
        for (int i = 0; i < before.length; i++) {
            planned[i] = before[i] == null ? null : before[i].clone();
        }
        ItemStack rest = before[heldSlot].clone();
        int remaining = rest.getAmount() - 1;
        planned[heldSlot] = signed.clone();
        planned[heldSlot].setAmount(1);
        int max = Math.min(rest.getMaxStackSize(), inventoryMaxStack);
        if (remaining > 0 && max <= 0) { return null; }
        // Merge first so empty slots remain available for other items.
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int i = 0; i < storageSize && remaining > 0; i++) {
                if (i == heldSlot) { continue; }
                ItemStack slot = planned[i];
                if (pass == 0 && isItem(slot) && rest.isSimilar(slot)) {
                    int moved = Math.min(remaining, Math.max(0, max - slot.getAmount()));
                    slot.setAmount(slot.getAmount() + moved);
                    remaining -= moved;
                } else if (pass == 1 && !isItem(slot)) {
                    int moved = Math.min(remaining, max);
                    ItemStack movedStack = rest.clone();
                    movedStack.setAmount(moved);
                    planned[i] = movedStack;
                    remaining -= moved;
                }
            }
        }
        return remaining == 0 ? planned : null;
    }

    private static boolean isItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getAmount() > 0;
    }

    private static Result result(Status status, String id, File file,
                                 List<String> types, String detail) {
        return new Result(status, id, file, types, detail);
    }

    public enum Status {
        SUCCESS, INVALID_ID, EMPTY_HAND, CONFIG_CONFLICT, ALREADY_EXISTS, DUPLICATE_ID,
        MISSING_TYPE, AMBIGUOUS_TYPE, NO_SPACE, INVALID_TEMPLATE, WRITE_FAILED,
        LOAD_FAILED, DELIVERY_FAILED, STALE_RUNTIME, RECOVERY_FAILED
    }

    public static final class Result {
        private final Status status;
        private final String id;
        private final File file;
        private final List<String> types;
        private final String detail;

        private Result(Status status, String id, File file, List<String> types, String detail) {
            this.status = status;
            this.id = id;
            this.file = file;
            this.types = types == null ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(types));
            this.detail = detail;
        }
        public Status getStatus() { return status; }
        public String getId() { return id; }
        public File getFile() { return file; }
        public List<String> getTypes() { return types; }
        public String getDetail() { return detail; }
        public boolean isSuccess() { return status == Status.SUCCESS; }
    }

    interface Store {
        File getFolder();
        File findFile(String id);
        Collection<String> getSlotTypes();
        Update prepare(String id, ItemStack template);
        ItemStack createInstance(CharmDefinition charm);
        default void invalidatePreparedReloads() { }
    }

    interface Update {
        boolean isFailed();
        String getError();
        CharmDefinition getCharm();
        boolean commit();
    }

    interface FileAccess {
        byte[] read(File file) throws IOException;
        void save(File file, YamlConfiguration yaml) throws IOException;
        YamlConfiguration load(File file) throws IOException;
        void restore(File file, byte[] original) throws IOException;
    }

    private static final class DuplicateTemplateException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
    }

    private static final class DiskFiles implements FileAccess {
        public byte[] read(File file) throws IOException {
            return Files.readAllBytes(file.toPath());
        }
        public void save(File file, YamlConfiguration yaml) throws IOException {
            YamlUtils.save(file, yaml);
        }
        public YamlConfiguration load(File file) throws IOException {
            YamlUtils.LoadResult loaded = YamlUtils.loadChecked(file, null);
            if (!loaded.isOk()) { throw new IOException("Could not load exported template", loaded.getError()); }
            return loaded.getConfiguration();
        }
        public void restore(File file, byte[] original) throws IOException {
            if (original == null) {
                Files.deleteIfExists(file.toPath());
                return;
            }
            Path temporary = Files.createTempFile(file.getAbsoluteFile().getParentFile().toPath(),
                    ".qc-restore-", ".tmp");
            try {
                Files.write(temporary, original, StandardOpenOption.TRUNCATE_EXISTING);
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    channel.force(true);
                }
                try {
                    Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
