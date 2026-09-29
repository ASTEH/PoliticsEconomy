package ru.zela.politicseconomy.country;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bed-based population model.
 *
 * Population no longer depends on Residential Building blocks.
 * A bed head represents two residents. A country's population is the sum of
 * beds in all chunks claimed by that country. A city's population is the same
 * calculation limited to chunks assigned to that city.
 *
 * Per-chunk bed counts are persisted in SavedData, so population survives
 * server/game restarts. Loaded chunks are rescanned to keep the counters current.
 */
public final class CountryPopulationService {
    private static final int RESIDENTS_PER_BED = 2;
    private static final Map<MinecraftServer, Cache> CACHE = new WeakHashMap<>();

    private CountryPopulationService() {}

    public static CountryPopulationSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                CountryPopulationSavedData::create,
                CountryPopulationSavedData::load,
                null
            ),
            CountryPopulationSavedData.DATA_NAME
        );
    }

    public static int population(MinecraftServer server, String countryName) {
        if (server == null || countryName == null || countryName.isBlank()) return 0;
        Cache cache = cache(server);
        return cache.byCountry.computeIfAbsent(
            countryName,
            name -> calculateCountry(server, name)
        );
    }

    public static int cityPopulation(
        MinecraftServer server,
        String countryName,
        String cityName
    ) {
        if (server == null
            || countryName == null || countryName.isBlank()
            || cityName == null || cityName.isBlank()) {
            return 0;
        }

        Cache cache = cache(server);
        String key = countryName + "\n" + cityName;
        return cache.byCity.computeIfAbsent(
            key,
            ignored -> calculateCity(server, countryName, cityName)
        );
    }

    /** Population represented by a specific claimed chunk. */
    public static int populationAtChunk(
        MinecraftServer server,
        ChunkPos chunk
    ) {
        if (server == null || chunk == null) return 0;
        return get(server).getBeds(chunk.toLong()) * RESIDENTS_PER_BED;
    }

    /** Rebuilds a loaded chunk's bed count after the chunk is loaded. */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        refreshChunk(level, chunk);
    }

    /**
     * Refreshes a bed's chunk and its neighboring chunks after a block change.
     * The task is delayed until after the break/place operation is complete.
     */
    public static void onBedChange(ServerLevel level, BlockPos pos) {
        level.getServer().execute(() -> refreshAround(level, pos));
    }

    /** Called after a player places a bed so invalid housing gets an immediate on-screen warning. */
    public static void onBedPlaced(ServerLevel level, BlockPos pos, ServerPlayer player) {
        level.getServer().execute(() -> {
            BlockPos head = findBedHead(level, pos);
            if (head != null && !isValidBed(level, head)) {
                player.displayClientMessage(
                    Component.literal("Кровать не засчитана: над ней нет крыши в пределах 5 блоков. Население не добавится.")
                        .withStyle(ChatFormatting.YELLOW),
                    true
                );
            } else if (head != null
                && PoliticsManager.get(level) != null
                && PoliticsManager.get(level).getCountryAt(new ChunkPos(head)) == null) {
                player.displayClientMessage(
                    Component.literal("Кровать не засчитана: этот чанк не принадлежит государству. Население не добавится.")
                        .withStyle(ChatFormatting.YELLOW),
                    true
                );
            }
            refreshAround(level, pos);
        });
    }

    /** Resolves either half of a bed to its HEAD block. */
    private static BlockPos findBedHead(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof BedBlock) {
            if (state.getValue(BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
                return pos;
            }
            Direction facing = state.getValue(BedBlock.FACING);
            BlockPos candidate = pos.relative(facing);
            var candidateState = level.getBlockState(candidate);
            if (candidateState.getBlock() instanceof BedBlock
                && candidateState.getValue(BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
                return candidate;
            }
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = pos.relative(direction);
            var candidateState = level.getBlockState(candidate);
            if (candidateState.getBlock() instanceof BedBlock
                && candidateState.getValue(BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
                return candidate;
            }
        }
        return null;
    }

    private static void refreshAround(ServerLevel level, BlockPos pos) {
        int centerX = pos.getX() >> 4;
        int centerZ = pos.getZ() >> 4;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                refreshChunk(level, level.getChunk(centerX + dx, centerZ + dz));
            }
        }
    }

    public static void refreshChunk(ServerLevel level, LevelChunk chunk) {
        int beds = countBeds(level, chunk);
        get(level.getServer()).setBeds(chunk.getPos().toLong(), beds);
        invalidate(level.getServer());
    }

    /** Counts every valid bed head; beds may be stacked vertically at the same X/Z. */
    private static int countBeds(ServerLevel level, LevelChunk chunk) {
        int validBeds = 0;

        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    var state = chunk.getBlockState(pos);
                    if (state.getBlock() instanceof BedBlock
                        && state.getValue(BedBlock.PART)
                        == net.minecraft.world.level.block.state.properties.BedPart.HEAD
                        && hasRoof(level, pos)) {
                        validBeds++;
                    }
                }
            }
        }

        return validBeds;
    }

    /** A bed is housing only when there is a solid roof within five blocks above its head. */
    private static boolean isValidBed(ServerLevel level, BlockPos bedHead) {
        return hasRoof(level, bedHead);
    }

    /**
     * Uses the same practical roof definition as PoliticsMod's old residential
     * scanner: any solid-rendering block within five blocks above the bed head.
     */
    private static boolean hasRoof(ServerLevel level, BlockPos bedHead) {
        for (int i = 1; i <= 5; i++) {
            BlockPos above = bedHead.above(i);
            var state = level.getBlockState(above);
            if (state.isSolidRender(level, above)) {
                return true;
            }
        }
        return false;
    }

    private static int calculateCountry(
        MinecraftServer server,
        String countryName
    ) {
        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null || politics.getCountry(countryName) == null) return 0;

        int totalBeds = 0;
        for (Map.Entry<Long, Integer> entry : get(server).snapshot().entrySet()) {
            Country owner = politics.getCountryAt(new ChunkPos(entry.getKey()));
            if (owner != null && countryName.equals(owner.getName())) {
                totalBeds += Math.max(0, entry.getValue());
            }
        }
        return totalBeds * RESIDENTS_PER_BED;
    }

    private static int calculateCity(
        MinecraftServer server,
        String countryName,
        String cityName
    ) {
        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null || politics.getCountry(countryName) == null) return 0;

        int totalBeds = 0;
        for (Map.Entry<Long, Integer> entry : get(server).snapshot().entrySet()) {
            ChunkPos chunk = new ChunkPos(entry.getKey());
            Country owner = politics.getCountryAt(chunk);
            if (owner == null || !countryName.equals(owner.getName())) continue;
            if (!cityName.equals(politics.getCityAt(chunk))) continue;
            totalBeds += Math.max(0, entry.getValue());
        }
        return totalBeds * RESIDENTS_PER_BED;
    }

    private static Cache cache(MinecraftServer server) {
        long second = server.overworld().getGameTime() / 20L;
        Cache cache = CACHE.get(server);
        if (cache == null || cache.second != second) {
            cache = new Cache(second);
            CACHE.put(server, cache);
        }
        return cache;
    }

    public static void invalidate(MinecraftServer server) {
        CACHE.remove(server);
    }

    private static final class Cache {
        private final long second;
        private final Map<String, Integer> byCountry = new HashMap<>();
        private final Map<String, Integer> byCity = new HashMap<>();

        private Cache(long second) {
            this.second = second;
        }
    }
}
