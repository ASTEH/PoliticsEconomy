package ru.zela.politicseconomy.integration;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;

/**
 * Small, isolated bridge to PoliticsMod's public data API.
 *
 * <p>Keep all direct PoliticsMod references here when possible. This makes it
 * easier to maintain the addon if the upstream mod changes its API later.</p>
 */
public final class PoliticsModIntegration {
    private PoliticsModIntegration() {}

    public static PoliticsManager manager(ServerLevel level) {
        return PoliticsManager.get(level);
    }

    public static Optional<Country> countryAt(ServerPlayer player) {
        PoliticsManager manager = manager(player.serverLevel());
        if (manager == null) {
            return Optional.empty();
        }
        ChunkPos chunk = player.chunkPosition();
        return Optional.ofNullable(manager.getCountryAt(chunk));
    }

    public static Optional<Country> playerCountry(ServerPlayer player) {
        PoliticsManager manager = manager(player.serverLevel());
        if (manager == null) {
            return Optional.empty();
        }
        String countryName = manager.getPlayerCountry(player.getUUID());
        return countryName == null
            ? Optional.empty()
            : Optional.ofNullable(manager.getCountry(countryName));
    }

    public static String countryNameAt(ServerPlayer player) {
        PoliticsManager manager = manager(player.serverLevel());
        return manager == null
            ? null
            : manager.getCountryNameAt(player.chunkPosition());
    }

    public static String cityAt(ServerPlayer player) {
        PoliticsManager manager = manager(player.serverLevel());
        return manager == null
            ? null
            : manager.getCityAt(player.chunkPosition());
    }

    public static CountryRole role(ServerPlayer player, Country country) {
        return country.getRole(player.getUUID());
    }
}
