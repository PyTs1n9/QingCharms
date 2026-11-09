package net.pytsing.qingcharms.creation;

import net.pytsing.qingcharms.api.ItemUniqueId;
import net.pytsing.qingcharms.model.CharmDefinition;
import net.pytsing.qingcharms.model.CharmType;
import net.pytsing.qingcharms.support.BukkitFixture;
import net.pytsing.qingcharms.util.YamlUtils;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import static net.pytsing.qingcharms.creation.CharmTemplateService.Status.*;
import static org.junit.Assert.*;

public final class CharmTemplateServiceTest {

    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private FakeStore store;
    private TestFiles files;
    private BukkitFixture.Inventory inventory;
    private CharmTemplateService service;

    @BeforeClass
    public static void installBukkitInterfaces() {
        BukkitFixture.install();
    }

    @Before
    public void setUp() throws IOException {
        store = new FakeStore(temporary.newFolder("qcItems"));
        files = new TestFiles();
        inventory = new BukkitFixture.Inventory(41);
        inventory.put(0, charm(1));
        service = new CharmTemplateService(store, files);
    }

    @Test
    public void exportsMinimalTemplateWithCompleteMetaAndFreshHeldIdentity() throws Exception {
        ItemStack original = charm(1);
        original.setDurability((short) 12);
        ItemMeta meta = original.getItemMeta();
        meta.setLore(Arrays.asList("§7项链", "", "§c攻击: +100", "§8来源: 测试", ""));
        meta.addEnchant(Enchantment.DURABILITY, 3, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        original.setItemMeta(meta);
        ItemStack clean = original.clone();
        original = ItemUniqueId.withUniqueId(original, "old-instance");
        original = ItemUniqueId.withIdentityToken(original, "@qc-auth|1|old|old-signature");
        inventory.put(0, original);

        CharmTemplateService.Result result = service.export(inventory.player(), "JADE", false);

        assertEquals(SUCCESS, result.getStatus());
        assertTrue(result.isSuccess());
        assertEquals("jade", result.getId());
        assertEquals(Collections.singletonList("项链"), result.getTypes());
        assertEquals(1, files.saves);
        assertEquals(1, store.commits);
        assertEquals(2, store.invalidations);
        YamlConfiguration saved = files.load(result.getFile());
        assertEquals(2, saved.getKeys(false).size());
        assertEquals(2, saved.getConfigurationSection("charm").getKeys(false).size());
        assertTrue(saved.getBoolean("charm.enabled"));
        assertEquals("qingcharms.charm.jade", saved.getString("charm.permission"));
        assertFalse(saved.contains("charm.type"));
        assertFalse(saved.contains("charm.requirements"));
        assertFalse(saved.contains("charm.equip-lore"));
        ItemStack restored = saved.getItemStack("Item");
        assertTrue("all visible Lore, flags, enchants and data survive", clean.isSimilar(restored));
        assertEquals("", ItemUniqueId.explicitFrom(restored));
        assertEquals("", ItemUniqueId.identityTokenFrom(restored));
        ItemStack held = inventory.get(0);
        assertEquals(1, held.getAmount());
        assertNotEquals("old-instance", ItemUniqueId.explicitFrom(held));
        assertFalse(ItemUniqueId.explicitFrom(held).isEmpty());
        assertTrue(ItemUniqueId.identityTokenFrom(held).startsWith("@qc-auth|1|jade|"));
        assertTrue(clean.isSimilar(ItemUniqueId.withoutIdentityLore(held)));
        assertEquals(12, restored.getDurability());
        assertEquals(3, restored.getItemMeta().getEnchantLevel(Enchantment.DURABILITY));
        assertTrue(restored.getItemMeta().hasItemFlag(ItemFlag.HIDE_ENCHANTS));
    }

    @Test
    public void rejectsInvalidIdsBeforeTouchingDiskOrRuntime() {
        String[] invalid = { null, "", "../jade", "bad.id", "has space", "饰品",
                "abcdefghijklmnopqrstuvwxyz1234567" };
        ItemStack[] before = inventory.snapshot();
        for (String id : invalid) {
            assertEquals(INVALID_ID, service.export(inventory.player(), id, false).getStatus());
        }
        assertArrayEquals(before, inventory.snapshot());
        assertEquals(0, files.saves);
        assertEquals(0, store.prepares);
        assertEquals(0, store.invalidations);
    }

    @Test
    public void rejectsEmptyHandIncludingAirAndZeroAmount() {
        inventory.put(0, null);
        assertEquals(EMPTY_HAND, service.export(inventory.player(), "jade", false).getStatus());
        inventory.put(0, new ItemStack(Material.AIR));
        assertEquals(EMPTY_HAND, service.export(inventory.player(), "jade", false).getStatus());
        inventory.put(0, charm(0));
        assertEquals(EMPTY_HAND, service.export(inventory.player(), "jade", false).getStatus());
        assertEquals(EMPTY_HAND, service.export(null, "jade", false).getStatus());
        assertEquals(0, files.saves);
    }

    @Test
    public void ignoresNameForTypeAndRejectsAmbiguousLore() {
        inventory.put(0, BukkitFixture.item(Material.GOLD_INGOT, 1, "项链", "攻击: +5"));
        assertEquals(MISSING_TYPE, service.export(inventory.player(), "jade", false).getStatus());
        inventory.put(0, BukkitFixture.item(Material.GOLD_INGOT, 1, "普通饰品", "项链", "戒指"));
        CharmTemplateService.Result result = service.export(inventory.player(), "jade", false);
        assertEquals(AMBIGUOUS_TYPE, result.getStatus());
        assertEquals(Arrays.asList("项链", "戒指"), result.getTypes());
        assertEquals(0, files.saves);
        assertEquals(0, store.prepares);
    }

    @Test
    public void rejectsOverlappingGuiTypesEvenOnOneLoreLine() {
        store.types = Arrays.asList("宝石", "天空宝石");
        inventory.put(0, BukkitFixture.item(Material.DIAMOND, 1, null, "天空宝石"));
        assertEquals(AMBIGUOUS_TYPE, service.export(inventory.player(), "jade", false).getStatus());
        assertEquals(0, files.saves);
    }

    @Test
    public void repeatedGuiSlotsForSameTypeRemainOneMatch() {
        store.types = Arrays.asList("项链", "项链", "戒指");
        assertEquals(SUCCESS, service.export(inventory.player(), "jade", false).getStatus());
    }

    @Test
    public void enabledConfigConflictIsRejectedBeforeAnyWrite() {
        store.error = "config-conflict";
        ItemStack[] before = inventory.snapshot();
        assertEquals(CONFIG_CONFLICT, service.export(inventory.player(), "jade", true).getStatus());
        assertArrayEquals(before, inventory.snapshot());
        assertEquals(0, files.saves);
        assertEquals(0, store.commits);
        assertEquals(0, store.invalidations);
    }

    @Test
    public void existingNestedFileNeedsExplicitOverwrite() throws Exception {
        store.existing = new File(temporary.newFolder("nested"), "JADE.yaml");
        byte[] original = "# existing template\r\nlegacy: true\r\n".getBytes(StandardCharsets.UTF_8);
        Files.write(store.existing.toPath(), original);
        assertEquals(ALREADY_EXISTS, service.export(inventory.player(), "jade", false).getStatus());
        assertArrayEquals(original, Files.readAllBytes(store.existing.toPath()));
        assertEquals(0, files.saves);

        CharmTemplateService.Result result = service.export(inventory.player(), "jade", true);
        assertEquals(SUCCESS, result.getStatus());
        assertEquals(store.existing, result.getFile());
        assertEquals("overwritten", result.getDetail());
        assertFalse(new File(store.folder, "jade.yml").exists());
    }

    @Test
    public void stackedExportKeepsOneSignedItemInHandAndPreservesUnsignedRemainder() {
        inventory.heldSlot = 5;
        inventory.put(0, null);
        ItemStack original = charm(3);
        inventory.put(5, original);
        ItemStack armor = new ItemStack(Material.DIAMOND_BOOTS);
        ItemStack offhand = new ItemStack(Material.DIRT, 5);
        inventory.put(36, armor);
        inventory.put(40, offhand);

        assertEquals(SUCCESS, service.export(inventory.player(), "jade", false).getStatus());
        assertEquals(1, inventory.get(5).getAmount());
        assertFalse(ItemUniqueId.identityTokenFrom(inventory.get(5)).isEmpty());
        assertEquals(2, inventory.get(0).getAmount());
        assertTrue(original.isSimilar(inventory.get(0)));
        assertEquals("", ItemUniqueId.identityTokenFrom(inventory.get(0)));
        assertEquals(armor, inventory.get(36));
        assertEquals(offhand, inventory.get(40));
        assertEquals(3, count(Material.GOLD_INGOT));
    }

    @Test
    public void remainderMergesIntoSimilarStorageStackBeforeUsingEmptySlot() {
        inventory.put(0, charm(4));
        inventory.put(12, charm(60));
        assertEquals(SUCCESS, service.export(inventory.player(), "jade", false).getStatus());
        assertEquals(63, inventory.get(12).getAmount());
        assertNull(inventory.get(1));
        assertEquals(64, count(Material.GOLD_INGOT));
    }

    @Test
    public void emptyArmorAndOffhandDoNotProvideSpaceForRemainder() {
        inventory.fillStorage();
        inventory.put(0, charm(2));
        ItemStack[] before = inventory.snapshot();
        assertEquals(NO_SPACE, service.export(inventory.player(), "jade", false).getStatus());
        assertArrayEquals(before, inventory.snapshot());
        assertEquals(0, inventory.setContentsCalls);
        assertEquals(0, files.saves);
        assertEquals(0, store.commits);
        assertFalse(new File(store.folder, "jade.yml").exists());
    }

    @Test
    public void singleItemExportsWithOtherwiseFullInventory() {
        inventory.fillStorage();
        inventory.put(0, charm(1));
        ItemStack[] before = inventory.snapshot();
        assertEquals(SUCCESS, service.export(inventory.player(), "jade", false).getStatus());
        for (int i = 1; i < before.length; i++) {
            assertEquals(before[i], inventory.get(i));
        }
        assertFalse(ItemUniqueId.identityTokenFrom(inventory.get(0)).isEmpty());
    }

    @Test
    public void writeFailureRestoresExistingFileBytesAndInventory() throws Exception {
        store.existing = new File(store.folder, "jade.yml");
        byte[] original = "# 原始配置\r\ncharm:\r\n  enabled: false\r\n".getBytes(StandardCharsets.UTF_8);
        Files.write(store.existing.toPath(), original);
        ItemStack[] before = inventory.snapshot();
        files.failSaveAfterWrite = true;

        CharmTemplateService.Result result = service.export(inventory.player(), "jade", true);

        assertEquals(WRITE_FAILED, result.getStatus());
        assertArrayEquals(original, Files.readAllBytes(store.existing.toPath()));
        assertArrayEquals(before, inventory.snapshot());
        assertEquals(1, files.restores);
        assertEquals(0, store.commits);
    }

    @Test
    public void failedReadOfOriginalDoesNotOverwriteExistingFile() throws Exception {
        store.existing = new File(store.folder, "jade.yml");
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        Files.write(store.existing.toPath(), original);
        files.failRead = true;
        assertEquals(WRITE_FAILED, service.export(inventory.player(), "jade", true).getStatus());
        assertArrayEquals(original, Files.readAllBytes(store.existing.toPath()));
        assertEquals(0, files.saves);
        assertEquals(0, files.restores);
    }

    @Test
    public void loadFailureDeletesNewFileAndAllowsRetryWithoutForce() {
        files.failLoad = true;
        ItemStack[] before = inventory.snapshot();
        CharmTemplateService.Result result = service.export(inventory.player(), "jade", false);
        assertEquals(LOAD_FAILED, result.getStatus());
        assertFalse(result.getFile().exists());
        assertArrayEquals(before, inventory.snapshot());
        assertEquals(0, store.commits);

        files.failLoad = false;
        assertEquals(2, store.invalidations);
        assertEquals(SUCCESS, service.export(inventory.player(), "jade", false).getStatus());
        assertEquals(4, store.invalidations);
    }

    @Test
    public void roundTripMustPreserveEnchantsNotJustMatchingAppearance() {
        ItemStack input = charm(1);
        ItemMeta meta = input.getItemMeta();
        meta.addEnchant(Enchantment.DURABILITY, 3, true);
        input.setItemMeta(meta);
        inventory.put(0, input);
        ItemStack[] before = inventory.snapshot();
        files.removeEnchantOnLoad = true;

        CharmTemplateService.Result result = service.export(inventory.player(), "jade", false);

        assertEquals(LOAD_FAILED, result.getStatus());
        assertArrayEquals(before, inventory.snapshot());
        assertFalse(result.getFile().exists());
        assertEquals(0, store.commits);
    }

    @Test
    public void partialInventoryWriteRestoresFileAndAllInventorySlots() {
        inventory.put(0, charm(3));
        inventory.put(36, new ItemStack(Material.DIAMOND_HELMET));
        inventory.put(40, new ItemStack(Material.DIAMOND, 6));
        ItemStack[] before = inventory.snapshot();
        inventory.failedWrites = 1;

        CharmTemplateService.Result result = service.export(inventory.player(), "jade", false);

        assertEquals(DELIVERY_FAILED, result.getStatus());
        assertArrayEquals(before, inventory.snapshot());
        assertFalse(result.getFile().exists());
        assertEquals(0, store.commits);
        assertEquals(2, inventory.setContentsCalls);
    }

    @Test
    public void staleRuntimeAfterDeliveryRollsBackInventoryAndFile() throws Exception {
        store.existing = new File(store.folder, "jade.yml");
        byte[] original = "# preserve original exactly\r\n".getBytes(StandardCharsets.UTF_8);
        Files.write(store.existing.toPath(), original);
        store.commitAllowed = false;
        inventory.put(0, charm(4));
        ItemStack[] before = inventory.snapshot();

        CharmTemplateService.Result result = service.export(inventory.player(), "jade", true);

        assertEquals(STALE_RUNTIME, result.getStatus());
        assertArrayEquals(before, inventory.snapshot());
        assertArrayEquals(original, Files.readAllBytes(store.existing.toPath()));
        assertEquals(1, store.commits);
        assertNull(store.published);
    }

    @Test
    public void failedFileRecoveryIsReportedExplicitly() {
        files.failLoad = true;
        files.failRestore = true;
        ItemStack[] before = inventory.snapshot();
        CharmTemplateService.Result result = service.export(inventory.player(), "jade", false);
        assertEquals(RECOVERY_FAILED, result.getStatus());
        assertTrue(result.getDetail().contains("file recovery"));
        assertArrayEquals(before, inventory.snapshot());
        assertEquals(0, store.commits);
    }

    @Test
    public void failedInventoryRecoveryStillAttemptsFileRecovery() {
        inventory.alwaysFailWrites = true;
        CharmTemplateService.Result result = service.export(inventory.player(), "jade", false);
        assertEquals(RECOVERY_FAILED, result.getStatus());
        assertTrue(result.getDetail().contains("inventory recovery"));
        assertEquals(1, files.restores);
        assertFalse(result.getFile().exists());
        assertEquals(0, store.commits);
    }

    @Test
    public void invalidCandidateAndFailedSigningDoNotWriteFiles() {
        store.error = "invalid-template";
        assertEquals(INVALID_TEMPLATE, service.export(inventory.player(), "jade", false).getStatus());
        store.error = null;
        store.failSigning = true;
        assertEquals(INVALID_TEMPLATE, service.export(inventory.player(), "jade", false).getStatus());
        assertEquals(0, files.saves);
        assertEquals(0, store.commits);
    }

    @Test
    public void independentExportsNeverReuseInstanceIdentity() {
        assertEquals(SUCCESS, service.export(inventory.player(), "jade", false).getStatus());
        String firstId = ItemUniqueId.explicitFrom(inventory.get(0));
        String firstToken = ItemUniqueId.identityTokenFrom(inventory.get(0));
        inventory.put(0, charm(1));
        assertEquals(SUCCESS, service.export(inventory.player(), "jade", true).getStatus());
        assertNotEquals(firstId, ItemUniqueId.explicitFrom(inventory.get(0)));
        assertNotEquals(firstToken, ItemUniqueId.identityTokenFrom(inventory.get(0)));
    }

    private int count(Material material) {
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack item = inventory.get(i);
            if (item != null && item.getType() == material) total += item.getAmount();
        }
        return total;
    }

