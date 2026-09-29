package ru.zela.politicseconomy.economy;

import ru.zela.politicseconomy.country.CountryDirection;
import ru.zela.politicseconomy.country.GovernmentType;
import ru.zela.politicseconomy.country.ReligionType;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Goods purchased by the population every economic cycle.
 *
 * All modifiers here are gameplay coefficients for Politics Economy.
 */
public final class PopulationDemandCatalog {
    public enum Category {
        FOOD,
        HOUSEHOLD,
        INDUSTRIAL,
        LUXURY
    }

    public record Good(
        String itemId,
        int unitsPer100Population,
        int basePrice,
        Category category
    ) {}

    private static final List<Good> GOODS = List.of(
        // Food
        new Good("minecraft:bread", 6, 2, Category.FOOD),
        new Good("minecraft:potato", 4, 1, Category.FOOD),
        new Good("minecraft:carrot", 3, 1, Category.FOOD),
        new Good("minecraft:beetroot", 2, 1, Category.FOOD),
        new Good("minecraft:wheat", 3, 1, Category.FOOD),
        new Good("minecraft:beef", 1, 8, Category.FOOD),
        new Good("minecraft:chicken", 1, 6, Category.FOOD),
        new Good("minecraft:porkchop", 1, 7, Category.FOOD),
        new Good("minecraft:cooked_cod", 1, 5, Category.FOOD),
        new Good("minecraft:cooked_salmon", 1, 6, Category.FOOD),
        new Good("minecraft:baked_potato", 2, 2, Category.FOOD),
        new Good("minecraft:cookie", 1, 4, Category.FOOD),
        new Good("minecraft:pumpkin_pie", 1, 10, Category.FOOD),

        // Household and everyday goods
        new Good("minecraft:oak_planks", 2, 2, Category.HOUSEHOLD),
        new Good("minecraft:cobblestone", 3, 1, Category.HOUSEHOLD),
        new Good("minecraft:coal", 2, 4, Category.HOUSEHOLD),
        new Good("minecraft:charcoal", 2, 3, Category.HOUSEHOLD),
        new Good("minecraft:glass", 2, 3, Category.HOUSEHOLD),
        new Good("minecraft:paper", 2, 4, Category.HOUSEHOLD),
        new Good("minecraft:leather", 1, 10, Category.HOUSEHOLD),
        new Good("minecraft:lantern", 1, 6, Category.HOUSEHOLD),
        new Good("minecraft:book", 1, 15, Category.HOUSEHOLD),

        // Industrial goods
        new Good("minecraft:iron_ingot", 2, 12, Category.INDUSTRIAL),
        new Good("minecraft:redstone", 1, 15, Category.INDUSTRIAL),
        new Good("minecraft:iron_pickaxe", 1, 35, Category.INDUSTRIAL),
        new Good("minecraft:iron_sword", 1, 30, Category.INDUSTRIAL),

        // Luxury goods
        new Good("minecraft:gold_ingot", 1, 30, Category.LUXURY),
        new Good("minecraft:emerald", 1, 40, Category.LUXURY),
        new Good("minecraft:cake", 1, 25, Category.LUXURY),
        new Good("minecraft:amethyst_shard", 1, 30, Category.LUXURY)
    );

    private PopulationDemandCatalog() {}

    public static List<Good> goods() {
        return GOODS;
    }

    public static Good find(String itemId) {
        if (itemId == null) return null;
        for (Good good : GOODS) {
            if (good.itemId().equals(itemId)) {
                return good;
            }
        }
        return null;
    }

    public static int demandFor(
        Good good,
        int population,
        CountryDirection direction,
        GovernmentType government,
        ReligionType religion
    ) {
        if (good == null || population <= 0) return 0;

        double multiplier = multiplier(good, direction, government, religion);
        double raw = population * good.unitsPer100Population() / 100.0D * multiplier;
        return raw <= 0.0D ? 0 : Math.max(1, (int) Math.ceil(raw));
    }

