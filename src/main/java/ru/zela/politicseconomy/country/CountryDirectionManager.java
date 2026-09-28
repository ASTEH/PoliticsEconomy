package ru.zela.politicseconomy.country;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Access point for country direction data.
 */
public final class CountryDirectionManager {
    private CountryDirectionManager() {}

    public static CountryDirectionSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                CountryDirectionSavedData::create,
                CountryDirectionSavedData::load,
                null
            ),
            CountryDirectionSavedData.DATA_NAME
        );
    }

    public static CountryDirection getDirection(MinecraftServer server, String countryName) {
        return get(server).get(countryName);
    }

    public static boolean setDirection(
        MinecraftServer server,
        String countryName,
        CountryDirection direction
    ) {
        CountryDirectionSavedData data = get(server);
        if (data.has(countryName)) {
            return false;
        }
        data.set(countryName, direction);
        return true;
    }

    /**
     * Changes a country's direction without the one-time lock.
     * Intended for operator/cheat testing only.
     */
    public static void forceSetDirection(
        MinecraftServer server,
        String countryName,
        CountryDirection direction
    ) {
        get(server).set(countryName, direction);
    }
}