    private static ItemStack charm(int amount) {
        return BukkitFixture.item(Material.GOLD_INGOT, amount, "§a青玉", "§7项链", "§c攻击: +100");
    }

    private static final class FakeStore implements CharmTemplateService.Store {
        final File folder;
        File existing;
        Collection<String> types = Arrays.asList("项链", "戒指");
        String error;
        int prepares;
        int commits;
        int invalidations;
        boolean failSigning;
        boolean commitAllowed = true;
        CharmDefinition published;

        FakeStore(File folder) { this.folder = folder; }
        @Override public File getFolder() { return folder; }
        @Override public File findFile(String id) { return existing; }
        @Override public Collection<String> getSlotTypes() { return types; }
        @Override public void invalidatePreparedReloads() { invalidations++; }
        @Override public CharmTemplateService.Update prepare(final String id, ItemStack template) {
            prepares++;
            final CharmDefinition candidate = new CharmDefinition(id, template.getType(),
                    template.getItemMeta().getDisplayName(), template.getItemMeta().getLore(),
                    template, CharmType.NONE, null, "项链", null,
                    template.getDurability(), "qingcharms.charm." + id);
            return new CharmTemplateService.Update() {
                @Override public boolean isFailed() { return error != null; }
                @Override public String getError() { return error; }
                @Override public CharmDefinition getCharm() { return candidate; }
                @Override public boolean commit() {
                    commits++;
                    if (commitAllowed) published = candidate;
                    return commitAllowed;
                }
            };
        }
        @Override public ItemStack createInstance(CharmDefinition charm) {
            if (failSigning) return null;
            String uuid = UUID.randomUUID().toString();
            ItemStack item = ItemUniqueId.withUniqueId(charm.getItemTemplate(), uuid);
            return ItemUniqueId.withIdentityToken(item,
                    "@qc-auth|1|" + charm.getId() + "|fixture-" + uuid);
        }
    }

