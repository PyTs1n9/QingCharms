package net.pytsing.qingcharms.manager;

import net.pytsing.qingcharms.model.CharmItemSignature;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Guesses a charm's equip type from the item's own display name and Lore.
 *
 * <p>Only types that already exist as equip slots in gui.yml are candidates, and
 * the comparison reuses {@link CharmItemSignature#normalizeSearch(String)}, which
 * is the same normalization the equipment Lore gate uses. A successfully inferred
 * type therefore always satisfies that gate.</p>
 */
public final class CharmTypeInference {

    private CharmTypeInference() {
    }

    /**
     * @return the inferred type, or an empty string when nothing matched.
     */
    public static String infer(ItemStack item, Collection<String> slotTypes) {
        if (item == null || slotTypes == null || slotTypes.isEmpty()) {
            return "";
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return "";
        }

        List<String> lines = new ArrayList<String>();
        if (meta.hasDisplayName()) {
            lines.add(meta.getDisplayName());
        }
        if (meta.hasLore() && meta.getLore() != null) {
            lines.addAll(meta.getLore());
        }

        for (String line : lines) {
            String normalizedLine = CharmItemSignature.normalizeSearch(line);
            if (normalizedLine.isEmpty()) {
                continue;
            }
            String best = "";
            int bestLength = 0;
            for (String type : slotTypes) {
                if (type == null) {
                    continue;
                }
                String candidate = CharmItemSignature.normalizeSearch(type);
                if (candidate.isEmpty() || !normalizedLine.contains(candidate)) {
                    continue;
                }
                if (candidate.length() > bestLength) {
                    best = type;
                    bestLength = candidate.length();
                }
            }
            if (!best.isEmpty()) {
                return best.trim();
            }
        }
        return "";
    }

    /** Returns the only GUI slot type named in Lore, or empty when ambiguous. */
    public static String inferUniqueLoreType(Collection<String> lore,
                                             Collection<String> slotTypes) {
        List<String> matches = findLoreTypeMatches(lore, slotTypes);
        return matches.size() == 1 ? matches.get(0) : "";
    }

    private static void addIgnoreCase(Collection<String> values, String value) {
        for (String existing : values) {
            if (existing.equalsIgnoreCase(value)) {
                return;
            }
        }
        values.add(value);
    }

    public static List<String> findLoreTypeMatches(Collection<String> lore,
                                                    Collection<String> slotTypes) {
        if (lore == null || slotTypes == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> matches = new LinkedHashSet<String>();
        for (String line : lore) {
            String normalizedLine = CharmItemSignature.normalizeSearch(line);
            if (normalizedLine.isEmpty()) {
                continue;
            }
            for (String type : slotTypes) {
                String candidate = CharmItemSignature.normalizeSearch(type);
                if (!candidate.isEmpty() && normalizedLine.contains(candidate)) {
                    addIgnoreCase(matches, type.trim());
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<String>(matches));
    }

    /** True when the equipment Lore gate would accept this type for the item. */
    public static boolean loreContainsType(ItemStack item, String type) {
        if (item == null || type == null || type.trim().isEmpty()) {
            return false;
        }
        return CharmItemSignature.from(item).containsSearchLore(type.trim());
    }

    public static boolean containsType(Collection<String> slotTypes, String type) {
        if (slotTypes == null || type == null) {
            return false;
        }
        String expected = type.trim();
        for (String candidate : slotTypes) {
            if (candidate != null && candidate.trim().equalsIgnoreCase(expected)) {
                return true;
            }
        }
        return false;
    }
}
