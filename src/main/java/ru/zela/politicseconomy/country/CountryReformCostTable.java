package ru.zela.politicseconomy.country;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Shared, client-safe table describing the costs of country reforms. */
public final class CountryReformCostTable {
    public static final int DIRECTION_MONEY = 1500;
    public static final int GOVERNMENT_MONEY = 2000;
    public static final int RELIGION_MONEY = 1200;

    private CountryReformCostTable() {}

    public record Cost(int money, Map<String, Integer> materials) {
        public boolean free() {
            return money <= 0 && materials.isEmpty();
        }
    }

    public static Cost cost(String action, boolean firstChoice) {
        if (firstChoice) return new Cost(0, Map.of());

        return switch (normalize(action)) {
            case "direction" -> new Cost(
                DIRECTION_MONEY,
                materials(
                    "minecraft:iron_ingot", 32,
                    "minecraft:gold_ingot", 16
                )
            );
            case "government" -> new Cost(
                GOVERNMENT_MONEY,
                materials(
                    "minecraft:iron_ingot", 16,
                    "minecraft:gold_ingot", 16,
                    "minecraft:paper", 16
                )
            );
            case "religion" -> new Cost(
                RELIGION_MONEY,
                materials(
                    "minecraft:gold_ingot", 8,
                    "minecraft:paper", 32,
                    "minecraft:wheat", 16
                )
            );
            default -> new Cost(0, Map.of());
        };
    }

    public static Set<String> allMaterialIds() {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        result.addAll(cost("direction", false).materials().keySet());
        result.addAll(cost("government", false).materials().keySet());
        result.addAll(cost("religion", false).materials().keySet());
        return Set.copyOf(result);
    }

    private static Map<String, Integer> materials(Object... values) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            result.put((String) values[i], (Integer) values[i + 1]);
        }
        return Map.copyOf(result);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
