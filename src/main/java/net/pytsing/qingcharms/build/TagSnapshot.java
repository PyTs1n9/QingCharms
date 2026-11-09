package net.pytsing.qingcharms.build;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 构筑标签计数。
 *
 * <p>标签由已装备饰品定义提供；每件饰品的去重标签各贡献一次。</p>
 */
public final class TagSnapshot {

    private final Map<String, Integer> buildCounts;

    public TagSnapshot(Map<String, Integer> buildCounts) {
        this.buildCounts = immutableCounts(buildCounts);
    }

    public int getBuildCount(String tag) {
        Integer value = buildCounts.get(normalize(tag));
        return value == null ? 0 : value.intValue();
    }

    public Map<String, Integer> getBuildCounts() {
        return buildCounts;
    }

    public boolean isEmpty() {
        return buildCounts.isEmpty();
    }

    public boolean hasAll(Set<String> tags) {
        for (String tag : tags) {
            if (getBuildCount(tag) <= 0) {
                return false;
            }
        }
        return !tags.isEmpty();
    }

    public boolean sameAs(TagSnapshot other) {
        return other != null && buildCounts.equals(other.buildCounts);
    }

    private static Map<String, Integer> immutableCounts(Map<String, Integer> values) {
        Map<String, Integer> copy = new LinkedHashMap<String, Integer>();
        if (values != null) {
            for (Map.Entry<String, Integer> entry : values.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null
                        && entry.getValue().intValue() > 0) {
                    copy.put(normalize(entry.getKey()),
                            Integer.valueOf(entry.getValue().intValue()));
                }
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.US);
    }
}
