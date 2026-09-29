package ru.zela.politicseconomy.integration;

import net.krona.politicsmod.config.PoliticsConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.zela.politicseconomy.country.CountryDirectionBonusService;
import ru.zela.politicseconomy.country.CountryDevelopmentService;
import ru.zela.politicseconomy.country.WorkforceSector;
import ru.zela.politicseconomy.economy.NationalMaterialConsumptionService;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Economic production layer for autonomous Millénaire states.
 *
 * Output is intentionally modest and uses real Millénaire warehouse storage.
 * A state with unresolved material debt stops production for the cycle.
 */
public final class MillenaireProductionService {
    private static final int MIN_CYCLE_TICKS = 20;

    private MillenaireProductionService() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (level == null || !MillenaireIntegration.isAvailable()) {
            return;
        }

        int cycleTicks = Math.max(
            MIN_CYCLE_TICKS,
            PoliticsConfig.get().economyCycleTicks
        );
        long cycle = level.getGameTime() / cycleTicks;

        MillenaireProductionSavedData data =
            MillenaireProductionSavedData.get(server);
        if (cycle <= data.lastProcessedCycle()) {
            return;
        }
        data.setLastProcessedCycle(cycle);

        var ledger = NationalMaterialConsumptionService.getLedger(server);

        for (MillenaireIntegration.VillageSnapshot state
            : MillenaireIntegration.snapshots(server)) {
            String key = state.stateKey();
            EnumMap<WorkforceSector, Integer> workers =
                MillenaireIntegration.sectorWorkers(server, key);

            if (workers.values().stream().mapToInt(Integer::intValue).sum() <= 0) {
                continue;
            }
            if (ledger.hasAnyDebt(key)) {
                continue;
            }

            int produced = 0;

            produced += produceAgriculture(server, key, workers,
                productionMultiplier(server, key, WorkforceSector.AGRICULTURE));
            produced += produceExtraction(server, key, workers,
                productionMultiplier(server, key, WorkforceSector.EXTRACTION));
            produced += produceIndustry(server, key, workers,
                productionMultiplier(server, key, WorkforceSector.INDUSTRY));
            produced += produceConstruction(server, key, workers,
                productionMultiplier(server, key, WorkforceSector.CONSTRUCTION_SERVICES));

            if (produced > 0) {
                CountryDevelopmentService.addActivity(
                    server,
                    key,
                    Math.min(12, 2 + produced / 16)
                );
            }
        }

