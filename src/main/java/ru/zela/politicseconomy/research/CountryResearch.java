package ru.zela.politicseconomy.research;

import ru.zela.politicseconomy.country.CountryDirection;

import java.util.List;

public enum CountryResearch {
    INDUSTRIAL_MECHANIZATION(
        "industrial_mechanization", CountryDirection.INDUSTRIAL, "Механизация производства",
        "Переход от ручного труда к массовой механической автоматизации.",
        1, 2, 150, "minecraft:mechanical_press", 0, 0,
        List.of(), List.of(
            "create:mechanical_press",
            "create:mechanical_mixer",
            "create:mechanical_crafter",
            "create:deployer"
        ),
        "Create: базовая автоматизация"
    ),
    INDUSTRIAL_ELECTRIFICATION(
        "industrial_electrification", CountryDirection.INDUSTRIAL, "Электрификация",
        "Освоение электрических приводов и промышленной электросети.",
        2, 3, 250, "createaddition:alternator", 1, 1,
        List.of("industrial_mechanization"), List.of(
            "createaddition:*",
            "powergrid:*"
        ),
        "Create Crafts & Additions + Create: Power Grid"
    ),
    INDUSTRIAL_MACHINERY(
        "industrial_machinery", CountryDirection.INDUSTRIAL, "Машиностроение",
        "Производство сложных промышленных узлов и автоматических линий.",
        3, 4, 400, "create:mechanical_arm", -1, 2,
        List.of("industrial_electrification"), List.of(
            "create:mechanical_arm",
            "create:sequenced_gearshift",
            "create:depot",
            "create:spout"
        ),
        "Create: сложные производственные узлы"
    ),
    INDUSTRIAL_MILITARY(
        "industrial_military", CountryDirection.INDUSTRIAL, "Военная промышленность",
        "Создание промышленной базы для крупнокалиберного вооружения.",
        4, 5, 600, "createbigcannons:cannon_mount", 0, 3,
        List.of("industrial_machinery"), List.of(
            "createbigcannons:*"
        ),
        "Create Big Cannons"
    ),
    INDUSTRIAL_RADAR(
        "industrial_radar", CountryDirection.INDUSTRIAL, "Радиолокация",
        "Военная электроника, обнаружение целей и управление орудиями.",
        5, 7, 900, "create_radar:radar_bearing", 1, 4,
        List.of("industrial_military"), List.of(
            "create_radar:*",
            "createradar:*"
        ),
        "Create: Radars"
    ),
    INDUSTRIAL_ADVANCED(
        "industrial_advanced", CountryDirection.INDUSTRIAL, "Передовая инженерия",
        "Высокоточная промышленность и самые сложные производственные узлы.",
        5, 7, 900, "create:precision_mechanism", -1, 4,
        List.of("industrial_machinery"), List.of(
            "create:precision_mechanism",
            "create:steam_engine",
            "create:steam_whistle"
        ),
        "Create: передовое производство"
    ),

    RESOURCE_MINING(
        "resource_mining", CountryDirection.RESOURCE, "Освоение недр",
        "Механизированная добыча руды и массовое извлечение сырья.",
        1, 2, 150, "create:mechanical_drill", 0, 0,
        List.of(), List.of(
            "create:mechanical_drill",
            "create:crushing_wheel",
            "create:millstone"
        ),
        "Create: механизированная добыча"
    ),
    RESOURCE_REFINING(
        "resource_refining", CountryDirection.RESOURCE, "Обогащение сырья",
        "Промышленная очистка и разделение добытого сырья.",
        2, 3, 250, "create:encased_fan", 0, 1,
        List.of("resource_mining"), List.of(
            "create:encased_fan",
            "create:bulk_washing",
            "create:bulk_blasting"
        ),
        "Create: переработка сырья"
    ),
    RESOURCE_AGRO(
        "resource_agro", CountryDirection.RESOURCE, "Агропромышленность",
        "Автоматизация крупных сельскохозяйственных комплексов.",
        3, 4, 400, "create:harvester", -1, 2,
        List.of("resource_refining"), List.of(
            "create:harvester",
            "create:mechanical_plough",
            "create:portable_storage_interface"
        ),
        "Create: аграрная автоматизация"
    ),
    RESOURCE_OIL(
        "resource_oil", CountryDirection.RESOURCE, "Нефтяная промышленность",
        "Освоение нефти, тяжёлой инженерии и глубокой переработки.",
        3, 4, 400, "tfmg:distillation_tower", 1, 2,
        List.of("resource_refining"), List.of(
            "tfmg:*"
        ),
        "Create: The Factory Must Grow"
    ),
    RESOURCE_DIESEL(
        "resource_diesel", CountryDirection.RESOURCE, "Дизельная энергетика",
        "Переход к промышленным дизельным генераторам и топливным системам.",
        4, 5, 600, "createdieselgenerators:diesel_engine", 1, 3,
        List.of("resource_oil"), List.of(
            "createdieselgenerators:*"
        ),
        "Create: Diesel Generators"
    ),
    RESOURCE_RESERVE(
        "resource_reserve", CountryDirection.RESOURCE, "Стратегические резервы",
        "Крупные государственные запасы сырья, жидкостей и промышленного инвентаря.",
        5, 7, 900, "create:item_vault", 0, 4,
        List.of("resource_diesel", "resource_agro"), List.of(
            "create:item_vault",
            "create:fluid_tank"
        ),
        "Create: государственная складская инфраструктура"
    ),

