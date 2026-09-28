package ru.zela.politicseconomy.infrastructure;

public enum InfrastructureCategory {
    DECORATIVE("Декоративная"),
    RESIDENTIAL("Жилая"),
    RESOURCE("Ресурсная"),
    INDUSTRIAL("Промышленная"),
    MILITARY("Военная"),
    TRANSPORT("Транспортная"),
    ADVANCED("Продвинутая");

    private final String displayName;

    InfrastructureCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
