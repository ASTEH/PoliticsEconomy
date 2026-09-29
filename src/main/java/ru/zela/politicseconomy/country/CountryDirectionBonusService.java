package ru.zela.politicseconomy.country;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import ru.zela.politicseconomy.economy.ResourceExtractionCategory;
import ru.zela.politicseconomy.research.CountryResearchService;
import net.krona.politicsmod.config.PoliticsConfig;

import java.util.function.ToDoubleFunction;

/**
 * Single access point for direction modifiers, with government/religion policy
 * modifiers layered on top.
 */
public final class CountryDirectionBonusService {
    private CountryDirectionBonusService() {}

    public static CountryDirectionProfile profile(MinecraftServer server, String countryName) {
        CountryDirection direction = CountryDirectionManager.getDirection(server, countryName);
        if (direction == null) {
            return null;
        }
        int developmentLevel = CountryDevelopmentService.level(server, countryName);
        return CountryDirectionProfile.forDirection(direction)
            .withDevelopmentLevel(direction, developmentLevel)
            .withResearch(CountryResearchService.completed(server, countryName));
    }

    /** Effective market fee percentage used by both server logic and the market UI. */
    public static int effectiveTradeFeePercent(MinecraftServer server, String countryName) {
        double base = PoliticsConfig.get().marketFeePercent;
        double directionModifier = modifier(server, countryName, CountryDirectionProfile::tradeFee);
        double policyModifier = CountryPolicyBonusService.profile(server, countryName).tradeFee();
        double feeMultiplier = 1.0D + (directionModifier + policyModifier) / 100.0D;
        double workforceEfficiency = CountryWorkforceService.marketEfficiencyBonusPercent(
            server, countryName
        );
        feeMultiplier *= Math.max(0.70D, 1.0D - workforceEfficiency / 100.0D);
        return Math.max(0, (int) Math.round(base * feeMultiplier));
    }

    public static double modifier(
        MinecraftServer server,
        String countryName,
        ToDoubleFunction<CountryDirectionProfile> selector
    ) {
        CountryDirectionProfile profile = profile(server, countryName);
        return profile == null ? 0.0 : selector.applyAsDouble(profile);
    }

    public static double populationWorkforceMultiplier(MinecraftServer server, String countryName) {
        return CountryPolicyBonusService.workforceMultiplier(server, countryName);
    }

    public static ServerLevel overworld(MinecraftServer server) {
        return server == null ? null : server.overworld();
    }
}
