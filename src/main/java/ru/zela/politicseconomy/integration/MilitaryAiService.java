package ru.zela.politicseconomy.integration;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.zela.politicseconomy.country.CountryDevelopmentService;
import ru.zela.politicseconomy.country.CountryWorkforceService;
import ru.zela.politicseconomy.country.WorkforceSector;
import ru.zela.politicseconomy.economy.NationalMaterialConsumptionService;
import ru.zela.politicseconomy.economy.PopulationMarketService;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MilitaryAiService {
    private static final long SCAN_INTERVAL_TICKS = 200L;
    private static final long DECISION_COOLDOWN_TICKS = 1200L;
    private static final double MIN_READINESS = 62.0D;
    private static final int MIN_POPULATION = 20;
    private static final long MIN_TREASURY = 150L;
    private static final int[][] DIRECTIONS = {{1,0},{-1,0},{0,1},{0,-1}};
    private static final Map<String, Set<String>> DEBUG_NEIGHBORS = new HashMap<>();

    private MilitaryAiService() {}

    /** Temporary in-memory links used only for local AI testing; never persisted. */
    public static void addDebugNeighbor(String a, String b) {
        if (a == null || b == null || a.equals(b)) return;
        DEBUG_NEIGHBORS.computeIfAbsent(a, ignored -> new HashSet<>()).add(b);
        DEBUG_NEIGHBORS.computeIfAbsent(b, ignored -> new HashSet<>()).add(a);
    }

    public static boolean isDebugNeighbor(String a, String b) {
        return a != null && b != null
            && DEBUG_NEIGHBORS.getOrDefault(a, Set.of()).contains(b);
    }

    public static void clearDebugNeighbors() {
        DEBUG_NEIGHBORS.clear();
    }

    /** Starts a real persistent ground-war record for debug testing, bypassing AI prerequisites. */
    public static boolean debugForceStartWar(
        MinecraftServer server,
        MillenaireIntegration.VillageSnapshot attacker,
        MillenaireIntegration.VillageSnapshot defender
    ) {
        if (server == null || attacker == null || defender == null
            || attacker.villageId().equals(defender.villageId())) {
            return false;
        }

        MilitaryWarSavedData wars = MilitaryWarSavedData.get(server);
        if (wars.isAtWar(attacker.stateKey(), defender.stateKey())) {
            return false;
        }

        long now = server.overworld().getGameTime();
        wars.startWar(
            attacker.stateKey(),
            defender.stateKey(),
            MilitaryWarSavedData.WarType.GROUND,
            MilitaryWarSavedData.WarCause.BORDER_CONFLICT,
            new ChunkPos(defender.center()),
            now
        );
        wars.setNextDecisionTick(
            attacker.stateKey(),
            now + DECISION_COOLDOWN_TICKS
        );

        MilitaryReadinessSavedData readiness = MilitaryReadinessSavedData.get(server);
        readiness.reduceReadiness(attacker.stateKey(), 7.0D);

        notifyInvolvedPlayers(
            server,
            attacker.stateKey(),
            defender.stateKey(),
            attacker.name(),
            defender.name()
        );
        return true;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld() == null || !MillenaireIntegration.isAvailable()) return;

        long now = server.overworld().getGameTime();
        if (now % SCAN_INTERVAL_TICKS != 0L) return;

        List<MillenaireIntegration.VillageSnapshot> states =
            MillenaireIntegration.snapshots(server);
        Map<ChunkPos, MillenaireIntegration.VillageSnapshot> millAtChunk =
            indexMillTerritory(states);
        MilitaryWarSavedData wars = MilitaryWarSavedData.get(server);

        for (MillenaireIntegration.VillageSnapshot state : states) {
            evaluate(server, wars, state, millAtChunk, now);
        }
    }

    private static void evaluate(
        MinecraftServer server,
        MilitaryWarSavedData wars,
        MillenaireIntegration.VillageSnapshot attacker,
        Map<ChunkPos, MillenaireIntegration.VillageSnapshot> millAtChunk,
        long now
    ) {
        String attackerKey = attacker.stateKey();
        if (wars.nextDecisionTick(attackerKey) > now) return;
        if (attacker.population() < MIN_POPULATION) {
            defer(wars, attackerKey, now);
            return;
        }

        if (MilitaryEconomyService.readiness(server, attackerKey) < MIN_READINESS) {
            defer(wars, attackerKey, now);
            return;
        }

        int militaryWorkers = CountryWorkforceService.sectorWorkers(
            server, attackerKey, WorkforceSector.MILITARY
        );
        if (militaryWorkers <= 0) {
            defer(wars, attackerKey, now);
            return;
        }

        if (NationalMaterialConsumptionService.getLedger(server)
            .hasAnyDebt(attackerKey)) {
            defer(wars, attackerKey, now);
            return;
        }

        MillenaireStateSavedData finances = MillenaireStateSavedData.get(server);
        long treasury = finances.treasury(attacker.villageId());
        if (treasury < MIN_TREASURY) {
            defer(wars, attackerKey, now);
            return;
        }

        int pressure = economicPressure(server, attacker);
        Candidate selected = selectTarget(
            server, wars, attacker, millAtChunk, pressure
        );
        if (selected == null || selected.score() < 55.0D) {
            defer(wars, attackerKey, now);
            return;
        }

        long cost = Math.max(
            MIN_TREASURY,
            Math.min(300L, Math.round(treasury * 0.08D))
        );
        finances.addTreasury(attacker.villageId(), -cost);

        wars.startWar(
            attackerKey,
            selected.stateKey(),
            MilitaryWarSavedData.WarType.GROUND,
            selected.cause(),
            selected.targetChunk(),
            now
        );
        wars.setNextDecisionTick(
            attackerKey, now + DECISION_COOLDOWN_TICKS
        );

        MilitaryReadinessSavedData readiness = MilitaryReadinessSavedData.get(server);
        readiness.reduceReadiness(attackerKey, 7.0D);
        MilitaryDiplomacyBridge.setWar(
            server,
            attackerKey,
            selected.stateKey()
        );
        notifyInvolvedPlayers(server, attackerKey, selected.stateKey(), attacker.name(), selected.name());
    }

    private static Candidate selectTarget(
        MinecraftServer server,
        MilitaryWarSavedData wars,
        MillenaireIntegration.VillageSnapshot attacker,
        Map<ChunkPos, MillenaireIntegration.VillageSnapshot> millAtChunk,
        int pressure
    ) {
        Map<String, Candidate> unique = new HashMap<>();
        PoliticsManager politics = PoliticsManager.get(server.overworld());

        for (ChunkPos own : attacker.territory()) {
            for (int[] dir : DIRECTIONS) {
                ChunkPos neighbour = new ChunkPos(own.x + dir[0], own.z + dir[1]);

                MillenaireIntegration.VillageSnapshot mill = millAtChunk.get(neighbour);
                if (mill != null && mill.villageId().equals(attacker.villageId())) mill = null;

                if (mill != null) {
                    int relation = attacker.relations().getOrDefault(mill.villageId(), 0);
                    if (relation > -20 || wars.isAtWar(attacker.stateKey(), mill.stateKey())) continue;

                    double score = targetScore(
                        power(server, attacker.stateKey(), true, attacker),
                        power(server, mill.stateKey(), true, mill),
                        Math.max(0, -relation) * 0.45D,
                        pressure * 12.0D
                    );
                    MilitaryWarSavedData.WarCause cause =
                        pressure >= 2
                            ? MilitaryWarSavedData.WarCause.RESOURCE_SHORTAGE
                            : MilitaryWarSavedData.WarCause.BORDER_CONFLICT;

                    Candidate next = new Candidate(
                        mill.stateKey(), mill.name(), neighbour, score, cause
                    );
                    unique.merge(mill.stateKey(), next,
                        (a, b) -> a.score() >= b.score() ? a : b);
                    continue;
                }

                if (politics == null) continue;
                String countryName = politics.getCountryNameAt(neighbour);
                if (countryName == null || countryName.isBlank()
                    || wars.isAtWar(attacker.stateKey(), countryName)) continue;

                Country country = politics.getCountry(countryName);
                if (country == null) continue;

                double score = targetScore(
                    power(server, attacker.stateKey(), true, attacker),
                    power(server, countryName, false, null),
                    0.0D,
                    pressure * 14.0D
                );
                MilitaryWarSavedData.WarCause cause =
                    pressure >= 2
                        ? MilitaryWarSavedData.WarCause.RESOURCE_SHORTAGE
                        : MilitaryWarSavedData.WarCause.STRATEGIC_OPPORTUNITY;

                Candidate next = new Candidate(
                    countryName, country.getName(), neighbour, score, cause
                );
                unique.merge(countryName, next,
                    (a, b) -> a.score() >= b.score() ? a : b);
            }
        }

        // Debug links allow testing two distant villages as neighbours without
        // changing Millénaire's actual territory or political-map ownership.
        for (String debugTargetKey : DEBUG_NEIGHBORS.getOrDefault(
            attacker.stateKey(), Set.of()
        )) {
            if (wars.isAtWar(attacker.stateKey(), debugTargetKey)) continue;
            MillenaireIntegration.VillageSnapshot target =
                MillenaireIntegration.snapshotForStateKey(server, debugTargetKey);
            if (target == null || target.villageId().equals(attacker.villageId())) continue;

            int relation = attacker.relations().getOrDefault(target.villageId(), 0);
            double score = targetScore(
                power(server, attacker.stateKey(), true, attacker),
                power(server, target.stateKey(), true, target),
                Math.max(0, -relation) * 0.45D,
                pressure * 12.0D
            );
            MilitaryWarSavedData.WarCause cause =
                pressure >= 2
                    ? MilitaryWarSavedData.WarCause.RESOURCE_SHORTAGE
                    : MilitaryWarSavedData.WarCause.BORDER_CONFLICT;

            Candidate next = new Candidate(
                target.stateKey(),
                target.name(),
                new ChunkPos(target.center()),
                score,
                cause
            );
            unique.merge(target.stateKey(), next,
                (x, y) -> x.score() >= y.score() ? x : y);
        }

        return unique.values().stream()
            .max(java.util.Comparator.comparingDouble(Candidate::score))
            .orElse(null);
    }

    private static double targetScore(
        double attackerPower,
        double targetPower,
        double hostility,
        double pressureBonus
    ) {
        double weakness = weaknessScore(attackerPower, targetPower);
        return clamp(35.0D + weakness + hostility + pressureBonus);
    }

    private static int economicPressure(
        MinecraftServer server,
        MillenaireIntegration.VillageSnapshot state
    ) {
        int shortages = 0;
        for (PopulationMarketService.DemandLine line :
            PopulationMarketService.demandLines(server, state.stateKey())) {
            if (line.baseDemand() <= 0 || line.remaining() <= 0) continue;
            if (line.remaining() / (double) line.baseDemand() >= 0.50D) shortages++;
        }
        return Math.min(3, shortages >= 4 ? 3 : shortages >= 2 ? 2 : shortages);
    }

    private static double power(
        MinecraftServer server,
        String stateKey,
        boolean millenaire,
        MillenaireIntegration.VillageSnapshot mill
    ) {
        int population = millenaire
            ? Math.max(0, mill.population())
            : ru.zela.politicseconomy.country.CountryPopulationService.population(
                server, stateKey
            );
        int workers = CountryWorkforceService.sectorWorkers(
            server, stateKey, WorkforceSector.MILITARY
        );
        double readiness = MilitaryEconomyService.readiness(server, stateKey);
        int development = CountryDevelopmentService.level(server, stateKey);

        return population + workers * 8.0D + readiness * 1.6D + development * 12.0D;
    }

    private static double weaknessScore(double attackerPower, double targetPower) {
        if (attackerPower <= 0.0D) return 0.0D;
        double ratio = targetPower / attackerPower;
        if (ratio <= 0.45D) return 42.0D;
        if (ratio <= 0.60D) return 30.0D;
        if (ratio <= 0.75D) return 18.0D;
        if (ratio <= 0.90D) return 8.0D;
        return ratio <= 1.05D ? 0.0D : -18.0D;
    }

    private static Map<ChunkPos, MillenaireIntegration.VillageSnapshot> indexMillTerritory(
        List<MillenaireIntegration.VillageSnapshot> states
    ) {
        Map<ChunkPos, MillenaireIntegration.VillageSnapshot> map = new HashMap<>();
        for (MillenaireIntegration.VillageSnapshot state : states) {
            for (ChunkPos chunk : state.territory()) map.putIfAbsent(chunk, state);
        }
        return map;
    }

    private static void notifyInvolvedPlayers(
        MinecraftServer server,
        String attackerKey,
        String defenderKey,
        String attackerName,
        String defenderName
    ) {
        Component message = Component.literal(
            "§c" + attackerName + " §fобъявляет войну §c" + defenderName + "§f."
        );

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String state = CountryContext.playerStateName(player);
            if (attackerKey.equals(state) || defenderKey.equals(state)) {
                player.sendSystemMessage(message);
            }
        }
    }

    private static void defer(MilitaryWarSavedData wars, String stateKey, long now) {
        wars.setNextDecisionTick(stateKey, now + SCAN_INTERVAL_TICKS);
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(100.0D, value));
    }

    private record Candidate(
        String stateKey,
        String name,
        ChunkPos targetChunk,
        double score,
        MilitaryWarSavedData.WarCause cause
    ) {}
}
