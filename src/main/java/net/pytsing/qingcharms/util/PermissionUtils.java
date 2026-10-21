package net.pytsing.qingcharms.util;

import org.bukkit.permissions.Permissible;

public final class PermissionUtils {

    private static final String CHARM_WILDCARD = "qingcharms.charm.*";

    private PermissionUtils() {
    }

    public static boolean hasCharmPermission(Permissible permissible, String permission) {
        if (permissible == null) {
            return false;
        }
        if (permission == null || permission.trim().isEmpty()) {
            return true;
        }
        String normalized = permission.trim();
        return permissible.hasPermission(normalized)
                || normalized.startsWith("qingcharms.charm.")
                && permissible.hasPermission(CHARM_WILDCARD);
    }
}
