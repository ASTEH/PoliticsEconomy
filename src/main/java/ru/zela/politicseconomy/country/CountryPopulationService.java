package ru.zela.politicseconomy.country;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.block.entity.ResidentialBuildingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import ru.zela.politicseconomy.infrastructure.InfrastructureManager;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Reads population from PoliticsMod's Residential Building block entities.
 * Politics Economy does not create or store a second population system.
 */
public final class CountryPopulationService {
    private static final Map<MinecraftServer, Cache> CACHE = new WeakHashMap<>();

    private CountryPopulationService() {}

    public static int population(MinecraftServer server, String countryName) {
        if (server == null || countryName == null || countryName.isBlank()) return 0;

        ServerLevel level = server.overworld();
        long tick = level.getGameTime();

        Cache cache = CACHE.get(server);
        if (cache == null || cache.tick != tick) {
            cache = new Cache(tick);
            CACHE.put(server, cache);
        }

        return cache.byCountry.computeIfAbsent(
            countryName,
            name -> calculate(level, name)
        );
    }

    private static int calculate(ServerLevel level, String countryName) {
        PoliticsManager politics = PoliticsManager.get(level);
        if (politics == null) return 0;

        int total = 0;
        for (Map.Entry<Long, Map<Long, String>> chunkEntry :
            InfrastructureManager.get(level.getServer()).getDimension(level).entrySet()) {

            CountryAtChunk countryAtChunk = countryAtChunk(politics, chunkEntry.getKey());
            if (countryAtChunk == null || !countryName.equals(countryAtChunk.name())) {
                continue;
            }

            for (Map.Entry<Long, String> entry : chunkEntry.getValue().entrySet()) {
                if (!"politicsmod:residential_building".equals(entry.getValue())) {
                    continue;
                }

                BlockPos pos = BlockPos.of(entry.getKey());
                if (!level.hasChunkAt(pos)) continue;

                if (level.getBlockEntity(pos) instanceof ResidentialBuildingEntity residential) {
                    total += Math.max(0, residential.getPop());
                }
            }
        }

        return total;
    }

    private static CountryAtChunk countryAtChunk(PoliticsManager politics, long chunkLong) {
        var country = politics.getCountryAt(new ChunkPos(chunkLong));
        return country == null ? null : new CountryAtChunk(country.getName());
    }

    private record CountryAtChunk(String name) {}

    private static final class Cache {
        private final long tick;
        private final Map<String, Integer> byCountry = new HashMap<>();

        private Cache(long tick) {
            this.tick = tick;
        }
    }
}
