package ru.zela.politicseconomy.country;

import net.minecraft.server.MinecraftServer;

import java.util.function.ToDoubleFunction;
import net.krona.politicsmod.config.PoliticsConfig;

/**
 * Single access point for direction modifiers.
 * Future economy systems should use this service rather than hard-coding
 * direction checks in multiple places.
 */
public final class CountryDirectionBonusService {
    private CountryDirectionBonusService() {}

    public static CountryDirectionProfile profile(MinecraftServer server, String countryName) {
        CountryDirection direction = CountryDirectionManager.getDirection(server, countryName);
        if (direction == null) {
            return null;
        }
        int developmentLevel = CountryDevelopmentService.level(server, countryName);
        return CountryDirectionProfile.forDirection(direction).withDevelopmentLevel(direction, developmentLevel);
    }

    /** Effective integer market fee percentage used by both server logic and the market UI. */
    public static int effectiveTradeFeePercent(MinecraftServer server, String countryName) {
        double base = PoliticsConfig.get().marketFeePercent;
        double modifier = modifier(server, countryName, CountryDirectionProfile::tradeFee);
        return Math.max(0, (int) Math.round(base * (1.0D + modifier / 100.0D)));
    }

    public static double modifier(
        MinecraftServer server,
        String countryName,
        ToDoubleFunction<CountryDirectionProfile> selector
    ) {
        CountryDirectionProfile profile = profile(server, countryName);
        return profile == null ? 0.0 : selector.applyAsDouble(profile);
    }
}
