package net.pytsing.qingcharms.model;

import java.util.Locale;

public enum CharmType {
    NONE("NONE"),
    HELMET("HELMET"),
    CHESTPLATE("CHESTPLATE"),
    LEGGINGS("LEGGINGS"),
    BOOTS("BOOTS");

    private final String configName;

    CharmType(String configName) {
        this.configName = configName;
    }

    public String getConfigName() {
        return configName;
    }

    public String getDisplayName() {
        switch (this) {
            case HELMET:
                return "头盔槽";
            case CHESTPLATE:
                return "胸甲槽";
            case LEGGINGS:
                return "护腿槽";
            case BOOTS:
                return "靴子槽";
            default:
                return "无槽位";
        }
    }

    public static CharmType parse(String value) {
        if (value == null) {
            return NONE;
        }
        String normalized = value.trim().toUpperCase(Locale.US)
                .replace("-", "").replace("_", "").replace(" ", "");
        if ("HELMET".equals(normalized) || "HEAD".equals(normalized)) {
            return HELMET;
        }
        if ("CHESTPLATE".equals(normalized) || "CHEST".equals(normalized)) {
            return CHESTPLATE;
        }
        if ("LEGGINGS".equals(normalized) || "LEGS".equals(normalized)) {
            return LEGGINGS;
        }
        if ("BOOTS".equals(normalized) || "BOOT".equals(normalized)) {
            return BOOTS;
        }
        return NONE;
    }

    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toUpperCase(Locale.US)
                .replace("-", "").replace("_", "").replace(" ", "");
        return "NONE".equals(normalized) || parse(value) != NONE;
    }
}
