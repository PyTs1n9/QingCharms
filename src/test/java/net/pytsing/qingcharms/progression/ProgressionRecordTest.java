package net.pytsing.qingcharms.progression;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ProgressionRecordTest {

    @Test
    public void savesAndLoadsProgressionStateAndManagedLore() {
        ProgressionRecord record = new ProgressionRecord("instance-1");
        record.setLevel(4);
        record.setEnhance(2);
        record.setBreakthrough(1);
        record.setStar(3);
        record.setAwakened(true);
        record.setKills(17L);
        record.setWearSeconds(42L);
        record.setBaseName("测试护符");
        record.setManagedLore(Arrays.asList("等级: 4/10", "生命: +5%"));

        YamlConfiguration yaml = new YamlConfiguration();
        record.save(yaml.createSection("state"));
        ProgressionRecord loaded = ProgressionRecord.load("instance-1",
                yaml.getConfigurationSection("state"));

        assertEquals(4, loaded.getLevel());
        assertEquals(2, loaded.getEnhance());
        assertEquals(1, loaded.getBreakthrough());
        assertEquals(3, loaded.getStar());
        assertTrue(loaded.isAwakened());
        assertEquals(17L, loaded.getKills());
        assertEquals(42L, loaded.getWearSeconds());
        assertEquals("测试护符", loaded.getBaseName());
        assertEquals(record.getManagedLore(), loaded.getManagedLore());
    }

    @Test
    public void copyPreservesManagedLore() {
        ProgressionRecord record = new ProgressionRecord("instance-1");
        record.setManagedLore(Arrays.asList("等级: 1/10"));

        assertEquals(record.getManagedLore(), record.copy().getManagedLore());
    }
}
