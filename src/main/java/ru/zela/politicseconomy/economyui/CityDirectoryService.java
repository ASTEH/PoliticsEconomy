package ru.zela.politicseconomy.economyui;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.politics.Country;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.Tags;
import ru.zela.politicseconomy.infrastructure.InfrastructureManager;
import ru.zela.politicseconomy.infrastructure.InfrastructureClassifier;

import net.krona.politicsmod.block.entity.ResidentialBuildingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Builds economic statistics for every city known to PoliticsMod. */
public final class CityDirectoryService {
    private static final int MAX_CITIES = 96;

    private CityDirectoryService() {}

    public record CitySnapshot(
        String name,
        String country,
        String mayor,
        int treasury,
        int incomePerCycle,
        int infrastructureBlocks,
        int population,
        int taxBlocks,
        boolean capital,
        boolean mine
    ) {}

    public static List<CitySnapshot> build(MinecraftServer server, ServerPlayer viewer) {
        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null) return List.of();

        List<CitySnapshot> result = new ArrayList<>();

        for (Country country : politics.getCountries().values()) {
            for (String city : country.cities) {
                int taxBlocks = countTaxBlocks(politics, country, city);
                int income = (int) Math.round(
                    taxBlocks * PoliticsConfig.get().incomePerTaxBlock *
                        Math.max(0, 100 - country.federalTaxRate) / 100.0D
                );

                CityInfrastructure stats = scanInfrastructure(server, politics, country.getName(), city);
                UUID mayorUuid = country.cityMayors.get(city);
                String mayorName = nameOf(server, mayorUuid);

                result.add(new CitySnapshot(
                    city,
                    country.getName(),
                    mayorName,
                    country.cityBalances.getOrDefault(city, 0),
                    income,
                    stats.infrastructureBlocks,
                    stats.population,
                    taxBlocks,
                    city.equals(country.capital),
                    mayorUuid != null && mayorUuid.equals(viewer.getUUID())
                ));
            }
        }

        result.sort(
            Comparator.comparingInt(CitySnapshot::treasury).reversed()
                .thenComparing(CitySnapshot::country)
                .thenComparing(CitySnapshot::name)
        );

        return result.size() <= MAX_CITIES ? List.copyOf(result) : List.copyOf(result.subList(0, MAX_CITIES));
    }

    private static int countTaxBlocks(PoliticsManager politics, Country country, String city) {
        int result = 0;
        for (Long posLong : country.taxBlocks) {
            ChunkPos chunk = new ChunkPos(BlockPos.of(posLong));
            if (city.equals(politics.getCityAt(chunk))) result++;
        }
        return result;
    }

    private static CityInfrastructure scanInfrastructure(
        MinecraftServer server,
        PoliticsManager politics,
        String countryName,
        String cityName
    ) {
        int blocks = 0;
        int population = 0;

        var level = server.overworld();
        for (var chunkEntry : InfrastructureManager.get(server).getDimension(level).entrySet()) {
            ChunkPos chunkPos = new ChunkPos(chunkEntry.getKey());
            Country owner = politics.getCountryAt(chunkPos);
            if (owner == null || !countryName.equals(owner.getName())) continue;
            if (!cityName.equals(politics.getCityAt(chunkPos))) continue;

            blocks += chunkEntry.getValue().size();

            for (var blockEntry : chunkEntry.getValue().entrySet()) {
                BlockPos pos = BlockPos.of(blockEntry.getKey());
                if (!level.hasChunkAt(pos)) continue;
                if (level.getBlockEntity(pos) instanceof ResidentialBuildingEntity residential) {
                    population += Math.max(0, residential.getPop());
                }
            }
        }

        return new CityInfrastructure(blocks, population);
    }

    private static String nameOf(MinecraftServer server, UUID uuid) {
        if (uuid == null) return "—";
        if (server.getProfileCache() != null) {
            return server.getProfileCache().get(uuid)
                .map(profile -> profile.getName())
                .orElse(uuid.toString().substring(0, 8));
        }
        return uuid.toString().substring(0, 8);
    }

    private record CityInfrastructure(int infrastructureBlocks, int population) {}
}
