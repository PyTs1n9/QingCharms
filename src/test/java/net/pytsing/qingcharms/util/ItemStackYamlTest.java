package net.pytsing.qingcharms.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ItemStackYamlTest {

    @Test
    public void exportsLegacyVariantsAsDataAndLoadsThemBack() throws Exception {
        Map<String, Object> source = new LinkedHashMap<String, Object>();
        source.put("type", "INK_SACK");
        source.put("amount", 1);
        source.put("damage", Short.valueOf((short) 12));
        Map<String, Object> serialized = ItemStackYaml.withLegacyDataField(source, true);
        assertEquals(Short.valueOf((short) 12), serialized.get("data"));
        assertFalse(serialized.containsKey("damage"));

        YamlConfiguration exported = new YamlConfiguration();
        exported.set("Item", serialized);
        String output = exported.saveToString();
        assertTrue(output.contains("data: 12"));
        assertFalse(output.contains("damage: 12"));

        YamlConfiguration imported = new YamlConfiguration();
        imported.loadFromString(ItemStackYaml.normalizeDataField(output));
        ItemStack restored = imported.getItemStack("Item");
        assertEquals("INK_SACK", restored.getType().name());
        assertEquals(12, restored.getDurability());
    }

    @Test
    public void keepsDamageableItemsDurabilityField() {
        Map<String, Object> source = new LinkedHashMap<String, Object>();
        source.put("type", "DIAMOND_SWORD");
        source.put("damage", Short.valueOf((short) 12));
        Map<String, Object> serialized = ItemStackYaml.withLegacyDataField(source, false);
        assertEquals(Short.valueOf((short) 12), serialized.get("damage"));
        assertFalse(serialized.containsKey("data"));
    }
}
