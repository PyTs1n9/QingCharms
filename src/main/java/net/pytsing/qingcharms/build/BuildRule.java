package net.pytsing.qingcharms.build;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Immutable build definition. Legacy accessors remain for API compatibility. */
public final class BuildRule {

    private final String id;
    private final String name;
    private final String group;
    private final Set<String> tags;
    private final Map<String, Integer> requirements;
    private final Set<String> classes;
    private final int count;
    private final List<String> attributes;
    private final List<BuildCondition> conditions;
    private final boolean legacy;

    /** Legacy constructor retained for source and binary compatibility. */
    public BuildRule(String id, String name, String group, Collection<String> tags,
                     Map<String, Integer> requirements, Collection<String> classes,
                     int count, Collection<String> addLore) {
        this.id = normalize(id);
        this.name = name == null || name.trim().isEmpty() ? this.id : name.trim();
        this.group = group == null || group.trim().isEmpty()
                ? null : normalize(group);
        this.tags = immutableTags(tags);
        this.requirements = immutableRequirements(requirements);
        this.classes = immutableTags(classes);
        this.count = Math.max(1, count);
        this.attributes = immutableStrings(addLore);
        this.conditions = Collections.emptyList();
        this.legacy = true;
    }

    /** Constructor for the independent builds.yml format. */
    public BuildRule(String id, String name, Collection<BuildCondition> conditions,
                     Collection<String> attributes) {
        this.id = normalize(id);
        this.name = name == null || name.trim().isEmpty() ? this.id : name.trim();
        this.group = null;
        List<BuildCondition> conditionCopy = new ArrayList<BuildCondition>();
        if (conditions != null) {
            conditionCopy.addAll(conditions);
        }
        this.conditions = Collections.unmodifiableList(conditionCopy);
        this.tags = conditionTags(conditionCopy);
        this.requirements = Collections.emptyMap();
        this.classes = Collections.emptySet();
        this.count = conditionCopy.isEmpty() ? 0 : conditionCopy.get(0).getCount();
        this.attributes = immutableStrings(attributes);
        this.legacy = false;
    }

    public String getId() { return id; }

    public String getName() { return name; }

    public String getGroup() { return group; }

    public String getGroupKey() {
        if (group != null) {
            return "group:" + group;
        }
        if (tags.isEmpty()) {
            return null;
        }
        List<String> sorted = new ArrayList<String>(tags);
        Collections.sort(sorted);
        StringBuilder builder = new StringBuilder("tags:");
        for (String tag : sorted) {
            builder.append(tag).append(',');
        }
        return builder.toString();
    }

    public Set<String> getTags() { return tags; }

    public Map<String, Integer> getRequirements() { return requirements; }

    public Set<String> getClasses() { return classes; }

    public int getCount() { return count; }

    public List<String> getAddLore() { return attributes; }

    public List<String> getAttributes() { return attributes; }

    public List<BuildCondition> getConditions() { return conditions; }

    public boolean matches(TagSnapshot tagSnapshot, Set<String> activeClasses) {
        if (legacy) {
            return matchesLegacy(tagSnapshot, activeClasses);
        }
        if (conditions.isEmpty()) {
            return false;
        }
        for (BuildCondition condition : conditions) {
            if (!condition.matches(tagSnapshot.getBuildCount(condition.getTag()))) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesLegacy(TagSnapshot tagSnapshot, Set<String> activeClasses) {
        if (!classes.isEmpty() && !containsAny(classes, activeClasses)) {
            return false;
        }
        for (Map.Entry<String, Integer> requirement : requirements.entrySet()) {
            if (tagSnapshot.getBuildCount(requirement.getKey())
                    < requirement.getValue().intValue()) {
                return false;
            }
        }
        if (tags.isEmpty()) {
            return !requirements.isEmpty();
        }
        int total = 0;
        for (String tag : tags) {
            total += tagSnapshot.getBuildCount(tag);
        }
        return total >= count;
    }

    private static Set<String> conditionTags(Collection<BuildCondition> conditions) {
        Set<String> result = new LinkedHashSet<String>();
        for (BuildCondition condition : conditions) {
            result.add(condition.getTag());
        }
        return Collections.unmodifiableSet(result);
    }

    private static boolean containsAny(Set<String> expected, Set<String> actual) {
        if (actual == null) {
            return false;
        }
        for (String value : expected) {
            if (actual.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> immutableTags(Collection<String> values) {
        Set<String> copy = new LinkedHashSet<String>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    copy.add(normalize(value));
                }
            }
        }
        return Collections.unmodifiableSet(copy);
    }

    private static List<String> immutableStrings(Collection<String> values) {
        List<String> copy = new ArrayList<String>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    copy.add(value);
                }
            }
        }
        return Collections.unmodifiableList(copy);
    }

    private static Map<String, Integer> immutableRequirements(Map<String, Integer> values) {
        Map<String, Integer> copy = new LinkedHashMap<String, Integer>();
        if (values != null) {
            for (Map.Entry<String, Integer> entry : values.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null
                        && entry.getValue().intValue() > 0) {
                    copy.put(normalize(entry.getKey()), entry.getValue());
                }
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }
}
