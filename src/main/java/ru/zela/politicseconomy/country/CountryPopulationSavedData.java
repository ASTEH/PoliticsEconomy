package ru.zela.politicseconomy.country;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent per-chunk bed counts used to derive population.
 * One detected bed head represents two residents.
 */
public final class CountryPopulationSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_country_population";
    private static final String CHUNKS = "chunks";

    private final Map<Long, Integer> bedsByChunk = new HashMap<>();

    public static CountryPopulationSavedData create() {
        return new CountryPopulationSavedData();
    }

    public static CountryPopulationSavedData load(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        CountryPopulationSavedData data = create();
        if (tag.contains(CHUNKS, Tag.TAG_COMPOUND)) {
            CompoundTag chunks = tag.getCompound(CHUNKS);
            for (String key : chunks.getAllKeys()) {
                try {
                    long chunkLong = Long.parseLong(key);
                    data.bedsByChunk.put(chunkLong, Math.max(0, chunks.getInt(key)));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed legacy entries.
                }
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        CompoundTag chunks = new CompoundTag();
        for (Map.Entry<Long, Integer> entry : bedsByChunk.entrySet()) {
            chunks.putInt(
                Long.toString(entry.getKey()),
                Math.max(0, entry.getValue())
            );
        }
        tag.put(CHUNKS, chunks);
        return tag;
    }

    public int getBeds(long chunkLong) {
        return Math.max(0, bedsByChunk.getOrDefault(chunkLong, 0));
    }

    public void setBeds(long chunkLong, int beds) {
        int normalized = Math.max(0, beds);
        if (bedsByChunk.getOrDefault(chunkLong, -1) == normalized) {
            return;
        }
        if (normalized == 0) {
            bedsByChunk.remove(chunkLong);
        } else {
            bedsByChunk.put(chunkLong, normalized);
        }
        setDirty();
    }

    public Map<Long, Integer> snapshot() {
        return Map.copyOf(bedsByChunk);
    }
}
