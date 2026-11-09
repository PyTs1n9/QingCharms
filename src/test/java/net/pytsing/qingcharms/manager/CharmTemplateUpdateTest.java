package net.pytsing.qingcharms.manager;

import net.pytsing.qingcharms.api.ItemUniqueId;
import net.pytsing.qingcharms.config.ConfigManager;
import net.pytsing.qingcharms.creation.CharmTemplateService;
import net.pytsing.qingcharms.model.CharmDefinition;
import net.pytsing.qingcharms.support.BukkitFixture;
import net.pytsing.qingcharms.util.YamlUtils;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public final class CharmTemplateUpdateTest {

    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private BukkitFixture.TestPlugin plugin;
    private CharmManager manager;
    private YamlConfiguration gui;
    private File folder;

    @BeforeClass
    public static void installBukkitInterfaces() {
        BukkitFixture.install();
    }

    @Before
    public void setUp() throws Exception {
        folder = temporary.newFolder("plugin");
        plugin = new BukkitFixture.TestPlugin(folder);
        plugin.getConfig().createSection("charms");
        manager = new CharmManager(plugin, new ConfigManager(plugin));
        gui = new YamlConfiguration();
        gui.loadFromString("gui:\n  row: 1\n  slots:\n"
                + "    necklace:\n      slot: 0\n      type: 项链\n      action: equip\n"
                + "    ring:\n      slot: 1\n      type: 戒指\n      action: equip\n");
        reload();
    }

    @Test
    public void prepareDoesNotPublishAndCommitChangesOnlyRequestedDefinition() {
        CharmManager.PreparedQcItemUpdate first = manager.prepareQcItemUpdate("first", charm(1));
        assertFalse(first.isFailed());
        assertTrue(manager.commitQcItemUpdate(first));
        CharmDefinition existing = manager.getCharm("first").get();
        ItemStack original = charm(8);
        CharmManager.PreparedQcItemUpdate update = manager.prepareQcItemUpdate("JADE", original);

        assertFalse(update.isFailed());
        assertFalse(manager.getCharm("jade").isPresent());
        assertSame(existing, manager.getCharm("first").get());
        assertEquals(8, original.getAmount());
        assertEquals(1, update.getCharm().getItemTemplate().getAmount());
        assertEquals("项链", update.getCharm().getTypeValue());
        assertEquals("qingcharms.charm.jade", update.getCharm().getPermission());
        assertEquals(0, update.getCharm().getRequiredLevel());
        assertTrue(update.getCharm().getClasses().isEmpty());

        assertTrue(manager.commitQcItemUpdate(update));
        assertSame(existing, manager.getCharm("first").get());
        assertTrue(manager.getCharm("jade").isPresent());
        assertTrue(manager.isQcItemCharm("jade"));
        ItemStack escaped = manager.getCharm("jade").get().getItemTemplate();
        escaped.setAmount(64);
        assertEquals(1, manager.getCharm("jade").get().getItemTemplate().getAmount());
    }

    @Test
    public void unrelatedBrokenYamlDoesNotBlockSingleTemplateUpdate() throws Exception {
        File broken = new File(manager.getQcItemsFolder(), "broken.yml");
        Files.write(broken.toPath(), "broken: [unterminated".getBytes(StandardCharsets.UTF_8));

        CharmManager.PreparedQcItemUpdate update = manager.prepareQcItemUpdate("jade", charm(1));

        assertFalse(update.isFailed());
        assertTrue(manager.commitQcItemUpdate(update));
        assertTrue(manager.getCharm("jade").isPresent());
        assertArrayEquals("broken: [unterminated".getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(broken.toPath()));
    }

    @Test
    public void staleExportCannotOverwriteAnotherCommittedExport() {
        CharmManager.PreparedQcItemUpdate older = manager.prepareQcItemUpdate("older", charm(1));
        CharmManager.PreparedQcItemUpdate newer = manager.prepareQcItemUpdate("newer", charm(1));

        assertTrue(manager.commitQcItemUpdate(newer));
        assertFalse(manager.commitQcItemUpdate(older));
        assertFalse(manager.getCharm("older").isPresent());
        assertTrue(manager.getCharm("newer").isPresent());
        assertFalse(manager.commitQcItemUpdate(newer));
    }

    @Test
    public void reloadPreparedBeforeExportCannotBuildOverNewRuntime() {
        CharmManager.PreparedReload reload = manager.prepareReload(plugin.getConfig(), gui);
        assertFalse(reload.isFailed());
        assertTrue(manager.commitQcItemUpdate(manager.prepareQcItemUpdate("jade", charm(1))));

        CharmManager.ReloadResult stale = manager.buildReload(reload);

        assertTrue(stale.isFailed());
        assertFalse(manager.commitReload(stale));
        assertTrue(manager.getCharm("jade").isPresent());
    }

    @Test
    public void reloadBuiltBeforeExportCannotCommitOverNewRuntime() {
        CharmManager.PreparedReload reload = manager.prepareReload(plugin.getConfig(), gui);
        CharmManager.ReloadResult built = manager.buildReload(reload);
        assertFalse(built.isFailed());
        assertTrue(manager.commitQcItemUpdate(manager.prepareQcItemUpdate("jade", charm(1))));

        assertFalse(manager.commitReload(built));
        assertTrue(manager.getCharm("jade").isPresent());
    }

    @Test
    public void enabledConfigIdConflictsCaseInsensitivelyEvenIfDefinitionIsInvalid() {
        plugin.getConfig().set("charms.JADE.enabled", true);
        plugin.getConfig().set("charms.JADE.material", "NOT_A_MATERIAL");
        CharmManager.PreparedQcItemUpdate rejected = manager.prepareQcItemUpdate("jade", charm(1));
        assertTrue(rejected.isFailed());
        assertEquals("config-conflict", rejected.getError());
        assertFalse(manager.commitQcItemUpdate(rejected));

        plugin.getConfig().set("charms.JADE.enabled", false);
        CharmManager.PreparedQcItemUpdate allowed = manager.prepareQcItemUpdate("jade", charm(1));
        assertFalse(allowed.isFailed());
        assertTrue(manager.commitQcItemUpdate(allowed));
    }

    @Test
    public void rejectsInvalidOrAmbiguousCandidatesWithoutChangingRuntime() {
        assertEquals("invalid-template", manager.prepareQcItemUpdate(null, charm(1)).getError());
        assertEquals("invalid-template", manager.prepareQcItemUpdate("jade", null).getError());
        assertEquals("invalid-template", manager.prepareQcItemUpdate("jade", new ItemStack(Material.AIR)).getError());
        assertEquals("invalid-template", manager.prepareQcItemUpdate("jade", charm(0)).getError());
        ItemStack noLore = BukkitFixture.item(Material.GOLD_INGOT, 1, "项链", "攻击: +100");
        assertEquals("missing-type", manager.prepareQcItemUpdate("jade", noLore).getError());
        ItemStack multiple = BukkitFixture.item(Material.GOLD_INGOT, 1, null, "项链", "戒指");
        assertEquals("multiple-types", manager.prepareQcItemUpdate("jade", multiple).getError());
        assertTrue(manager.getCharms().isEmpty());
    }

    @Test
    public void signedIdWinsWhenTemplatesHaveIdenticalAppearanceAndInstancesAreFresh() {
        assertTrue(manager.commitQcItemUpdate(manager.prepareQcItemUpdate("first", charm(1))));
        assertTrue(manager.commitQcItemUpdate(manager.prepareQcItemUpdate("second", charm(1))));
        CharmDefinition first = manager.getCharm("first").get();
        CharmDefinition second = manager.getCharm("second").get();
        ItemStack firstInstance = manager.createCharmInstance(first);
        ItemStack secondInstance = manager.createCharmInstance(second);
        ItemStack anotherSecond = manager.createCharmInstance(second);

        assertEquals("first", manager.getCharmByItem(firstInstance).get().getId());
        assertEquals("second", manager.getCharmByItem(secondInstance).get().getId());
        assertFalse(manager.getCharmByItem(charm(1)).isPresent());
        assertNotEquals(ItemUniqueId.explicitFrom(secondInstance), ItemUniqueId.explicitFrom(anotherSecond));
        byte[] secret = Base64.getUrlDecoder().decode(plugin.getConfig()
                .getString("settings.charm-auth-secret"));
        assertTrue(CharmItemAuth.verifyToken(ItemUniqueId.identityTokenFrom(secondInstance),
                secret, ItemUniqueId.explicitFrom(secondInstance)));
        assertFalse(CharmItemAuth.verifyToken(ItemUniqueId.identityTokenFrom(secondInstance),
                secret, ItemUniqueId.explicitFrom(anotherSecond)));
        assertEquals("", ItemUniqueId.explicitFrom(second.getItemTemplate()));
        assertEquals("", ItemUniqueId.identityTokenFrom(second.getItemTemplate()));
    }

    @Test
    public void legacyExplicitTypeRootMetadataChineseIdAndRestrictionsStillLoad() throws Exception {
        ItemStack template = BukkitFixture.item(Material.INK_SACK, 1, null, "旧版饰品", "防御: +10");
        template.setDurability((short) 12);
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("item", template);
        legacy.set("enabled", true);
        legacy.set("type", "戒指");
        legacy.set("equip-lore", "旧版饰品");
        legacy.set("permission", "custom.legacy");
        legacy.set("required-level", 7);
        legacy.set("classes", Collections.singletonList("warrior"));
        legacy.set("requirements.player-level", 30);
        legacy.set("requirements.classes", Collections.singletonList("paladin"));
        legacy.set("data", 3);
        File nested = new File(manager.getQcItemsFolder(), "nested");
        assertTrue(nested.mkdirs());
        YamlUtils.save(new File(nested, "伪誓之舌.yml"), legacy);

        reload();

        CharmDefinition loaded = manager.getCharm("伪誓之舌").get();
        assertEquals("戒指", loaded.getTypeValue());
        assertEquals("custom.legacy", loaded.getPermission());
        assertEquals(30, loaded.getRequiredLevel());
        assertEquals(2, loaded.getClasses().size());
        assertTrue(loaded.getClasses().containsAll(Arrays.asList("warrior", "paladin")));
        assertEquals(12, loaded.getData());
        assertNull(loaded.getNormalizedDisplayName());
        assertTrue(loaded.matches(template));
        ItemStack signed = manager.createCharmInstance(loaded);
        assertEquals("伪誓之舌", manager.getCharmByItem(signed).get().getId());
        assertEquals(12, signed.getDurability());
    }

    @Test
    public void typeLessLegacyFileInfersTypeFromLoreAndConfigDefinitionStillWins() throws Exception {
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("Item", charm(1));
        legacy.set("charm.enabled", true);
        YamlUtils.save(new File(manager.getQcItemsFolder(), "legacy.yml"), legacy);
        plugin.getConfig().set("charms.conflict.enabled", true);
        plugin.getConfig().set("charms.conflict.material", "DIAMOND");
        plugin.getConfig().set("charms.conflict.type", "戒指");
        plugin.getConfig().set("charms.conflict.lore", Collections.singletonList("戒指"));
        YamlUtils.save(new File(manager.getQcItemsFolder(), "conflict.yml"), legacy);

        reload();

        assertEquals("项链", manager.getCharm("legacy").get().getTypeValue());
        assertTrue(manager.isQcItemCharm("legacy"));
        assertEquals(Material.DIAMOND, manager.getCharm("conflict").get().getMaterial());
        assertFalse(manager.isQcItemCharm("conflict"));
    }

    @Test
    public void duplicateFileLookupIncludesNestedCaseAndYamlExtension() throws Exception {
        File items = manager.getQcItemsFolder();
        File nested = new File(items, "nested");
        assertTrue(nested.mkdirs());
        File rootFile = new File(items, "jade.yml");
        File nestedFile = new File(nested, "JADE.yaml");
        Files.write(rootFile.toPath(), new byte[0]);
        Files.write(nestedFile.toPath(), new byte[0]);
        List<File> matches = manager.findQcItemFiles("JaDe");
        assertEquals(2, matches.size());
        assertTrue(matches.contains(rootFile));
        assertTrue(matches.contains(nestedFile));
        assertEquals(matches.get(0), manager.findQcItemFile("jade"));
        try {
            matches.clear();
            fail("file lookup must not expose a mutable list");
        } catch (UnsupportedOperationException expected) {
            // This collection is part of the manager's read-only lookup contract.
        }
    }

    @Test
    public void realServicePublishesImmediatelyAndTemplateSurvivesReloadWithOldSignatures() {
        BukkitFixture.Inventory inventory = new BukkitFixture.Inventory(41);
        inventory.put(0, charm(2));
        CharmTemplateService service = new CharmTemplateService(manager);

        CharmTemplateService.Result result = service.export(inventory.player(), "jade", false);

        assertEquals(result.getDetail(), CharmTemplateService.Status.SUCCESS, result.getStatus());
        assertEquals("jade", manager.getCharmByItem(inventory.get(0)).get().getId());
        CharmDefinition definition = manager.getCharm("jade").get();
        ItemStack oldSignature = manager.createCharmInstance(definition);
        assertNotEquals(ItemUniqueId.explicitFrom(oldSignature), ItemUniqueId.explicitFrom(inventory.get(0)));
        assertEquals(1, inventory.get(0).getAmount());
        assertEquals(1, inventory.get(1).getAmount());

        reload();

        assertEquals("jade", manager.getCharmByItem(oldSignature).get().getId());
        assertEquals("jade", manager.getCharmByItem(inventory.get(0)).get().getId());
        assertEquals("项链", manager.getCharm("jade").get().getTypeValue());
        assertEquals("", ItemUniqueId.identityTokenFrom(manager.getCharm("jade").get().getItemTemplate()));
    }

    @Test
    public void realServiceRejectsDuplicateFilesWithoutChangingEitherFileEvenWithForce() throws Exception {
        File items = manager.getQcItemsFolder();
        File nested = new File(items, "nested");
        assertTrue(nested.mkdirs());
        File first = new File(items, "jade.yml");
        File second = new File(nested, "JADE.yaml");
        byte[] firstBytes = "# first\n".getBytes(StandardCharsets.UTF_8);
        byte[] secondBytes = "# second\r\n".getBytes(StandardCharsets.UTF_8);
        Files.write(first.toPath(), firstBytes);
        Files.write(second.toPath(), secondBytes);
        BukkitFixture.Inventory inventory = new BukkitFixture.Inventory(41);
        inventory.put(0, charm(1));
        ItemStack[] before = inventory.snapshot();

        CharmTemplateService.Result result = new CharmTemplateService(manager)
                .export(inventory.player(), "jade", true);

        assertFalse(result.isSuccess());
        assertArrayEquals(firstBytes, Files.readAllBytes(first.toPath()));
        assertArrayEquals(secondBytes, Files.readAllBytes(second.toPath()));
        assertArrayEquals(before, inventory.snapshot());
        assertFalse(manager.getCharm("jade").isPresent());
    }

    @Test
    public void reloadReadingTemporaryFileCannotPublishAfterRollbackWithoutRuntimeChange() throws Exception {
        manager.invalidatePreparedReloads();
        File file = new File(manager.getQcItemsFolder(), "temporary.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Item", charm(1));
        yaml.set("charm.enabled", true);
        YamlUtils.save(file, yaml);
        CharmManager.PreparedReload readDuringExport = manager.prepareReload(plugin.getConfig(), gui);
        assertFalse(readDuringExport.isFailed());
        Files.delete(file.toPath());
        manager.invalidatePreparedReloads();

        CharmManager.ReloadResult stale = manager.buildReload(readDuringExport);

        assertTrue(stale.isFailed());
        assertFalse(manager.commitReload(stale));
        assertTrue(manager.getCharms().isEmpty());
        assertFalse(file.exists());
        reload();
        assertFalse(manager.getCharm("temporary").isPresent());
    }

    @Test
    public void invalidationAlsoRejectsReloadAlreadyBuiltBeforeRollback() {
        CharmManager.ReloadResult built = manager.buildReload(manager.prepareReload(plugin.getConfig(), gui));
        assertFalse(built.isFailed());
        manager.invalidatePreparedReloads();
        assertFalse(manager.commitReload(built));
        assertTrue(manager.getCharms().isEmpty());
    }

    private void reload() {
        CharmManager.PreparedReload prepared = manager.prepareReload(plugin.getConfig(), gui);
        assertFalse(prepared.getError(), prepared.isFailed());
        CharmManager.ReloadResult built = manager.buildReload(prepared);
        assertFalse(built.getError(), built.isFailed());
        assertTrue(manager.commitReload(built));
    }

    private static ItemStack charm(int amount) {
        return BukkitFixture.item(Material.GOLD_INGOT, amount, "§a青玉", "§7项链", "§c攻击: +100");
    }
}
