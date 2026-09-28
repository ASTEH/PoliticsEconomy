package ru.zela.politicseconomy.country;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class CountryPolicyManager {
    private CountryPolicyManager() {}

    public static CountryPolicySavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                CountryPolicySavedData::create,
                CountryPolicySavedData::load,
                null
            ),
            CountryPolicySavedData.DATA_NAME
        );
    }

    public static GovernmentType getGovernment(MinecraftServer server, String countryName) {
        return get(server).getGovernment(countryName);
    }

    public static ReligionType getReligion(MinecraftServer server, String countryName) {
        return get(server).getReligion(countryName);
    }

    public static boolean setGovernment(MinecraftServer server, String countryName, GovernmentType government) {
        CountryPolicySavedData data = get(server);
        if (data.hasGovernment(countryName)) return false;
        data.setGovernment(countryName, government);
        return true;
    }

    public static boolean setReligion(MinecraftServer server, String countryName, ReligionType religion) {
        CountryPolicySavedData data = get(server);
        if (data.hasReligion(countryName)) return false;
        data.setReligion(countryName, religion);
        return true;
    }

    public static void forceSetGovernment(MinecraftServer server, String countryName, GovernmentType government) {
        get(server).setGovernment(countryName, government);
    }

    public static void forceSetReligion(MinecraftServer server, String countryName, ReligionType religion) {
        get(server).setReligion(countryName, religion);
    }
}
