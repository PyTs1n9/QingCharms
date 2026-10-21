package net.pytsing.qingcharms.util;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class YamlUtils {

    private YamlUtils() {
    }

    public static YamlConfiguration load(File file, Logger logger) {
        LoadResult result = loadChecked(file, logger);
        if (result.isFailed()) {
            throw new IllegalStateException("Could not parse YAML file: "
                    + (file == null ? "null" : file.getAbsolutePath()), result.getError());
        }
        return result.getConfiguration();
    }

    public static LoadResult loadChecked(File file, Logger logger) {
        if (file == null || !file.exists()) {
            return LoadResult.missing();
        }
        if (!file.isFile()) {
            return LoadResult.failed(new IOException("Not a regular file: "
                    + file.getAbsolutePath()));
        }

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String source = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.loadFromString(ItemStackYaml.normalizeDataField(source));
            return LoadResult.ok(configuration);
        } catch (IOException | InvalidConfigurationException | RuntimeException exception) {
            if (logger != null) {
                logger.log(Level.WARNING, "Could not read YAML file as UTF-8: "
                        + file.getAbsolutePath(), exception);
            }
            return LoadResult.failed(exception);
        }
    }

    public static LoadResult loadResource(JavaPlugin plugin, String resourceName,
                                          Logger logger) {
        if (plugin == null || resourceName == null) {
            return LoadResult.failed(new IOException("Plugin resource is unavailable"));
        }
        InputStream input = plugin.getResource(resourceName);
        if (input == null) {
            return LoadResult.failed(new IOException("Plugin resource not found: "
                    + resourceName));
        }
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.load(reader);
            return LoadResult.ok(configuration);
        } catch (IOException | InvalidConfigurationException | RuntimeException exception) {
            if (logger != null) {
                logger.log(Level.WARNING, "Could not read YAML resource as UTF-8: "
                        + resourceName, exception);
            }
            return LoadResult.failed(exception);
        }
    }

    public static void save(File file, YamlConfiguration configuration) throws IOException {
        if (file == null || configuration == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory: " + parent.getAbsolutePath());
        }
        if (file.exists() && !file.isFile()) {
            throw new IOException("Cannot replace non-file YAML path: " + file.getAbsolutePath());
        }

        byte[] bytes = configuration.saveToString().getBytes(StandardCharsets.UTF_8);
        File temporary = File.createTempFile("." + file.getName() + ".", ".tmp",
                parent == null ? file.getAbsoluteFile().getParentFile() : parent);
        boolean moved = false;
        try {
            Files.write(temporary.toPath(), bytes, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            try (FileChannel channel = FileChannel.open(temporary.toPath(), StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            moveReplacing(temporary, file);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary.toPath());
            }
        }
    }

    private static void moveReplacing(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            try {
                Files.move(source.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException fallbackException) {
                fallbackException.addSuppressed(exception);
                throw fallbackException;
            }
        }
    }

    public static void copyResource(JavaPlugin plugin, String resourceName,
                                     File target) throws IOException {
        copyResource(plugin, resourceName, target, false);
    }

    public static void copyResource(JavaPlugin plugin, String resourceName,
                                     File target, boolean overwrite) throws IOException {
        if (target == null || (target.exists() && !overwrite)) {
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory: " + parent.getAbsolutePath());
        }

        InputStream input = plugin.getResource(resourceName);
        if (input == null) {
            throw new IOException("Plugin resource not found: " + resourceName);
        }
        try {
            File temporary = File.createTempFile("." + target.getName() + ".", ".tmp",
                    parent == null ? target.getAbsoluteFile().getParentFile() : parent);
            boolean moved = false;
            try {
                Files.copy(input, temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
                try (FileChannel channel = FileChannel.open(temporary.toPath(), StandardOpenOption.WRITE)) {
                    channel.force(true);
                }
                moveReplacing(temporary, target);
                moved = true;
            } finally {
                if (!moved) {
                    Files.deleteIfExists(temporary.toPath());
                }
            }
        } finally {
            input.close();
        }
    }

    public static final class LoadResult {

        public enum Status {
            OK,
            MISSING,
            FAILED
        }

        private final Status status;
        private final YamlConfiguration configuration;
        private final Exception error;

        private LoadResult(Status status, YamlConfiguration configuration,
                           Exception error) {
            this.status = status;
            this.configuration = configuration == null
                    ? new YamlConfiguration() : configuration;
            this.error = error;
        }

        private static LoadResult ok(YamlConfiguration configuration) {
            return new LoadResult(Status.OK, configuration, null);
        }

        private static LoadResult missing() {
            return new LoadResult(Status.MISSING, new YamlConfiguration(), null);
        }

        private static LoadResult failed(Exception error) {
            return new LoadResult(Status.FAILED, new YamlConfiguration(), error);
        }

        public Status getStatus() {
            return status;
        }

        public YamlConfiguration getConfiguration() {
            return configuration;
        }

        public Exception getError() {
            return error;
        }

        public boolean isOk() {
            return status == Status.OK;
        }

        public boolean isMissing() {
            return status == Status.MISSING;
        }

        public boolean isFailed() {
            return status == Status.FAILED;
        }
    }
}
