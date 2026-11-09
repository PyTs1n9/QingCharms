package net.pytsing.qingcharms.build;

import net.pytsing.qingcharms.model.CharmDefinition;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Counts each configured tag once for every equipped charm. */
public final class TagResolver {

    public TagSnapshot resolve(Map<String, CharmDefinition> equipment) {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        if (equipment != null) {
            for (Map.Entry<String, CharmDefinition> entry : equipment.entrySet()) {
                CharmDefinition charm = entry.getValue();
                if (entry.getKey() == null || charm == null) {
                    continue;
                }
                for (String tag : charm.getTags()) {
                    Integer count = counts.get(tag);
                    counts.put(tag, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
                }
            }
        }
        return new TagSnapshot(counts);
    }

    /** Compatibility overload; class definitions no longer contribute build tags. */
    public TagSnapshot resolve(Map<String, CharmDefinition> equipment,
                               Collection<ClassDefinition> ignoredClasses) {
        return resolve(equipment);
    }
}
