package net.pytsing.qingcharms.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public final class ClassesConfigResourceTest {

    @Test
    public void separatesClassesAndRulesIntoOwnResource() throws Exception {
        YamlConfiguration config = loadResource("config.yml");
        YamlConfiguration classes = loadResource("classes.yml");

        assertFalse(config.isConfigurationSection("classes"));
        assertFalse(config.isConfigurationSection("rules"));
        assertNotNull(classes.getConfigurationSection("classes"));
        assertNotNull(classes.getConfigurationSection("rules"));
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
