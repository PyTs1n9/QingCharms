package net.pytsing.qingcharms.progression;

import net.pytsing.qingcharms.QingCharmsPlugin;
import net.pytsing.qingcharms.api.ItemUniqueId;
import net.pytsing.qingcharms.api.event.CharmAwakeningEvent;
import net.pytsing.qingcharms.api.event.CharmBreakthroughEvent;
import net.pytsing.qingcharms.api.event.CharmEnhanceEvent;
import net.pytsing.qingcharms.api.event.CharmLevelEvent;
import net.pytsing.qingcharms.api.event.CharmProgressionChangeEvent;
import net.pytsing.qingcharms.api.event.CharmStarEvent;
import net.pytsing.qingcharms.model.CharmDefinition;
import net.pytsing.qingcharms.model.PlayerData;
import net.pytsing.qingcharms.util.EventUtils;
import net.pytsing.qingcharms.util.MessageUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Applies manual charm growth state and compiles final Lore for ILS.
 */
public final class ProgressionService {

    private final QingCharmsPlugin plugin;
    private final ProgressionConfig config;
    private final ProgressionLoreCompiler loreCompiler;
    private final ResourceService resourceService;
    private final ProgressionFeedback feedback;

    public ProgressionService(QingCharmsPlugin plugin) {
        this.plugin = plugin;
        this.config = new ProgressionConfig(plugin);
        this.loreCompiler = new ProgressionLoreCompiler();
        this.resourceService = new ResourceService();
        this.feedback = new ProgressionFeedback(config);
    }

