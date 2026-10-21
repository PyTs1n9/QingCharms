package net.pytsing.qingcharms.gui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public final class GuiDefinitionTest {

    @Test
    public void loadsNamedSlotsWithEditableRowsAndPositions() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(
                "gui:\n"
                        + "  row: 2\n"
                        + "  slots:\n"
                        + "    helmet:\n"
                        + "      slot: 3\n"
                        + "      type: helmet\n"
                        + "      action: equip\n"
                        + "    close:\n"
                        + "      position: 17\n"
                        + "      action: close\n");

        ConfigurationSection gui = yaml.getConfigurationSection("gui");
        GuiDefinition definition = GuiDefinition.fromConfig(gui);

        assertNotNull(definition);
        assertEquals(2, definition.getRows());
        assertEquals(2, definition.getSlots().size());
        assertEquals("helmet", definition.getSlot(3).getType());
        assertEquals("close", definition.getSlot(17).getAction());
    }

    @Test
    public void keepsNumericKeysAsPositions() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(
                "gui:\n"
                        + "  rows: 1\n"
                        + "  slots:\n"
                        + "    0:\n"
                        + "      type: first\n"
                        + "    8:\n"
                        + "      action: close\n");

        GuiDefinition definition = GuiDefinition.fromConfig(yaml.getConfigurationSection("gui"));

        assertNotNull(definition);
        assertEquals(1, definition.getRows());
        assertEquals("first", definition.getSlot(0).getType());
        assertEquals("close", definition.getSlot(8).getAction());
    }

    @Test
    public void dropsEquipSlotsWithoutUsableType() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(
                "gui:\n"
                        + "  row: 3\n"
                        + "  slots:\n"
                        + "    missing-type:\n"
                        + "      slot: 0\n"
                        + "      action: equip\n"
                        + "    none-type:\n"
                        + "      slot: 1\n"
                        + "      type: NONE\n"
                        + "      action: equip\n"
                        + "    blank-type:\n"
                        + "      slot: 2\n"
                        + "      type: \"  \"\n"
                        + "      action: unequip\n"
                        + "    typed:\n"
                        + "      slot: 4\n"
                        + "      type: 项链\n"
                        + "      action: equip\n"
                        + "    close:\n"
                        + "      slot: 8\n"
                        + "      action: close\n");

        GuiDefinition definition = GuiDefinition.fromConfig(yaml.getConfigurationSection("gui"));

        assertNotNull(definition);
        assertEquals(2, definition.getSlots().size());
        assertNull(definition.getSlot(0));
        assertNull(definition.getSlot(1));
        assertNull(definition.getSlot(2));
        assertEquals("项链", definition.getSlot(4).getType());
        assertEquals("close", definition.getSlot(8).getAction());
    }

    @Test
    public void keepsDecorativeAndUnknownActionSlotsWithoutType() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(
                "gui:\n"
                        + "  row: 1\n"
                        + "  slots:\n"
                        + "    filler:\n"
                        + "      slot: 0\n"
                        + "    legacy-interface:\n"
                        + "      slot: 1\n"
                        + "      type: 项链\n"
                        + "      action: interface\n");

        GuiDefinition definition = GuiDefinition.fromConfig(yaml.getConfigurationSection("gui"));

        assertNotNull(definition);
        assertEquals(2, definition.getSlots().size());
        assertEquals("none", definition.getSlot(0).getAction());
        assertEquals("interface", definition.getSlot(1).getAction());
        assertFalse(definition.getSlot(1).requiresType());
    }
}