package net.pytsing.qingcharms.progression;

import net.pytsing.qingcharms.util.MessageUtils;
import net.pytsing.qingcharms.util.SoundUtils;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public final class ProgressionFeedback {

    private final ProgressionConfig config;

    public ProgressionFeedback(ProgressionConfig config) {
        this.config = config;
    }

    public void play(Player player, String action, ProgressionRecord state,
                     ProgressionConfig.BreakthroughStage breakthrough,
                     ProgressionConfig.StarStage star) {
        ProgressionConfig.FeedbackSettings settings = config.getFeedback(action);
        if (player == null || settings == null || !settings.isEnabled()) {
            return;
        }
        try {
            if (!settings.getSound().isEmpty()) {
                Sound sound = SoundUtils.parse(settings.getSound());
                if (sound != null) {
                    player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
                }
            }
            Effect effect = parseEffect(settings.getParticles());
            if (effect != null && settings.getCount() > 0) {
                Location center = player.getLocation().clone().add(0.0D, 1.0D, 0.0D);
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int index = 0; index < settings.getCount(); index++) {
                    Location point = center.clone().add(
                            offset(random, settings.getRadius()),
                            offset(random, settings.getRadius()),
                            offset(random, settings.getRadius()));
                    player.getWorld().playEffect(point, effect, 0);
                }
            }
            if (!settings.getMessage().isEmpty()) {
                String message = settings.getMessage()
                        .replace("{level}", String.valueOf(state.getLevel()))
                        .replace("{max}", String.valueOf(config.getLevel().getMaxLevel()))
                        .replace("{enhance}", String.valueOf(state.getEnhance()))
                        .replace("{stage}", breakthrough == null
                                ? "" : breakthrough.getName())
                        .replace("{star}", star == null
                                ? config.getStar().format(state.getStar()) : star.getFormat());
                player.sendMessage(MessageUtils.color(message));
            }
        } catch (RuntimeException exception) {
            // Cosmetic failure must never roll back a completed progression action.
        }
    }

    private static double offset(ThreadLocalRandom random, double radius) {
        return radius <= 0.0D ? 0.0D : (random.nextDouble() * 2.0D - 1.0D) * radius;
    }


    private static Effect parseEffect(String value) {
        try {
            return Effect.valueOf(value.trim().toUpperCase(java.util.Locale.US));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}

