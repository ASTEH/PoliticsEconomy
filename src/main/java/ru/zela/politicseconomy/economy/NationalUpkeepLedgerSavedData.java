package ru.zela.politicseconomy.economy;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Persistent national resource stockpiles and unpaid strategic-resource debt. */
public final class NationalUpkeepLedgerSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_national_upkeep";

    private static final String INITIALIZED = "initialized";
    private static final String LAST_CYCLE = "lastCycle";
    private static final String COUNTRIES = "countries";
    private static final String STOCKPILE = "stockpile";
    private static final String DEBT = "debt";

    private boolean initialized;
    private long lastProcessedCycle;
    private final Map<String, EnumMap<NationalResource, Integer>> stockpiles = new HashMap<>();
    private final Map<String, EnumMap<NationalResource, Integer>> debts = new HashMap<>();

    public static NationalUpkeepLedgerSavedData create() {
        return new NationalUpkeepLedgerSavedData();
    }

    public static NationalUpkeepLedgerSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NationalUpkeepLedgerSavedData data = create();
        data.initialized = tag.getBoolean(INITIALIZED);
        data.lastProcessedCycle = tag.getLong(LAST_CYCLE);

        if (!tag.contains(COUNTRIES, Tag.TAG_COMPOUND)) {
            return data;
        }

        CompoundTag countriesTag = tag.getCompound(COUNTRIES);
        for (String countryName : countriesTag.getAllKeys()) {
            CompoundTag countryTag = countriesTag.getCompound(countryName);
            data.stockpiles.put(countryName, readMap(countryTag, STOCKPILE));
            data.debts.put(countryName, readMap(countryTag, DEBT));
        }
        return data;
    }

    private static EnumMap<NationalResource, Integer> readMap(CompoundTag countryTag, String key) {
        EnumMap<NationalResource, Integer> result = new EnumMap<>(NationalResource.class);
        if (!countryTag.contains(key, Tag.TAG_COMPOUND)) {
            return result;
        }
        CompoundTag mapTag = countryTag.getCompound(key);
        for (NationalResource resource : NationalResource.values()) {
            String resourceKey = resource.id();
            if (mapTag.contains(resourceKey, Tag.TAG_INT)) {
                result.put(resource, Math.max(0, mapTag.getInt(resourceKey)));
            }
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(INITIALIZED, initialized);
        tag.putLong(LAST_CYCLE, lastProcessedCycle);

        CompoundTag countriesTag = new CompoundTag();
        for (String countryName : unionCountryNames()) {
            CompoundTag countryTag = new CompoundTag();
            writeMap(countryTag, STOCKPILE, stockpiles.get(countryName));
            writeMap(countryTag, DEBT, debts.get(countryName));
            countriesTag.put(countryName, countryTag);
        }
        tag.put(COUNTRIES, countriesTag);
        return tag;
    }

    private void writeMap(CompoundTag countryTag, String key, EnumMap<NationalResource, Integer> values) {
        CompoundTag mapTag = new CompoundTag();
        if (values != null) {
            for (NationalResource resource : NationalResource.values()) {
                mapTag.putInt(resource.id(), get(values, resource));
            }
        }
        countryTag.put(key, mapTag);
    }

    private java.util.Set<String> unionCountryNames() {
        java.util.Set<String> names = new java.util.HashSet<>(stockpiles.keySet());
        names.addAll(debts.keySet());
        return names;
    }

    private static int get(EnumMap<NationalResource, Integer> map, NationalResource resource) {
        return map == null ? 0 : Math.max(0, map.getOrDefault(resource, 0));
    }

    public boolean initialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
        setDirty();
    }

    public long lastProcessedCycle() {
        return lastProcessedCycle;
    }

    public void setLastProcessedCycle(long cycle) {
        this.lastProcessedCycle = cycle;
        setDirty();
    }

    public boolean hasCountry(String countryName) {
        return stockpiles.containsKey(countryName) || debts.containsKey(countryName);
    }

    public void initializeCountry(String countryName, ru.zela.politicseconomy.country.CountryDirection direction) {
        if (hasCountry(countryName)) {
            return;
        }
        EnumMap<NationalResource, Integer> reserve = new EnumMap<>(NationalResource.class);
        for (NationalResource resource : NationalResource.values()) {
            reserve.put(resource, NationalUpkeepCalculator.startingReserve(direction, resource));
        }
        stockpiles.put(countryName, reserve);
        debts.put(countryName, new EnumMap<>(NationalResource.class));
        setDirty();
    }

    public int getStockpile(String countryName, NationalResource resource) {
        return get(stockpiles.get(countryName), resource);
    }

    public void setStockpile(String countryName, NationalResource resource, int value) {
        stockpiles.computeIfAbsent(countryName, key -> new EnumMap<>(NationalResource.class))
            .put(resource, Math.max(0, value));
        setDirty();
    }

    public void addStockpile(String countryName, NationalResource resource, int amount) {
        setStockpile(countryName, resource, getStockpile(countryName, resource) + Math.max(0, amount));
    }

    public int getDebt(String countryName, NationalResource resource) {
        return get(debts.get(countryName), resource);
    }

    public void setDebt(String countryName, NationalResource resource, int value) {
        debts.computeIfAbsent(countryName, key -> new EnumMap<>(NationalResource.class))
            .put(resource, Math.max(0, value));
        setDirty();
    }

    public void setCountryMaps(
        String countryName,
        EnumMap<NationalResource, Integer> stockpile,
        EnumMap<NationalResource, Integer> debt
    ) {
        stockpiles.put(countryName, new EnumMap<>(stockpile));
        debts.put(countryName, new EnumMap<>(debt));
        setDirty();
    }
}
