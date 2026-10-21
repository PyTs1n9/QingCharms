package net.pytsing.qingcharms.api.event;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

final class EventSets {

    private EventSets() {
    }

    static Set<String> copy(Collection<String> values) {
        Set<String> copy = new LinkedHashSet<String>();
        if (values != null) {
            copy.addAll(values);
        }
        return Collections.unmodifiableSet(copy);
    }
}