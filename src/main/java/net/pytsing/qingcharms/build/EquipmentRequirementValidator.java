package net.pytsing.qingcharms.build;

import net.pytsing.qingcharms.model.CharmDefinition;
import net.pytsing.qingcharms.model.CharmItemSignature;
import net.pytsing.qingcharms.util.PermissionUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Shared equipment gate used by GUI, bag-to-inventory equip attempts and API
 * operations.
 */
public final class EquipmentRequirementValidator {

    private EquipmentRequirementValidator() {
    }

    public static EquipmentRequirementResult validateUsage(
            Player player, CharmDefinition charm, ClassResolver classResolver) {
        if (player == null || charm == null) {
            return EquipmentRequirementResult.invalid(
                    EquipmentRequirementResult.Reason.INVALID_ITEM);
        }
        if (charm.getRequiredLevel() > 0
                && player.getLevel() < charm.getRequiredLevel()) {
            return EquipmentRequirementResult.level(charm.getRequiredLevel());
        }
        if (!classResolver.canEquip(player, charm)) {
            return EquipmentRequirementResult.classes(
                    classResolver.getRequiredClassNames(charm.getClasses()));
        }
        if (!PermissionUtils.hasCharmPermission(player, charm.getPermission())) {
            return EquipmentRequirementResult.invalid(
                    EquipmentRequirementResult.Reason.CHARM_PERMISSION);
        }
        return EquipmentRequirementResult.valid();
    }

    public static EquipmentRequirementResult validate(
            Player player, ItemStack item, String slotId, CharmDefinition charm,
            ClassResolver classResolver) {
        if (player == null || charm == null || item == null
                || item.getType() == org.bukkit.Material.AIR
                || !charm.matches(item)) {
            return EquipmentRequirementResult.invalid(
                    EquipmentRequirementResult.Reason.INVALID_ITEM);
        }

        String expectedSlot = charm.getTypeValue();
        if (slotId == null || expectedSlot == null
                || !expectedSlot.equalsIgnoreCase(slotId.trim())) {
            return EquipmentRequirementResult.invalid(
                    EquipmentRequirementResult.Reason.WRONG_SLOT);
        }

        CharmItemSignature signature = CharmItemSignature.from(item);
        String equipLore = charm.getEquipLore();
        boolean loreMatches = equipLore != null && !equipLore.trim().isEmpty()
                ? signature.getNormalizedLore().contains(
                        CharmItemSignature.normalizeMatch(equipLore))
                : signature.containsSearchLore(expectedSlot);
        if (!loreMatches) {
            return EquipmentRequirementResult.invalid(
                    EquipmentRequirementResult.Reason.MISSING_LORE);
        }

        if (charm.getRequiredLevel() > 0
                && player.getLevel() < charm.getRequiredLevel()) {
            return EquipmentRequirementResult.level(charm.getRequiredLevel());
        }
        if (!classResolver.canEquip(player, charm)) {
            return EquipmentRequirementResult.classes(
                    classResolver.getRequiredClassNames(charm.getClasses()));
        }
        if (!PermissionUtils.hasCharmPermission(player, charm.getPermission())) {
            return EquipmentRequirementResult.invalid(
                    EquipmentRequirementResult.Reason.CHARM_PERMISSION);
        }
        return EquipmentRequirementResult.valid();
    }
}
