package ru.zela.politicseconomy.integration;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent military readiness and combat-loss statistics for every economic state.
 *
 * <p>The state key is either a PoliticsMod country name or a stable
 * millenaire:<uuid> key. Population and workplaces remain live data; only the
 * military condition, accumulated combat losses and cycle marker are persisted.</p>
 */
public final class MilitaryReadinessSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_military_readiness";
    private static final String STATES = "states";
    private static final String LAST_CYCLE = "lastCycle";

    private final Map<String, StateRecord> states = new HashMap<>();
    private long lastProcessedCycle = Long.MIN_VALUE;

    public static MilitaryReadinessSavedData create() {
        return new MilitaryReadinessSavedData();
    }

    public static MilitaryReadinessSavedData load(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        MilitaryReadinessSavedData data = create();
        data.lastProcessedCycle = tag.getLong(LAST_CYCLE);
        if (!tag.contains(STATES, Tag.TAG_COMPOUND)) {
            return data;
        }

        CompoundTag statesTag = tag.getCompound(STATES);
        for (String stateKey : statesTag.getAllKeys()) {
            CompoundTag state = statesTag.getCompound(stateKey);
            double readiness = Math.max(
                0.0D,
                Math.min(100.0D, state.getDouble("readiness"))
            );
            long casualties = Math.max(
                0L,
                state.getLong("casualties")
            );
            data.states.put(
                stateKey,
                new StateRecord(readiness, casualties)
            );
        }
        return data;
    }

    public static MilitaryReadinessSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                MilitaryReadinessSavedData::create,
                MilitaryReadinessSavedData::load,
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

    public void ensureState(String stateKey) {
        if (stateKey == null || stateKey.isBlank()) {
            return;
        }
        states.computeIfAbsent(
            stateKey,
            ignored -> new StateRecord(20.0D, 0L)
        );
    }

    public double readiness(String stateKey) {
        StateRecord state = states.get(stateKey);
        return state == null ? 0.0D : state.readiness();
    }

    public void setReadiness(String stateKey, double value) {
        if (stateKey == null || stateKey.isBlank()) {
            return;
        }

        StateRecord existing = states.getOrDefault(
            stateKey,
            new StateRecord(20.0D, 0L)
        );
        states.put(
            stateKey,
            new StateRecord(
                Math.max(0.0D, Math.min(100.0D, value)),
                existing.casualties()
            )
        );
        setDirty();
    }

    public double moveTowards(
        String stateKey,
        double target,
        double factor
    ) {
        ensureState(stateKey);

        double current = readiness(stateKey);
        double clampedTarget = Math.max(
            0.0D,
            Math.min(100.0D, target)
        );
        double clampedFactor = Math.max(
            0.0D,
            Math.min(1.0D, factor)
        );
        double next =
            current + (clampedTarget - current) * clampedFactor;

        setReadiness(stateKey, next);
        return next;
    }

    public double reduceReadiness(String stateKey, double amount) {
        if (stateKey == null || stateKey.isBlank() || amount <= 0.0D) {
            return readiness(stateKey);
        }

        ensureState(stateKey);
        double next = Math.max(
            0.0D,
            readiness(stateKey) - amount
        );
        setReadiness(stateKey, next);
        return next;
    }

    public long casualties(String stateKey) {
        StateRecord state = states.get(stateKey);
        return state == null ? 0L : state.casualties();
    }

    public void addCasualties(String stateKey, long amount) {
        if (stateKey == null || stateKey.isBlank() || amount <= 0L) {
            return;
        }

        StateRecord existing = states.getOrDefault(
            stateKey,
            new StateRecord(20.0D, 0L)
        );
        long next = existing.casualties();
        if (Long.MAX_VALUE - next < amount) {
            next = Long.MAX_VALUE;
        } else {
            next += amount;
        }

        states.put(
            stateKey,
            new StateRecord(existing.readiness(), next)
        );
        setDirty();
    }

    public Map<String, StateRecord> snapshot() {
        return Map.copyOf(states);
    }

    @Override
    public CompoundTag save(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        tag.putLong(LAST_CYCLE, lastProcessedCycle);

        CompoundTag statesTag = new CompoundTag();
        for (Map.Entry<String, StateRecord> entry : states.entrySet()) {
            CompoundTag state = new CompoundTag();
            state.putDouble("readiness", entry.getValue().readiness());
            state.putLong("casualties", entry.getValue().casualties());
            statesTag.put(entry.getKey(), state);
        }
        tag.put(STATES, statesTag);
        return tag;
    }

    public record StateRecord(
        double readiness,
        long casualties
    ) {}
}
