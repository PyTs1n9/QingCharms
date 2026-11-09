package net.pytsing.qingcharms.build;

import java.util.Locale;

/** One tag-count comparison in a build definition. */
public final class BuildCondition {

    private final String tag;
    private final int count;
    private final Operator operator;

    public BuildCondition(String tag, int count, String operator) {
        if (tag == null || tag.trim().isEmpty()) {
            throw new IllegalArgumentException("tag cannot be empty");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative");
        }
        this.tag = tag.trim().toLowerCase(Locale.US);
        this.count = count;
        this.operator = Operator.parse(operator);
    }

    public String getTag() {
        return tag;
    }

    public int getCount() {
        return count;
    }

    public String getOperator() {
        return operator.configName;
    }

    public boolean matches(int actual) {
        switch (operator) {
            case LT: return actual < count;
            case GT: return actual > count;
            case EQ: return actual == count;
            case GTEQ: return actual >= count;
            case LTEQ: return actual <= count;
            default: return false;
        }
    }

    private enum Operator {
        LT("lt"), GT("gt"), EQ("eq"), GTEQ("gteq"), LTEQ("lteq");

        private final String configName;

        Operator(String configName) {
            this.configName = configName;
        }

        private static Operator parse(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
            for (Operator operator : values()) {
                if (operator.configName.equals(normalized)) {
                    return operator;
                }
            }
            throw new IllegalArgumentException("unsupported operator '" + value + "'");
        }
    }
}
