package ru.zela.politicseconomy.country;

import java.util.Locale;

public enum GovernmentType {
    DEMOCRACY(
        "democracy",
        "Демократия",
        new CountryPolicyProfile(-5, 0, 5, -5, 10, -10, 5, 0, -5, 10)
    ),
    COMMUNISM(
        "communism",
        "Коммунизм",
        new CountryPolicyProfile(15, 5, 0, 5, -15, 10, -10, -5, 0, 5)
    ),
    MONARCHY(
        "monarchy",
        "Монархия",
        new CountryPolicyProfile(-5, 0, 10, 10, -5, 5, -5, 0, 5, 0)
    ),
    FASCISM(
        "fascism",
        "Фашизм",
        new CountryPolicyProfile(15, -5, -10, 30, -20, 15, 10, 0, 10, -10)
    );

    private final String commandName;
    private final String displayName;
    private final CountryPolicyProfile profile;

    GovernmentType(String commandName, String displayName, CountryPolicyProfile profile) {
        this.commandName = commandName;
        this.displayName = displayName;
        this.profile = profile;
    }

    public String commandName() { return commandName; }
    public String displayName() { return displayName; }
    public CountryPolicyProfile profile() { return profile; }

    public static GovernmentType fromCommandName(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (GovernmentType type : values()) {
            if (type.commandName.equals(normalized)) return type;
        }
        return null;
    }
}
