package ru.zela.politicseconomy.country;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Dynamic reform costs. Larger and more developed countries pay more for structural change. */
public final class CountryReformCostTable {
    public static final int DIRECTION_MONEY = 1500;
    public static final int GOVERNMENT_MONEY = 2000;
    public static final int RELIGION_MONEY = 1200;

    private CountryReformCostTable() {}

    public record Cost(int money, Map<String, Integer> materials) {
        public boolean free() { return money <= 0 && materials.isEmpty(); }
    }

    public static Cost cost(String action, boolean firstChoice) {
        return cost(action, firstChoice, 0, 1);
    }

    public static Cost cost(String action, boolean firstChoice, int population, int developmentLevel) {
        if (firstChoice) return new Cost(0, Map.of());

        int pop = Math.max(0, population);
        int development = Math.max(1, Math.min(5, developmentLevel));
        int populationMoney = (int) Math.floor(Math.sqrt(pop) * 60.0D);
        int populationTier = Math.min(4, pop / 1000);
        int developmentMoney = (development - 1) * 750;
        double materialScale = 1.0D + populationTier * 0.25D + (development - 1) * 0.10D;

        int baseMoney = switch (normalize(action)) {
            case "direction" -> DIRECTION_MONEY;
            case "government" -> GOVERNMENT_MONEY;
            case "religion" -> RELIGION_MONEY;
            default -> 0;
        };

        int money = baseMoney + populationMoney + populationTier * 500 + developmentMoney;
        Map<String, Integer> materials = switch (normalize(action)) {
            case "direction" -> materials(
                scaled(32, materialScale), "minecraft:iron_ingot",
                scaled(16, materialScale), "minecraft:gold_ingot",
                scaled(16, materialScale), "minecraft:coal");
            case "government" -> materials(
                scaled(16, materialScale), "minecraft:iron_ingot",
                scaled(16, materialScale), "minecraft:gold_ingot",
                scaled(16, materialScale), "minecraft:paper",
                scaled(8, materialScale), "minecraft:bread");
            case "religion" -> materials(
                scaled(8, materialScale), "minecraft:gold_ingot",
                scaled(32, materialScale), "minecraft:paper",
                scaled(16, materialScale), "minecraft:wheat",
                scaled(8, materialScale), "minecraft:book");
            default -> Map.of();
        };
        return new Cost(money, materials);
    }

    public static String summary(String action, boolean firstChoice, int population, int developmentLevel) {
        Cost cost = cost(action, firstChoice, population, developmentLevel);
        if (cost.free()) return "ПЕРВЫЙ ВЫБОР • БЕСПЛАТНО";
        StringBuilder text = new StringBuilder("$").append(cost.money());
        for (Map.Entry<String, Integer> entry : cost.materials().entrySet()) {
            text.append(" • ").append(entry.getValue()).append(' ').append(shortName(entry.getKey()));
        }
        return text.toString();
    }

    public static Set<String> allMaterialIds() {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        result.addAll(cost("direction", false).materials().keySet());
        result.addAll(cost("government", false).materials().keySet());
        result.addAll(cost("religion", false).materials().keySet());
        return Set.copyOf(result);
    }

    private static int scaled(int base, double scale) {
        return Math.max(base, (int) Math.ceil(base * scale));
    }

    private static Map<String, Integer> materials(Object... values) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) result.put((String) values[i + 1], (Integer) values[i]);
        return Map.copyOf(result);
    }

    private static String shortName(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
