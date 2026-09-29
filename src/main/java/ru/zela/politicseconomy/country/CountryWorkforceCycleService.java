package ru.zela.politicseconomy.country;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.config.PoliticsConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.zela.politicseconomy.economy.NationalMaterialConsumptionService;
import ru.zela.politicseconomy.economy.NationalMaterialLedgerSavedData;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CountryWorkforceCycleService {
    private static final int MIN_CYCLE_TICKS = 20;
    private static final int DIVIDEND_CAP_BASE = 4;
    private static final int DIVIDEND_CAP_MAX = 16;

    private CountryWorkforceCycleService() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (level == null) return;

        int ticks = Math.max(MIN_CYCLE_TICKS, PoliticsConfig.get().economyCycleTicks);
        long cycle = level.getGameTime() / ticks;
        processCycle(server, cycle);
    }

    private static void processCycle(MinecraftServer server, long cycle) {
        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null) return;

        CountryWorkforceSavedData workforceData = CountryWorkforceService.get(server);
        if (cycle <= workforceData.lastProcessedCycle()) {
            return;
        }
        workforceData.setLastProcessedCycle(cycle);

        NationalMaterialLedgerSavedData ledger =
            NationalMaterialConsumptionService.getLedger(server);

        for (Country country : politics.getCountries().values()) {
            String name = country.getName();
            CountryPoliticalService.processCycle(server, name, cycle);
            grantDividend(server, name, ledger);
        }

        // Millénaire villages are autonomous economic states. They use their
        // actual residents/buildings, but share the same political/workforce
        // calculation pipeline as player countries.
        for (ru.zela.politicseconomy.integration.MillenaireIntegration.VillageSnapshot state
            : ru.zela.politicseconomy.integration.MillenaireIntegration.snapshots(server)) {
            String name = state.stateKey();
            CountryPoliticalService.processCycle(server, name, cycle);
            grantDividend(server, name, ledger);
            grantMillenaireRevenue(server, state);
        }
    }

    private static void grantDividend(
        MinecraftServer server,
        String country,
        NationalMaterialLedgerSavedData ledger
    ) {
        CountryWorkforceSavedData workforceData = CountryWorkforceService.get(server);
        double politicalFactor = CountryPoliticalService.productiveWorkforceFactor(server, country);

        EnumMap<WorkforceSector, Integer> workers =
            new EnumMap<>(WorkforceSector.class);
        for (WorkforceSector sector : WorkforceSector.values()) {
            int employed = CountryWorkforceService.sectorWorkers(server, country, sector);
            workers.put(sector, (int) Math.floor(employed * politicalFactor));
        }

        int employedTotal = workers.values().stream().mapToInt(Integer::intValue).sum();
        if (employedTotal <= 0) return;

        Map<String, Double> desired = new LinkedHashMap<>();

        addDesired(desired, "minecraft:wheat",
            workers.get(WorkforceSector.AGRICULTURE), 180,
            factor(server, country, WorkforceSector.AGRICULTURE));
        addDesired(desired, "minecraft:coal",
            workers.get(WorkforceSector.EXTRACTION), 300,
            factor(server, country, WorkforceSector.EXTRACTION));
        addDesired(desired, "minecraft:iron_ingot",
            workers.get(WorkforceSector.EXTRACTION), 600,
            factor(server, country, WorkforceSector.EXTRACTION));
        addDesired(desired, "minecraft:iron_ingot",
            workers.get(WorkforceSector.INDUSTRY), 750,
            factor(server, country, WorkforceSector.INDUSTRY));
        addDesired(desired, "minecraft:gunpowder",
            workers.get(WorkforceSector.MILITARY), 900,
            factor(server, country, WorkforceSector.MILITARY));
        addDesired(desired, "minecraft:paper",
            workers.get(WorkforceSector.TRADE_LOGISTICS), 500,
            factor(server, country, WorkforceSector.TRADE_LOGISTICS));
        addDesired(desired, "minecraft:stone",
            workers.get(WorkforceSector.CONSTRUCTION_SERVICES), 500,
            factor(server, country, WorkforceSector.CONSTRUCTION_SERVICES));

        double totalDesired = desired.values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();
        if (totalDesired <= 0.0D) return;

        int cap = Math.min(
            DIVIDEND_CAP_MAX,
            DIVIDEND_CAP_BASE + employedTotal / 1000
        );
        double scale = totalDesired > cap ? cap / totalDesired : 1.0D;

        for (Map.Entry<String, Double> entry : desired.entrySet()) {
            double raw = workforceData.getDividendRemainder(country, entry.getKey())
                + entry.getValue() * scale;
            int amount = (int) Math.floor(raw);

            workforceData.setDividendRemainder(
                country,
                entry.getKey(),
                raw - amount
            );

            if (amount > 0) {
                if (ru.zela.politicseconomy.integration.MillenaireIntegration.isStateKey(country)) {
                    ru.zela.politicseconomy.integration.MillenaireIntegration.addToWarehouse(
                        server, country, entry.getKey(), amount
                    );
                } else {
                    ledger.addStockpile(country, entry.getKey(), amount);
                }
            }
        }

        ledger.setDirty();
    }

    private static void addDesired(
        Map<String, Double> target,
        String item,
        int workers,
        int workersPerItem,
        double factor
    ) {
        if (workers <= 0) return;

        double amount = workers / (double) workersPerItem * factor;
        if (amount > 0.0D) {
            target.merge(item, amount, Double::sum);
        }
    }

    private static void grantMillenaireRevenue(
        MinecraftServer server,
        ru.zela.politicseconomy.integration.MillenaireIntegration.VillageSnapshot state
    ) {
        int adults = Math.max(0, state.adults());
        int productiveWorkers =
            ru.zela.politicseconomy.integration.MillenaireIntegration
                .sectorWorkers(server, state.stateKey())
                .values()
                .stream()
                .mapToInt(Integer::intValue)
                .sum();
        if (adults <= 0) return;

        // Local tax/productive revenue is intentionally modest: the village
        // still needs trade and real production to become wealthy.
        long revenue = Math.max(
            1L,
            Math.round(adults * 0.50D + productiveWorkers * 0.50D)
        );

        ru.zela.politicseconomy.integration.MillenaireStateSavedData
            .get(server)
            .addTreasury(state.villageId(), revenue);
    }

    private static double factor(
        MinecraftServer server,
        String country,
        WorkforceSector sector
    ) {
        CountryDirection direction = CountryDirectionManager.getDirection(server, country);
        if (direction == null) return 1.0D;

        return switch (direction) {
            case INDUSTRIAL ->
                sector == WorkforceSector.INDUSTRY || sector == WorkforceSector.MILITARY
                    ? 1.25D : 1.0D;
            case RESOURCE ->
                sector == WorkforceSector.AGRICULTURE || sector == WorkforceSector.EXTRACTION
                    ? 1.25D : 1.0D;
            case TRADE ->
                sector == WorkforceSector.TRADE_LOGISTICS ? 1.25D : 1.0D;
        };
    }
}