    TRADE_MARKET(
        "trade_market", CountryDirection.TRADE, "Единый рынок",
        "Формирование национальной торговой инфраструктуры.",
        1, 2, 150, "create:stockpile", 0, 0,
        List.of(), List.of(
            "create:display_board",
            "create:display_link",
            "create:stockpile"
        ),
        "Create: торговые узлы"
    ),
    TRADE_RAIL(
        "trade_rail", CountryDirection.TRADE, "Железнодорожная сеть",
        "Открытие грузовых железных дорог и полноценной поездной логистики.",
        2, 3, 250, "create:track", -1, 1,
        List.of("trade_market"), List.of(
            "create:track",
            "create:track_station",
            "create:controls",
            "create:train_signal"
        ),
        "Create: железнодорожная логистика"
    ),
    TRADE_LOGISTICS(
        "trade_logistics", CountryDirection.TRADE, "Логистические узлы",
        "Постоянная работа удалённых торговых и складских объектов.",
        2, 3, 250, "create_power_loader:chunk_loader", 1, 1,
        List.of("trade_market"), List.of(
            "create_power_loader:*"
        ),
        "Create: Power Loader"
    ),
    TRADE_ROAD(
        "trade_road", CountryDirection.TRADE, "Автотранспорт",
        "Переход от железной дороги к гибкой автомобильной доставке.",
        3, 4, 400, "immersive_vehicles:vehicle_workbench", -1, 2,
        List.of("trade_rail"), List.of(
            "immersive_vehicles:*"
        ),
        "Immersive Vehicles / MTS"
    ),
    TRADE_AIR(
        "trade_air", CountryDirection.TRADE, "Воздушная логистика",
        "Высокоскоростная доставка и авиационная инфраструктура.",
        4, 5, 600, "hero_aviation:helicopter", -1, 3,
        List.of("trade_road"), List.of(
            "hero_aviation:*",
            "mts:*"
        ),
        "Hero Aviation / MTS content packs"
    ),
    TRADE_HUB(
        "trade_hub", CountryDirection.TRADE, "Международный торговый узел",
        "Объединение железных дорог, складов и транспорта в единую сеть.",
        5, 7, 900, "create:portable_storage_interface", 1, 4,
        List.of("trade_logistics", "trade_air"), List.of(
            "create:linked_controller",
            "create:clipboard"
        ),
        "Create: интегрированная логистика"
    );

    private final String id;
    private final CountryDirection direction;
    private final String title;
    private final String description;
    private final int minLevel;
    private final int researchCost;
    private final int moneyCost;
    private final String icon;
    private final int column;
    private final int row;
    private final List<String> prerequisites;
    private final List<String> contentRules;
    private final String contentSummary;

    CountryResearch(
        String id,
        CountryDirection direction,
        String title,
        String description,
        int minLevel,
        int researchCost,
        int moneyCost,
        String icon,
        int column,
        int row,
        List<String> prerequisites,
        List<String> contentRules,
        String contentSummary
    ) {
        this.id = id;
        this.direction = direction;
        this.title = title;
        this.description = description;
        this.minLevel = minLevel;
        this.researchCost = researchCost;
        this.moneyCost = moneyCost;
        this.icon = icon;
        this.column = column;
        this.row = row;
        this.prerequisites = List.copyOf(prerequisites);
        this.contentRules = List.copyOf(contentRules);
        this.contentSummary = contentSummary;
    }

    public static CountryResearch byId(String id) {
        if (id == null) return null;
        for (CountryResearch research : values()) {
            if (research.id.equals(id)) return research;
        }
        return null;
    }

    public String id() { return id; }
    public CountryDirection direction() { return direction; }
    public String title() { return title; }
    public String description() { return description; }
    public int minLevel() { return minLevel; }
    public int researchCost() { return researchCost; }
    public int moneyCost() { return moneyCost; }
    public String icon() { return icon; }
    public int column() { return column; }
    public int row() { return row; }
    public List<String> prerequisites() { return prerequisites; }
    public List<String> contentRules() { return contentRules; }
    public String contentSummary() { return contentSummary; }
}
