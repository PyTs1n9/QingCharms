package net.pytsing.qingcharms.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class PlayerDataRollbackTest {

    @Test
    public void restoresEquipmentAndIdentityStateAfterFailedOperation() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        ItemStack necklace = new ItemStack(Material.DIAMOND, 1);
        ItemStack ring = new ItemStack(Material.GOLD_INGOT, 1);
        Map<String, ItemStack> equipmentBefore = new LinkedHashMap<String, ItemStack>();
        equipmentBefore.put("necklace", necklace);
        Map<String, ItemIdentity> identitiesBefore = new LinkedHashMap<String, ItemIdentity>();
        identitiesBefore.put("inventory:0", new ItemIdentity("before", "fingerprint"));

        data.restoreEquipmentState(equipmentBefore, identitiesBefore);

        Map<String, ItemStack> failedEquipment = new LinkedHashMap<String, ItemStack>();
        failedEquipment.put("ring", ring);
        Map<String, ItemIdentity> failedIdentities = new LinkedHashMap<String, ItemIdentity>();
        failedIdentities.put("inventory:0", new ItemIdentity("after", "fingerprint"));
        data.restoreEquipmentState(failedEquipment, failedIdentities);

        data.restoreEquipmentState(equipmentBefore, identitiesBefore);

        assertEquals(Material.DIAMOND, data.getEquipped("necklace").getType());
        assertNull(data.getEquipped("ring"));
        assertEquals(identitiesBefore, data.getItemIdentities());
    }
}
