package net.pytsing.qingcharms.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Charm identity and equipment constraints only. Combat attributes are read from
 * the complete item Lore by ILS; QingCharms does not parse or calculate them.
 */
public final class CharmDefinition {

    private final String id;
    private final Material material;
    private final short data;
    private final String displayName;
    private final List<String> lore;
    private final String normalizedDisplayName;
    private final Set<String> normalizedLore;
    private final String normalizedEquipLore;
    private final ItemStack itemTemplate;
    private final String permission;
    private final CharmType type;
    private final String equipLore;
    private final String slot;
    private final String slotLore;
    private final Set<String> classes;
    private final int requiredLevel;

    public CharmDefinition(String id, Material material, String displayName,
                           List<String> lore, String permission) {
        this(id, material, displayName, lore, null, CharmType.NONE, null,
                null, null, (short) -1, permission,
                Collections.<String>emptyList(), 0);
    }

    public CharmDefinition(String id, Material material, String displayName,
                           List<String> lore, ItemStack itemTemplate, CharmType type,
                           String equipLore, String slot, String slotLore,
                           short data, String permission) {
        this(id, material, displayName, lore, itemTemplate, type, equipLore,
                slot, slotLore, data, permission,
                Collections.<String>emptyList(), 0);
    }

    public CharmDefinition(String id, Material material, String displayName,
                           List<String> lore, ItemStack itemTemplate, CharmType type,
                           String equipLore, String slot, String slotLore,
                           short data, String permission,
                           Collection<String> classes) {
        this(id, material, displayName, lore, itemTemplate, type, equipLore,
                slot, slotLore, data, permission, classes, 0);
    }

    public CharmDefinition(String id, Material material, String displayName,
                           List<String> lore, ItemStack itemTemplate, CharmType type,
                           String equipLore, String slot, String slotLore,
                           short data, String permission,
                           Collection<String> classes, int requiredLevel) {
        this.id = id.toLowerCase(Locale.US);
        this.material = material;
        this.data = data;
        this.displayName = displayName;
        this.lore = Collections.unmodifiableList(new ArrayList<String>(lore));
        this.normalizedDisplayName = normalize(displayName);
        this.normalizedLore = normalizedSet(lore);
        this.normalizedEquipLore = normalize(equipLore);
        this.itemTemplate = itemTemplate == null ? null : itemTemplate.clone();
        this.permission = permission;
        this.type = type == null ? CharmType.NONE : type;
        this.equipLore = equipLore == null ? null : equipLore.trim();
        this.slot = slot == null || slot.trim().isEmpty() ? null : slot.trim();
        this.slotLore = slotLore == null || slotLore.trim().isEmpty()
                ? null : slotLore.trim();
        this.classes = immutableValues(classes);
        this.requiredLevel = Math.max(0, requiredLevel);
    }

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public int getData() {
        return data;
    }

    public String getDisplayName() {
        return displayName == null ? id : displayName;
    }

    /**
     * Name used for item matching, or null when this charm does not compare
     * names at all (no display name was configured or found in its template).
     */
    public String getNormalizedDisplayName() {
        return normalizedDisplayName;
    }

    public List<String> getLore() {
        return lore;
    }

    public ItemStack getItemTemplate() {
        return itemTemplate == null ? null : itemTemplate.clone();
    }

    public String getPermission() {
        return permission;
    }

    public CharmType getType() {
        return type;
    }

    public String getSlot() {
        return slot;
    }

    public String getTypeValue() {
        return slot == null ? type.getConfigName() : slot;
    }

    public String getSlotId() {
        return getTypeValue();
    }

    public String getEquipLore() {
        return equipLore;
    }

    public String getSlotLore() {
        return slotLore;
    }

    public Set<String> getClasses() {
        return classes;
    }

    /** Alias for {@link #getClasses()}. */
    public Set<String> getRequiredClassIds() {
        return classes;
    }

    /**
     * Returns the required Minecraft player level. This is intentionally
     * separate from charm progression level fields.
     */
    public int getRequiredLevel() {
        return requiredLevel;
    }

    /** Alias for {@link #getRequiredLevel()}. */
    public int getRequiredPlayerLevel() {
        return requiredLevel;
    }

    public boolean matches(ItemStack candidate) {
        return matches(CharmItemSignature.from(candidate));
    }

    public boolean matches(CharmItemSignature candidate) {
        if (candidate == null || candidate.getMaterial() != material) {
            return false;
        }
        if (data >= 0 && candidate.getDurability() != data) {
            return false;
        }
        if (normalizedDisplayName != null
                && !normalizedDisplayName.equals(candidate.getNormalizedName())) {
            return false;
        }
        return normalizedLore.isEmpty()
                || candidate.getNormalizedLore().containsAll(normalizedLore);
    }

    public boolean hasRequiredEquipLore(ItemStack candidate) {
        if (normalizedEquipLore == null || normalizedEquipLore.isEmpty()) {
            return true;
        }
        CharmItemSignature signature = CharmItemSignature.from(candidate);
        return signature != null
                && signature.getNormalizedLore().contains(normalizedEquipLore);
    }

    public boolean canEquipIn(CharmType slotType) {
        return type != CharmType.NONE && type == slotType && equipLore != null
                && !equipLore.isEmpty();
    }

    private static Set<String> immutableValues(Collection<String> values) {
        Set<String> copy = new LinkedHashSet<String>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    copy.add(value.trim().toLowerCase(Locale.US));
                }
            }
        }
        return Collections.unmodifiableSet(copy);
    }

    private static Set<String> normalizedSet(List<String> values) {
        Set<String> normalized = new LinkedHashSet<String>();
        if (values != null) {
            for (String value : values) {
                normalized.add(normalize(value));
            }
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? null : value.replace('\u00a7', '&').trim();
    }

    @Override
    public String toString() {
        return id;
    }
}