    public void reload() {
        config.reload();
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public ItemStack bind(PlayerData data, Player player, ItemStack source) {
        String uniqueId = ItemUniqueId.explicitFrom(source);
        if (uniqueId.isEmpty()) {
            uniqueId = UUID.randomUUID().toString();
        }
        String itemKey = "instance:" + uniqueId;
        return bind(data, player, source, itemKey, uniqueId);
    }

    public String resolveUniqueId(PlayerData data, ItemStack source, String itemKey) {
        if (data == null || source == null || source.getType() == Material.AIR) {
            return "";
        }
        String explicit = ItemUniqueId.explicitFrom(source);
        if (!explicit.isEmpty()) {
            return explicit;
        }
        String stored = data.getItemUniqueId(itemKey, source);
        if (!stored.isEmpty()) {
            return stored;
        }
        return UUID.randomUUID().toString();
    }

    public String resolveSourceUniqueId(PlayerData data, ItemStack source,
                                        int sourceIndex, String targetKey) {
        if (data == null || source == null || sourceIndex < 0) {
            return resolveUniqueId(data, source, targetKey);
        }
        String explicit = ItemUniqueId.explicitFrom(source);
        if (!explicit.isEmpty()) {
            return explicit;
        }
        String sourceId = data.getItemUniqueId("inventory:" + sourceIndex, source);
        return sourceId.isEmpty()
                ? resolveUniqueId(data, source, targetKey) : sourceId;
    }

    public ItemStack bind(PlayerData data, Player player, ItemStack source,
                          String itemKey, String uniqueId) {
        if (!config.isEnabled() || data == null || source == null
                || source.getType() == Material.AIR) {
            return source;
        }
        if (uniqueId == null || uniqueId.trim().isEmpty()) {
            uniqueId = resolveUniqueId(data, source, itemKey);
        }
        ProgressionRecord state = data.getProgression(uniqueId);
        if (state == null && !config.hasActivationLore(source)) {
            return source;
        }
        if (state == null) {
            state = ProgressionRecord.fromItem(uniqueId, source);
        }
        if (state.getBaseName().isEmpty() && source.hasItemMeta()
                && source.getItemMeta() != null && source.getItemMeta().hasDisplayName()) {
            state.setBaseName(source.getItemMeta().getDisplayName());
        }
        ItemStack bound = ItemUniqueId.withUniqueId(
                updateItem(data, player, source, state), uniqueId);
        String authToken = ItemUniqueId.identityTokenFrom(source);
        if (!authToken.isEmpty()) {
            bound = ItemUniqueId.withIdentityToken(bound, authToken);
        }
        data.putProgression(state);
        data.setItemIdentity(itemKey, uniqueId, bound);
        return bound;
    }

    public void refreshEquipment(Player player) {
        if (!config.isEnabled() || player == null) {
            return;
        }
        PlayerData data = plugin.getDataManager().loadPlayer(player.getUniqueId());
        boolean changed = false;
        for (String slotId : data.getEquippedSlotIds()) {
            ItemStack item = data.getEquipped(slotId);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            ItemStack bound = bind(data, player, item,
                    "equipped:" + slotId, resolveUniqueId(data, item, "equipped:" + slotId));
            if (!bound.equals(item)) {
                data.setEquipped(slotId, bound);
                changed = true;
            }
        }
        if (changed) {
            plugin.getDataManager().savePlayer(data);
            plugin.getGuiManager().invalidateEquipmentSnapshot(player.getUniqueId());
        }
    }

    public void tickWear(Player player) {
        if (!config.isEnabled() || player == null || !player.isOnline()) {
            return;
        }
        PlayerData data = plugin.getDataManager().loadPlayer(player.getUniqueId());
        boolean changed = false;
        for (String slotId : data.getEquippedSlotIds()) {
            ItemStack item = data.getEquipped(slotId);
            if (!isGrowable(data, item, "equipped:" + slotId)) {
                continue;
            }
            String uniqueId = itemIdentity(data, item, "equipped:" + slotId);
            ProgressionRecord state = data.getProgression(uniqueId);
            if (state != null) {
                state.addWearSeconds(1L);
                data.putProgression(state);
                changed = true;
            }
        }
        if (changed) {
            plugin.getDataManager().savePlayer(data);
        }
    }

    public void recordKill(Player player) {
        if (!config.isEnabled() || player == null) {
            return;
        }
        PlayerData data = plugin.getDataManager().loadPlayer(player.getUniqueId());
        boolean changed = false;
        for (String slotId : data.getEquippedSlotIds()) {
            ItemStack item = data.getEquipped(slotId);
            if (!isGrowable(data, item, "equipped:" + slotId)) {
                continue;
            }
            String uniqueId = itemIdentity(data, item, "equipped:" + slotId);
            ProgressionRecord state = data.getProgression(uniqueId);
            if (state != null) {
                state.addKills(1L);
                data.putProgression(state);
                changed = true;
            }
        }
        if (changed) {
            plugin.getDataManager().savePlayer(data);
        }
    }

    public ProgressionRecord record(Player player, String slotId) {
        Target target = resolve(player, slotId, false);
        return target == null || target.state == null ? null : target.state.copy();
    }

    public boolean levelUp(Player player, String slotId) {
        Target target = resolve(player, slotId, true);
        if (!prepare(target, "level")) {
            return false;
        }
        ProgressionConfig.LevelSettings settings = config.getLevel();
        if (!settings.isEnabled()) {
            return failure(player, "progression.level.disabled");
        }
        int oldLevel = target.state.getLevel();
        int newLevel = oldLevel + 1;
        int maximum = maxLevel(target.state);
        if (oldLevel >= maximum) {
            return failure(player, "progression.level.max");
        }
        ResourceCost cost = settings.getCost(newLevel);
        StringBuilder missing = new StringBuilder();
        if (!resourceService.canConsume(player, target.state, cost, missing)) {
            return failure(player, "progression.resource.insufficient", missing.toString());
        }
        if (!EventUtils.callCancellable(new CharmLevelEvent(player,
                target.state.getUniqueId(), target.charmId, oldLevel, newLevel,
                Collections.<ItemStack>emptyList()))) {
            return false;
        }
        if (!resourceService.consume(player, target.state, cost, missing)) {
            return failure(player, "progression.resource.failed", missing.toString());
        }
        target.state.setLevel(newLevel);
        finish(target, "level", "Level:" + oldLevel, "Level:" + newLevel);
        feedback.play(player, "level", target.state, null, null);
        MessageUtils.send(player, "progression.level.success",
                Integer.valueOf(newLevel), Integer.valueOf(maximum));
        return true;
    }

    public boolean enhance(Player player, String slotId) {
        Target target = resolve(player, slotId, true);
        if (!prepare(target, "enhance")) {
            return false;
        }
        ProgressionConfig.EnhanceSettings settings = config.getEnhance();
        if (!settings.isEnabled()) {
            return failure(player, "progression.enhance.disabled");
        }
        int oldEnhance = target.state.getEnhance();
        int newEnhance = oldEnhance + 1;
        int maximum = maxEnhance(target.state);
        if (oldEnhance >= maximum) {
            return failure(player, "progression.enhance.max");
        }
        ResourceCost cost = settings.getCost(newEnhance);
        StringBuilder missing = new StringBuilder();
        if (!resourceService.canConsume(player, target.state, cost, missing)) {
            return failure(player, "progression.resource.insufficient", missing.toString());
        }
        boolean success = ThreadLocalRandom.current().nextDouble()
                < settings.getSuccessRate();
        if (success && !EventUtils.callCancellable(new CharmEnhanceEvent(player,
                target.state.getUniqueId(), target.charmId, oldEnhance, newEnhance,
                Collections.<ItemStack>emptyList()))) {
            return false;
        }
        if (!resourceService.consume(player, target.state, cost, missing)) {
            return failure(player, "progression.resource.failed", missing.toString());
        }
        if (!success) {
            plugin.getDataManager().savePlayer(target.data);
            MessageUtils.send(player, "progression.enhance.failure");
            return false;
        }
        target.state.setEnhance(newEnhance);
        finish(target, "enhance", "Enhance:" + oldEnhance, "Enhance:" + newEnhance);
        feedback.play(player, "enhance", target.state, null, null);
        MessageUtils.send(player, "progression.enhance.success",
                Integer.valueOf(newEnhance));
        return true;
    }

    public boolean breakthrough(Player player, String slotId) {
        Target target = resolve(player, slotId, true);
        if (!prepare(target, "breakthrough")) {
            return false;
        }
        ProgressionConfig.BreakthroughSettings settings = config.getBreakthrough();
        if (!settings.isEnabled()) {
            return failure(player, "progression.breakthrough.disabled");
        }
        int oldStage = target.state.getBreakthrough();
        ProgressionConfig.BreakthroughStage next = settings.getStage(oldStage);
        if (next == null) {
            return failure(player, "progression.breakthrough.max");
        }
        if (target.state.getEnhance() < maxEnhance(target.state)) {
            return failure(player, "progression.breakthrough.enhance-required",
                    Integer.valueOf(maxEnhance(target.state)));
        }
        StringBuilder missing = new StringBuilder();
        if (!resourceService.canConsume(player, target.state, next.getCost(), missing)) {
            return failure(player, "progression.resource.insufficient", missing.toString());
        }
        int newStage = oldStage + 1;
        if (!EventUtils.callCancellable(new CharmBreakthroughEvent(player,
                target.state.getUniqueId(), target.charmId, oldStage, newStage,
                Collections.<ItemStack>emptyList()))) {
            return false;
        }
        if (!resourceService.consume(player, target.state, next.getCost(), missing)) {
            return failure(player, "progression.resource.failed", missing.toString());
        }
        target.state.setBreakthrough(newStage);
        finish(target, "breakthrough", "Breakthrough:" + oldStage,
                "Breakthrough:" + newStage);
        feedback.play(player, next.getFeedback(), target.state, next, null);
        MessageUtils.send(player, "progression.breakthrough.success", next.getName());
        return true;
    }

    public boolean star(Player player, String slotId) {
        Target target = resolve(player, slotId, true);
        if (!prepare(target, "star")) {
            return false;
        }
        ProgressionConfig.StarSettings settings = config.getStar();
        if (!settings.isEnabled()) {
            return failure(player, "progression.star.disabled");
        }
        int oldStar = target.state.getStar();
        ProgressionConfig.StarStage next = settings.getStage(oldStar);
        if (next == null) {
            return failure(player, "progression.star.max");
        }
        if (target.state.getBreakthrough() < next.getRequiredBreakthrough()) {
            return failure(player, "progression.star.breakthrough-required",
                    Integer.valueOf(next.getRequiredBreakthrough()));
        }
        StringBuilder missing = new StringBuilder();
        if (!resourceService.canConsume(player, target.state, next.getCost(), missing)) {
            return failure(player, "progression.resource.insufficient", missing.toString());
        }
        int newStar = oldStar + 1;
        if (!EventUtils.callCancellable(new CharmStarEvent(player,
                target.state.getUniqueId(), target.charmId, oldStar, newStar,
                Collections.<ItemStack>emptyList()))) {
            return false;
        }
        if (!resourceService.consume(player, target.state, next.getCost(), missing)) {
            return failure(player, "progression.resource.failed", missing.toString());
        }
        target.state.setStar(newStar);
        finish(target, "star", "Star:" + oldStar, "Star:" + newStar);
        feedback.play(player, next.getFeedback(), target.state, null, next);
        MessageUtils.send(player, "progression.star.success",
                settings.format(newStar));
        return true;
    }

    public boolean awaken(Player player, String slotId) {
        Target target = resolve(player, slotId, true);
        if (!prepare(target, "awakening")) {
            return false;
        }
        ProgressionConfig.AwakeningSettings settings = config.getAwakening();
        if (!settings.isEnabled()) {
            return failure(player, "progression.awakening.disabled");
        }
        if (settings.isOneTime() && target.state.isAwakened()) {
            return failure(player, "progression.awakening.already");
        }
        if (target.state.getLevel() < settings.getRequiredLevel()
                || target.state.getEnhance() < settings.getRequiredEnhance()
                || target.state.getBreakthrough() < settings.getRequiredBreakthrough()
                || target.state.getStar() < settings.getRequiredStar()) {
            return failure(player, "progression.awakening.requirements");
        }
        StringBuilder missing = new StringBuilder();
        if (!resourceService.canConsume(player, target.state, settings.getCost(), missing)) {
            return failure(player, "progression.resource.insufficient", missing.toString());
        }
        if (!EventUtils.callCancellable(new CharmAwakeningEvent(player,
                target.state.getUniqueId(), target.charmId, false, true,
                Collections.<ItemStack>emptyList()))) {
            return false;
        }
        if (!resourceService.consume(player, target.state, settings.getCost(), missing)) {
            return failure(player, "progression.resource.failed", missing.toString());
        }
        target.state.setAwakened(true);
        finish(target, "awakening", "Awakened:false", "Awakened:true");
        feedback.play(player, settings.getFeedback(), target.state, null, null);
        MessageUtils.send(player, "progression.awakening.success");
        return true;
    }

    private boolean prepare(Target target, String action) {
        if (target == null || target.state == null) {
            return false;
        }
        if (!config.isEnabled()) {
            MessageUtils.send(target.player, "progression.disabled");
            return false;
        }
        return true;
    }

    private void finish(Target target, String action, Object oldState, Object newState) {
        EventUtils.call(new CharmProgressionChangeEvent(target.player,
                target.state.getUniqueId(), target.charmId, oldState, newState,
                Collections.<ItemStack>emptyList()));
        ItemStack updated = ItemUniqueId.withUniqueId(
                updateItem(target.data, target.player, target.item, target.state),
                target.state.getUniqueId());
        String authToken = ItemUniqueId.identityTokenFrom(target.item);
        if (!authToken.isEmpty()) {
            updated = ItemUniqueId.withIdentityToken(updated, authToken);
        }
        target.data.putProgression(target.state);
        target.data.setItemIdentity(target.itemKey, target.state.getUniqueId(), updated);
        target.write(updated);
        plugin.getDataManager().savePlayer(target.data);
        plugin.getGuiManager().invalidateEquipmentSnapshot(target.player.getUniqueId());
    }

    private ItemStack updateItem(PlayerData data, Player player, ItemStack source,
                                 ProgressionRecord state) {
        Optional<CharmDefinition> charm = plugin.getCharmManager().getCharmByItem(source);
        return loreCompiler.compile(source, state, config,
                charm.isPresent() ? charm.get() : null);
    }

    private Target resolve(Player player, String slotId, boolean notify) {
        if (player == null || slotId == null) {
            return null;
        }
        PlayerData data = plugin.getDataManager().loadPlayer(player.getUniqueId());
        if ("hand".equalsIgnoreCase(slotId) || "held".equalsIgnoreCase(slotId)) {
            ItemStack item = player.getItemInHand();
            if (item == null || item.getType() == Material.AIR
                    || !plugin.getCharmManager().isCharm(item)) {
                if (notify) {
                    MessageUtils.send(player, "progression.target.hand");
                }
                return null;
            }
            String itemKey = "inventory:" + player.getInventory().getHeldItemSlot();
            if (!isGrowable(data, item, itemKey)) {
                if (notify) {
                    MessageUtils.send(player, "progression.not-growable");
                }
                return null;
            }
            String uniqueId = resolveUniqueId(data, item, itemKey);
            ItemStack bound = bind(data, player, item, itemKey, uniqueId);
            player.setItemInHand(bound);
            return new HandTarget(player, data, itemKey, bound,
                    data.getProgression(uniqueId), "");
        }

        ItemStack item = data.getEquipped(slotId);
        if (item == null || item.getType() == Material.AIR
                || !plugin.getCharmManager().isCharm(item)) {
            if (notify) {
                MessageUtils.send(player, "progression.target.slot", slotId);
            }
            return null;
        }
        String itemKey = "equipped:" + slotId;
        if (!isGrowable(data, item, itemKey)) {
            if (notify) {
                MessageUtils.send(player, "progression.not-growable");
            }
            return null;
        }
        String uniqueId = resolveUniqueId(data, item, itemKey);
        ItemStack bound = bind(data, player, item, itemKey, uniqueId);
        if (!bound.equals(item)) {
            data.setEquipped(slotId, bound);
        }
        return new SlotTarget(player, data, itemKey, slotId, bound,
                data.getProgression(uniqueId), "");
    }

    private boolean isGrowable(PlayerData data, ItemStack item, String itemKey) {
        if (config.hasActivationLore(item)) {
            return true;
        }
        String storedId = data.getItemUniqueId(itemKey, item);
        if (!storedId.isEmpty() && data.getProgression(storedId) != null) {
            return true;
        }
        return data.getProgression(itemIdentity(data, item, itemKey)) != null;
    }

    private String itemIdentity(PlayerData data, ItemStack item, String itemKey) {
        String explicit = ItemUniqueId.explicitFrom(item);
        if (!explicit.isEmpty()) {
            return explicit;
        }
        String stored = data.getItemUniqueId(itemKey, item);
        return stored;
    }

    private String charmId(ItemStack item) {
        Optional<CharmDefinition> charm = plugin.getCharmManager().getCharmByItem(item);
        return charm.isPresent() ? charm.get().getId() : "";
    }

    private boolean failure(Player player, String key, Object... args) {
        MessageUtils.send(player, key, args);
        return false;
    }

    public int maxLevel(ProgressionRecord state) {
        int maximum = config.getLevel().getMaxLevel();
        ProgressionConfig.BreakthroughStage stage = config.getBreakthrough()
                .getStage(Math.max(0, state.getBreakthrough() - 1));
        if (stage != null) {
            maximum = Math.max(maximum, stage.getLevelCap());
        }
        ProgressionConfig.StarStage star =
                config.getStar().getStage(Math.max(0, state.getStar() - 1));
        return maximum + (star == null ? 0 : star.getLevelCapBonus());
    }

    public int maxEnhance(ProgressionRecord state) {
        int maximum = config.getEnhance().getMax();
        ProgressionConfig.BreakthroughStage stage = config.getBreakthrough()
                .getStage(Math.max(0, state.getBreakthrough() - 1));
        if (stage != null) {
            maximum = Math.max(maximum, stage.getEnhanceCap());
        }
        ProgressionConfig.StarStage star =
                config.getStar().getStage(Math.max(0, state.getStar() - 1));
        return maximum + (star == null ? 0 : star.getEnhanceCapBonus());
    }

    private abstract static class Target {

        protected final Player player;
        protected final PlayerData data;
        protected final String itemKey;
        protected final ItemStack item;
        protected final ProgressionRecord state;
        private final String charmId;

        private Target(Player player, PlayerData data, String itemKey, ItemStack item,
                       ProgressionRecord state, String charmId) {
            this.player = player;
            this.data = data;
            this.itemKey = itemKey == null ? "" : itemKey;
            this.item = item.clone();
            this.state = state;
            this.charmId = charmId == null ? "" : charmId;
        }

        protected abstract void write(ItemStack item);
    }

    private static final class SlotTarget extends Target {

        private final String slotId;

        private SlotTarget(Player player, PlayerData data, String itemKey, String slotId,
                           ItemStack item, ProgressionRecord state, String charmId) {
            super(player, data, itemKey, item, state, charmId);
            this.slotId = slotId;
        }

        @Override
        protected void write(ItemStack item) {
            data.setEquipped(slotId, item);
        }
    }

    private static final class HandTarget extends Target {

        private HandTarget(Player player, PlayerData data, String itemKey, ItemStack item,
                           ProgressionRecord state, String charmId) {
            super(player, data, itemKey, item, state, charmId);
        }

        @Override
        protected void write(ItemStack item) {
            player.setItemInHand(item);
        }
    }
}









