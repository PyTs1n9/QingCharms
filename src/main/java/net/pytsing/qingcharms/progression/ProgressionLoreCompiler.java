package net.pytsing.qingcharms.progression;

import net.pytsing.qingcharms.api.ItemUniqueId;
import net.pytsing.qingcharms.model.CharmDefinition;
import net.pytsing.qingcharms.util.MessageUtils;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Replaces the configured activation Lore and rebuilds progression-owned Lore
 * while preserving the item's other static ILS Lore.
 */
public final class ProgressionLoreCompiler {

    private static final String LEGACY_MANAGED_SUFFIX =
            "\u00a70\u00a72\u00a70\u00a72\u00a70";

    public ItemStack compile(ItemStack source, ProgressionRecord state,
                             ProgressionConfig config, CharmDefinition charm) {
        if (source == null || state == null || config == null) {
            return source;
        }
        ItemStack item = source.clone();
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta == null) {
            return item;
        }

        List<String> lore = new ArrayList<String>();
        Set<String> visible = new HashSet<String>();
        Set<String> previousManaged = new HashSet<String>();
        for (String line : state.getManagedLore()) {
            previousManaged.add(normalize(line));
        }
        List<String> managedLore = new ArrayList<String>();
        int progressionIndex = -1;
        if (meta.hasLore()) {
            for (String raw : meta.getLore()) {
                if (raw == null) {
                    continue;
                }
                // Identity data may be appended to a visible line, so strip it
                // instead of dropping the whole line.
                String line = ItemUniqueId.stripHiddenData(raw);
                if (line.isEmpty() || line.endsWith(LEGACY_MANAGED_SUFFIX)
                        || previousManaged.contains(normalize(line))) {
                    continue;
                }
                if (config.isActivationLore(line)) {
                    if (progressionIndex < 0) {
                        progressionIndex = lore.size();
                    }
                    continue;
                }
                String normalized = normalize(line);
                lore.add(line);
                visible.add(normalized);
            }
        }

        List<String> progressionLore = new ArrayList<String>();
        if (config.getLevel().isEnabled()) {
            addManaged(progressionLore, visible, managedLore, format(config, "level", state));
        }
        if (config.getEnhance().isEnabled()) {
            addManaged(progressionLore, visible, managedLore, format(config, "enhance", state));
        }
        if (config.getBreakthrough().isEnabled()) {
            addManaged(progressionLore, visible, managedLore, format(config, "breakthrough", state));
        }
        if (config.getStar().isEnabled()) {
            addManaged(progressionLore, visible, managedLore, format(config, "star", state));
        }
        if (state.isAwakened()) {
            addManaged(progressionLore, visible, managedLore, format(config, "awakened", state));
        }

        addAllManaged(progressionLore, visible, managedLore, config.getLevel().getUnlockLore(state.getLevel()));
        addAllManaged(progressionLore, visible, managedLore,
                config.getEnhance().getUnlockLore(state.getEnhance()));
        ProgressionConfig.BreakthroughStage breakthrough =
                config.getBreakthrough().getStage(Math.max(0, state.getBreakthrough() - 1));
        if (breakthrough != null) {
            addAllManaged(progressionLore, visible, managedLore, breakthrough.getUnlockLore());
        }
        if (state.isAwakened()) {
            addAllManaged(progressionLore, visible, managedLore, config.getAwakening().getUnlockLore());
        }

        if (progressionIndex < 0) {
            progressionIndex = lore.size();
        }
        lore.addAll(progressionIndex, progressionLore);
        state.setManagedLore(managedLore);
        meta.setLore(lore);
        if (state.isAwakened() && !config.getAwakening().getDisplayName().isEmpty()) {
            String baseName = state.getBaseName();
            if (baseName.isEmpty() && meta.hasDisplayName()) {
                baseName = meta.getDisplayName();
            }
            meta.setDisplayName(MessageUtils.color(
                    config.getAwakening().getDisplayName()
                            .replace("{name}", ChatColor.stripColor(baseName))));
        }
        item.setItemMeta(meta);
        return item;
    }

    private void addAllManaged(List<String> lore, Set<String> visible,
                               List<String> managedLore, List<String> lines) {
        for (String line : lines) {
            addManaged(lore, visible, managedLore, line);
        }
    }

    private void addManaged(List<String> lore, Set<String> visible,
                            List<String> managedLore, String line) {
        String normalized = normalize(line);
        if (!normalized.isEmpty() && visible.add(normalized)) {
            String rendered = MessageUtils.color(line);
            lore.add(rendered);
            managedLore.add(rendered);
        }
    }

    private String format(ProgressionConfig config, String type,
                          ProgressionRecord state) {
        if ("level".equals(type)) {
            return config.getLoreTemplate(type, "&7等级: {level}/{max}")
                    .replace("{level}", String.valueOf(state.getLevel()))
                    .replace("{max}", String.valueOf(maxLevel(config, state)));
        }
        if ("enhance".equals(type)) {
            return config.getLoreTemplate(type, "&7强化: +{enhance}")
                    .replace("{enhance}", String.valueOf(state.getEnhance()))
                    .replace("{max}", String.valueOf(maxEnhance(config, state)));
        }
        if ("breakthrough".equals(type)) {
            ProgressionConfig.BreakthroughStage stage = config.getBreakthrough()
                    .getStage(Math.max(0, state.getBreakthrough() - 1));
            return config.getLoreTemplate(type, "&7突破: {stage}阶段")
                    .replace("{stage}", stage == null ? "无" : stage.getName());
        }
        if ("star".equals(type)) {
            return config.getLoreTemplate(type, "&7星级: {star}")
                    .replace("{star}", config.getStar().format(state.getStar()));
        }
        return config.getLoreTemplate("awakened", "&7觉醒: 已觉醒");
    }

    private int maxLevel(ProgressionConfig config, ProgressionRecord state) {
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

    private int maxEnhance(ProgressionConfig config, ProgressionRecord state) {
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

    private static boolean isIdentityLine(String normalized) {
        return normalized.matches(
                "^(?:unique[\\s_-]*id|uuid|item[\\s_-]*id|唯一[\\s_-]*id)\\s*[:=].*$");
    }

    private static String normalize(String value) {
        return ChatColor.stripColor(MessageUtils.color(value))
                .trim().toLowerCase(Locale.US);
    }
}



