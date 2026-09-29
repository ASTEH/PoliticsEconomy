package ru.zela.politicseconomy.country;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class CountryWorkforceSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_country_workforce";
    private static final String COUNTRIES = "countries";
    private static final String REMAINDERS = "dividend_remainders";

    private final Map<String, EnumMap<WorkforceSector, Integer>> allocations = new HashMap<>();
    private final Map<String, Map<String, Double>> dividendRemainders = new HashMap<>();

    public static CountryWorkforceSavedData create() {
        return new CountryWorkforceSavedData();
    }

    public static CountryWorkforceSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CountryWorkforceSavedData data = create();

        if (tag.contains(COUNTRIES, Tag.TAG_COMPOUND)) {
            CompoundTag countries = tag.getCompound(COUNTRIES);
            for (String countryName : countries.getAllKeys()) {
                CompoundTag country = countries.getCompound(countryName);
                EnumMap<WorkforceSector, Integer> allocation = new EnumMap<>(WorkforceSector.class);
                for (WorkforceSector sector : WorkforceSector.values()) {
                    allocation.put(sector, Math.max(0, country.getInt(sector.commandName())));
                }
                data.allocations.put(countryName, normalize(allocation));
            }
        }

        if (tag.contains(REMAINDERS, Tag.TAG_COMPOUND)) {
            CompoundTag allRemainders = tag.getCompound(REMAINDERS);
            for (String countryName : allRemainders.getAllKeys()) {
                CompoundTag country = allRemainders.getCompound(countryName);
                Map<String, Double> values = new HashMap<>();
                for (String itemId : country.getAllKeys()) {
                    values.put(itemId, Math.max(0.0D, country.getDouble(itemId)));
                }
                if (!values.isEmpty()) {
                    data.dividendRemainders.put(countryName, values);
                }
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag countries = new CompoundTag();
        for (Map.Entry<String, EnumMap<WorkforceSector, Integer>> entry : allocations.entrySet()) {
            CompoundTag country = new CompoundTag();
            for (WorkforceSector sector : WorkforceSector.values()) {
                country.putInt(sector.commandName(), entry.getValue().getOrDefault(sector, 0));
            }
            countries.put(entry.getKey(), country);
        }
        tag.put(COUNTRIES, countries);

        CompoundTag allRemainders = new CompoundTag();
        for (Map.Entry<String, Map<String, Double>> entry : dividendRemainders.entrySet()) {
            CompoundTag country = new CompoundTag();
            for (Map.Entry<String, Double> remainder : entry.getValue().entrySet()) {
                country.putDouble(remainder.getKey(), Math.max(0.0D, remainder.getValue()));
            }
            if (!country.isEmpty()) {
                allRemainders.put(entry.getKey(), country);
            }
        }
        tag.put(REMAINDERS, allRemainders);

        return tag;
    }

    public EnumMap<WorkforceSector, Integer> getOrCreate(
        String countryName,
        CountryDirection direction
    ) {
        EnumMap<WorkforceSector, Integer> existing = allocations.get(countryName);
        if (existing != null) return copy(existing);

        EnumMap<WorkforceSector, Integer> defaults = CountryWorkforceService.defaultAllocation(direction);
        allocations.put(countryName, normalize(defaults));
        setDirty();
        return copy(defaults);
    }

    public void setAllocation(String countryName, EnumMap<WorkforceSector, Integer> allocation) {
        allocations.put(countryName, normalize(allocation));
        setDirty();
    }

    public double getDividendRemainder(String countryName, String itemId) {
        return dividendRemainders
            .getOrDefault(countryName, Map.of())
            .getOrDefault(itemId, 0.0D);
    }

    public void setDividendRemainder(String countryName, String itemId, double value) {
        dividendRemainders
            .computeIfAbsent(countryName, ignored -> new HashMap<>())
            .put(itemId, Math.max(0.0D, Math.min(0.999999999D, value)));
        setDirty();
    }

    public int getShare(String countryName, CountryDirection direction, WorkforceSector sector) {
        return getOrCreate(countryName, direction).getOrDefault(sector, 0);
    }

    private static EnumMap<WorkforceSector, Integer> copy(
        Map<WorkforceSector, Integer> source
    ) {
        EnumMap<WorkforceSector, Integer> result = new EnumMap<>(WorkforceSector.class);
        result.putAll(source);
        return result;
    }

    static EnumMap<WorkforceSector, Integer> normalize(Map<WorkforceSector, Integer> source) {
        EnumMap<WorkforceSector, Integer> result = new EnumMap<>(WorkforceSector.class);
        int sum = 0;
        for (WorkforceSector sector : WorkforceSector.values()) {
            int value = Math.max(0, Math.min(100, source.getOrDefault(sector, 0)));
            result.put(sector, value);
            sum += value;
        }

        if (sum == 100) return result;

        if (sum <= 0) {
            int base = 100 / WorkforceSector.values().length;
            int remainder = 100 - base * WorkforceSector.values().length;
            for (WorkforceSector sector : WorkforceSector.values()) {
                int value = base;
                if (remainder > 0) {
                    value++;
                    remainder--;
                }
                result.put(sector, value);
            }
            return result;
        }

        double scale = 100.0D / sum;
        EnumMap<WorkforceSector, Double> fractional = new EnumMap<>(WorkforceSector.class);
        int roundedSum = 0;
        for (WorkforceSector sector : WorkforceSector.values()) {
            double raw = result.get(sector) * scale;
            int floor = (int) Math.floor(raw);
            result.put(sector, floor);
            fractional.put(sector, raw - floor);
            roundedSum += floor;
        }

        int left = 100 - roundedSum;
        while (left > 0) {
            WorkforceSector best = WorkforceSector.AGRICULTURE;
            double bestFraction = -1.0D;
            for (WorkforceSector sector : WorkforceSector.values()) {
                double fraction = fractional.getOrDefault(sector, 0.0D);
                if (fraction > bestFraction) {
                    bestFraction = fraction;
                    best = sector;
                }
            }
            result.put(best, result.get(best) + 1);
            fractional.put(best, -1.0D);
            left--;
        }

        return result;
    }
}
