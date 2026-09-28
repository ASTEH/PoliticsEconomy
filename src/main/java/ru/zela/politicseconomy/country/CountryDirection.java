package ru.zela.politicseconomy.country;

import java.util.Locale;

/**
 * Main economic specialization of a country.
 *
 * 
 */
public enum CountryDirection {
    INDUSTRIAL("industrial", "Индустриальная держава"),
    RESOURCE("resource", "Ресурсно-аграрная держава"),
    TRADE("trade", "Торгово-транспортная держава");

    private final String commandName;
    private final String displayName;

    CountryDirection(String commandName, String displayName) {
        this.commandName = commandName;
        this.displayName = displayName;
    }

    public String commandName() {
        return commandName;
    }

    public String displayName() {
        return displayName;
    }

    public static CountryDirection fromCommandName(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (CountryDirection direction : values()) {
            if (direction.commandName.equals(normalized)) {
                return direction;
            }
        }
        return null;
    }
}
