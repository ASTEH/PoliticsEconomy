package ru.zela.politicseconomy.country;

import java.util.Locale;

public enum ReligionType {
    SECULAR(
        "secular",
        "Светское государство",
        new CountryPolicyProfile(5, 0, 0, 0, 5, -5, 0, 0, 0, 5)
    ),
    CHRISTIANITY(
        "christianity",
        "Христианство",
        new CountryPolicyProfile(0, 0, 5, -5, 5, -5, 0, 0, 0, 5)
    ),
    ISLAM(
        "islam",
        "Ислам",
        new CountryPolicyProfile(-5, 0, 10, 0, 10, -5, 0, 0, 0, 5)
    ),
    BUDDHISM(
        "buddhism",
        "Буддизм",
        new CountryPolicyProfile(-5, 0, 5, -10, 0, 0, -5, 0, 0, 10)
    ),
    JUDAISM(
        "judaism",
        "Иудаизм",
        new CountryPolicyProfile(5, 0, -5, 0, 15, -10, 5, 0, 0, 0)
    );

    private final String commandName;
    private final String displayName;
    private final CountryPolicyProfile profile;

    ReligionType(String commandName, String displayName, CountryPolicyProfile profile) {
        this.commandName = commandName;
        this.displayName = displayName;
        this.profile = profile;
    }

    public String commandName() { return commandName; }
    public String displayName() { return displayName; }
    public CountryPolicyProfile profile() { return profile; }

    public static ReligionType fromCommandName(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (ReligionType type : values()) {
            if (type.commandName.equals(normalized)) return type;
        }
        return null;
    }
}
