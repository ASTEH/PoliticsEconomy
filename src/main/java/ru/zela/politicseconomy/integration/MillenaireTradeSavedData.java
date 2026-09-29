package ru.zela.politicseconomy.integration;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent cycle marker for autonomous Millénaire trade. */
public final class MillenaireTradeSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_millenaire_trade";
    private static final String LAST_CYCLE = "lastCycle";

    private long lastProcessedCycle;

    public static MillenaireTradeSavedData create() {
        return new MillenaireTradeSavedData();
    }

    public static MillenaireTradeSavedData load(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        MillenaireTradeSavedData data = create();
        data.lastProcessedCycle = tag.getLong(LAST_CYCLE);
        return data;
    }

    public static MillenaireTradeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                MillenaireTradeSavedData::create,
                MillenaireTradeSavedData::load,
                null
            ),
            DATA_NAME
        );
    }

    public long lastProcessedCycle() {
        return lastProcessedCycle;
    }

    public void setLastProcessedCycle(long cycle) {
        lastProcessedCycle = cycle;
        setDirty();
    }

    @Override
    public CompoundTag save(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        tag.putLong(LAST_CYCLE, lastProcessedCycle);
        return tag;
    }
}
