package ru.zela.politicseconomy.country;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import ru.zela.politicseconomy.infrastructure.InfrastructureManager;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Calculates real job capacity from explicitly placed workplace blocks inside
 * the country's current territory.
 */
public final class CountryWorkplaceService {
    private static MinecraftServer cachedServer;
    private static long cachedSecond = Long.MIN_VALUE;
    private static final Map<String, Snapshot> CACHE = new HashMap<>();

    private CountryWorkplaceService() {}

    public static Snapshot snapshot(MinecraftServer server, String countryName) {
        if (server == null || countryName == null || countryName.isBlank()) {
            return Snapshot.empty();
        }
        if (ru.zela.politicseconomy.integration.MillenaireIntegration.isStateKey(countryName)) {
            return ru.zela.politicseconomy.integration.MillenaireIntegration.workplaceSnapshot(server, countryName);
        }

        long second = server.overworld().getGameTime() / 20L;
        if (server != cachedServer) {
            cachedServer = server;
            cachedSecond = Long.MIN_VALUE;
            CACHE.clear();
        }

        Snapshot cached = CACHE.get(countryName);
        if (cached != null && cachedSecond == second) {
            return cached;
        }

        Snapshot result = calculate(server, countryName);
        cachedSecond = second;
        CACHE.put(countryName, result);
        return result;
    }

    public static void invalidate(MinecraftServer server) {
        if (server == cachedServer) {
            CACHE.clear();
            cachedSecond = Long.MIN_VALUE;
        }
    }

    private static Snapshot calculate(MinecraftServer server, String countryName) {
        ServerLevel level = server.overworld();
        PoliticsManager politics = PoliticsManager.get(level);
        if (politics == null) {
            return Snapshot.empty();
        }

        Country country = politics.getCountry(countryName);
        if (country == null) {
            return Snapshot.empty();
        }

        EnumMap<WorkforceSector, Integer> counts = emptyMap();
        EnumMap<WorkforceSector, Integer> slots = emptyMap();

        for (Map.Entry<Long, Map<Long, String>> chunkEntry
            : InfrastructureManager.get(server).getDimension(level).entrySet()) {

            ChunkPos chunkPos = new ChunkPos(chunkEntry.getKey());
            Country owner = politics.getCountryAt(chunkPos);
            if (owner == null || !owner.getName().equals(countryName)) {
                continue;
            }

            for (String blockId : chunkEntry.getValue().values()) {
                WorkplaceClassifier.WorkplaceDefinition definition =
                    WorkplaceClassifier.classify(blockId);
                if (definition == null) {
                    continue;
                }

                counts.merge(definition.sector(), 1, Integer::sum);
                slots.merge(definition.sector(), definition.capacity(), Integer::sum);
            }
        }

        return new Snapshot(counts, slots);
    }

    private static EnumMap<WorkforceSector, Integer> emptyMap() {
        EnumMap<WorkforceSector, Integer> map = new EnumMap<>(WorkforceSector.class);
        for (WorkforceSector sector : WorkforceSector.values()) {
            map.put(sector, 0);
        }
        return map;
    }

    public record Snapshot(
        EnumMap<WorkforceSector, Integer> workplaceCounts,
        EnumMap<WorkforceSector, Integer> workplaceSlots
    ) {
        public Snapshot {
            workplaceCounts = copy(workplaceCounts);
            workplaceSlots = copy(workplaceSlots);
        }

        public int totalSlots() {
            int total = 0;
            for (int value : workplaceSlots.values()) {
                total += value;
            }
            return total;
        }

        public static Snapshot empty() {
            return new Snapshot(emptyMap(), emptyMap());
        }

        private static EnumMap<WorkforceSector, Integer> copy(
            Map<WorkforceSector, Integer> source
        ) {
            EnumMap<WorkforceSector, Integer> result = new EnumMap<>(WorkforceSector.class);
            for (WorkforceSector sector : WorkforceSector.values()) {
                result.put(sector, Math.max(0, source.getOrDefault(sector, 0)));
            }
            return result;
        }
    }
}
