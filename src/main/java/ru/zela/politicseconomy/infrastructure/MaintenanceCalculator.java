package ru.zela.politicseconomy.infrastructure;

import net.minecraft.server.MinecraftServer;
import ru.zela.politicseconomy.country.CountryDirectionBonusService;
import ru.zela.politicseconomy.country.CountryDirectionProfile;
import ru.zela.politicseconomy.country.CountryPolicyBonusService;
import ru.zela.politicseconomy.country.CountryPolicyProfile;

public final class MaintenanceCalculator {
    private MaintenanceCalculator() {}

    public static double calculate(MinecraftServer server, String countryName, InfrastructureBlockInfo info, int count) {
        return Math.max(0.0, info.baseMaintenance() * count * directionMultiplier(server, countryName, info.category()));
    }

    public static double categoryCostPerBlock(InfrastructureCategory category) {
        return switch (category) {
            case DECORATIVE -> 0.02;
            case RESIDENTIAL -> 0.10;
            case RESOURCE -> 0.20;
            case INDUSTRIAL -> 0.80;
            case MILITARY -> 1.50;
            case TRANSPORT -> 0.70;
            case ADVANCED -> 1.20;
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
        return Math.max(0.0, 1.0 + modifier / 100.0);
    }
}
