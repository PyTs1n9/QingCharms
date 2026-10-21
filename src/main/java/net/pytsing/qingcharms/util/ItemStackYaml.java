package net.pytsing.qingcharms.util;

import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ItemStackYaml {

    private static final String ITEM_STACK_ALIAS = "==: org.bukkit.inventory.ItemStack";

    private ItemStackYaml() {
    }

    public static Map<String, Object> serializeForExport(ItemStack item) {
        if (item == null) {
            throw new IllegalArgumentException("item cannot be null");
        }

        // Legacy Bukkit uses the same field for item variants and tool wear.
        boolean useData = item.getType().getMaxDurability() <= 0;
        return withLegacyDataField(item.serialize(), useData);
    }

    static Map<String, Object> withLegacyDataField(Map<String, Object> source,
                                                    boolean useData) {
        Map<String, Object> serialized = new LinkedHashMap<String, Object>();
        serialized.put("==", "org.bukkit.inventory.ItemStack");
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = useData && "damage".equals(entry.getKey())
                    ? "data" : entry.getKey();
            serialized.put(key, entry.getValue());
        }
        return serialized;
    }

    static String normalizeDataField(String source) {
        if (source == null || source.isEmpty()) {
            return source;
        }

        // Normalize our exported `data` field before Bukkit deserializes ItemStack.
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String body = lineBody(lines[i]);
            int indent = indentation(lines[i], body.length());
            if (!ITEM_STACK_ALIAS.equals(body.substring(indent).trim())) {
                continue;
            }

            int dataLine = -1;
            boolean hasDamage = false;
            for (int j = i + 1; j < lines.length; j++) {
                String candidate = lineBody(lines[j]);
                if (candidate.trim().isEmpty() || candidate.trim().startsWith("#")) {
                    continue;
                }
                int candidateIndent = indentation(lines[j], candidate.length());
                if (candidateIndent < indent) {
                    break;
                }
                if (candidateIndent != indent) {
                    continue;
                }

                String key = keyOf(candidate.substring(candidateIndent));
                if ("data".equals(key)) {
                    dataLine = j;
                } else if ("damage".equals(key)) {
                    hasDamage = true;
                }
            }

            if (dataLine >= 0 && !hasDamage) {
                lines[dataLine] = replaceKey(lines[dataLine], "data", "damage");
            }
        }
        return joinLines(lines);
    }

    private static String lineBody(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private static int indentation(String line, int length) {
        int indent = 0;
        while (indent < length && line.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }

    private static String keyOf(String line) {
        int colon = line.indexOf(':');
        return colon < 0 ? "" : line.substring(0, colon).trim();
    }

    private static String replaceKey(String line, String oldKey, String newKey) {
        int offset = 0;
        while (offset < line.length() && line.charAt(offset) == ' ') {
            offset++;
        }
        return line.substring(0, offset) + newKey + line.substring(offset + oldKey.length());
    }

    private static String joinLines(String[] lines) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                result.append('\n');
            }
            result.append(lines[i]);
        }
        return result.toString();
    }
}
