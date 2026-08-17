package datura.svcaddon.config;

import java.util.Locale;

public enum PlayerPathPreference {
    DEFAULT,
    ON,
    OFF;

    public static PlayerPathPreference parse(String value) {
        if (value == null) {
            return DEFAULT;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "on", "true", "enabled" -> ON;
            case "off", "false", "disabled" -> OFF;
            default -> DEFAULT;
        };
    }

    public String serializedName() {
        return name().toLowerCase();
    }
}
