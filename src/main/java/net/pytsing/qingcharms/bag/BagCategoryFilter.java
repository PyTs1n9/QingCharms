package net.pytsing.qingcharms.bag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BagCategoryFilter {

    public static final String ALL = "全部";

    public List<CharmBagEntry> filter(List<CharmBagEntry> source, String category) {
        List<CharmBagEntry> result = new ArrayList<CharmBagEntry>();
        if (source == null) {
            return result;
        }
        String normalized = normalize(category);
        for (CharmBagEntry entry : source) {
            if (entry == null) {
                continue;
            }
            if (normalized.isEmpty() || ALL.equals(normalized)
                    || normalize(entry.getCategory()).equals(normalized)) {
                result.add(entry);
            }
        }
        return result;
    }

    public List<String> categories(List<CharmBagEntry> entries) {
        Set<String> values = new LinkedHashSet<String>();
        values.add(ALL);
        values.add("头盔");
        values.add("胸甲");
        values.add("护腿");
        values.add("靴子");
        values.add("项链");
        values.add("天空");
        if (entries != null) {
            for (CharmBagEntry entry : entries) {
                if (entry != null && entry.getCategory() != null
                        && !entry.getCategory().trim().isEmpty()) {
                    values.add(entry.getCategory().trim());
                }
            }
        }
        values.add("其他");
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }

    public String next(List<String> categories, String current) {
        if (categories == null || categories.isEmpty()) {
            return ALL;
        }
        for (int index = 0; index < categories.size(); index++) {
            if (normalize(categories.get(index)).equals(normalize(current))) {
                return categories.get((index + 1) % categories.size());
            }
        }
        return categories.get(0);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}