    private static final class TestFiles implements CharmTemplateService.FileAccess {
        int saves;
        int restores;
        boolean failRead;
        boolean failSaveAfterWrite;
        boolean failLoad;
        boolean failRestore;
        boolean removeEnchantOnLoad;

        @Override public byte[] read(File file) throws IOException {
            if (failRead) throw new IOException("injected backup read failure");
            return Files.readAllBytes(file.toPath());
        }
        @Override public void save(File file, YamlConfiguration yaml) throws IOException {
            saves++;
            YamlUtils.save(file, yaml);
            if (failSaveAfterWrite) throw new IOException("injected failure after saving");
        }
        @Override public YamlConfiguration load(File file) throws IOException {
            if (failLoad) throw new IOException("injected read-back failure");
            YamlUtils.LoadResult result = YamlUtils.loadChecked(file, null);
            if (!result.isOk()) throw new IOException("could not parse exported YAML", result.getError());
            YamlConfiguration yaml = result.getConfiguration();
            if (removeEnchantOnLoad) {
                ItemStack item = yaml.getItemStack("Item");
                ItemMeta meta = item.getItemMeta();
                meta.removeEnchant(Enchantment.DURABILITY);
                item.setItemMeta(meta);
                yaml.set("Item", item);
            }
            return yaml;
        }
        @Override public void restore(File file, byte[] original) throws IOException {
            restores++;
            if (failRestore) throw new IOException("injected restore failure");
            if (original == null) Files.deleteIfExists(file.toPath());
            else Files.write(file.toPath(), original);
        }
    }
}
