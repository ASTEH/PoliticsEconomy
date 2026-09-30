package ru.zela.politicseconomy.infrastructure;

import net.minecraft.server.MinecraftServer;
import ru.zela.politicseconomy.country.CountryDirectionBonusService;
import ru.zela.politicseconomy.country.CountryDirectionProfile;
import ru.zela.politicseconomy.country.CountryPolicyBonusService;
import ru.zela.politicseconomy.country.CountryPolicyProfile;
import ru.zela.politicseconomy.country.CountryWorkforceService;

public final class MaintenanceCalculator {
    private MaintenanceCalculator() {}

    public static double calculate(MinecraftServer server, String countryName, InfrastructureBlockInfo info, int count) {
        return Math.max(0.0, info.baseMaintenance() * count * directionMultiplier(server, countryName, info.category()));
    }

    public static double categoryCostPerBlock(InfrastructureCategory category) {
        return switch (category) {
            case DECORATIVE -> 0.01;
            case RESIDENTIAL -> 0.05;
            case RESOURCE -> 0.10;
            case INDUSTRIAL -> 0.35;
            case MILITARY -> 0.75;
            case TRANSPORT -> 0.30;
            case ADVANCED -> 0.60;
        };
    }

    public static double directionMultiplier(MinecraftServer server, String countryName, InfrastructureCategory category) {
        double modifier = 0.0;
        CountryDirectionProfile direction = CountryDirectionBonusService.profile(server, countryName);
        CountryPolicyProfile policy = CountryPolicyBonusService.profile(server, countryName);

        if (direction != null) {
            modifier += switch (category) {
                case INDUSTRIAL, ADVANCED, MILITARY -> direction.industrialMaintenance();
                case RESOURCE -> direction.resourceMaintenance();
                case TRANSPORT -> direction.transportMaintenance();
                case RESIDENTIAL, DECORATIVE -> 0.0;
            };
        }
        modifier += switch (category) {
            case INDUSTRIAL, ADVANCED, MILITARY -> policy.industrialMaintenance();
            case RESOURCE -> policy.resourceMaintenance();
            case TRANSPORT -> policy.transportMaintenance();
            case RESIDENTIAL, DECORATIVE -> 0.0;
        };
        double baseMultiplier = Math.max(0.0, 1.0 + modifier / 100.0);
        double constructionEfficiency = CountryWorkforceService.constructionEfficiencyBonusPercent(
            server, countryName
        );
        double workforceMultiplier = Math.max(
            0.85D,
            1.0D - constructionEfficiency / 100.0D * 0.35D
        );
        return baseMultiplier * workforceMultiplier;
    }
}
