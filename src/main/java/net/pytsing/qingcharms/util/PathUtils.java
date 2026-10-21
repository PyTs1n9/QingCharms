package net.pytsing.qingcharms.util;

import java.io.File;
import java.io.IOException;

public final class PathUtils {

    private PathUtils() {
    }

    public static File resolveWithin(File base, String configuredPath) throws IOException {
        if (base == null) {
            throw new IOException("Base directory is not available");
        }
        String value = configuredPath == null ? "" : configuredPath.trim();
        if (value.isEmpty()) {
            throw new IOException("Path is empty");
        }
        if (new File(value).isAbsolute()) {
            throw new IOException("Absolute paths are not allowed");
        }

        File baseDirectory = base.getCanonicalFile();
        File resolved = new File(baseDirectory, value).getCanonicalFile();
        if (!resolved.toPath().startsWith(baseDirectory.toPath())) {
            throw new IOException("Path escapes the plugin data directory");
        }
        return resolved;
    }
}
