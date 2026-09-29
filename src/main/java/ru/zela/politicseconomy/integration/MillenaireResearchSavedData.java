package ru.zela.politicseconomy.integration;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class MillenaireResearchSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_millenaire_research";

    private long lastProcessedCycle = Long.MIN_VALUE;

    public static MillenaireResearchSavedData create() {
        return new MillenaireResearchSavedData();
    }

    public static MillenaireResearchSavedData load(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        MillenaireResearchSavedData data = create();
        if (tag.contains("last_cycle", Tag.TAG_LONG)) {
            data.lastProcessedCycle = tag.getLong("last_cycle");
        }
        return data;
    }

    public static MillenaireResearchSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                MillenaireResearchSavedData::create,
                MillenaireResearchSavedData::load,
                null
            ),
            DATA_NAME
        );
    }

    public long lastProcessedCycle() {
        return lastProcessedCycle;
    }

    public void setLastProcessedCycle(long cycle) {
        if (lastProcessedCycle == cycle) return;
        lastProcessedCycle = cycle;
        setDirty();
    }

    @Override
    public CompoundTag save(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        tag.putLong("last_cycle", lastProcessedCycle);
        return tag;
    }
}
