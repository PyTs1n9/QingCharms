package net.pytsing.qingcharms.progression;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ResourceCost {

    private final Map<ResourceType, Double> amounts;

    public ResourceCost(Map<ResourceType, Double> amounts) {
        EnumMap<ResourceType, Double> copy =
                new EnumMap<ResourceType, Double>(ResourceType.class);
        if (amounts != null) {
            for (Map.Entry<ResourceType, Double> entry : amounts.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null
                        && entry.getValue().doubleValue() > 0.0D) {
                    copy.put(entry.getKey(), Double.valueOf(entry.getValue().doubleValue()));
                }
            }
        }
        this.amounts = Collections.unmodifiableMap(copy);
    }

    public static ResourceCost fromMap(Map<?, ?> configured) {
        EnumMap<ResourceType, Double> values =
                new EnumMap<ResourceType, Double>(ResourceType.class);
        if (configured != null) {
            for (Map.Entry<?, ?> entry : configured.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                try {
                    double amount = Double.parseDouble(
                            String.valueOf(entry.getValue()));
                    if (amount > 0.0D) {
                        values.put(ResourceType.parse(String.valueOf(entry.getKey())),
                                Double.valueOf(amount));
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore malformed optional progression costs.
                }
            }
        }
        return new ResourceCost(values);
    }

    public static ResourceCost from(ConfigurationSection section) {
        EnumMap<ResourceType, Double> values =
                new EnumMap<ResourceType, Double>(ResourceType.class);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                double amount = section.getDouble(key, 0.0D);
                if (amount > 0.0D) {
                    values.put(ResourceType.parse(key), Double.valueOf(amount));
                }
            }
        }
        return new ResourceCost(values);
    }

    public Map<ResourceType, Double> getAmounts() {
        return amounts;
    }

    public double get(ResourceType type) {
        Double value = amounts.get(type);
        return value == null ? 0.0D : value.doubleValue();
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }
}

