package net.pytsing.qingcharms.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class SoundUtilsTest {

    @Test
    public void resolvesLegacyAndModernSoundNames() {
        assertEquivalent("LEVEL_UP", "ENTITY_PLAYER_LEVELUP");
        assertEquivalent("CLICK", "UI_BUTTON_CLICK");
        assertEquivalent("ANVIL_LAND", "BLOCK_ANVIL_LAND");
        assertEquivalent("WITHER_SPAWN", "ENTITY_WITHER_SPAWN");
        assertEquivalent("ENDERDRAGON_GROWL", "ENTITY_ENDERDRAGON_GROWL");
        assertEquivalent("VILLAGER_NO", "ENTITY_VILLAGER_NO");
    }

    @Test
    public void normalizesConfiguredNames() {
        assertEquals("LEVEL_UP",
                SoundUtils.aliasOf("entity player levelup"));
        assertNotNull(SoundUtils.parse("block-anvil-land"));
    }

    @Test
    public void ignoresEmptyAndUnknownSounds() {
        assertNull(SoundUtils.parse(null));
        assertNull(SoundUtils.parse(" "));
        assertNull(SoundUtils.parse("none"));
        assertNull(SoundUtils.parse("not_a_real_sound"));
    }

    private static void assertEquivalent(String legacyName, String modernName) {
        assertNotNull(SoundUtils.parse(legacyName));
        assertNotNull(SoundUtils.parse(modernName));
        assertSame(SoundUtils.parse(legacyName), SoundUtils.parse(modernName));
    }
}

