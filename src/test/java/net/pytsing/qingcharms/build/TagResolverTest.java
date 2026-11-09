package net.pytsing.qingcharms.build;

import net.pytsing.qingcharms.model.CharmDefinition;
import net.pytsing.qingcharms.model.CharmType;
import org.bukkit.Material;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TagResolverTest {

    @Test
    public void eachEquippedCharmContributesEachUniqueTagOnce() {
        Map<String, CharmDefinition> equipment =
                new LinkedHashMap<String, CharmDefinition>();
        equipment.put("one", charm("one", Arrays.asList("火", "火", "水")));
        equipment.put("two", charm("two", Arrays.asList("火")));

        TagSnapshot tags = new TagResolver().resolve(equipment);

        assertEquals(2, tags.getBuildCount("火"));
        assertEquals(1, tags.getBuildCount("水"));
    }

    private static CharmDefinition charm(String id, java.util.Collection<String> tags) {
        return new CharmDefinition(id, Material.PAPER, id, java.util.Collections.<String>emptyList(),
                null, CharmType.NONE, null, null, null, (short) -1, null,
                java.util.Collections.<String>emptyList(), 0, tags);
    }
}
