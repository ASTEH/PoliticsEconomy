package ru.zela.politicseconomy.integration;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import ru.zela.politicseconomy.infrastructure.InfrastructureManager;

/**
 * Resolves the country that should supply economic modifiers.
 *
 * Player actions use the player's own country, regardless of the chunk where
 * the action happens. Autonomous Create machines use the country recorded when
 * the machine block was placed; this prevents moving a machine outside national
 * borders from bypassing its owner's economic modifiers.
 */
public final class CountryContext {
    private CountryContext() {}

    public static Country playerCountry(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        PoliticsManager manager = PoliticsManager.get(player.serverLevel());
        if (manager == null) {
            return null;
        }
        String countryName = manager.getPlayerCountry(player.getUUID());
        if (countryName == null || countryName.isBlank()) {
            return null;
        }
        return manager.getCountry(countryName);
    }

    public static String playerCountryName(ServerPlayer player) {
        Country country = playerCountry(player);
        return country == null ? null : country.getName();
    }

    /**
     * Resolves the economic state in which a player currently acts.
     * Player countries use their PoliticsMod name; otherwise a player standing
     * inside Millénaire territory acts within that autonomous village state.
     */
    public static String playerStateName(ServerPlayer player) {
        if (player == null) return null;

        Country country = playerCountry(player);
        if (country != null) {
            return country.getName();
        }

        ru.zela.politicseconomy.integration.MillenaireIntegration.VillageSnapshot village =
            ru.zela.politicseconomy.integration.MillenaireIntegration.snapshotAtChunk(
                player.getServer(),
                player.chunkPosition()
            );
        return village == null ? null : village.stateKey();
    }

    /**
     * Returns the economic state key owning a machine position. Player countries
     * keep their PoliticsMod name; autonomous Millénaire villages use a stable
     * millenaire:<uuid> key.
     */
    public static String machineStateName(ServerLevel level, BlockPos machinePos) {
        Country country = machineCountry(level, machinePos);
        if (country != null) {
            return country.getName();
        }

        ru.zela.politicseconomy.integration.MillenaireIntegration.VillageSnapshot village =
            ru.zela.politicseconomy.integration.MillenaireIntegration.snapshotAtChunk(
                level.getServer(),
                new ChunkPos(machinePos)
            );
        return village == null ? null : village.stateKey();
    }

    public static Country machineCountry(ServerLevel level, BlockPos machinePos) {
        if (level == null || machinePos == null) {
            return null;
        }

        String ownerCountryName = InfrastructureManager.getPlacedOwnerCountry(level, machinePos);
        if (ownerCountryName != null && !ownerCountryName.isBlank()) {
            PoliticsManager manager = PoliticsManager.get(level);
            if (manager != null) {
                Country owner = manager.getCountry(ownerCountryName);
                if (owner != null) {
                    return owner;
                }
            }
        }

        // Compatibility fallback for machines that were placed before this
        // version started recording their owner's country. For newly placed
        // machines this path is not used.
        PoliticsManager manager = PoliticsManager.get(level);
        return manager == null ? null : manager.getCountryAt(new ChunkPos(machinePos));
    }
}
