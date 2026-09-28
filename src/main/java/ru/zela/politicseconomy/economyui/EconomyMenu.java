package ru.zela.politicseconomy.economyui;

import net.krona.politicsmod.politics.Country;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import ru.zela.politicseconomy.country.CountryDirection;
import ru.zela.politicseconomy.country.CountryDirectionBonusService;
import ru.zela.politicseconomy.country.CountryDirectionManager;
import ru.zela.politicseconomy.country.CountryDevelopmentService;
import ru.zela.politicseconomy.country.CountryDirectionProfile;
import ru.zela.politicseconomy.economy.NationalMaterialDemandService;
import ru.zela.politicseconomy.economy.NationalMaterialConsumptionService;
import ru.zela.politicseconomy.economy.NationalMaterialLedgerSavedData;
import ru.zela.politicseconomy.infrastructure.InfrastructureManager;
import ru.zela.politicseconomy.infrastructure.MaintenanceLedgerSavedData;
import ru.zela.politicseconomy.infrastructure.MaintenanceService;
import ru.zela.politicseconomy.integration.PoliticsModIntegration;
import ru.zela.politicseconomy.network.EconomyNetwork;
import ru.zela.politicseconomy.network.EconomySnapshotPayload;

/** Server-side source for the modern country-economy dashboard. */
public final class EconomyMenu {
    private EconomyMenu() {}

    public static void open(ServerPlayer player) {
        Country country = PoliticsModIntegration.playerCountry(player).orElse(null);
        if (country == null) {
            player.sendSystemMessage(Component.literal("Ты не состоишь ни в одной стране.").withStyle(ChatFormatting.RED));
            return;
        }
        EconomyNetwork.send(player, buildSnapshot(player, country));
    }

    private static String networkSafeMaterialId(NationalMaterialDemandService.MaterialDemand material, int index) {
        String key = material.key();
        if (key.length() <= 120) {
            return key;
        }
        if (!material.acceptedItemIds().isEmpty()) {
            String representative = material.acceptedItemIds().get(0);
            if (representative.length() <= 120) {
                return representative;
            }
        }
        return "material_" + index;
    }

    public static EconomySnapshotPayload buildSnapshot(ServerPlayer player, Country country) {
        String countryName = country.getName();
        CountryDirection direction = CountryDirectionManager.getDirection(player.getServer(), countryName);
        if (direction == null) {
            direction = CountryDirection.INDUSTRIAL;
        }

        NationalMaterialLedgerSavedData national = NationalMaterialConsumptionService.getLedger(player.getServer());
        national.initializeCountry(countryName);

        MaintenanceLedgerSavedData moneyLedger = MaintenanceService.getLedger(player.getServer());
        var stats = InfrastructureManager.getCountryStats(player.getServer(), countryName);
        double infrastructureCost = stats.adjustedMaintenance();
        double moneyDebt = moneyLedger.getDebt(countryName);
        var materialDemand = NationalMaterialDemandService.dashboard(player.serverLevel(), countryName);

        String[] materialIds = new String[materialDemand.size()];
        String[] materialNames = new String[materialDemand.size()];
        int[] materialStockpile = new int[materialDemand.size()];
        int[] materialDebt = new int[materialDemand.size()];
        double[] materialPerCycle = new double[materialDemand.size()];
        double totalMaterialPerCycle = 0.0D;
        for (int i = 0; i < materialDemand.size(); i++) {
            var material = materialDemand.get(i);
            materialIds[i] = networkSafeMaterialId(material, i);
            materialNames[i] = NationalMaterialDemandService.displayName(player.serverLevel(), material.acceptedItemIds());
            materialStockpile[i] = material.acceptedItemIds().stream()
                .mapToInt(itemId -> national.getStockpile(countryName, itemId))
                .sum();
            materialDebt[i] = national.getDebt(countryName, material.key());
            materialPerCycle[i] = material.perCycleConsumption();
            totalMaterialPerCycle += material.perCycleConsumption();
        }

        CountryDirectionProfile profile = CountryDirectionBonusService.profile(player.getServer(), countryName);
        double dieselModifier = profile == null ? 0.0D : profile.dieselFuelConsumption();
        int developmentLevel = CountryDevelopmentService.level(player.getServer(), countryName);
        int developmentPoints = CountryDevelopmentService.points(player.getServer(), countryName);
        int developmentNextThreshold = CountryDevelopmentService.nextThreshold(player.getServer(), countryName);
        String developmentPerk = CountryDevelopmentService.currentPerk(direction, developmentLevel);
        String developmentNextPerk = CountryDevelopmentService.nextPerk(direction, developmentLevel);

        return new EconomySnapshotPayload(
            countryName,
            direction.displayName(),
            country.balance,
            infrastructureCost,
            moneyDebt,
            dieselModifier,
            totalMaterialPerCycle,
            materialIds,
            materialNames,
            materialStockpile,
            materialDebt,
            materialPerCycle,
            developmentLevel,
            developmentPoints,
            developmentNextThreshold,
            developmentPerk,
            developmentNextPerk
        );
    }
}
