package net.pytsing.qingcharms.manager;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class CharmTypeInferenceTest {

    @Test
    public void infersOnlyTypeMarkersFoundInLore() {
        assertEquals("项链", CharmTypeInference.inferUniqueLoreType(
                Collections.singletonList("§7饰品类型：项链"),
                Arrays.asList("耳环", "项链", "戒指")));
    }

    @Test
    public void rejectsLoreWithNoTypeOrMultipleTypes() {
        assertEquals("", CharmTypeInference.inferUniqueLoreType(
                Collections.singletonList("§9防御: +100%"),
                Arrays.asList("耳环", "项链")));
        assertEquals("", CharmTypeInference.inferUniqueLoreType(
                Arrays.asList("项链", "戒指"), Arrays.asList("耳环", "项链", "戒指")));
    }

    @Test
    public void rejectsOverlappingGuiTypesAsAmbiguous() {
        assertEquals(Arrays.asList("宝石", "天空宝石"),
                CharmTypeInference.findLoreTypeMatches(
                        Collections.singletonList("天空宝石"),
                        Arrays.asList("宝石", "天空宝石")));
    }

    @Test
    public void ignoresGuiTypesWithNoVisibleText() {
        assertEquals("", CharmTypeInference.inferUniqueLoreType(
                Collections.singletonList("攻击: +100"),
                Arrays.asList(null, "", " ", "§a")));
        assertEquals(Collections.singletonList("项链"),
                CharmTypeInference.findLoreTypeMatches(Collections.singletonList("项链"),
                        Arrays.asList("§a", "项链")));
    }
}
