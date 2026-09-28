package ru.zela.politicseconomy.economy;

import ru.zela.politicseconomy.country.CountryDirection;

/**
 * Calculates the abstract strategic-resource cost of maintaining one country
 * for one economy cycle. These are intentionally large, readable units rather
 * than direct item counts; later systems (warehouse/trade/production) can feed
 * the same stockpile.
 */
public final class NationalUpkeepCalculator {
    private NationalUpkeepCalculator() {}

    public static int cost(CountryDirection direction, NationalResource resource) {
        int base = switch (resource) {
            case FOOD -> 200;
            case METAL -> 120;
            case WOOD -> 80;
            case FUEL -> 100;
        };

        double modifier = switch (direction) {
            case INDUSTRIAL -> switch (resource) {
                case FOOD -> 30.0;
                case METAL -> 50.0;
                case WOOD -> 0.0;
                case FUEL -> 20.0;
            };
            case RESOURCE -> switch (resource) {
                case FOOD -> -5.0;
                case METAL -> -20.0;
                case WOOD -> 35.0;
                case FUEL -> -20.0;
            };
            case TRADE -> switch (resource) {
                case FOOD -> 10.0;
                case METAL -> -10.0;
                case WOOD -> -20.0;
                case FUEL -> 10.0;
            };
        };

        return Math.max(0, (int) Math.round(base * (1.0D + modifier / 100.0D)));
    }

    public static int total(CountryDirection direction) {
        int total = 0;
        for (NationalResource resource : NationalResource.values()) {
            total += cost(direction, resource);
        }
        return total;
    }

    /** Five cycles of strategic reserve are granted the first time a country is registered. */
    public static int startingReserve(CountryDirection direction, NationalResource resource) {
        return cost(direction, resource) * 5;
    }
}
