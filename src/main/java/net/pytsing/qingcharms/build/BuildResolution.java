package net.pytsing.qingcharms.build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BuildResolution {

    private final BuildSnapshot snapshot;

    public BuildResolution(BuildSnapshot snapshot) {
        this.snapshot = snapshot == null
                ? new BuildSnapshot(null, Collections.<String>emptySet(),
                new TagSnapshot(null), Collections.<BuildRule>emptyList())
                : snapshot;
    }

    public BuildSnapshot getSnapshot() {
        return snapshot;
    }

    public Set<String> getClassIds() {
        return snapshot.getClassIds();
    }

    public TagSnapshot getTags() {
        return snapshot.getTags();
    }

    public List<BuildRule> getActiveRules() {
        return snapshot.getActiveRules();
    }

    public List<BuildRule> getLoreRules() {
        return Collections.unmodifiableList(
                new ArrayList<BuildRule>(snapshot.getActiveRules()));
    }

    public Set<String> getActiveRuleIds() {
        Set<String> result = new LinkedHashSet<String>();
        for (BuildRule rule : getActiveRules()) {
            result.add(rule.getId());
        }
        return Collections.unmodifiableSet(result);
    }
}
