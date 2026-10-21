package net.pytsing.qingcharms.storage;

public final class SaveStatus {

    private final long timestamp;
    private final long durationNanos;
    private final boolean successful;
    private final int playerCount;
    private final String message;

    public SaveStatus(long timestamp, long durationNanos, boolean successful,
                      int playerCount, String message) {
        this.timestamp = timestamp;
        this.durationNanos = Math.max(0L, durationNanos);
        this.successful = successful;
        this.playerCount = Math.max(0, playerCount);
        this.message = message == null ? "" : message;
    }

    public static SaveStatus never() {
        return new SaveStatus(0L, 0L, true, 0, "尚未执行保存");
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

    public int getPlayerCount() {
        return playerCount;
    }

    public String getMessage() {
        return message;
    }
}
