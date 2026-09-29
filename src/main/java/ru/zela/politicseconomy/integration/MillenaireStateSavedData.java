package ru.zela.politicseconomy.integration;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent economic identity/state for Millénaire villages.
 * Dynamic population/building/warehouse data remains sourced from Millénaire.
 */
public final class MillenaireStateSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_millenaire_states";
    private static final String STATES = "states";

    private final Map<UUID, StateRecord> states = new HashMap<>();

    public static MillenaireStateSavedData create() {
        return new MillenaireStateSavedData();
    }

    public static MillenaireStateSavedData load(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        MillenaireStateSavedData data = create();
        if (!tag.contains(STATES, Tag.TAG_COMPOUND)) return data;

        CompoundTag statesTag = tag.getCompound(STATES);
        for (String key : statesTag.getAllKeys()) {
            try {
                UUID id = UUID.fromString(key);
                CompoundTag state = statesTag.getCompound(key);
                StateRecord record = new StateRecord(
                    state.getString("name"),
                    Math.max(0L, state.getLong("treasury")),
                    state.getLong("createdTick"),
                    state.getLong("lastSeenTick")
                );
                data.states.put(id, record);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    public static MillenaireStateSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                MillenaireStateSavedData::create,
                MillenaireStateSavedData::load,
                null
            ),
            DATA_NAME
        );
    }

    public void ensureState(UUID id, String name, long tick) {
        if (id == null) return;

        StateRecord existing = states.get(id);
        if (existing == null) {
            states.put(id, new StateRecord(
                name == null || name.isBlank() ? id.toString().substring(0, 8) : name,
                1000L,
                tick,
                tick
            ));
        } else {
            String safeName = name == null || name.isBlank() ? existing.name() : name;
            states.put(id, new StateRecord(
                safeName,
                existing.treasury(),
                existing.createdTick(),
                tick
            ));
        }
        setDirty();
    }

    public boolean hasState(UUID id) {
        return id != null && states.containsKey(id);
    }

    public String name(UUID id) {
        StateRecord record = states.get(id);
        return record == null ? "" : record.name();
    }

    public long treasury(UUID id) {
        StateRecord record = states.get(id);
        return record == null ? 0L : record.treasury();
    }

    public void setTreasury(UUID id, long treasury) {
        StateRecord record = states.get(id);
        if (record == null) {
            ensureState(id, id == null ? "" : id.toString().substring(0, 8), 0L);
            record = states.get(id);
        }
        states.put(id, new StateRecord(
            record.name(),
            Math.max(0L, treasury),
            record.createdTick(),
            record.lastSeenTick()
        ));
        setDirty();
    }

    public long addTreasury(UUID id, long amount) {
        if (id == null || amount == 0L) return treasury(id);
        long current = treasury(id);
        long updated;
        if (amount > 0L && current > Long.MAX_VALUE - amount) {
            updated = Long.MAX_VALUE;
        } else if (amount < 0L && current + amount < 0L) {
            updated = 0L;
        } else {
            updated = current + amount;
        }
        setTreasury(id, updated);
        return updated;
    }

    public Map<UUID, StateRecord> snapshot() {
        return Map.copyOf(states);
    }

    @Override
    public CompoundTag save(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        CompoundTag statesTag = new CompoundTag();
        for (Map.Entry<UUID, StateRecord> entry : states.entrySet()) {
            CompoundTag state = new CompoundTag();
            state.putString("name", entry.getValue().name());
            state.putLong("treasury", entry.getValue().treasury());
            state.putLong("createdTick", entry.getValue().createdTick());
            state.putLong("lastSeenTick", entry.getValue().lastSeenTick());
            statesTag.put(entry.getKey().toString(), state);
        }
        tag.put(STATES, statesTag);
        return tag;
    }

    public record StateRecord(
        String name,
        long treasury,
        long createdTick,
        long lastSeenTick
    ) {}
}
