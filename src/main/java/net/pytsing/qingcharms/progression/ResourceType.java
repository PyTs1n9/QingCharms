package net.pytsing.qingcharms.progression;

public enum ResourceType {
    WEAR_SECONDS,
    KILLS,
    EXPERIENCE,
    VAULT,
    PLAYER_POINTS;

    public static ResourceType parse(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.US);
        if ("wear-seconds".equals(normalized) || "wear".equals(normalized)
                || "time".equals(normalized)) {
            return WEAR_SECONDS;
        }
        if ("kills".equals(normalized) || "kill".equals(normalized)
                || "mob-kills".equals(normalized)) {
            return KILLS;
        }
        if ("experience".equals(normalized) || "exp".equals(normalized)
                || "xp".equals(normalized)) {
            return EXPERIENCE;
        }
        if ("vault".equals(normalized) || "money".equals(normalized)
                || "coins".equals(normalized)) {
            return VAULT;
        }
        if ("playerpoints".equals(normalized) || "points".equals(normalized)) {
            return PLAYER_POINTS;
        }
        throw new IllegalArgumentException("Unknown progression resource: " + value);
    }
}
