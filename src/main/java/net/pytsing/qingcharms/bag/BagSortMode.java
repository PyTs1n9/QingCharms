package net.pytsing.qingcharms.bag;

import java.util.Locale;

public enum BagSortMode {

    DEFAULT,
    NAME,
    LEVEL,
    RARITY,
    TYPE;

    public static BagSortMode parse(String value, BagSortMode fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback == null ? RARITY : fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.US)
                    .replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException exception) {
            return fallback == null ? RARITY : fallback;
        }
    }

    public BagSortMode next() {
        switch (this) {
            case NAME:
                return LEVEL;
            case LEVEL:
                return RARITY;
            case RARITY:
                return TYPE;
            case TYPE:
                return NAME;
            default:
                return NAME;
        }
    }
}