package net.pytsing.qingcharms.bag;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class BagSorter {

    public List<CharmBagEntry> sort(List<CharmBagEntry> source, BagSortMode mode) {
        List<CharmBagEntry> result = source == null
                ? new ArrayList<CharmBagEntry>()
                : new ArrayList<CharmBagEntry>(source);
        final BagSortMode effective = mode == null ? BagSortMode.RARITY : mode;
        final Collator collator = Collator.getInstance(Locale.CHINA);
        Collections.sort(result, new Comparator<CharmBagEntry>() {
            @Override
            public int compare(CharmBagEntry left, CharmBagEntry right) {
                int value;
                switch (effective) {
                    case NAME:
                        value = collator.compare(left.getName(), right.getName());
                        break;
                    case LEVEL:
                        value = Integer.compare(right.getLevel(), left.getLevel());
                        break;
                    case TYPE:
                        value = collator.compare(left.getType(), right.getType());
                        break;
                    case RARITY:
                    default:
                        value = Integer.compare(
                                right.getRarity().ordinal(), left.getRarity().ordinal());
                        break;
                }
                if (value != 0) {
                    return value;
                }
                return left.getUniqueId().compareTo(right.getUniqueId());
            }
        });
        return result;
    }
}