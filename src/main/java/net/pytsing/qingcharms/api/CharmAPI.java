package net.pytsing.qingcharms.api;

import net.pytsing.qingcharms.build.BuildResolution;
import net.pytsing.qingcharms.model.CharmDefinition;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Aggregated QingCharms service.
 *
 * <p>{@link CharmQueryAPI} contains read-only item and player queries.
 * {@link CharmOperationAPI} contains cancellable equipment operations and
 * trigger-notification dispatch. Older methods remain on this interface for
 * source and binary compatibility.</p>
 *
 * @since 0.0.1
 */
public interface CharmAPI extends CharmQueryAPI, CharmOperationAPI {

    /** API contract version for integrations compiled against this surface. */
    String API_VERSION = "1.0.0";

    /**
     * @deprecated Material alone cannot uniquely identify a charm.
     * Use {@link #getCharmByMaterial(Material, ItemStack)} instead.
     */
    @Deprecated
    Optional<CharmDefinition> getCharmByMaterial(Material material);

    /** Compatibility overload that verifies the candidate item. */
    default Optional<CharmDefinition> getCharmByMaterial(Material material,
                                                        ItemStack candidate) {
        if (material == null || candidate == null
                || candidate.getType() != material) {
            return Optional.empty();
        }
        return getCharmByItem(candidate);
    }

    /** Legacy item-aware charm lookup. Prefer {@link CharmQueryAPI#isCharm}. */
    Optional<CharmDefinition> getCharmByItem(ItemStack item);

    /** Returns all registered charm definitions. */
    Collection<CharmDefinition> getCharms();

    /**
     * Returns complete equipped ItemStacks, including Lore.
     */
    EquipmentSnapshot getEquipmentSnapshot(Player player);

    /**
     * Resolves build tags and rules. QingCharms does not calculate ILS combat
     * attributes from this result.
     */
    BuildResolution resolveBuild(Player player);

    /** Returns active class IDs used by build constraints. */
    Set<String> getActiveClasses(Player player);

    /** Returns class recommendation tags; no item data is modified. */
    Set<String> getRecommendedTags(Player player);
}