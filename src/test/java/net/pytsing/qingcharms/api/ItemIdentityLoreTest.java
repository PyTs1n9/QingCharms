package net.pytsing.qingcharms.api;

import net.pytsing.qingcharms.support.BukkitFixture;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public final class ItemIdentityLoreTest {

    @BeforeClass
    public static void installBukkitInterfaces() {
        BukkitFixture.install();
    }

    @Test
    public void signingAndCleaningPreserveMiddleAndTrailingBlankLines() {
        ItemStack clean = BukkitFixture.item(Material.GOLD_INGOT, 1, "青玉",
                "项链", "", "攻击: +100", "", "");
        ItemStack signed = ItemUniqueId.withUniqueId(clean, "instance");
        signed = ItemUniqueId.withIdentityToken(signed, "@qc-auth|1|jade|token");

        assertEquals(5, signed.getItemMeta().getLore().size());
        assertEquals("", signed.getItemMeta().getLore().get(3));
        assertEquals("", signed.getItemMeta().getLore().get(4));
        assertEquals("instance", ItemUniqueId.explicitFrom(signed));
        assertEquals("@qc-auth|1|jade|token", ItemUniqueId.identityTokenFrom(signed));
        assertEquals(clean, ItemUniqueId.withoutIdentityLore(signed));
        assertEquals(Arrays.asList("项链", "", "攻击: +100", "", ""),
                clean.getItemMeta().getLore());
    }

    @Test
    public void allBlankLoreKeepsItsOriginalRowsWhenIdentityIsRemoved() {
        ItemStack clean = BukkitFixture.item(Material.GOLD_INGOT, 1, null, "", "");
        ItemStack signed = ItemUniqueId.withUniqueId(clean, "instance");
        signed = ItemUniqueId.withIdentityToken(signed, "@qc-auth|1|jade|token");

        assertEquals("instance", ItemUniqueId.explicitFrom(signed));
        assertEquals("@qc-auth|1|jade|token", ItemUniqueId.identityTokenFrom(signed));
        assertEquals(clean, ItemUniqueId.withoutIdentityLore(signed));
        assertEquals(Arrays.asList("", ""), ItemUniqueId.withoutIdentityLore(signed).getItemMeta().getLore());
    }

    @Test
    public void legacyIdentityRowsAreRemovedWithoutRemovingVisibleLoreOrWhitespace() {
        ItemStack item = BukkitFixture.item(Material.GOLD_INGOT, 1, null,
                "项链", "", "Unique ID: legacy", " ", "攻击: +5");

        ItemStack cleaned = ItemUniqueId.withoutIdentityLore(item);

        assertEquals(Arrays.asList("项链", "", " ", "攻击: +5"), cleaned.getItemMeta().getLore());
        assertEquals("", ItemUniqueId.explicitFrom(cleaned));
        assertEquals("legacy", ItemUniqueId.explicitFrom(item));
    }

    @Test
    public void standaloneIdentityOnlyLoreDoesNotLeaveBlankTooltipRow() {
        ItemStack clean = BukkitFixture.item(Material.GOLD_INGOT, 1, null);
        ItemStack signed = ItemUniqueId.withUniqueId(clean, "instance");
        signed = ItemUniqueId.withIdentityToken(signed, "@qc-auth|1|jade|token");

        assertEquals(1, signed.getItemMeta().getLore().size());
        ItemStack restored = ItemUniqueId.withoutIdentityLore(signed);

        assertFalse(restored.getItemMeta().hasLore());
        assertEquals(clean, restored);
    }
}
