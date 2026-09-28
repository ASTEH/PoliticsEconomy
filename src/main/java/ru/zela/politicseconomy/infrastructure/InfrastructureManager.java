package ru.zela.politicseconomy.infrastructure;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Access point for player-built infrastructure data. */
public final class InfrastructureManager {
    private InfrastructureManager() {}

    public static InfrastructureSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                InfrastructureSavedData::create,
                InfrastructureSavedData::load,
                null
            ),
            InfrastructureSavedData.DATA_NAME
        );
    }

    public static void add(ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        add(level, pos, state, null);
    }

    public static void add(
        ServerLevel level,
        net.minecraft.core.BlockPos pos,
        net.minecraft.world.level.block.state.BlockState state,
        String ownerCountry
    ) {
        get(level.getServer()).add(level, pos, state, ownerCountry);
    }

    public static String getPlacedOwnerCountry(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return get(level.getServer()).getOwnerCountry(level, pos);
    }

    public static boolean remove(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return get(level.getServer()).remove(level, pos);
    }

    public static Map<String, Integer> getCurrentChunkCounts(ServerPlayer player) {
        Map<Long, String> positions = get(player.server).getChunk(player.serverLevel(), player.chunkPosition());
        return countBlocks(positions);
    }

    public static CountryInfrastructureStats getCountryStats(ServerPlayer player) {
        PoliticsManager politics = PoliticsManager.get(player.serverLevel());
        if (politics == null) {
            return emptyStats("");
        }

        String countryName = politics.getPlayerCountry(player.getUUID());
        if (countryName == null) {
            return emptyStats("");
        }

        return getCountryStats(player.getServer(), player.serverLevel(), countryName);
    }

    /**
     * Calculates a country's infrastructure using the claims currently stored
     * by PoliticsMod. Economy processing uses the overworld because PoliticsMod
     * stores national territory as overworld chunks.
     */
    public static CountryInfrastructureStats getCountryStats(
        MinecraftServer server,
        String countryName
    ) {
        return getCountryStats(server, server.overworld(), countryName);
    }

    public static CountryInfrastructureStats getCountryStats(
        MinecraftServer server,
        ServerLevel level,
        String countryName
    ) {
        PoliticsManager politics = PoliticsManager.get(level);
        if (politics == null || countryName == null || countryName.isBlank()) {
            return emptyStats(countryName == null ? "" : countryName);
        }

        Country country = politics.getCountry(countryName);
        if (country == null) {
            return emptyStats(countryName);
        }

        Map<String, Integer> counts = new HashMap<>();
        Map<InfrastructureCategory, Integer> categoryCounts = new EnumMap<>(InfrastructureCategory.class);
        int total = 0;
        double baseMaintenance = 0.0;
        double adjustedMaintenance = 0.0;

        for (Map.Entry<Long, Map<Long, String>> chunkEntry : get(server).getDimension(level).entrySet()) {
            ChunkPos chunkPos = new ChunkPos(chunkEntry.getKey());
            Country owner = politics.getCountryAt(chunkPos);
            if (owner == null || !owner.getName().equals(countryName)) {
                continue;
            }

            for (String blockId : chunkEntry.getValue().values()) {
                InfrastructureBlockInfo info = InfrastructureClassifier.classify(blockId);
                counts.merge(blockId, 1, Integer::sum);
                categoryCounts.merge(info.category(), 1, Integer::sum);
                total++;
                baseMaintenance += info.baseMaintenance();
            }
        }

        for (Map.Entry<InfrastructureCategory, Integer> entry : categoryCounts.entrySet()) {
            double perBlock = MaintenanceCalculator.categoryCostPerBlock(entry.getKey());
            double multiplier = MaintenanceCalculator.directionMultiplier(
                server, countryName, entry.getKey()
            );
            adjustedMaintenance += perBlock * entry.getValue() * multiplier;
        }

        return new CountryInfrastructureStats(
            countryName,
            total,
            Map.copyOf(counts),
            Map.copyOf(categoryCounts),
            baseMaintenance,
            adjustedMaintenance
        );
    }

    private static CountryInfrastructureStats emptyStats(String countryName) {
        return new CountryInfrastructureStats(countryName, 0, Map.of(), Map.of(), 0.0, 0.0);
    }

    private static Map<String, Integer> countBlocks(Map<Long, String> positions) {
        Map<String, Integer> result = new HashMap<>();
        for (String blockId : positions.values()) {
            result.merge(blockId, 1, Integer::sum);
        }
        return Map.copyOf(result);
    }

    public record CountryInfrastructureStats(
        String countryName,
        int totalBlocks,
        Map<String, Integer> blocks,
        Map<InfrastructureCategory, Integer> categories,
        double baseMaintenance,
        double adjustedMaintenance
    ) {}
}
