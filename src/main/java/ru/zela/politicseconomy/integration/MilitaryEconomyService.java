package ru.zela.politicseconomy.integration;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.politics.Country;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.zela.politicseconomy.country.CountryDirectionBonusService;
import ru.zela.politicseconomy.country.CountryPolicyBonusService;
import ru.zela.politicseconomy.research.CountryResearchService;
import ru.zela.politicseconomy.country.CountryWorkforceService;
import ru.zela.politicseconomy.country.WorkforceSector;
import ru.zela.politicseconomy.economy.NationalMaterialConsumptionService;
import ru.zela.politicseconomy.economy.NationalMaterialLedgerSavedData;

import java.util.List;

/**
 * Concrete military-economy layer.
 *
 * <p>Military manpower is not just a percentage in the dashboard anymore:
 * staffed military workplaces build a real weapon reserve from iron and wood,
 * military material debts suppress readiness, research improves the ceiling,
 * and MTS combat damage against Millénaire villagers creates an immediate
 * readiness shock and persistent casualty count.</p>
 */
public final class MilitaryEconomyService {
    private static final int MIN_CYCLE_TICKS = 20;
    private static final double READINESS_LERP = 0.35D;

    private MilitaryEconomyService() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (level == null) {
            return;
        }

        int cycleTicks = Math.max(
            MIN_CYCLE_TICKS,
            PoliticsConfig.get().economyCycleTicks
        );
        long cycle = level.getGameTime() / cycleTicks;

        MilitaryReadinessSavedData data =
            MilitaryReadinessSavedData.get(server);
        if (cycle <= data.lastProcessedCycle()) {
            return;
        }

        data.setLastProcessedCycle(cycle);

        PoliticsManager politics = PoliticsManager.get(level);
        if (politics != null) {
            for (Country country : politics.getCountries().values()) {
                updateState(server, country.getName(), false);
            }
        }

        if (MillenaireIntegration.isAvailable()) {
            for (MillenaireIntegration.VillageSnapshot state :
                MillenaireIntegration.snapshots(server)) {
                updateState(server, state.stateKey(), true);
            }
        }

