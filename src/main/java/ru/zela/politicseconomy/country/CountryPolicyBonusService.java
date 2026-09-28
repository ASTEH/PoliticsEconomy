package ru.zela.politicseconomy.country;

import net.minecraft.server.MinecraftServer;

import java.util.Locale;

/**
 * Combines government + religion gameplay modifiers and exposes effective
 * workforce scaling based on the population supplied by PoliticsMod.
 */
public final class CountryPolicyBonusService {
    private CountryPolicyBonusService() {}

    public static CountryPolicyProfile profile(MinecraftServer server, String countryName) {
        GovernmentType government = CountryPolicyManager.getGovernment(server, countryName);
        ReligionType religion = CountryPolicyManager.getReligion(server, countryName);

        CountryPolicyProfile result = CountryPolicyProfile.ZERO;
        if (government != null) result = result.add(government.profile());
        if (religion != null) result = result.add(religion.profile());
        return result;
    }

    /**
     * Population is taken from PoliticsMod residential buildings.
     * Up to +25% workforce efficiency comes from population itself, then
     * government/religion can modify that efficiency.
     */
    public static double workforceMultiplier(MinecraftServer server, String countryName) {
        int population = CountryPopulationService.population(server, countryName);
        double populationBonus = Math.min(25.0D, Math.max(0.0D, population / 100.0D));
        double policyBonus = profile(server, countryName).populationEfficiency();
        return Math.max(0.0D, 1.0D + (populationBonus + policyBonus) / 100.0D);
    }

    public static double workforcePercent(MinecraftServer server, String countryName) {
        return (workforceMultiplier(server, countryName) - 1.0D) * 100.0D;
    }

    public static String summary(MinecraftServer server, String countryName) {
        CountryPolicyProfile p = profile(server, countryName);
        return String.format(
            Locale.ROOT,
            "Промышленность %+.0f%% | сырьё %+.0f%% | сельхоз %+.0f%% | армия %+.0f%% | торговая комиссия %+.0f%% | содержание %+.0f%%",
            p.industrialProduction(),
            p.resourceProduction(),
            p.agriculturalProduction(),
            p.militaryProduction(),
            p.tradeFee(),
            p.industrialMaintenance()
        );
    }

    public static String formatPercent(double value) {
        if (Math.abs(value) < 0.000001D) return "0%";
        return String.format(Locale.ROOT, "%+.0f%%", value);
    }
}
