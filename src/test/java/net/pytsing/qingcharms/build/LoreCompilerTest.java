package net.pytsing.qingcharms.build;

import net.pytsing.qingcharms.model.PlayerData;
import net.pytsing.qingcharms.support.BukkitFixture;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class LoreCompilerTest {

    @BeforeClass
    public static void installBukkitInterfaces() {
        BukkitFixture.install();
    }

    @Test
    public void keepsSameAttributeOncePerActiveBuild() {
        BuildRule first = build("first");
        BuildRule second = build("second");
        BuildResolution resolution = new BuildResolution(new BuildSnapshot(
                UUID.randomUUID(), Collections.<String>emptySet(), new TagSnapshot(null),
                Arrays.asList(first, second)));

        ItemStack item = new LoreCompiler().createLoreItem(
                new PlayerData(UUID.randomUUID()), resolution);

        assertNotNull(item);
        assertEquals(Arrays.asList("暴击率: +10%", "暴击率: +10%"),
                item.getItemMeta().getLore());
    }

    private static BuildRule build(String id) {
        return new BuildRule(id, id,
                Collections.singletonList(new BuildCondition("火", 1, "gteq")),
                Collections.singletonList("暴击率: +10%"));
    }
}
