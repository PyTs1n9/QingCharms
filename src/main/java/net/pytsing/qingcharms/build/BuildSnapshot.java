package net.pytsing.qingcharms.build;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class BuildSnapshot {

    private final UUID ownerId;
    private final Set<String> classIds;
    private final TagSnapshot tags;
    private final List<BuildRule> activeRules;

    public BuildSnapshot(UUID ownerId, Set<String> classIds, TagSnapshot tags,
                         Collection<BuildRule> activeRules) {
        this.ownerId = ownerId;
        Set<String> classes = new LinkedHashSet<String>();
        if (classIds != null) {
            classes.addAll(classIds);
        }
        this.classIds = Collections.unmodifiableSet(classes);
        this.tags = tags == null ? new TagSnapshot(null) : tags;
        this.activeRules = immutableRules(activeRules);
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Set<String> getClassIds() {
        return classIds;
    }

    public TagSnapshot getTags() {
        return tags;
    }

    public List<BuildRule> getActiveRules() {
        return activeRules;
    }

    public String getSignature() {
        StringBuilder builder = new StringBuilder();
        for (String classId : sorted(classIds)) {
            builder.append("c:").append(classId).append(';');
        }
        for (String tag : sorted(tags.getBuildCounts().keySet())) {
            builder.append("t:").append(tag).append('=')
                    .append(tags.getBuildCount(tag)).append(';');
        }
        for (BuildRule rule : activeRules) {
            builder.append("r:").append(rule.getId()).append(';');
        }
        return builder.toString();
    }

    private static List<String> sorted(Collection<String> values) {
        List<String> copy = new ArrayList<String>(values);
        Collections.sort(copy);
        return copy;
    }

    private static List<BuildRule> immutableRules(Collection<BuildRule> values) {
        List<BuildRule> copy = new ArrayList<BuildRule>();
        if (values != null) {
            copy.addAll(values);
        }
        return Collections.unmodifiableList(copy);
    }
}
