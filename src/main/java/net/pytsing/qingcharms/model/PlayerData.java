package net.pytsing.qingcharms.model;

import org.bukkit.Material;
import net.pytsing.qingcharms.progression.ProgressionRecord;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class PlayerData {

    private static final AtomicLong NEXT_EQUIPMENT_VERSION = new AtomicLong(System.currentTimeMillis() * 1000L);

    private final UUID owner;
    private final Set<String> charmIds = new LinkedHashSet<String>();
    private final Map<String, ItemStack> equipped = new LinkedHashMap<String, ItemStack>();
    private final Map<String, ProgressionRecord> progressions =
            new LinkedHashMap<String, ProgressionRecord>();
    private final Map<String, ItemIdentity> itemIdentities =
            new LinkedHashMap<String, ItemIdentity>();
    private final Map<String, String> identityClaims = new LinkedHashMap<String, String>();
    private final AtomicLong revision = new AtomicLong();
    private final AtomicLong equipmentVersion;
    private final AtomicBoolean dirty = new AtomicBoolean();

    public PlayerData(UUID owner) {
        this.owner = owner;
        this.equipmentVersion = new AtomicLong(NEXT_EQUIPMENT_VERSION.incrementAndGet());
    }

    public UUID getOwner() {
        return owner;
    }

    public boolean addCharm(String charmId) {
        boolean changed = charmIds.add(normalize(charmId));
        if (changed) {
            markChanged();
        }
        return changed;
    }

    public boolean removeCharm(String charmId) {
        boolean changed = charmIds.remove(normalize(charmId));
        if (changed) {
            markChanged();
        }
        return changed;
    }

    public boolean hasCharm(String charmId) {
        return charmIds.contains(normalize(charmId));
    }

    public Set<String> getCharmIds() {
        return Collections.unmodifiableSet(charmIds);
    }

    public ItemStack getEquipped(String slotId) {
        ItemStack item = equipped.get(normalize(slotId));
        return item == null ? null : item.clone();
    }

    public void setEquipped(String slotId, ItemStack item) {
        String key = normalize(slotId);
        if (key.isEmpty()) {
            return;
        }
        if (item == null || item.getType() == Material.AIR) {
            if (equipped.remove(key) != null) {
                markChanged();
                markEquipmentChanged();
            }
            removeItemIdentity("equipped:" + key);
        } else {
            String explicit = net.pytsing.qingcharms.api.ItemUniqueId.explicitFrom(item);
            ItemStack stored = net.pytsing.qingcharms.api.ItemUniqueId.withoutInstanceIdentityLore(item);
            if (!explicit.isEmpty()) {
                stored = net.pytsing.qingcharms.api.ItemUniqueId.withUniqueId(stored, explicit);
            }
            equipped.put(key, stored.clone());
            if (!explicit.isEmpty()) {
                setItemIdentity("equipped:" + key, explicit, stored);
            }
            markChanged();
            markEquipmentChanged();
        }
    }

    public ItemStack removeEquipped(String slotId) {
        ItemStack item = equipped.remove(normalize(slotId));
        if (item != null) {
            markChanged();
            markEquipmentChanged();
        }
        removeItemIdentity("equipped:" + normalize(slotId));
        return item == null ? null : item.clone();
    }

    public Set<String> getEquippedSlotIds() {
        return Collections.unmodifiableSet(equipped.keySet());
    }

    public Collection<ItemStack> getEquippedItems() {
        Collection<ItemStack> items = new ArrayList<ItemStack>(equipped.size());
        for (ItemStack item : equipped.values()) {
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }
        return items;
    }

    public ProgressionRecord getProgression(String uniqueId) {
        if (uniqueId == null || uniqueId.trim().isEmpty()) {
            return null;
        }
        return progressions.get(uniqueId.trim());
    }

    public String getItemUniqueId(String itemKey, ItemStack item) {
        String explicit = net.pytsing.qingcharms.api.ItemUniqueId.explicitFrom(item);
        if (!explicit.isEmpty()) {
            return explicit;
        }
        String key = normalize(itemKey);
        ItemIdentity identity = itemIdentities.get(key);
        if (identity == null || item == null
                || !identity.getFingerprint().equals(net.pytsing.qingcharms.api.ItemUniqueId.fingerprint(item))) {
            return "";
        }
        String claimedBy = identityClaims.get(identity.getUniqueId());
        if (claimedBy != null && !claimedBy.equals(key)) {
            return "";
        }
        identityClaims.put(identity.getUniqueId(), key);
        return identity.getUniqueId();
    }

    public void setItemIdentity(String itemKey, String uniqueId, ItemStack item) {
        String key = normalize(itemKey);
        String id = uniqueId == null ? "" : uniqueId.trim();
        String fingerprint = net.pytsing.qingcharms.api.ItemUniqueId.fingerprint(item);
        if (key.isEmpty() || id.isEmpty() || fingerprint.isEmpty()) {
            return;
        }
        itemIdentities.put(key, new ItemIdentity(id, fingerprint));
        identityClaims.put(id, key);
        markChanged();
    }

    public void removeItemIdentity(String itemKey) {
        String key = normalize(itemKey);
        ItemIdentity removed = itemIdentities.remove(key);
        if (removed != null) {
            if (key.equals(identityClaims.get(removed.getUniqueId()))) {
                identityClaims.remove(removed.getUniqueId());
            }
            markChanged();
        }
    }

    public Map<String, ItemIdentity> getItemIdentities() {
        return new LinkedHashMap<String, ItemIdentity>(itemIdentities);
    }

    public void restoreItemIdentities(Map<String, ItemIdentity> previous) {
        itemIdentities.clear();
        identityClaims.clear();
        if (previous != null) {
            for (Map.Entry<String, ItemIdentity> entry : previous.entrySet()) {
                ItemIdentity identity = entry.getValue();
                if (identity != null) {
                    itemIdentities.put(normalize(entry.getKey()), identity);
                }
            }
        }
        markChanged();
    }

    public void restoreEquipmentState(Map<String, ItemStack> previousEquipment,
                                      Map<String, ItemIdentity> previousIdentities) {
        equipped.clear();
        if (previousEquipment != null) {
            for (Map.Entry<String, ItemStack> entry : previousEquipment.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    equipped.put(normalize(entry.getKey()), entry.getValue().clone());
                }
            }
        }
        restoreItemIdentities(previousIdentities);
        markChanged();
        markEquipmentChanged();
    }

    public void putProgression(ProgressionRecord record) {
        if (record == null || record.getUniqueId().isEmpty()) {
            return;
        }
        progressions.put(record.getUniqueId(), record.copy());
        markChanged();
    }

    public Map<String, ProgressionRecord> getProgressions() {
        Map<String, ProgressionRecord> copy =
                new LinkedHashMap<String, ProgressionRecord>();
        for (Map.Entry<String, ProgressionRecord> entry : progressions.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().copy());
        }
        return Collections.unmodifiableMap(copy);
    }

    public long getEquipmentDataVersion() {
        return equipmentVersion.get();
    }

    public long getRevision() {
        return revision.get();
    }

    public boolean isDirty() {
        return dirty.get();
    }

    public void markClean(long savedRevision) {
        if (revision.get() == savedRevision) {
            dirty.set(false);
        }
    }

    public void resetDirty() {
        revision.set(0L);
        dirty.set(false);
    }

    private void markChanged() {
        revision.incrementAndGet();
        dirty.set(true);
    }

    private void markEquipmentChanged() {
        equipmentVersion.set(NEXT_EQUIPMENT_VERSION.incrementAndGet());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }
}





