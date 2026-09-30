package ru.zela.politicseconomy.event;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** Persistent event cooldowns by economic state. */
public final class RandomEventSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_random_events";
    private final Map<String, Long> nextTicks = new HashMap<>();

    public static RandomEventSavedData create() {
        return new RandomEventSavedData();
    }

    public static RandomEventSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        RandomEventSavedData data = create();
        for (String key : tag.getAllKeys()) {
            data.nextTicks.put(key, tag.getLong(key));
        }
        return data;
    }

    public static RandomEventSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(RandomEventSavedData::create, RandomEventSavedData::load, null),
            DATA_NAME
        );
    }

    public long nextEventTick(String stateKey) {
        return nextTicks.getOrDefault(stateKey, 0L);
    }

    public void setNextEventTick(String stateKey, long tick) {
        if (stateKey == null || stateKey.isBlank()) return;
        nextTicks.put(stateKey, Math.max(0L, tick));
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        for (Map.Entry<String, Long> entry : nextTicks.entrySet()) {
            tag.putLong(entry.getKey(), entry.getValue());
        }
        return tag;
    }
}
