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
import ru.zela.politicseconomy.country.CountryPolicyBonusService;
import ru.zela.politicseconomy.country.CountryPolicyManager;
import ru.zela.politicseconomy.country.CountryPopulationService;
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

        var government = CountryPolicyManager.getGovernment(player.getServer(), countryName);
        var religion = CountryPolicyManager.getReligion(player.getServer(), countryName);
        int population = CountryPopulationService.population(player.getServer(), countryName);
        double workforce = CountryPolicyBonusService.workforcePercent(player.getServer(), countryName);
        String policySummary = CountryPolicyBonusService.summary(player.getServer(), countryName);
        CountryPolicyProfile policy = CountryPolicyBonusService.profile(player.getServer(), countryName);

        java.util.List<String> modifierNames = new java.util.ArrayList<>();
        java.util.List<Double> modifierValues = new java.util.ArrayList<>();
        addModifier(modifierNames, modifierValues, "Промышленное производство", profile == null ? 0 : profile.industrialProduction() + policy.industrialProduction());
        addModifier(modifierNames, modifierValues, "Добыча сырья", profile == null ? 0 : profile.resourceProduction() + policy.resourceProduction());
        addModifier(modifierNames, modifierValues, "Сельское хозяйство", profile == null ? 0 : profile.agriculturalProduction() + policy.agriculturalProduction());
        addModifier(modifierNames, modifierValues, "Военное производство", profile == null ? 0 : profile.militaryProduction() + policy.militaryProduction());
        addModifier(modifierNames, modifierValues, "Доход от торговли", profile == null ? 0 : profile.tradeIncome() + policy.tradeIncome());
        addModifier(modifierNames, modifierValues, "Комиссия торговли", profile == null ? 0 : profile.tradeFee() + policy.tradeFee());
        addModifier(modifierNames, modifierValues, "Содержание промышленности", profile == null ? 0 : profile.industrialMaintenance() + policy.industrialMaintenance());
        addModifier(modifierNames, modifierValues, "Расход дизельного топлива", profile == null ? 0 : profile.dieselFuelConsumption());
        addModifier(modifierNames, modifierValues, "Содержание ресурсной инфраструктуры", profile == null ? 0 : profile.resourceMaintenance() + policy.resourceMaintenance());
        addModifier(modifierNames, modifierValues, "Содержание транспорта", profile == null ? 0 : profile.transportMaintenance() + policy.transportMaintenance());
        addModifier(modifierNames, modifierValues, "Потребление еды", profile == null ? 0 : profile.foodConsumption());
        addModifier(modifierNames, modifierValues, "Рост населения", profile == null ? 0 : profile.populationGrowth());
        addModifier(modifierNames, modifierValues, "Стоимость сложной промышленности", profile == null ? 0 : profile.advancedIndustryCost());
        addModifier(modifierNames, modifierValues, "Рабочая сила", workforce);

        return new EconomySnapshotPayload(
            countryName,
            direction.displayName(),
            government == null ? "Не выбрано" : government.displayName(),
            religion == null ? "Не выбрано" : religion.displayName(),
            population,
            workforce,
            policySummary,
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
            modifierNames.toArray(String[]::new),
            modifierValues.stream().mapToDouble(Double::doubleValue).toArray(),
            developmentLevel,
            developmentPoints,
            developmentNextThreshold,
            developmentPerk,
            developmentNextPerk
        );
    }

    private static void addModifier(java.util.List<String> names, java.util.List<Double> values, String name, double value) {
        names.add(name);
        values.add(value);
    }
}
