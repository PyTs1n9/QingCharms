package net.pytsing.qingcharms.config;

import org.bukkit.Effect;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class FeedbackEffectResourceTest {

    @Test
    public void defaultFeedbackEffectsAreValidBukkitEffects() throws Exception {
        YamlConfiguration config = loadResource("config.yml");
        YamlConfiguration progression = loadResource("progression.yml");

        assertTrue(config.getBoolean(
                "settings.require-charm-authentication"));

        assertValidEffect(config, "feedback.charms.example.equip.particles");
        assertValidEffect(progression, "feedback.level.particles");
    }

    private static void assertValidEffect(YamlConfiguration config, String path) {
        String name = config.getString(path);
        assertNotNull("Missing effect at " + path, name);
        assertNotNull("Invalid effect at " + path, Effect.valueOf(name));
    }

    private YamlConfiguration loadResource(String name) throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(name);
        assertNotNull("Missing resource " + name, stream);
        try {
            InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
            return YamlConfiguration.loadConfiguration(reader);
        } finally {
            stream.close();
        }
    }
}


