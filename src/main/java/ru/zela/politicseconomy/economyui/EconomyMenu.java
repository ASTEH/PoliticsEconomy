package ru.zela.politicseconomy.economyui;

import net.krona.politicsmod.politics.Country;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import ru.zela.politicseconomy.country.CountryDirection;
import ru.zela.politicseconomy.country.CountryDirectionBonusService;
import ru.zela.politicseconomy.country.CountryDirectionManager;
import ru.zela.politicseconomy.country.CountryDevelopmentService;
import ru.zela.politicseconomy.country.CountryDirectionProfile;
import ru.zela.politicseconomy.country.CountryPolicyBonusService;
import ru.zela.politicseconomy.country.CountryPolicyManager;
import ru.zela.politicseconomy.country.CountryPolicyProfile;
import ru.zela.politicseconomy.country.CountryPopulationService;
import ru.zela.politicseconomy.country.CountryPoliticalService;
import ru.zela.politicseconomy.country.CountryPoliticalSavedData;
import ru.zela.politicseconomy.country.CountryWorkforceService;
import ru.zela.politicseconomy.country.WorkforceSector;
import ru.zela.politicseconomy.economy.NationalMaterialDemandService;
import ru.zela.politicseconomy.economy.NationalMaterialConsumptionService;
import ru.zela.politicseconomy.economy.NationalMaterialLedgerSavedData;
import ru.zela.politicseconomy.infrastructure.InfrastructureManager;
import ru.zela.politicseconomy.infrastructure.MaintenanceLedgerSavedData;
import ru.zela.politicseconomy.infrastructure.MaintenanceService;
import ru.zela.politicseconomy.integration.PoliticsModIntegration;
import ru.zela.politicseconomy.network.EconomyNetwork;
import ru.zela.politicseconomy.network.EconomySnapshotPayload;
import ru.zela.politicseconomy.trade.TradeSavedData;
import ru.zela.politicseconomy.research.CountryResearchService;
import ru.zela.politicseconomy.trade.TradeService;

/** Server-side source for the country-economy dashboard. */
public final class EconomyMenu {
    private EconomyMenu() {}

    public static void open(ServerPlayer player) {
        Country country = PoliticsModIntegration.playerCountry(player).orElse(null);
        if (country != null) {
            EconomyNetwork.send(player, new ru.zela.politicseconomy.network.EconomyOpenPayload());
            EconomyNetwork.send(player, buildSnapshot(player, country));
            return;
        }

        var village = ru.zela.politicseconomy.integration.MillenaireIntegration.snapshotAtChunk(
            player.getServer(), player.chunkPosition()
        );
        if (village == null) {
            player.sendSystemMessage(
                Component.literal("Ты не находишься на территории государства.")
                    .withStyle(ChatFormatting.RED)
            );
            return;
        }

        var saved = ru.zela.politicseconomy.integration.MillenaireStateSavedData
            .get(player.getServer());
        saved.ensureState(
            village.villageId(),
            village.name(),
            player.getServer().getTickCount()
        );

        EconomyNetwork.send(player, new ru.zela.politicseconomy.network.EconomyOpenPayload());
        EconomyNetwork.send(
            player,
            buildSnapshot(
                player,
                village.stateKey(),
                (int) Math.min(Integer.MAX_VALUE, saved.treasury(village.villageId())),
                true
            )
        );
    }

    /** Builds the dashboard payload for the Millénaire state at the player's current position. */
    public static EconomySnapshotPayload buildMillenaireSnapshot(ServerPlayer player) {
        var village = ru.zela.politicseconomy.integration.MillenaireIntegration.snapshotAtChunk(
            player.getServer(), player.chunkPosition()
        );
        if (village == null) {
            return null;
        }

        var saved = ru.zela.politicseconomy.integration.MillenaireStateSavedData
            .get(player.getServer());
        saved.ensureState(
            village.villageId(),
            village.name(),
            player.getServer().getTickCount()
        );

        return buildSnapshot(
            player,
            village.stateKey(),
            (int) Math.min(Integer.MAX_VALUE, saved.treasury(village.villageId())),
            true
        );
    }

