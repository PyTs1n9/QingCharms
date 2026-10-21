package net.pytsing.qingcharms.api;

import net.pytsing.qingcharms.model.CharmDefinition;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only charm queries.
 *
 * <p>Returned item objects are copies. Implementations must not mutate the
 * supplied item, equipped items, player data, or Lore. QingCharms never parses
 * Lore attributes and never calculates ItemLoreStats values.</p>
 *
 * @since 0.1.0
 */
public interface CharmQueryAPI {

    /** Returns the registered charm definition, or an empty result. */
    Optional<CharmDefinition> getCharm(String charmId);

    /** Returns whether the item matches a registered charm definition. */
    boolean isCharm(ItemStack item);

    /**
     * Returns the item's unique ID from its final Lore, or a stable content ID
     * when no explicit Lore ID exists. The item is not modified.
     */
    String getUniqueId(ItemStack item);

    /** Returns a copy of the charm equipped in {@code slotId}, or {@code null}. */
    ItemStack getEquippedItem(Player player, String slotId);

    /** Returns copies of all equipped charm items in stable slot order. */
    List<ItemStack> getEquippedCharms(Player player);

    /** Returns an immutable copy of the charm IDs owned by the player. */
    Set<String> getOwnedCharmIds(Player player);
}