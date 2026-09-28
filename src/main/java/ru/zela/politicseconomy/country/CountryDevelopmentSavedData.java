package ru.zela.politicseconomy.country;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** Persistent economic-development points and levels for each country. */
public final class CountryDevelopmentSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_country_development";
    private static final String COUNTRIES = "countries";
    private static final String POINTS = "points";
    private static final String LEVEL = "level";

    private final Map<String, Integer> points = new HashMap<>();
    private final Map<String, Integer> levels = new HashMap<>();

    public static CountryDevelopmentSavedData create() {
        return new CountryDevelopmentSavedData();
    }

    public static CountryDevelopmentSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CountryDevelopmentSavedData data = create();
        if (!tag.contains(COUNTRIES, Tag.TAG_COMPOUND)) {
            return data;
        }

        CompoundTag countries = tag.getCompound(COUNTRIES);
        for (String countryName : countries.getAllKeys()) {
            CompoundTag country = countries.getCompound(countryName);
            data.points.put(countryName, Math.max(0, country.getInt(POINTS)));
            data.levels.put(countryName, Math.max(1, Math.min(5, country.getInt(LEVEL))));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag countries = new CompoundTag();
        for (String countryName : countryNames()) {
            CompoundTag country = new CompoundTag();
            country.putInt(POINTS, getPoints(countryName));
            country.putInt(LEVEL, getLevel(countryName));
            countries.put(countryName, country);
        }
        tag.put(COUNTRIES, countries);
        return tag;
    }

    private java.util.Set<String> countryNames() {
        java.util.Set<String> result = new java.util.HashSet<>(points.keySet());
        result.addAll(levels.keySet());
        return result;
    }

    public int getPoints(String countryName) {
        return Math.max(0, points.getOrDefault(countryName, 0));
    }

    public void setPoints(String countryName, int value) {
        points.put(countryName, Math.max(0, value));
        setDirty();
    }

    public int addPoints(String countryName, int amount) {
        int before = getPoints(countryName);
        int after = Math.min(Integer.MAX_VALUE, before + Math.max(0, amount));
        points.put(countryName, after);
        setDirty();
        return after;
    }

    public int getLevel(String countryName) {
        return Math.max(1, Math.min(5, levels.getOrDefault(countryName, 1)));
    }

    public void setLevel(String countryName, int value) {
        levels.put(countryName, Math.max(1, Math.min(5, value)));
        setDirty();
    }
}
