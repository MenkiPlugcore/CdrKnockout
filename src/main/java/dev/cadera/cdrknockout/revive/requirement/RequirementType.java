package dev.cadera.cdrknockout.revive.requirement;

import java.util.Locale;

public enum RequirementType {
    ITEM("item"),
    XP_LEVEL("xp-level"),
    MONEY("money"),
    AURASKILLS("auraskills"),
    PERMISSION("permission");

    private final String configKey;

    RequirementType(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static RequirementType parse(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
