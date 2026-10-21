package net.pytsing.qingcharms.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ItemUniqueIdTest {

    @Test
    public void hiddenIdentityCodecRoundTripsInstanceValues() {
        assertRoundTrip("instance-a");
        assertRoundTrip("550e8400-e29b-41d4-a716-446655440000");
        assertRoundTrip("护符实例/一号");
    }

    @Test
    public void recognizesOnlyValidIdentityLines() {
        String hidden = ItemUniqueId.hiddenIdentityLine("instance");
        assertTrue(ItemUniqueId.isIdentityLine(hidden));
        assertTrue(ItemUniqueId.isIdentityLine("Unique ID: legacy"));
        assertFalse(ItemUniqueId.isIdentityLine("Visible lore"));
        assertFalse(ItemUniqueId.isIdentityLine("\u00a7r\u00a70\u00a7r"));
    }

    @Test
    public void hiddenDataCanRideOnAVisibleLoreLine() {
        String visible = "\u00a7f\u653b\u51fb: \u00a7c+10";
        String line = visible
                + ItemUniqueId.hiddenIdentityLine("instance")
                + ItemUniqueId.hiddenIdentityLine("@qc-auth|1|charm|signature");
        assertEquals(visible, ItemUniqueId.stripHiddenData(line));
        assertEquals("instance", ItemUniqueId.hiddenIdFromLine(line));
        assertTrue(ItemUniqueId.isIdentityLine(line));
    }

    @Test
    public void standaloneHiddenLineDisappearsWhenStripped() {
        String hidden = ItemUniqueId.hiddenIdentityLine("instance");
        assertEquals("", ItemUniqueId.stripHiddenData(hidden));
    }

    private static void assertRoundTrip(String uniqueId) {
        String line = ItemUniqueId.hiddenIdentityLine(uniqueId);
        assertEquals(uniqueId, ItemUniqueId.hiddenIdFromLine(line));
    }
}
