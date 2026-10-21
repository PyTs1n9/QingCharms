package net.pytsing.qingcharms.storage;

import java.io.File;

public final class BackupStatus {

    private final long timestamp;
    private final long durationNanos;
    private final boolean successful;
    private final File file;
    private final String message;

    public BackupStatus(long timestamp, long durationNanos, boolean successful,
                        File file, String message) {
        this.timestamp = timestamp;
        this.durationNanos = Math.max(0L, durationNanos);
        this.successful = successful;
        this.file = file;
        this.message = message == null ? "" : message;
    }

    public static BackupStatus never() {
        return new BackupStatus(0L, 0L, true, null, "尚未执行备份");
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getDurationNanos() {
        return durationNanos;
    }

    public double getDurationMillis() {
        return durationNanos / 1000000.0D;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public File getFile() {
        return file;
    }

    public String getMessage() {
        return message;
    }
}
