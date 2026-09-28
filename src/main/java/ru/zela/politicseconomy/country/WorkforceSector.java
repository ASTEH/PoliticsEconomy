package ru.zela.politicseconomy.country;

public enum WorkforceSector {
    AGRICULTURE("agriculture", "Сельское хозяйство", "minecraft:wheat", 0.50D),
    EXTRACTION("extraction", "Добыча ресурсов", "minecraft:iron_pickaxe", 0.50D),
    INDUSTRY("industry", "Промышленность", "minecraft:iron_ingot", 0.60D),
    MILITARY("military", "Военная промышленность", "minecraft:iron_sword", 0.45D),
    TRADE_LOGISTICS("trade_logistics", "Торговля и логистика", "minecraft:emerald", 0.50D),
    CONSTRUCTION_SERVICES("construction_services", "Строительство и услуги", "minecraft:stonecutter", 0.35D);

    private final String commandName;
    private final String displayName;
    private final String iconItemId;
    private final double bonusPer100Workers;

    WorkforceSector(String commandName, String displayName, String iconItemId, double bonusPer100Workers) {
        this.commandName = commandName;
        this.displayName = displayName;
        this.iconItemId = iconItemId;
        this.bonusPer100Workers = bonusPer100Workers;
    }

    public String commandName() {
        return commandName;
    }

    public String displayName() {
        return displayName;
    }

    public String iconItemId() {
        return iconItemId;
    }

    public double bonusPer100Workers() {
        return bonusPer100Workers;
    }

    public static WorkforceSector fromCommandName(String value) {
        if (value == null) return null;
        for (WorkforceSector sector : values()) {
            if (sector.commandName.equalsIgnoreCase(value)) return sector;
        }
        return null;
    }
}
