package ru.zela.politicseconomy.integration;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class MillenaireProductionSavedData extends SavedData {
    public static final String DATA_NAME =
        "politicseconomy_millenaire_production";

    private static final String LAST_CYCLE = "last_cycle";

    private long lastCycle = Long.MIN_VALUE;

    public static MillenaireProductionSavedData create() {
        return new MillenaireProductionSavedData();
    }

    public static MillenaireProductionSavedData load(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        MillenaireProductionSavedData data =
            new MillenaireProductionSavedData();
        data.lastCycle = tag.getLong(LAST_CYCLE);
        return data;
    }

    public static MillenaireProductionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                MillenaireProductionSavedData::create,
                MillenaireProductionSavedData::load,
                null
            ),
            DATA_NAME
        );
    }

    public long lastProcessedCycle() {
        return lastCycle;
    }

    public void setLastProcessedCycle(long cycle) {
        if (lastCycle != cycle) {
            lastCycle = cycle;
            setDirty();
        }
    }
}
