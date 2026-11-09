package net.pytsing.qingcharms.build;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ClassDefinition {

    private final String id;
    private final String displayName;
    private final String permission;
    private final List<String> titles;
    private final Set<String> tags;
    private final Set<String> recommendedTags;

    public ClassDefinition(String id, String displayName, String permission,
                           Collection<String> titles, Collection<String> tags,
                           Collection<String> recommendedTags) {
        this.id = normalizeId(id);
        this.displayName = displayName == null || displayName.trim().isEmpty()
                ? this.id : displayName.trim();
        this.permission = permission == null ? "" : permission.trim();
        this.titles = immutableStrings(titles);
        this.tags = immutableTags(tags);
        Set<String> recommendations = immutableTags(recommendedTags);
        if (recommendations.isEmpty()) {
            recommendations = this.tags;
        }
        this.recommendedTags = recommendations;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPermission() {
        return permission;
    }

    public List<String> getTitles() {
        return titles;
    }

    public Set<String> getTags() {
        return tags;
    }

    public Set<String> getRecommendedTags() {
        return recommendedTags;
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static List<String> immutableStrings(Collection<String> values) {
        List<String> copy = new ArrayList<String>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    copy.add(value.trim());
                }
            }
        }
        return Collections.unmodifiableList(copy);
    }

    private static Set<String> immutableTags(Collection<String> values) {
        Set<String> copy = new LinkedHashSet<String>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    copy.add(value.trim().toLowerCase(Locale.US));
                }
            }
        }
        return Collections.unmodifiableSet(copy);
    }
}