    private static String networkSafeMaterialId(NationalMaterialDemandService.MaterialDemand material, int index) {
        String key = material.key();
        if (key.length() <= 120) return key;
        if (!material.acceptedItemIds().isEmpty()) {
            String representative = material.acceptedItemIds().get(0);
            if (representative.length() <= 120) return representative;
        }
        return "material_" + index;
    }

    public static EconomySnapshotPayload buildSnapshot(ServerPlayer player, Country country) {
        return buildSnapshot(player, country.getName(), Math.max(0, country.balance), false);
    }

    private static EconomySnapshotPayload buildSnapshot(
        ServerPlayer player,
        String countryName,
        int treasury,
        boolean millenaireState
    ) {
        CountryDirection selectedDirection = CountryDirectionManager.getDirection(player.getServer(), countryName);
        CountryDirection direction = selectedDirection == null ? CountryDirection.INDUSTRIAL : selectedDirection;
        NationalMaterialLedgerSavedData national = NationalMaterialConsumptionService.getLedger(player.getServer());
        national.initializeCountry(countryName);
        MaintenanceLedgerSavedData moneyLedger = MaintenanceService.getLedger(player.getServer());
        var stats = InfrastructureManager.getCountryStats(player.getServer(), countryName);
        var millenaireSnapshot = millenaireState
            ? ru.zela.politicseconomy.integration.MillenaireIntegration.snapshotForStateKey(
                player.getServer(), countryName)
            : null;
        double infrastructureCost = millenaireState && millenaireSnapshot != null
            ? ru.zela.politicseconomy.integration.MillenaireIntegration.maintenanceCost(
                player.getServer(), millenaireSnapshot)
            : stats.adjustedMaintenance();
        double moneyDebt = moneyLedger.getDebt(countryName);
        var materialDemand = NationalMaterialDemandService.dashboard(player.serverLevel(), countryName);
        String[] materialIds = new String[materialDemand.size()], materialNames = new String[materialDemand.size()];
        int[] materialStockpile = new int[materialDemand.size()], materialDebt = new int[materialDemand.size()];
        double[] materialPerCycle = new double[materialDemand.size()]; double totalMaterialPerCycle = 0.0D;
        for (int i = 0; i < materialDemand.size(); i++) {
            var material = materialDemand.get(i);
            materialIds[i] = networkSafeMaterialId(material, i);
            materialNames[i] = NationalMaterialDemandService.displayName(player.serverLevel(), material.acceptedItemIds());
            materialStockpile[i] = millenaireState
                ? material.acceptedItemIds().stream().mapToInt(
                    itemId -> ru.zela.politicseconomy.integration.MillenaireIntegration
                        .warehouse(player.getServer(), countryName)
                        .getOrDefault(itemId, 0)
                ).sum()
                : material.acceptedItemIds().stream()
                    .mapToInt(itemId -> national.getStockpile(countryName, itemId))
                    .sum();
            materialDebt[i] = national.getDebt(countryName, material.key());
            materialPerCycle[i] = material.perCycleConsumption(); totalMaterialPerCycle += material.perCycleConsumption();
        }
        CountryDirectionProfile profile = CountryDirectionBonusService.profile(player.getServer(), countryName);
        double dieselModifier = profile == null ? 0.0D : profile.dieselFuelConsumption();
        int developmentLevel = CountryDevelopmentService.level(player.getServer(), countryName);
        int developmentPoints = CountryDevelopmentService.points(player.getServer(), countryName);
        int developmentNextThreshold = CountryDevelopmentService.nextThreshold(player.getServer(), countryName);
        String developmentTitle = CountryDevelopmentService.levelTitle(direction, developmentLevel);
        String developmentNextTitle = CountryDevelopmentService.nextTitle(direction, developmentLevel);
        String developmentPerk = CountryDevelopmentService.currentPerk(direction, developmentLevel);
        String developmentNextPerk = CountryDevelopmentService.nextPerk(direction, developmentLevel);
        int researchPoints = CountryResearchService.points(player.getServer(), countryName);
        String[] researchRows = CountryResearchService.dashboard(player.getServer(), countryName);
        var government = CountryPolicyManager.getGovernment(player.getServer(), countryName);
        var religion = CountryPolicyManager.getReligion(player.getServer(), countryName);
        int population = CountryPopulationService.population(player.getServer(), countryName);
        double workforce = CountryPolicyBonusService.workforcePercent(player.getServer(), countryName);
        int workingPopulation = CountryWorkforceService.workingPopulation(player.getServer(), countryName);
        int employedPopulation = CountryWorkforceService.employedPopulation(player.getServer(), countryName);
        int unemployedPopulation = CountryWorkforceService.unemployedPopulation(player.getServer(), countryName);
        int workplaceCapacity = CountryWorkforceService.workplaceCapacity(player.getServer(), countryName);

        int[] workplaceCounts = new int[WorkforceSector.values().length];
        int[] workplaceSlots = new int[WorkforceSector.values().length];
        int[] sectorWorkers = new int[WorkforceSector.values().length];
        int[] sectorAllocation = new int[WorkforceSector.values().length];
        double[] sectorBonuses = new double[WorkforceSector.values().length];

        var workplaceSnapshot = ru.zela.politicseconomy.country.CountryWorkplaceService.snapshot(
            player.getServer(), countryName
        );
        var allocation = CountryWorkforceService.allocation(player.getServer(), countryName);
        for (int i = 0; i < WorkforceSector.values().length; i++) {
            WorkforceSector sector = WorkforceSector.values()[i];
            workplaceCounts[i] = workplaceSnapshot.workplaceCounts().getOrDefault(sector, 0);
            workplaceSlots[i] = workplaceSnapshot.workplaceSlots().getOrDefault(sector, 0);
            sectorWorkers[i] = CountryWorkforceService.sectorWorkers(
                player.getServer(), countryName, sector
            );
            sectorAllocation[i] = allocation.getOrDefault(sector, 0);
            sectorBonuses[i] = CountryWorkforceService.sectorBonusPercent(
                player.getServer(), countryName, sector
            );
        }
        String policySummary = CountryPolicyBonusService.summary(player.getServer(), countryName);
        CountryPolicyProfile policy = CountryPolicyBonusService.profile(player.getServer(), countryName);
        CountryPoliticalService.initialize(player.getServer(), countryName);
        CountryPoliticalSavedData political = CountryPoliticalService.get(player.getServer());
        int unrest = political.getUnrest(countryName);
        String demand = CountryPoliticalService.demandDisplay(player.getServer(), countryName);
        String supportSummary = CountryPoliticalService.supportSummary(player.getServer(), countryName);

        java.util.List<CountryDirectoryService.CountrySnapshot> topCountries =
            CountryDirectoryService.build(player.getServer());
        String[] cityNames = new String[topCountries.size()];
        String[] cityCountries = new String[topCountries.size()];
        String[] cityMayors = new String[topCountries.size()];
        int[] cityTreasuries = new int[topCountries.size()];
        int[] cityIncome = new int[topCountries.size()];
        int[] cityInfrastructure = new int[topCountries.size()];
        int[] cityPopulation = new int[topCountries.size()];
        int[] cityTaxBlocks = new int[topCountries.size()];
        boolean[] cityCapitals = new boolean[topCountries.size()];
        boolean[] cityMine = new boolean[topCountries.size()];
        for (int i = 0; i < topCountries.size(); i++) {
            var row = topCountries.get(i);
            cityNames[i] = row.name();
            cityCountries[i] = row.type();
            cityMayors[i] = String.format(java.util.Locale.ROOT, "%.0f", row.score());
            cityTreasuries[i] = (int) Math.min(Integer.MAX_VALUE, row.treasury());
            cityIncome[i] = (int) Math.min(Integer.MAX_VALUE, Math.round(row.score()));
            cityInfrastructure[i] = row.development();
            cityPopulation[i] = row.population();
            cityTaxBlocks[i] = row.militaryWorkers();
            cityCapitals[i] = row.name().equals(countryName);
            cityMine[i] = false;
        }
        var marketLines = ru.zela.politicseconomy.economy.PopulationMarketService.demandLines(player.getServer(), countryName);
        String[] marketItemIds = new String[marketLines.size()], marketItemNames = new String[marketLines.size()];
        int[] marketBaseDemand = new int[marketLines.size()], marketRemaining = new int[marketLines.size()], marketSold = new int[marketLines.size()], marketImported = new int[marketLines.size()], marketPrices = new int[marketLines.size()];
        for (int i = 0; i < marketLines.size(); i++) {
            var line = marketLines.get(i);
            marketItemIds[i] = line.itemId();
            marketItemNames[i] = ru.zela.politicseconomy.economy.PopulationDemandCatalog.shortName(line.itemId());
            marketBaseDemand[i] = line.baseDemand();
            marketRemaining[i] = line.remaining();
            marketSold[i] = line.sold();
            marketImported[i] = line.imported();
            marketPrices[i] = line.pricePerUnit();
        }
        long personalWallet = ru.zela.politicseconomy.economy.PopulationMarketService.wallet(player.getServer(), player.getUUID());

        TradeSavedData.Terminal tradeTerminal = TradeService.terminal(player.getServer(), countryName);
        boolean tradeTerminalSet = tradeTerminal != null;
        String tradeTerminalPosition = tradeTerminal == null
            ? "Не назначен"
            : new ChunkPos(net.minecraft.core.BlockPos.of(tradeTerminal.pos())).toString();

        java.util.List<String> tradeOwnOrders = new java.util.ArrayList<>();
        for (TradeSavedData.Order order : TradeService.countryOrders(player.getServer(), countryName)) {
            tradeOwnOrders.add(
                order.id() + "|" + order.itemId() + "|"
                    + order.remaining() + "|" + order.quantity() + "|"
                    + order.maxUnitPrice() + "|"
                    + safeTradeText(order.sellerCountry()) + "|"
                    + order.status().name() + "|" + order.reservedFunds()
            );
        }

        java.util.List<String> tradeOpenOrders = new java.util.ArrayList<>();
        for (TradeSavedData.Order order : TradeService.openOrders(player.getServer(), countryName)) {
            tradeOpenOrders.add(
                order.id() + "|" + order.itemId() + "|"
                    + safeTradeText(order.buyerCountry()) + "|"
                    + order.remaining() + "|" + order.maxUnitPrice() + "|"
                    + order.agreedUnitPrice() + "|" + order.status().name()
            );
        }

        java.util.List<String> tradeHistory = new java.util.ArrayList<>();
        for (TradeSavedData.Order order : TradeService.countryHistory(player.getServer(), countryName)) {
            tradeHistory.add(
                order.id() + "|" + order.itemId() + "|" + order.quantity() + "|"
                    + order.maxUnitPrice() + "|"
                    + safeTradeText(order.buyerCountry()) + "|"
                    + safeTradeText(order.sellerCountry()) + "|"
                    + order.status().name() + "|"
                    + safeTradeText(order.cancelReason()) + "|"
                    + order.createdAt() + "|" + order.agreedUnitPrice()
            );
        }

        java.util.List<String> tradeShipments = new java.util.ArrayList<>();
        for (TradeSavedData.Shipment shipment : TradeService.get(player.getServer()).shipments().values()) {
            if (!countryName.equals(shipment.sellerCountry())
                && !countryName.equals(shipment.buyerCountry())
                && shipment.status() != TradeSavedData.ShipmentStatus.WAITING_LOGISTICS) {
                continue;
            }
            ChunkPos originChunk = new ChunkPos(net.minecraft.core.BlockPos.of(shipment.originPos()));
            ChunkPos destinationChunk = new ChunkPos(net.minecraft.core.BlockPos.of(shipment.destinationPos()));
            tradeShipments.add(
                shipment.id() + "|" + shipment.orderId() + "|"
                    + shipment.itemId() + "|" + shipment.quantity() + "|"
                    + safeTradeText(shipment.sellerCountry()) + "|"
                    + safeTradeText(shipment.buyerCountry()) + "|"
                    + shipment.status().name() + "|"
                    + (shipment.courier() == null ? "нет" : "назначен") + "|"
                    + originChunk.x + "," + originChunk.z + "|"
                    + destinationChunk.x + "," + destinationChunk.z
            );
        }

        java.util.List<String> modifierNames = new java.util.ArrayList<>(); java.util.List<Double> modifierValues = new java.util.ArrayList<>();
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
        addModifier(modifierNames, modifierValues, "Рабочие места заняты", employedPopulation <= 0
            ? 0.0D
            : employedPopulation * 100.0D / Math.max(1, workingPopulation));
        addModifier(modifierNames, modifierValues, "Промышленность от рабочих",
            CountryWorkforceService.sectorBonusPercent(player.getServer(), countryName, WorkforceSector.INDUSTRY));
        addModifier(modifierNames, modifierValues, "Добыча от рабочих",
            CountryWorkforceService.sectorBonusPercent(player.getServer(), countryName, WorkforceSector.EXTRACTION));
        addModifier(modifierNames, modifierValues, "Сельское хозяйство от рабочих",
            CountryWorkforceService.sectorBonusPercent(player.getServer(), countryName, WorkforceSector.AGRICULTURE));
        addModifier(modifierNames, modifierValues, "Военная промышленность от рабочих",
            CountryWorkforceService.sectorBonusPercent(player.getServer(), countryName, WorkforceSector.MILITARY));
        addModifier(modifierNames, modifierValues, "Военная готовность",
            ru.zela.politicseconomy.integration.MilitaryEconomyService.readiness(
                player.getServer(), countryName
            ));
        addModifier(modifierNames, modifierValues, "Военное снабжение",
            ru.zela.politicseconomy.integration.MilitaryEconomyService.supplyPercent(
                player.getServer(), countryName
            ));
        java.util.List<String> newsRows = new java.util.ArrayList<>();
        for (var item : ru.zela.politicseconomy.event.NewsSavedData.get(player.getServer()).recent(24)) {
            newsRows.add(
                item.tick() + "|" + safeTradeText(item.category()) + "|"
                    + safeTradeText(item.title()) + "|" + safeTradeText(item.body())
            );
        }

        String displayStateName = millenaireState && millenaireSnapshot != null
            ? millenaireSnapshot.name()
            : countryName;

        return new EconomySnapshotPayload(displayStateName, selectedDirection == null ? "Не выбрано" : selectedDirection.displayName(), government == null ? "Не выбрано" : government.displayName(), religion == null ? "Не выбрано" : religion.displayName(), population, workforce, workingPopulation, employedPopulation, unemployedPopulation, workplaceCapacity, workplaceCounts, workplaceSlots, sectorWorkers, sectorAllocation, sectorBonuses, policySummary, unrest, demand, supportSummary, treasury, infrastructureCost, moneyDebt, dieselModifier, totalMaterialPerCycle, materialIds, materialNames, materialStockpile, materialDebt, materialPerCycle, modifierNames.toArray(String[]::new), modifierValues.stream().mapToDouble(Double::doubleValue).toArray(), researchPoints, researchRows, cityNames, cityCountries, cityMayors, cityTreasuries, cityIncome, cityInfrastructure, cityPopulation, cityTaxBlocks, cityCapitals, cityMine, developmentLevel, developmentPoints, developmentNextThreshold, developmentTitle, developmentNextTitle, developmentPerk, developmentNextPerk, marketItemIds, marketItemNames, marketBaseDemand, marketRemaining, marketSold, marketImported, marketPrices, personalWallet, tradeTerminalSet, tradeTerminalPosition, tradeOwnOrders.toArray(String[]::new), tradeOpenOrders.toArray(String[]::new), tradeShipments.toArray(String[]::new), tradeHistory.toArray(String[]::new), newsRows.toArray(String[]::new));
    }

    private static void addModifier(java.util.List<String> names, java.util.List<Double> values, String name, double value) { names.add(name); values.add(value); }

    private static String safeTradeText(String value) {
        return value == null ? "—" : value.replace("|", "/");
    }
}
