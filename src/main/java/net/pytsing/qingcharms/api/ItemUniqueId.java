package net.pytsing.qingcharms.api;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves item instance identity and carries hidden authentication metadata in
 * Lore that survives inventory movement, drops, and YAML persistence.
 */
public final class ItemUniqueId {

    private static final Pattern EXPLICIT_ID = Pattern.compile(
            "^(?:unique[\\s_-]*id|uuid|item[\\s_-]*id|唯一[\\s_-]*id)\\s*[:=]\\s*(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final String HIDDEN_PREFIX = "\u00a7r\u00a70\u00a70\u00a70\u00a70";
    private static final String HIDDEN_SUFFIX = "\u00a7r";
    private static final String AUTH_TOKEN_PREFIX = "@qc-auth|1|";
    private static final char COLOR_CHAR = '\u00a7';
    private static final char[] NIBBLE_COLORS = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    private ItemUniqueId() {
    }

    public static String from(ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) {
            return "";
        }
        String explicit = explicitFrom(item);
        if (!explicit.isEmpty()) {
            return explicit;
        }
        byte[] canonical = canonical(item).getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return UUID.nameUUIDFromBytes(digest.digest(canonical)).toString();
        } catch (NoSuchAlgorithmException exception) {
            return UUID.nameUUIDFromBytes(canonical).toString();
        }
    }

    public static String explicitFrom(ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) {
            return "";
        }
        String hidden = hiddenIdFromLore(item);
        return hidden.isEmpty() ? readExplicitId(item) : hidden;
    }

    public static ItemStack withUniqueId(ItemStack item, String uniqueId) {
        if (item == null || item.getType() == org.bukkit.Material.AIR
                || uniqueId == null || uniqueId.trim().isEmpty()) {
            return item == null ? null : item.clone();
        }
        ItemStack copy = filterIdentityLore(item, true);
        return addHiddenLine(copy, hiddenIdentityLine(uniqueId.trim()));
    }

    public static String identityTokenFrom(ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR
                || !item.hasItemMeta()) {
            return "";
        }
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null) {
            return "";
        }
        for (String line : meta.getLore()) {
            for (String value : hiddenPayloads(line)) {
                if (isAuthToken(value)) {
                    return value;
                }
            }
        }
        return "";
    }

    public static ItemStack withIdentityToken(ItemStack item, String token) {
        if (item == null || item.getType() == org.bukkit.Material.AIR
                || token == null || !isAuthToken(token.trim())) {
            return item == null ? null : item.clone();
        }
        String uniqueId = explicitFrom(item);
        ItemStack copy = withoutIdentityLore(item);
        if (!uniqueId.isEmpty()) {
            copy = withUniqueId(copy, uniqueId);
        }
        return addHiddenLine(copy, hiddenIdentityLine(token.trim()));
    }

    public static ItemStack withoutInstanceIdentityLore(ItemStack item) {
        return filterIdentityLore(item, true);
    }

    public static ItemStack withoutIdentityLore(ItemStack item) {
        return filterIdentityLore(item, false);
    }

    public static String fingerprint(ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) {
            return "";
        }
        return UUID.nameUUIDFromBytes(
                canonical(item).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static boolean isIdentityLine(String line) {
        return line != null
                && (isLegacyIdentityLine(line) || !hiddenPayloads(line).isEmpty());
    }

    /**
     * Appends hidden identity data to the last existing Lore line. A dedicated
     * (and therefore blank-looking) Lore line is only created when the item has
     * no Lore at all, because Bukkit renders every Lore entry as its own
     * tooltip row even when it carries no visible glyphs.
     */
    private static ItemStack addHiddenLine(ItemStack item, String line) {
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
        if (lore.isEmpty()) {
            lore.add(line);
        } else {
            lore.set(lore.size() - 1, lore.get(lore.size() - 1) + line);
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack filterIdentityLore(ItemStack item, boolean keepAuthToken) {
        if (item == null || item.getType() == org.bukkit.Material.AIR || !item.hasItemMeta()) {
            return item == null ? null : item.clone();
        }
        ItemStack copy = item.clone();
        org.bukkit.inventory.meta.ItemMeta meta = copy.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return copy;
        }
        List<String> lore = new ArrayList<String>();
        for (String line : meta.getLore()) {
            if (line == null) {
                continue;
            }
            StringBuilder rebuilt = new StringBuilder(
                    isLegacyIdentityLine(line) ? "" : stripHiddenData(line));
            if (keepAuthToken) {
                for (String payload : hiddenPayloads(line)) {
                    if (isAuthToken(payload)) {
                        rebuilt.append(hiddenIdentityLine(payload));
                    }
                }
            }
            if (rebuilt.length() > 0) {
                lore.add(rebuilt.toString());
            }
        }
        meta.setLore(lore.isEmpty() ? null : lore);
        copy.setItemMeta(meta);
        return copy;
    }

    private static String readExplicitId(ItemStack item) {
        if (!item.hasItemMeta()) {
            return "";
        }
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null) {
            return "";
        }
        for (String line : meta.getLore()) {
            if (line == null) {
                continue;
            }
            String plain = ChatColor.stripColor(line).trim();
            Matcher matcher = EXPLICIT_ID.matcher(plain);
            if (matcher.matches()) {
                String value = ChatColor.stripColor(matcher.group(1)).trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return "";
    }

    private static String hiddenIdFromLore(ItemStack item) {
        if (!item.hasItemMeta()) {
            return "";
        }
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null) {
            return "";
        }
        for (String line : meta.getLore()) {
            for (String value : hiddenPayloads(line)) {
                if (!isAuthToken(value)) {
                    return value;
                }
            }
        }
        return "";
    }

    static String hiddenIdFromLine(String line) {
        for (String value : hiddenPayloads(line)) {
            if (!isAuthToken(value)) {
                return value;
            }
        }
        return "";
    }

    /**
     * Removes every hidden identity block from a Lore line and returns the text
     * the player actually sees. A line that only carried hidden data becomes
     * empty, which lets callers drop it completely.
     */
    public static String stripHiddenData(String line) {
        if (line == null || line.isEmpty() || line.indexOf(HIDDEN_PREFIX) < 0) {
            return line == null ? "" : line;
        }
        StringBuilder visible = new StringBuilder(line.length());
        int cursor = 0;
        while (cursor < line.length()) {
            int start = line.indexOf(HIDDEN_PREFIX, cursor);
            if (start < 0) {
                visible.append(line, cursor, line.length());
                break;
            }
            visible.append(line, cursor, start);
            int end = hiddenBlockEnd(line, start);
            if (end < 0) {
                visible.append(line, start, line.length());
                break;
            }
            cursor = end;
        }
        return visible.toString();
    }

    /**
     * Returns every hidden payload carried by a Lore line, in order. Supports
     * both the legacy standalone hidden line and hidden data appended to a
     * visible line.
     */
    private static List<String> hiddenPayloads(String line) {
        List<String> payloads = new ArrayList<String>();
        if (line == null || line.isEmpty()) {
            return payloads;
        }
        int cursor = 0;
        while (cursor < line.length()) {
            int start = line.indexOf(HIDDEN_PREFIX, cursor);
            if (start < 0) {
                break;
            }
            int end = hiddenBlockEnd(line, start);
            if (end < 0) {
                break;
            }
            String decoded = decodeHidden(line.substring(
                    start + HIDDEN_PREFIX.length(), end - HIDDEN_SUFFIX.length()));
            if (!decoded.isEmpty()) {
                payloads.add(decoded);
            }
            cursor = end;
        }
        return payloads;
    }

    private static int hiddenBlockEnd(String line, int start) {
        int cursor = start + HIDDEN_PREFIX.length();
        while (cursor + 1 < line.length() && line.charAt(cursor) == COLOR_CHAR
                && nibbleOf(line.charAt(cursor + 1)) >= 0) {
            cursor += 2;
        }
        return cursor > start + HIDDEN_PREFIX.length()
                && cursor + 1 < line.length()
                && line.charAt(cursor) == COLOR_CHAR
                && line.charAt(cursor + 1) == 'r'
                ? cursor + 2 : -1;
    }

    private static String decodeHidden(String encoded) {
        if (encoded == null || encoded.length() == 0 || (encoded.length() & 1) != 0) {
            return "";
        }
        StringBuilder hex = new StringBuilder(encoded.length() / 2);
        for (int index = 0; index < encoded.length(); index += 2) {
            if (encoded.charAt(index) != COLOR_CHAR) {
                return "";
            }
            int nibble = nibbleOf(encoded.charAt(index + 1));
            if (nibble < 0) {
                return "";
            }
            hex.append(Integer.toHexString(nibble));
        }
        try {
            byte[] bytes = new byte[hex.length() / 2];
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = (byte) Integer.parseInt(
                        hex.substring(index * 2, index * 2 + 2), 16);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (RuntimeException exception) {
            return "";
        }
    }

    static String hiddenIdentityLine(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder line = new StringBuilder(HIDDEN_PREFIX);
        for (byte item : bytes) {
            int unsigned = item & 0xff;
            line.append(COLOR_CHAR).append(NIBBLE_COLORS[unsigned >>> 4]);
            line.append(COLOR_CHAR).append(NIBBLE_COLORS[unsigned & 0x0f]);
        }
        return line.append(HIDDEN_SUFFIX).toString();
    }

    private static boolean isAuthToken(String token) {
        return token != null && token.startsWith(AUTH_TOKEN_PREFIX);
    }

    private static int nibbleOf(char color) {
        for (int index = 0; index < NIBBLE_COLORS.length; index++) {
            if (NIBBLE_COLORS[index] == color) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isLegacyIdentityLine(String line) {
        return EXPLICIT_ID.matcher(ChatColor.stripColor(line == null ? "" : line).trim()).matches();
    }

    private static String canonical(ItemStack item) {
        ItemStack normalized = item.clone();
        if (normalized.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = normalized.getItemMeta();
            if (meta != null && meta.hasLore()) {
                List<String> lore = new ArrayList<String>();
                for (String line : meta.getLore()) {
                    if (line == null || isLegacyIdentityLine(line)) {
                        continue;
                    }
                    String visible = stripHiddenData(line);
                    if (!visible.isEmpty()) {
                        lore.add(visible);
                    }
                }
                meta.setLore(lore);
                normalized.setItemMeta(meta);
            }
        }

        Map<String, Object> serialized;
        try {
            serialized = normalized.serialize();
        } catch (RuntimeException exception) {
            return item.getType().name() + '|'
                    + item.getDurability() + '|'
                    + String.valueOf(item.getItemMeta());
        }

        Map<String, Object> copy = new java.util.TreeMap<String, Object>(serialized);
        copy.remove("amount");
        StringBuilder result = new StringBuilder();
        appendValue(result, copy);
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendValue(StringBuilder target, Object value) {
        if (value == null) {
            target.append("<null>");
        } else if (value instanceof Map) {
            Map<String, Object> sorted =
                    new java.util.TreeMap<String, Object>((Map<String, Object>) value);
            target.append('{');
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                target.append(entry.getKey()).append('=');
                appendValue(target, entry.getValue());
                target.append(';');
            }
            target.append('}');
        } else if (value instanceof Collection) {
            List<Object> values = new ArrayList<Object>((Collection<Object>) value);
            target.append('[');
            for (Object element : values) {
                appendValue(target, element);
                target.append(',');
            }
            target.append(']');
        } else {
            target.append(value.getClass().getName()).append(':').append(value);
        }
    }
}

