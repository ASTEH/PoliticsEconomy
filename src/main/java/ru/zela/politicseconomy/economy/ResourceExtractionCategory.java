package ru.zela.politicseconomy.economy;

/** Economic category used to apply different extraction penalties. */
public enum ResourceExtractionCategory {
    ORE("Руды"),
    WOOD("Древесина"),
    AGRICULTURE("Сельское хозяйство"),
    FUEL("Топливо"),
    RAW_MATERIAL("Прочее сырьё");

    private final String displayName;

    ResourceExtractionCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
