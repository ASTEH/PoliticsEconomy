package ru.zela.politicseconomy.country;

/**
 * Percentage modifiers supplied by the country's government and religion.
 * These are gameplay modifiers for Politics Economy, not real-world claims.
 */
public record CountryPolicyProfile(
    double industrialProduction,
    double resourceProduction,
    double agriculturalProduction,
    double militaryProduction,
    double tradeIncome,
    double tradeFee,
    double industrialMaintenance,
    double resourceMaintenance,
    double transportMaintenance,
    double populationEfficiency
) {
    public static final CountryPolicyProfile ZERO = new CountryPolicyProfile(
        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
    );

    public CountryPolicyProfile add(CountryPolicyProfile other) {
        if (other == null) return this;
        return new CountryPolicyProfile(
            industrialProduction + other.industrialProduction,
            resourceProduction + other.resourceProduction,
            agriculturalProduction + other.agriculturalProduction,
            militaryProduction + other.militaryProduction,
            tradeIncome + other.tradeIncome,
            tradeFee + other.tradeFee,
            industrialMaintenance + other.industrialMaintenance,
            resourceMaintenance + other.resourceMaintenance,
            transportMaintenance + other.transportMaintenance,
            populationEfficiency + other.populationEfficiency
        );
    }
}
