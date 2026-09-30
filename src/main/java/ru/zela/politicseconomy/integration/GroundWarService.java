package ru.zela.politicseconomy.integration;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.zela.politicseconomy.country.CountryResearchService;
import ru.zela.politicseconomy.country.CountryWorkforceService;
import ru.zela.politicseconomy.country.WorkforceSector;
import ru.zela.politicseconomy.economy.NationalMaterialConsumptionService;

import java.util.Comparator;
import java.util.List;

/**
 * PoliticsEconomy-native war resolution.
 *
 * <p>Millénaire remains an economic/statistical data source only. No Millénaire
 * NPC is spawned, moved, damaged, or placed into a native Millénaire raid.
 * Wars are resolved from military workforce, readiness, supply, development,
 * research and population.</p>
 */
public final class GroundWarService {
    private static final long BATTLE_INTERVAL_TICKS = 100L;
    private static final double BASE_BATTLE_POWER = 18.0D;
    private static final double MAX_ROUND_LOSS = 6.0D;

    private GroundWarService() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (level == null) return;

        long now = level.getGameTime();
        if (now % BATTLE_INTERVAL_TICKS != 0L) return;

        MilitaryWarSavedData wars = MilitaryWarSavedData.get(server);
        for (MilitaryWarSavedData.War war : wars.wars()) {
            if (war.type() != MilitaryWarSavedData.WarType.GROUND) continue;
            resolveRound(server, wars, war, now);
        }
    }

    private static void resolveRound(
        MinecraftServer server,
        MilitaryWarSavedData wars,
        MilitaryWarSavedData.War war,
        long now
    ) {
        if (war.attacker().isBlank() || war.defender().isBlank()) return;

        double attackerPower = militaryPower(server, war.attacker());
        double defenderPower = militaryPower(server, war.defender());

        if (attackerPower <= 0.0D || defenderPower <= 0.0D) {
            finishIfDisarmed(server, wars, war, attackerPower, defenderPower);
            return;
        }

        double total = attackerPower + defenderPower;
        double attackerShare = attackerPower / total;
        double defenderShare = defenderPower / total;

        // The weaker side takes heavier losses; the winner still pays a
        // meaningful cost so wars cannot become free infinite damage.
        double attackerLoss = clamp(
            BASE_BATTLE_POWER * (0.35D + defenderShare),
            0.5D,
            MAX_ROUND_LOSS
        );
        double defenderLoss = clamp(
            BASE_BATTLE_POWER * (0.35D + attackerShare),
            0.5D,
            MAX_ROUND_LOSS
        );

        double attackerPressure = relativePressure(attackerPower, defenderPower);
        double defenderPressure = relativePressure(defenderPower, attackerPower);

        MilitaryReadinessSavedData readiness =
            MilitaryReadinessSavedData.get(server);

        readiness.ensureState(war.attacker());
        readiness.ensureState(war.defender());

        readiness.reduceReadiness(
            war.attacker(),
            attackerLoss * 1.15D
        );
        readiness.reduceReadiness(
            war.defender(),
            defenderLoss * 1.15D
        );

        readiness.addCasualties(
            war.attacker(),
            Math.max(1L, Math.round(attackerLoss))
        );
        readiness.addCasualties(
            war.defender(),
            Math.max(1L, Math.round(defenderLoss))
        );

        long attackerTreasuryDamage = Math.max(
            2L,
            Math.round(attackerLoss * 3.0D)
        );
        long defenderTreasuryDamage = Math.max(
            2L,
            Math.round(defenderLoss * 3.0D)
        );

        applyWarCost(server, war.attacker(), attackerTreasuryDamage);
        applyWarCost(server, war.defender(), defenderTreasuryDamage);

        // A round pushes a war toward a decisive front. The target chunk is
        // considered captured only after a sustained advantage, keeping
        // territorial changes separate from ordinary economic statistics.
        long startedForRound = war.startedTick();
        long elapsedRounds = Math.max(
            1L,
            (now - startedForRound) / BATTLE_INTERVAL_TICKS
        );

        double attackerControl =
            attackerPressure * Math.min(1.0D, elapsedRounds / 20.0D);
        double defenderControl =
            defenderPressure * Math.min(1.0D, elapsedRounds / 20.0D);

        if (attackerControl >= 1.0D && attackerPower > defenderPower * 1.20D) {
            resolveVictory(server, wars, war, true);
            return;
        }

        if (defenderControl >= 1.0D && defenderPower > attackerPower * 1.20D) {
            resolveVictory(server, wars, war, false);
            return;
        }

        wars.setDirty();
    }

    private static double militaryPower(
        MinecraftServer server,
        String stateKey
    ) {
        if (stateKey == null || stateKey.isBlank()) return 0.0D;

        boolean millenaire = MillenaireIntegration.isStateKey(stateKey);

        int population;
        int militaryWorkers;
        int development;

        if (millenaire) {
            MillenaireIntegration.VillageSnapshot state =
                MillenaireIntegration.snapshotForStateKey(server, stateKey);
            if (state == null) return 0.0D;
            population = state.population();
            militaryWorkers = CountryWorkforceService.sectorWorkers(
                server,
                stateKey,
                WorkforceSector.MILITARY
            );
            development = 0;
        } else {
            population = ru.zela.politicseconomy.country.CountryPopulationService.population(
                server,
                stateKey
            );
            militaryWorkers = CountryWorkforceService.sectorWorkers(
                server,
                stateKey,
                WorkforceSector.MILITARY
            );
            development = ru.zela.politicseconomy.country.CountryDevelopmentService.level(
                server,
                stateKey
            );
        }

        double readiness =
            MilitaryEconomyService.readiness(server, stateKey);
        double supply =
            MilitaryEconomyService.supplyPercent(server, stateKey);

        double populationPower =
            Math.sqrt(Math.max(0.0D, population)) * 8.0D;
        double workforcePower =
            Math.max(0, militaryWorkers) * 10.0D;
        double readinessPower =
            Math.max(0.0D, readiness) * 0.70D;
        double supplyPower =
            Math.max(0.0D, supply) * 0.35D;
        double developmentPower =
            Math.max(0, development) * 8.0D;

        double researchPower = researchBonus(server, stateKey);
        double debtPenalty =
            NationalMaterialConsumptionService.getLedger(server)
                .hasAnyDebt(stateKey)
                ? 0.35D
                : 1.0D;

        return Math.max(
            0.0D,
            (
                populationPower
                    + workforcePower
                    + readinessPower
                    + supplyPower
                    + developmentPower
                    + researchPower
            ) * debtPenalty
        );
    }

    private static double researchBonus(
        MinecraftServer server,
        String stateKey
    ) {
        double bonus = 0.0D;
        java.util.Set<String> completed =
            CountryResearchService.completed(server, stateKey);

        if (completed.contains("industrial_military")) {
            bonus += 18.0D;
        }
        if (completed.contains("industrial_radar")) {
            bonus += 8.0D;
        }
        return bonus;
    }

    private static double relativePressure(
        double power,
        double enemyPower
    ) {
        if (enemyPower <= 0.0D) return 1.0D;
        return clamp(
            (power / enemyPower - 0.70D) / 1.00D,
            0.0D,
            1.0D
        );
    }

    private static void applyWarCost(
        MinecraftServer server,
        String stateKey,
        long amount
    ) {
        if (amount <= 0L) return;

        if (MillenaireIntegration.isStateKey(stateKey)) {
            var snapshot =
                MillenaireIntegration.snapshotForStateKey(server, stateKey);
            if (snapshot != null) {
                MillenaireStateSavedData.get(server)
                    .addTreasury(snapshot.villageId(), -amount);
            }
        }
        // PoliticsMod countries do not use the Millénaire treasury. Their war
        // costs are represented by readiness, supply and casualties until a
        // dedicated PE military treasury is introduced.
    }

    private static void finishIfDisarmed(
        MinecraftServer server,
        MilitaryWarSavedData wars,
        MilitaryWarSavedData.War war,
        double attackerPower,
        double defenderPower
    ) {
        if (attackerPower <= 0.0D && defenderPower <= 0.0D) {
            endWithPeace(server, wars, war);
            return;
        }

        resolveVictory(
            server,
            wars,
            war,
            attackerPower > defenderPower
        );
    }

    private static void resolveVictory(
        MinecraftServer server,
        MilitaryWarSavedData wars,
        MilitaryWarSavedData.War war,
        boolean attackerWins
    ) {
        String winner = attackerWins ? war.attacker() : war.defender();
        String loser = attackerWins ? war.defender() : war.attacker();

        MilitaryReadinessSavedData readiness =
            MilitaryReadinessSavedData.get(server);
        readiness.reduceReadiness(winner, 2.0D);
        readiness.reduceReadiness(loser, 12.0D);

        endWithPeace(server, wars, war);

        // Future territorial warfare can replace this with explicit
        // chunk-by-chunk occupation. For now the winner is persisted in the
        // war resolution notification and no Millénaire territory is mutated.
        notifyWarResult(server, winner, loser);
    }

    private static void endWithPeace(
        MinecraftServer server,
        MilitaryWarSavedData wars,
        MilitaryWarSavedData.War war
    ) {
        wars.endWar(war.attacker(), war.defender());
        MilitaryDiplomacyBridge.setPeace(
            server,
            war.attacker(),
            war.defender()
        );
    }

    private static void notifyWarResult(
        MinecraftServer server,
        String winner,
        String loser
    ) {
        String winnerName =
            MillenaireIntegration.displayName(server, winner);
        String loserName =
            MillenaireIntegration.displayName(server, loser);

        server.getPlayerList().broadcastSystemMessage(
            net.minecraft.network.chat.Component.literal(
                "§6" + winnerName + " §fпобеждает в войне против §c"
                    + loserName + "§f."
            ),
            false
        );
    }

    private static double clamp(
        double value,
        double min,
        double max
    ) {
        return Math.max(min, Math.min(max, value));
    }
}
