package net.pytsing.qingcharms.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public final class GuiConfigResourceTest {

    @Test
    public void separatesGuiConfigurationResources() throws Exception {
        YamlConfiguration config = loadResource("config.yml");
        YamlConfiguration gui = loadResource("gui.yml");

        assertFalse(config.isConfigurationSection("gui"));
        assertFalse(config.isConfigurationSection("charm-bag"));
        assertNotNull(gui.getConfigurationSection("gui"));
        assertFalse(gui.isConfigurationSection("charm-bag"));
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