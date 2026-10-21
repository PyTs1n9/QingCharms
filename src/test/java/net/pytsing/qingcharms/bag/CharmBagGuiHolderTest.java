package net.pytsing.qingcharms.bag;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CharmBagGuiHolderTest {

    @Test
    public void copyPreservesRemoteBagOwner() {
        UUID ownerId = UUID.randomUUID();
        CharmBagGuiHolder source = new CharmBagGuiHolder(
                BagSortMode.NAME, "项链", 2, ownerId, true);

        CharmBagGuiHolder copy = source.copy(BagSortMode.LEVEL, "其他", 0);

        assertEquals(ownerId, copy.getOwnerId());
        assertTrue(copy.isAdminView());
        assertEquals(BagSortMode.LEVEL, copy.getSortMode());
        assertEquals("其他", copy.getCategory());
        assertEquals(0, copy.getPage());
    }
}