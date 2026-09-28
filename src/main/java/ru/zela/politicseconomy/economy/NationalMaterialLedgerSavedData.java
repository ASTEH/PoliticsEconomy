package ru.zela.politicseconomy.economy;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Exact national material warehouse and unpaid material demand.
 * Stockpiles are stored by concrete item id, while debt/remainders are stored
 * by a canonical material-choice key (one item or an allowed set of items).
 */
public final class NationalMaterialLedgerSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_national_materials";

    private static final String COUNTRIES = "countries";
    private static final String STOCKPILE = "stockpile";
    private static final String DEBT = "debt";
    private static final String REMAINDER = "remainder";
    private static final String LAST_CYCLE = "lastCycle";

    private final Map<String, Map<String, Integer>> stockpiles = new HashMap<>();
    private final Map<String, Map<String, Integer>> debts = new HashMap<>();
    private final Map<String, Map<String, Double>> remainders = new HashMap<>();
    private long lastProcessedCycle;

    public static NationalMaterialLedgerSavedData create() {
        return new NationalMaterialLedgerSavedData();
    }

    public static NationalMaterialLedgerSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NationalMaterialLedgerSavedData data = create();
        data.lastProcessedCycle = tag.getLong(LAST_CYCLE);
        if (!tag.contains(COUNTRIES, Tag.TAG_COMPOUND)) {
            return data;
        }

        CompoundTag countries = tag.getCompound(COUNTRIES);

        for (String countryName : countries.getAllKeys()) {
            CompoundTag country = countries.getCompound(countryName);
            data.stockpiles.put(countryName, readIntMap(country, STOCKPILE));
            data.debts.put(countryName, readIntMap(country, DEBT));
            data.remainders.put(countryName, readDoubleMap(country, REMAINDER));
        }
        return data;
    }

    private static Map<String, Integer> readIntMap(CompoundTag country, String key) {
        Map<String, Integer> result = new HashMap<>();
        if (!country.contains(key, Tag.TAG_COMPOUND)) {
            return result;
        }
        CompoundTag values = country.getCompound(key);
        for (String entry : values.getAllKeys()) {
            result.put(entry, Math.max(0, values.getInt(entry)));
        }
        return result;
    }

    private static Map<String, Double> readDoubleMap(CompoundTag country, String key) {
        Map<String, Double> result = new HashMap<>();
        if (!country.contains(key, Tag.TAG_COMPOUND)) {
            return result;
        }
        CompoundTag values = country.getCompound(key);
        for (String entry : values.getAllKeys()) {
            double value = values.getDouble(entry);
            if (value > 1.0E-9D) {
                result.put(entry, value);
            }
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag countries = new CompoundTag();
        for (String countryName : countryNames()) {
            CompoundTag country = new CompoundTag();
            writeIntMap(country, STOCKPILE, stockpiles.get(countryName));
            writeIntMap(country, DEBT, debts.get(countryName));
            writeDoubleMap(country, REMAINDER, remainders.get(countryName));
            countries.put(countryName, country);
        }
        tag.put(COUNTRIES, countries);
        tag.putLong(LAST_CYCLE, lastProcessedCycle);
        return tag;
    }

    private static void writeIntMap(CompoundTag country, String key, Map<String, Integer> values) {
        CompoundTag tag = new CompoundTag();
        if (values != null) {
            for (Map.Entry<String, Integer> entry : values.entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0) {
                    tag.putInt(entry.getKey(), entry.getValue());
                }
            }
        }
        country.put(key, tag);
    }

    private static void writeDoubleMap(CompoundTag country, String key, Map<String, Double> values) {
        CompoundTag tag = new CompoundTag();
        if (values != null) {
            for (Map.Entry<String, Double> entry : values.entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 1.0E-9D) {
                    tag.putDouble(entry.getKey(), entry.getValue());
                }
            }
        }
        country.put(key, tag);
    }

    private Set<String> countryNames() {
        Set<String> result = new HashSet<>(stockpiles.keySet());
        result.addAll(debts.keySet());
        result.addAll(remainders.keySet());
        return result;
    }


    public long lastProcessedCycle() {
        return lastProcessedCycle;
    }

    public void setLastProcessedCycle(long cycle) {
        lastProcessedCycle = cycle;
        setDirty();
    }

    public boolean hasCountry(String countryName) {
        return stockpiles.containsKey(countryName)
            || debts.containsKey(countryName)
            || remainders.containsKey(countryName);
    }

    public void initializeCountry(String countryName) {
        stockpiles.computeIfAbsent(countryName, ignored -> new HashMap<>());
        debts.computeIfAbsent(countryName, ignored -> new HashMap<>());
        remainders.computeIfAbsent(countryName, ignored -> new HashMap<>());
    }

    public int getStockpile(String countryName, String itemId) {
        return stockpiles.getOrDefault(countryName, Map.of()).getOrDefault(itemId, 0);
    }

    public void setStockpile(String countryName, String itemId, int amount) {
        initializeCountry(countryName);
        if (amount <= 0) {
            stockpiles.get(countryName).remove(itemId);
        } else {
            stockpiles.get(countryName).put(itemId, amount);
        }
        setDirty();
    }

    public void addStockpile(String countryName, String itemId, int amount) {
        if (amount <= 0) {
            return;
        }
        setStockpile(countryName, itemId, getStockpile(countryName, itemId) + amount);
    }

    public Map<String, Integer> getStockpile(String countryName) {
        return Map.copyOf(stockpiles.getOrDefault(countryName, Map.of()));
    }

    public int getDebt(String countryName, String choiceKey) {
        return debts.getOrDefault(countryName, Map.of()).getOrDefault(choiceKey, 0);
    }

    public void setDebt(String countryName, String choiceKey, int amount) {
        initializeCountry(countryName);
        if (amount <= 0) {
            debts.get(countryName).remove(choiceKey);
        } else {
            debts.get(countryName).put(choiceKey, amount);
        }
        setDirty();
    }

    public double getRemainder(String countryName, String choiceKey) {
        return remainders.getOrDefault(countryName, Map.of()).getOrDefault(choiceKey, 0.0D);
    }

    public void setRemainder(String countryName, String choiceKey, double value) {
        initializeCountry(countryName);
        if (value <= 1.0E-9D) {
            remainders.get(countryName).remove(choiceKey);
        } else {
            remainders.get(countryName).put(choiceKey, value);
        }
        setDirty();
    }

    /**
     * Consumes whole item units from any concrete item accepted by the recipe choice.
     * The caller is responsible for ensuring the due amount is non-negative.
     */
    public int consumeAccepted(String countryName, java.util.List<String> acceptedItemIds, int due) {
        if (due <= 0 || acceptedItemIds.isEmpty()) {
            return 0;
        }

        int remaining = due;
        Map<String, Integer> stock = stockpiles.getOrDefault(countryName, Map.of());
        for (String itemId : acceptedItemIds) {
            if (remaining <= 0) {
                break;
            }
            int available = stock.getOrDefault(itemId, 0);
            int used = Math.min(available, remaining);
            if (used <= 0) {
                continue;
            }
            setStockpile(countryName, itemId, available - used);
            remaining -= used;
        }
        return due - remaining;
    }

    public boolean hasAnyDebt(String countryName) {
        return debts.getOrDefault(countryName, Map.of()).values().stream().anyMatch(value -> value > 0);
    }

    public int totalDebt(String countryName) {
        long total = 0;
        for (int value : debts.getOrDefault(countryName, Map.of()).values()) {
            total += Math.max(0, value);
            if (total >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) total;
    }

    public Map<String, Integer> getDebts(String countryName) {
        return Map.copyOf(debts.getOrDefault(countryName, Map.of()));
    }
}
