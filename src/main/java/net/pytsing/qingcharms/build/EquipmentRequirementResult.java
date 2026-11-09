package net.pytsing.qingcharms.build;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Result of the single equipment requirement gate.
 */
public final class EquipmentRequirementResult {

    public enum Reason {
        VALID,
        INVALID_ITEM,
        WRONG_SLOT,
        MISSING_LORE,
        LEVEL,
        CLASS_PERMISSION,
        CHARM_PERMISSION
    }

    private final Reason reason;
    private final int requiredLevel;
    private final List<String> requiredClassNames;

    private EquipmentRequirementResult(Reason reason, int requiredLevel,
                                       Collection<String> requiredClassNames) {
        this.reason = reason;
        this.requiredLevel = requiredLevel;
        List<String> copy = new ArrayList<String>();
        if (requiredClassNames != null) {
            copy.addAll(requiredClassNames);
        }
        this.requiredClassNames = Collections.unmodifiableList(copy);
    }

    static EquipmentRequirementResult valid() {
        return new EquipmentRequirementResult(Reason.VALID, 0,
                Collections.<String>emptyList());
    }

    static EquipmentRequirementResult invalid(Reason reason) {
        return new EquipmentRequirementResult(reason, 0,
                Collections.<String>emptyList());
    }

    static EquipmentRequirementResult level(int requiredLevel) {
        return new EquipmentRequirementResult(Reason.LEVEL, requiredLevel,
                Collections.<String>emptyList());
    }

    static EquipmentRequirementResult classes(Collection<String> classNames) {
        return new EquipmentRequirementResult(
                Reason.CLASS_PERMISSION, 0, classNames);
    }

    public boolean isAllowed() {
        return reason == Reason.VALID;
    }

    public Reason getReason() {
        return reason;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public List<String> getRequiredClassNames() {
        return requiredClassNames;
    }

    public String getMessageKey() {
        switch (reason) {
            case INVALID_ITEM:
                return "charm.equip.invalid-item";
            case WRONG_SLOT:
                return "charm.equip.wrong-slot";
            case MISSING_LORE:
                return "charm.equip.no-lore";
            case LEVEL:
                return "charm.equip.level";
            case CLASS_PERMISSION:
                return requiredClassNames.size() == 1
                        ? "charm.equip.class" : "charm.equip.class-any";
            case CHARM_PERMISSION:
                return "charm.equip.no-permission";
            default:
                return "";
        }
    }

    public Object[] getMessageArguments() {
        if (reason == Reason.LEVEL) {
            return new Object[]{Integer.valueOf(requiredLevel)};
        }
        if (reason == Reason.CLASS_PERMISSION) {
            return new Object[]{join(requiredClassNames)};
        }
        return new Object[0];
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append('、');
            }
            result.append(value);
        }
        return result.toString();
    }
}