        data.setDirty();
    }

    private static int produceAgriculture(
        MinecraftServer server,
        String key,
        Map<WorkforceSector, Integer> workers,
        double multiplier
    ) {
        int farmers = workers.getOrDefault(
            WorkforceSector.AGRICULTURE,
            0
        );
        if (farmers <= 0) return 0;

        int wheat = scaled(farmers / 4.0D, multiplier);
        int carrots = scaled(farmers / 9.0D, multiplier);
        int potatoes = scaled(farmers / 9.0D, multiplier);

        return add(server, key, "minecraft:wheat", wheat)
            + add(server, key, "minecraft:carrot", carrots)
            + add(server, key, "minecraft:potato", potatoes);
    }

    private static int produceExtraction(
        MinecraftServer server,
        String key,
        Map<WorkforceSector, Integer> workers,
        double multiplier
    ) {
        int miners = workers.getOrDefault(
            WorkforceSector.EXTRACTION,
            0
        );
        if (miners <= 0) return 0;

        int coal = scaled(miners / 6.0D, multiplier);
        int ironOre = scaled(miners / 8.0D, multiplier);
        int logs = scaled(miners / 7.0D, multiplier);

        return add(server, key, "minecraft:coal", coal)
            + add(server, key, "minecraft:raw_iron", ironOre)
            + add(server, key, "minecraft:oak_log", logs);
    }

    private static int produceIndustry(
        MinecraftServer server,
        String key,
        Map<WorkforceSector, Integer> workers,
        double multiplier
    ) {
        int craftsmen = workers.getOrDefault(
            WorkforceSector.INDUSTRY,
            0
        );
        if (craftsmen <= 0) return 0;

        int potentialBread = scaled(craftsmen / 5.0D, multiplier);
        int wheatAvailable = MillenaireIntegration.consumeFromWarehouse(
            server,
            key,
            List.of("minecraft:wheat"),
            potentialBread * 3
        );
        int bread = Math.min(
            potentialBread,
            wheatAvailable / 3
        );

        if (bread < potentialBread) {
            MillenaireIntegration.addToWarehouse(
                server,
                key,
                "minecraft:wheat",
                wheatAvailable % 3
            );
        }

        int potentialIron = scaled(craftsmen / 5.0D, multiplier);
        int removedOre = MillenaireIntegration.consumeFromWarehouse(
            server,
            key,
            List.of("minecraft:raw_iron"),
            potentialIron
        );
        int removedCoal = MillenaireIntegration.consumeFromWarehouse(
            server,
            key,
            List.of("minecraft:coal"),
            removedOre
        );
        int ironIngots = Math.min(removedOre, removedCoal);

        if (removedOre > ironIngots) {
            MillenaireIntegration.addToWarehouse(
                server,
                key,
                "minecraft:raw_iron",
                removedOre - ironIngots
            );
        }
        if (removedCoal > ironIngots) {
            MillenaireIntegration.addToWarehouse(
                server,
                key,
                "minecraft:coal",
                removedCoal - ironIngots
            );
        }

        int potentialPickaxes = scaled(craftsmen / 20.0D, multiplier);
        int removedIronForTools = MillenaireIntegration.consumeFromWarehouse(
            server,
            key,
            List.of("minecraft:iron_ingot"),
            potentialPickaxes * 3
        );
        int removedPlanks = MillenaireIntegration.consumeFromWarehouse(
            server,
            key,
            List.of("minecraft:oak_planks"),
            potentialPickaxes
        );
        int pickaxes = Math.min(
            removedIronForTools / 3,
            removedPlanks
        );

        if (removedIronForTools > pickaxes * 3) {
            MillenaireIntegration.addToWarehouse(
                server,
                key,
                "minecraft:iron_ingot",
                removedIronForTools - pickaxes * 3
            );
        }
        if (removedPlanks > pickaxes) {
            MillenaireIntegration.addToWarehouse(
                server,
                key,
                "minecraft:oak_planks",
                removedPlanks - pickaxes
            );
        }

        int planks = scaled(
            MillenaireIntegration.warehouse(server, key)
                .getOrDefault("minecraft:oak_log", 0) / 2.0D,
            multiplier
        );
        int logs = MillenaireIntegration.consumeFromWarehouse(
            server,
            key,
            List.of("minecraft:oak_log"),
            planks
        );

        int produced = add(server, key, "minecraft:bread", bread);
        produced += add(
            server,
            key,
            "minecraft:iron_ingot",
            ironIngots
        );
        produced += add(
            server,
            key,
            "minecraft:iron_pickaxe",
            pickaxes
        );
        produced += add(
            server,
            key,
            "minecraft:oak_planks",
            logs * 2
        );

        return produced;
    }

    private static int produceConstruction(
        MinecraftServer server,
        String key,
        Map<WorkforceSector, Integer> workers,
        double multiplier
    ) {
        int workersCount = workers.getOrDefault(
            WorkforceSector.CONSTRUCTION_SERVICES,
            0
        );
        if (workersCount <= 0) return 0;

        int cobble = scaled(workersCount / 8.0D, multiplier);
        return add(
            server,
            key,
            "minecraft:cobblestone",
            cobble
        );
    }

    private static int add(
        MinecraftServer server,
        String key,
        String itemId,
        int amount
    ) {
        if (amount <= 0) return 0;
        return MillenaireIntegration.addToWarehouse(
            server,
            key,
            itemId,
            amount
        );
    }

    private static int scaled(double raw, double multiplier) {
        if (raw <= 0.0D) return 0;
        return Math.max(0, (int) Math.floor(raw * multiplier));
    }

    private static double productionMultiplier(
        MinecraftServer server,
        String key,
        WorkforceSector sector
    ) {
        var profile = CountryDirectionBonusService.profile(server, key);
        if (profile == null) return 1.0D;

        double bonus = switch (sector) {
            case AGRICULTURE -> profile.agriculturalProduction();
            case EXTRACTION -> profile.resourceProduction();
            case INDUSTRY -> profile.industrialProduction();
            case MILITARY -> profile.militaryProduction();
            case TRADE_LOGISTICS -> profile.tradeIncome() / 2.0D;
            case CONSTRUCTION_SERVICES -> profile.resourceProduction() / 2.0D;
        };

        return Math.max(0.20D, 1.0D + bonus / 100.0D);
    }
}
