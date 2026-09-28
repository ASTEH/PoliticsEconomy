package ru.zela.politicseconomy.country;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CountryPolicySavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_country_policies";

    private static final String COUNTRIES = "countries";
    private static final String GOVERNMENT = "government";
    private static final String RELIGION = "religion";

    private final Map<String, GovernmentType> governments = new HashMap<>();
    private final Map<String, ReligionType> religions = new HashMap<>();

    public static CountryPolicySavedData create() {
        return new CountryPolicySavedData();
    }

    public static CountryPolicySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CountryPolicySavedData data = create();
        if (!tag.contains(COUNTRIES, Tag.TAG_COMPOUND)) {
            return data;
        }

        CompoundTag countries = tag.getCompound(COUNTRIES);
        for (String countryName : countries.getAllKeys()) {
            CompoundTag country = countries.getCompound(countryName);

            if (country.contains(GOVERNMENT, Tag.TAG_STRING)) {
                GovernmentType government = GovernmentType.fromCommandName(country.getString(GOVERNMENT));
                if (government != null) data.governments.put(countryName, government);
            }

            if (country.contains(RELIGION, Tag.TAG_STRING)) {
                ReligionType religion = ReligionType.fromCommandName(country.getString(RELIGION));
                if (religion != null) data.religions.put(countryName, religion);
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag countries = new CompoundTag();
        Set<String> names = new HashSet<>(governments.keySet());
        names.addAll(religions.keySet());

        for (String countryName : names) {
            CompoundTag country = new CompoundTag();
            GovernmentType government = governments.get(countryName);
            ReligionType religion = religions.get(countryName);

            if (government != null) country.putString(GOVERNMENT, government.commandName());
            if (religion != null) country.putString(RELIGION, religion.commandName());

            if (!country.isEmpty()) countries.put(countryName, country);
        }

        tag.put(COUNTRIES, countries);
        return tag;
    }

    public GovernmentType getGovernment(String countryName) {
        return governments.get(countryName);
    }

    public ReligionType getReligion(String countryName) {
        return religions.get(countryName);
    }

    public boolean hasGovernment(String countryName) {
        return governments.containsKey(countryName);
    }

    public boolean hasReligion(String countryName) {
        return religions.containsKey(countryName);
    }

    public void setGovernment(String countryName, GovernmentType government) {
        governments.put(countryName, government);
        setDirty();
    }

    public void setReligion(String countryName, ReligionType religion) {
        religions.put(countryName, religion);
        setDirty();
    }
}
