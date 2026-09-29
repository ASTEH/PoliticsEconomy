package ru.zela.politicseconomy.country;

import ru.zela.politicseconomy.economy.ResourceExtractionCategory;

import java.util.Map;

/** Numeric modifiers for a country's chosen economic direction. */
public record CountryDirectionProfile(
    double industrialProduction,
    double resourceProduction,
    double agriculturalProduction,
    double militaryProduction,
    double tradeIncome,
    double tradeFee,
    double industrialMaintenance,
    double dieselFuelConsumption,
    double resourceMaintenance,
    double transportMaintenance,
    double foodConsumption,
    double populationGrowth,
    double advancedIndustryCost,
    double resourceExtractionLoss,
    Map<ResourceExtractionCategory, Double> categoryExtractionLosses
) {
    public static CountryDirectionProfile forDirection(CountryDirection direction) {
        return switch (direction) {
            case INDUSTRIAL -> new CountryDirectionProfile(
                40.0, -20.0, -25.0, 20.0, 0.0, 0.0,
                -35.0, -50.0, 15.0, 10.0, 30.0, -5.0, -25.0, 20.0,
                Map.of(
                    ResourceExtractionCategory.ORE, 30.0,
                    ResourceExtractionCategory.WOOD, 35.0,
                    ResourceExtractionCategory.AGRICULTURE, 35.0,
                    ResourceExtractionCategory.FUEL, 25.0,
                    ResourceExtractionCategory.RAW_MATERIAL, 30.0
                )
            );
            case RESOURCE -> new CountryDirectionProfile(
                -25.0, 35.0, 50.0, -10.0, 0.0, 10.0,
                20.0, 0.0, -30.0, 10.0, -5.0, 10.0, 35.0, 5.0,
                Map.of(
                    ResourceExtractionCategory.ORE, 5.0,
                    ResourceExtractionCategory.WOOD, 5.0,
                    ResourceExtractionCategory.AGRICULTURE, 0.0,
                    ResourceExtractionCategory.FUEL, 5.0,
                    ResourceExtractionCategory.RAW_MATERIAL, 5.0
                )
            );
            case TRADE -> new CountryDirectionProfile(
                -25.0, -20.0, 10.0, -10.0, 40.0, -50.0,
                15.0, 0.0, 15.0, -40.0, 10.0, 20.0, 20.0, 30.0,
                Map.of(
                    ResourceExtractionCategory.ORE, 40.0,
                    ResourceExtractionCategory.WOOD, 40.0,
                    ResourceExtractionCategory.AGRICULTURE, 30.0,
                    ResourceExtractionCategory.FUEL, 35.0,
                    ResourceExtractionCategory.RAW_MATERIAL, 40.0
                )
            );
        };
    }


    /** Applies permanent bonuses unlocked by the country's economic-development level. */
    public CountryDirectionProfile withDevelopmentLevel(CountryDirection direction, int level) {
        int clamped = Math.max(1, Math.min(5, level));
        if (clamped <= 1) {
            return this;
        }

        double industrial = industrialProduction;
        double resource = resourceProduction;
        double agriculture = agriculturalProduction;
        double military = militaryProduction;
        double trade = tradeIncome;
        double fee = tradeFee;
        double industrialUpkeep = industrialMaintenance;
        double transportUpkeep = transportMaintenance;
        double resourceUpkeep = resourceMaintenance;
        double advancedCost = advancedIndustryCost;
        double population = populationGrowth;
        double defaultLoss = resourceExtractionLoss;
        Map<ResourceExtractionCategory, Double> losses = new java.util.EnumMap<>(ResourceExtractionCategory.class);
        losses.putAll(categoryExtractionLosses);

        if (clamped >= 2) {
            switch (direction) {
                case INDUSTRIAL -> industrial += 10.0;
                case RESOURCE -> resource += 10.0;
                case TRADE -> trade += 10.0;
            }
        }
        if (clamped >= 3) {
            switch (direction) {
                case INDUSTRIAL -> industrialUpkeep -= 10.0;
                case RESOURCE -> resourceUpkeep -= 10.0;
                case TRADE -> fee -= 10.0;
            }
        }
        if (clamped >= 4) {
            switch (direction) {
                case INDUSTRIAL -> military += 10.0;
                case RESOURCE -> agriculture += 10.0;
                case TRADE -> transportUpkeep -= 10.0;
            }
        }
        if (clamped >= 5) {
            switch (direction) {
                case INDUSTRIAL -> advancedCost -= 10.0;
                case RESOURCE -> {
                    defaultLoss = Math.max(0.0, defaultLoss - 5.0);
                    for (ResourceExtractionCategory category : ResourceExtractionCategory.values()) {
                        losses.put(category, Math.max(0.0, losses.getOrDefault(category, defaultLoss) - 5.0));
                    }
                }
                case TRADE -> population += 10.0;
            }
        }

        return new CountryDirectionProfile(
            industrial, resource, agriculture, military, trade, fee,
            industrialUpkeep, dieselFuelConsumption, resourceUpkeep, transportUpkeep,
            foodConsumption, population, advancedCost, defaultLoss, Map.copyOf(losses)
        );
    }

    public CountryDirectionProfile withResearch(java.util.Set<String> completedResearch) {
        double industrial = industrialProduction, resource = resourceProduction;
        double agriculture = agriculturalProduction, military = militaryProduction;
        double trade = tradeIncome, fee = tradeFee;
        double industrialUpkeep = industrialMaintenance, diesel = dieselFuelConsumption;
        double resourceUpkeep = resourceMaintenance, transportUpkeep = transportMaintenance;
        double population = populationGrowth, advancedCost = advancedIndustryCost;
        double defaultLoss = resourceExtractionLoss;
        java.util.Map<ResourceExtractionCategory, Double> losses =
            new java.util.EnumMap<>(ResourceExtractionCategory.class);
        losses.putAll(categoryExtractionLosses);

        if (completedResearch.contains("industrial_mechanization")) industrial += 5.0;
        if (completedResearch.contains("industrial_electrification")) diesel -= 5.0;
        if (completedResearch.contains("industrial_machinery")) industrialUpkeep -= 5.0;
        if (completedResearch.contains("industrial_military")) military += 5.0;
        if (completedResearch.contains("industrial_advanced")) advancedCost -= 5.0;

        if (completedResearch.contains("resource_mining")) {
            defaultLoss = Math.max(0.0, defaultLoss - 5.0);
            for (ResourceExtractionCategory category : ResourceExtractionCategory.values()) {
                losses.put(category, Math.max(0.0, losses.getOrDefault(category, defaultLoss) - 5.0));
            }
        }
        if (completedResearch.contains("resource_refining")) resource += 5.0;
        if (completedResearch.contains("resource_agro")) agriculture += 5.0;
        if (completedResearch.contains("resource_oil")) diesel -= 5.0;
        if (completedResearch.contains("resource_reserve")) resourceUpkeep -= 5.0;

        if (completedResearch.contains("trade_market")) trade += 5.0;
        if (completedResearch.contains("trade_logistics")) fee -= 5.0;
        if (completedResearch.contains("trade_rail")) transportUpkeep -= 5.0;
        if (completedResearch.contains("trade_motor")) population += 5.0;
        if (completedResearch.contains("trade_hub")) trade += 5.0;

        return new CountryDirectionProfile(
            industrial, resource, agriculture, military, trade, fee,
            industrialUpkeep, diesel, resourceUpkeep, transportUpkeep,
            foodConsumption, population, advancedCost, defaultLoss, java.util.Map.copyOf(losses)
        );
    }

    public double extractionLoss(ResourceExtractionCategory category) {
        return categoryExtractionLosses.getOrDefault(category, resourceExtractionLoss);
    }

    /**
     * Production bonus/penalty used before extraction loss.
     * Resource economies are deliberately strong in ores, wood, fuel and raw
     * materials (+35%), while agriculture has its own stronger +50% bonus.
     */
    public double extractionProduction(ResourceExtractionCategory category) {
        return category == ResourceExtractionCategory.AGRICULTURE
            ? agriculturalProduction
            : resourceProduction;
    }

    /** Final multiplier after both production modifier and extraction loss. */
    public double extractionMultiplier(ResourceExtractionCategory category) {
        double production = Math.max(0.0D, 1.0D + extractionProduction(category) / 100.0D);
        double loss = Math.max(0.0D, 1.0D - extractionLoss(category) / 100.0D);
        return production * loss;
    }
}
