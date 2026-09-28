package ru.zela.politicseconomy.infrastructure;

import net.minecraft.server.MinecraftServer;
import ru.zela.politicseconomy.country.CountryDirectionBonusService;
import ru.zela.politicseconomy.country.CountryDirectionProfile;

public final class MaintenanceCalculator {
    private MaintenanceCalculator() {}

    public static double calculate(
        MinecraftServer server,
        String countryName,
        InfrastructureBlockInfo info,
        int count
    ) {
        double multiplier = directionMultiplier(server, countryName, info.category());
        return Math.max(0.0, info.baseMaintenance() * count * multiplier);
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

    public static double directionMultiplier(
        MinecraftServer server,
        String countryName,
        InfrastructureCategory category
    ) {
        double modifier = 0.0;
        CountryDirectionProfile profile = CountryDirectionBonusService.profile(server, countryName);
        if (profile != null) {
            modifier = switch (category) {
                case INDUSTRIAL, ADVANCED -> profile.industrialMaintenance();
                case RESOURCE -> profile.resourceMaintenance();
                case TRANSPORT -> profile.transportMaintenance();
                case MILITARY -> profile.industrialMaintenance();
                case RESIDENTIAL, DECORATIVE -> 0.0;
            };
        }
        return Math.max(0.0, 1.0 + modifier / 100.0);
    }
}
