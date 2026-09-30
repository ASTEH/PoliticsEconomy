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
    private static final String RESIDENTS = "residents";
    private static final String FOOD_REMAINDER = "foodRemainder";
    private static final String FED_CYCLES = "fedCycles";
    private static final String STARVATION_CYCLES = "starvationCycles";
    private static final String DEVELOPMENT_PROGRESS = "developmentProgress";

    private final Map<Long, Integer> bedsByChunk = new HashMap<>();
    private final Map<String, Integer> residentsByCountry = new HashMap<>();
    private final Map<String, Double> foodRemainderByCountry = new HashMap<>();
    private final Map<String, Integer> fedCyclesByCountry = new HashMap<>();
    private final Map<String, Integer> starvationCyclesByCountry = new HashMap<>();
    private final Map<String, Integer> developmentProgressByCountry = new HashMap<>();

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

        readIntMap(tag, RESIDENTS, data.residentsByCountry);
        readDoubleMap(tag, FOOD_REMAINDER, data.foodRemainderByCountry);
        readIntMap(tag, FED_CYCLES, data.fedCyclesByCountry);
        readIntMap(tag, STARVATION_CYCLES, data.starvationCyclesByCountry);
        readIntMap(tag, DEVELOPMENT_PROGRESS, data.developmentProgressByCountry);
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
        writeIntMap(tag, RESIDENTS, residentsByCountry);
        writeDoubleMap(tag, FOOD_REMAINDER, foodRemainderByCountry);
        writeIntMap(tag, FED_CYCLES, fedCyclesByCountry);
        writeIntMap(tag, STARVATION_CYCLES, starvationCyclesByCountry);
        writeIntMap(tag, DEVELOPMENT_PROGRESS, developmentProgressByCountry);
        return tag;
    }

    private static void readIntMap(CompoundTag root, String key, Map<String, Integer> target) {
        if (!root.contains(key, Tag.TAG_COMPOUND)) return;
        CompoundTag values = root.getCompound(key);
        for (String name : values.getAllKeys()) {
            target.put(name, values.getInt(name));
        }
    }

    private static void readDoubleMap(CompoundTag root, String key, Map<String, Double> target) {
        if (!root.contains(key, Tag.TAG_COMPOUND)) return;
        CompoundTag values = root.getCompound(key);
        for (String name : values.getAllKeys()) {
            double value = values.getDouble(name);
            if (value > 1.0E-9D) target.put(name, value);
        }
    }

    private static void writeIntMap(CompoundTag root, String key, Map<String, Integer> values) {
        CompoundTag out = new CompoundTag();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            out.putInt(entry.getKey(), entry.getValue());
        }
        root.put(key, out);
    }

    private static void writeDoubleMap(CompoundTag root, String key, Map<String, Double> values) {
        CompoundTag out = new CompoundTag();
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            if (entry.getValue() > 1.0E-9D) out.putDouble(entry.getKey(), entry.getValue());
        }
        root.put(key, out);
    }

    public boolean hasResidents(String countryName) {
        return residentsByCountry.containsKey(countryName);
    }

    public int getResidents(String countryName) {
        return Math.max(0, residentsByCountry.getOrDefault(countryName, 0));
    }

    public void setResidents(String countryName, int residents) {
        residentsByCountry.put(countryName, Math.max(0, residents));
        setDirty();
    }

    public double getFoodRemainder(String countryName) {
        return Math.max(0.0D, foodRemainderByCountry.getOrDefault(countryName, 0.0D));
    }

    public void setFoodRemainder(String countryName, double value) {
        if (value <= 1.0E-9D) foodRemainderByCountry.remove(countryName);
        else foodRemainderByCountry.put(countryName, value);
        setDirty();
    }

    public int getFedCycles(String countryName) {
        return Math.max(0, fedCyclesByCountry.getOrDefault(countryName, 0));
    }

    public void setFedCycles(String countryName, int cycles) {
        fedCyclesByCountry.put(countryName, Math.max(0, cycles));
        setDirty();
    }

    public int getStarvationCycles(String countryName) {
        return Math.max(0, starvationCyclesByCountry.getOrDefault(countryName, 0));
    }

    public void setStarvationCycles(String countryName, int cycles) {
        starvationCyclesByCountry.put(countryName, Math.max(0, cycles));
        setDirty();
    }

    public int getDevelopmentProgress(String countryName) {
        return Math.max(0, developmentProgressByCountry.getOrDefault(countryName, 0));
    }

    public void setDevelopmentProgress(String countryName, int progress) {
        developmentProgressByCountry.put(countryName, Math.max(0, progress));
        setDirty();
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