    public static String shortName(String itemId) {
        if (itemId == null) return "?";
        int colon = itemId.indexOf(':');
        String path = colon >= 0 ? itemId.substring(colon + 1) : itemId;
        return path.replace('_', ' ');
    }

    private static double multiplier(
        Good good,
        CountryDirection direction,
        GovernmentType government,
        ReligionType religion
    ) {
        double multiplier = 1.0D;

        switch (direction == null ? CountryDirection.INDUSTRIAL : direction) {
            case INDUSTRIAL -> multiplier *= industrialMultiplier(good);
            case RESOURCE -> multiplier *= resourceMultiplier(good);
            case TRADE -> multiplier *= tradeMultiplier(good);
        }

        switch (government) {
            case DEMOCRACY -> {
                if (isOneOf(good, "minecraft:paper", "minecraft:book", "minecraft:glass", "minecraft:cake")) {
                    multiplier *= 1.15D;
                }
            }
            case COMMUNISM -> {
                if (isOneOf(good, "minecraft:bread", "minecraft:potato", "minecraft:wheat", "minecraft:coal", "minecraft:iron_ingot")) {
                    multiplier *= 1.25D;
                }
                if (good.category() == Category.LUXURY) multiplier *= 0.65D;
            }
            case MONARCHY -> {
                if (isOneOf(good, "minecraft:beef", "minecraft:leather", "minecraft:cake", "minecraft:gold_ingot", "minecraft:book")) {
                    multiplier *= 1.20D;
                }
            }
            case FASCISM -> {
                if (isOneOf(good, "minecraft:iron_ingot", "minecraft:coal", "minecraft:leather", "minecraft:bread")) {
                    multiplier *= 1.25D;
                }
                if (good.category() == Category.LUXURY) multiplier *= 0.70D;
            }
        }

        switch (religion) {
            case SECULAR -> {}
            case CHRISTIANITY -> {
                if (isOneOf(good, "minecraft:bread", "minecraft:book", "minecraft:cake")) multiplier *= 1.20D;
            }
            case ISLAM -> {
                if (isOneOf(good, "minecraft:wheat", "minecraft:bread", "minecraft:beef", "minecraft:book")) multiplier *= 1.20D;
                if ("minecraft:porkchop".equals(good.itemId())) multiplier *= 0.10D;
            }
            case BUDDHISM -> {
                if (isOneOf(good, "minecraft:carrot", "minecraft:potato", "minecraft:cooked_cod", "minecraft:cooked_salmon", "minecraft:book")) multiplier *= 1.15D;
                if (isOneOf(good, "minecraft:beef", "minecraft:porkchop")) multiplier *= 0.60D;
            }
            case JUDAISM -> {
                if (isOneOf(good, "minecraft:bread", "minecraft:wheat", "minecraft:cooked_cod", "minecraft:cooked_salmon", "minecraft:book")) multiplier *= 1.20D;
                if ("minecraft:porkchop".equals(good.itemId())) multiplier *= 0.10D;
            }
        }

        return Math.max(0.10D, Math.min(3.0D, multiplier));
    }

    private static double industrialMultiplier(Good good) {
        return switch (good.category()) {
            case FOOD -> 0.90D;
            case HOUSEHOLD -> 1.15D;
            case INDUSTRIAL -> 1.45D;
            case LUXURY -> 0.95D;
        };
    }

    private static double resourceMultiplier(Good good) {
        return switch (good.category()) {
            case FOOD -> 1.35D;
            case HOUSEHOLD -> 1.10D;
            case INDUSTRIAL -> 0.75D;
            case LUXURY -> 0.70D;
        };
    }

    private static double tradeMultiplier(Good good) {
        return switch (good.category()) {
            case FOOD -> 1.05D;
            case HOUSEHOLD -> 1.20D;
            case INDUSTRIAL -> 1.10D;
            case LUXURY -> 1.45D;
        };
    }

    private static boolean isOneOf(Good good, String... ids) {
        Set<String> values = Set.of(ids);
        return values.contains(good.itemId());
    }
}