        data.setDirty();
    }

    public static double readiness(
        MinecraftServer server,
        String stateKey
    ) {
        if (server == null || stateKey == null || stateKey.isBlank()) {
            return 0.0D;
        }
        return MilitaryReadinessSavedData.get(server)
            .readiness(stateKey);
    }

    public static long casualties(
        MinecraftServer server,
        String stateKey
    ) {
        if (server == null || stateKey == null || stateKey.isBlank()) {
            return 0L;
        }
        return MilitaryReadinessSavedData.get(server)
            .casualties(stateKey);
    }

    public static double supplyPercent(
        MinecraftServer server,
        String stateKey
    ) {
        if (server == null || stateKey == null || stateKey.isBlank()) {
            return 0.0D;
        }

        boolean millenaire =
            MillenaireIntegration.isStateKey(stateKey);
        int workers = CountryWorkforceService.sectorWorkers(
            server,
            stateKey,
            WorkforceSector.MILITARY
        );
        if (workers <= 0) {
            return 0.0D;
        }

        double supplyUnits =
            itemCount(
                server,
                stateKey,
                "minecraft:gunpowder",
                millenaire
            )
            + itemCount(
                server,
                stateKey,
                "minecraft:iron_sword",
                millenaire
            ) * 2.0D
            + itemCount(
                server,
                stateKey,
                "minecraft:shield",
                millenaire
            ) * 3.0D;

        double required = Math.max(
            8.0D,
            workers * 3.0D
        );
        return Math.max(
            0.0D,
            Math.min(100.0D, supplyUnits * 100.0D / required)
        );
    }

    /**
     * Called by the optional MTS integration after a real hit against a
     * Millénaire villager has been observed.
     */
    public static void recordMillCombatHit(
        LivingEntity victim,
        double damage,
        boolean casualty
    ) {
        if (victim == null
            || damage <= 0.0D
            || victim.level().isClientSide) {
            return;
        }

        MinecraftServer server = victim.level().getServer();
        if (server == null || !MillenaireIntegration.isAvailable()) {
            return;
        }

        MillenaireIntegration.VillageSnapshot state =
            MillenaireIntegration.snapshotAtChunk(
                server,
                new ChunkPos(victim.blockPosition())
            );
        if (state == null) {
            return;
        }

        MilitaryReadinessSavedData data =
            MilitaryReadinessSavedData.get(server);
        data.ensureState(state.stateKey());

        /*
         * Small-arms hits create pressure; deaths create a much larger
         * immediate shock. The persistent casualty counter is intentionally
         * separate from live Millénaire population, which remains authoritative.
         */
        double penalty = Math.min(
            8.0D,
            Math.max(0.25D, damage * 0.10D)
        );
        if (casualty) {
            penalty += 7.5D;
            data.addCasualties(state.stateKey(), 1L);
        }

        data.reduceReadiness(state.stateKey(), penalty);
    }

    private static void updateState(
        MinecraftServer server,
        String stateKey,
        boolean millenaire
    ) {
        if (stateKey == null || stateKey.isBlank()) {
            return;
        }

        MilitaryReadinessSavedData data =
            MilitaryReadinessSavedData.get(server);
        data.ensureState(stateKey);

        int workers = CountryWorkforceService.sectorWorkers(
            server,
            stateKey,
            WorkforceSector.MILITARY
        );
        int capacity = ru.zela.politicseconomy.country.CountryWorkplaceService.snapshot(
            server,
            stateKey
        ).workplaceSlots().getOrDefault(
            WorkforceSector.MILITARY,
            0
        );

        boolean hasDebt =
            NationalMaterialConsumptionService.getLedger(server)
                .hasAnyDebt(stateKey);

        double workerRatio = capacity <= 0
            ? 0.0D
            : Math.max(
                0.0D,
                Math.min(
                    1.0D,
                    workers / (double) capacity
                )
            );

        double supply =
            supplyPercent(server, stateKey) / 100.0D;

        var directionProfile =
            CountryDirectionBonusService.profile(
                server,
                stateKey
            );
        var policyProfile =
            CountryPolicyBonusService.profile(
                server,
                stateKey
            );

        double militaryModifier =
            (directionProfile == null
                ? 0.0D
                : directionProfile.militaryProduction())
            + policyProfile.militaryProduction();

        double technologyBonus = 0.0D;
        var completed =
            CountryResearchService.completed(server, stateKey);
        if (completed.contains("industrial_military")) {
            technologyBonus += 10.0D;
        }
        if (completed.contains("industrial_radar")) {
            technologyBonus += 5.0D;
        }

        double target =
            workerRatio * 45.0D
            + supply * 35.0D
            + Math.max(
                -10.0D,
                Math.min(10.0D, militaryModifier / 2.0D)
            )
            + technologyBonus;

        if (workers <= 0 || capacity <= 0) {
            target = 0.0D;
        }
        if (hasDebt) {
            target -= 30.0D;
        }

        target = Math.max(
            0.0D,
            Math.min(100.0D, target)
        );

        if (!hasDebt && workers > 0) {
            produceWeapons(
                server,
                stateKey,
                millenaire,
                workers,
                militaryModifier
            );

            supply =
                supplyPercent(server, stateKey) / 100.0D;
            target =
                workerRatio * 45.0D
                + supply * 35.0D
                + Math.max(
                    -10.0D,
                    Math.min(10.0D, militaryModifier / 2.0D)
                )
                + technologyBonus;
            target = Math.max(
                0.0D,
                Math.min(100.0D, target)
            );
        }

        data.moveTowards(
            stateKey,
            target,
            READINESS_LERP
        );
    }

    private static void produceWeapons(
        MinecraftServer server,
        String stateKey,
        boolean millenaire,
        int workers,
        double militaryModifier
    ) {
        double multiplier = Math.max(
            0.20D,
            1.0D + militaryModifier / 100.0D
        );

        int swordTarget = Math.max(
            1,
            (int) Math.ceil(workers / 10.0D)
        );
        int shieldTarget = Math.max(
            1,
            (int) Math.ceil(workers / 14.0D)
        );

        int currentSwords =
            itemCount(
                server,
                stateKey,
                "minecraft:iron_sword",
                millenaire
            );
        int currentShields =
            itemCount(
                server,
                stateKey,
                "minecraft:shield",
                millenaire
            );

        int swordDeficit =
            Math.max(0, swordTarget - currentSwords);
        int shieldDeficit =
            Math.max(0, shieldTarget - currentShields);

        int cycleWeaponCap = Math.max(
            1,
            (int) Math.ceil(
                workers / 8.0D * multiplier
            )
        );

        int swords = Math.min(
            swordDeficit,
            cycleWeaponCap
        );
        if (swords > 0) {
            int actual = craft(
                server,
                stateKey,
                millenaire,
                "minecraft:iron_sword",
                swords,
                2,
                1
            );
            cycleWeaponCap -= actual;
        }

        if (cycleWeaponCap > 0 && shieldDeficit > 0) {
            int shields = Math.min(
                shieldDeficit,
                cycleWeaponCap
            );
            craft(
                server,
                stateKey,
                millenaire,
                "minecraft:shield",
                shields,
                1,
                6
            );
        }
    }

    /**
     * Simplified vanilla military production:
     * 2 iron + 1 plank = 1 sword (plank becomes a stick),
     * 1 iron + 6 planks = 1 shield.
     */
    private static int craft(
        MinecraftServer server,
        String stateKey,
        boolean millenaire,
        String outputItem,
        int requested,
        int ironPerUnit,
        int plankPerUnit
    ) {
        if (requested <= 0) {
            return 0;
        }

        int ironNeeded = requested * ironPerUnit;
        int planksNeeded = requested * plankPerUnit;

        int iron = consumeItem(
            server,
            stateKey,
            "minecraft:iron_ingot",
            ironNeeded,
            millenaire
        );
        int unitsByIron = iron / ironPerUnit;

        if (unitsByIron < requested) {
            if (iron > 0) {
                addItem(
                    server,
                    stateKey,
                    "minecraft:iron_ingot",
                    iron,
                    millenaire
                );
            }
            return 0;
        }

        int planks = consumeItem(
            server,
            stateKey,
            "minecraft:oak_planks",
            planksNeeded,
            millenaire
        );
        int unitsByWood =
            planks / plankPerUnit;
        int actual = Math.min(
            unitsByIron,
            unitsByWood
        );

        if (actual <= 0) {
            if (iron > 0) {
                addItem(
                    server,
                    stateKey,
                    "minecraft:iron_ingot",
                    iron,
                    millenaire
                );
            }
            if (planks > 0) {
                addItem(
                    server,
                    stateKey,
                    "minecraft:oak_planks",
                    planks,
                    millenaire
                );
            }
            return 0;
        }

        int unusedIron =
            iron - actual * ironPerUnit;
        int unusedPlanks =
            planks - actual * plankPerUnit;

        if (unusedIron > 0) {
            addItem(
                server,
                stateKey,
                "minecraft:iron_ingot",
                unusedIron,
                millenaire
            );
        }
        if (unusedPlanks > 0) {
            addItem(
                server,
                stateKey,
                "minecraft:oak_planks",
                unusedPlanks,
                millenaire
            );
        }

        int added = addItem(
            server,
            stateKey,
            outputItem,
            actual,
            millenaire
        );

        if (added < actual) {
            addItem(
                server,
                stateKey,
                "minecraft:iron_ingot",
                (actual - added) * ironPerUnit,
                millenaire
            );
            addItem(
                server,
                stateKey,
                "minecraft:oak_planks",
                (actual - added) * plankPerUnit,
                millenaire
            );
        }

        return added;
    }

    private static int itemCount(
        MinecraftServer server,
        String stateKey,
        String itemId,
        boolean millenaire
    ) {
        if (millenaire) {
            return MillenaireIntegration.warehouse(
                server,
                stateKey
            ).getOrDefault(itemId, 0);
        }

        return NationalMaterialConsumptionService.getLedger(server)
            .getStockpile(stateKey, itemId);
    }

    private static int consumeItem(
        MinecraftServer server,
        String stateKey,
        String itemId,
        int amount,
        boolean millenaire
    ) {
        if (millenaire) {
            return MillenaireIntegration.consumeFromWarehouse(
                server,
                stateKey,
                List.of(itemId),
                amount
            );
        }

        NationalMaterialLedgerSavedData ledger =
            NationalMaterialConsumptionService.getLedger(server);
        return ledger.consumeAccepted(
            stateKey,
            List.of(itemId),
            amount
        );
    }

    private static int addItem(
        MinecraftServer server,
        String stateKey,
        String itemId,
        int amount,
        boolean millenaire
    ) {
        if (amount <= 0) {
            return 0;
        }

        if (millenaire) {
            return MillenaireIntegration.addToWarehouse(
                server,
                stateKey,
                itemId,
                amount
            );
        }

        NationalMaterialLedgerSavedData ledger =
            NationalMaterialConsumptionService.getLedger(server);
        ledger.addStockpile(
            stateKey,
            itemId,
            amount
        );
        return amount;
    }
}
