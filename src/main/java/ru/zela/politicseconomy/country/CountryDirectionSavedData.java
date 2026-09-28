package ru.zela.politicseconomy.country;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent server-wide storage for country economic directions.
 */
public final class CountryDirectionSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_country_directions";
    private static final String DIRECTIONS_TAG = "directions";

    private final Map<String, CountryDirection> directions = new HashMap<>();

    public static CountryDirectionSavedData create() {
        return new CountryDirectionSavedData();
    }

    public static CountryDirectionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CountryDirectionSavedData data = create();

        if (tag.contains(DIRECTIONS_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag directionsTag = tag.getCompound(DIRECTIONS_TAG);
            for (String countryName : directionsTag.getAllKeys()) {
                CountryDirection direction = CountryDirection.fromCommandName(
                    directionsTag.getString(countryName)
                );
                if (direction != null) {
                    data.directions.put(countryName, direction);
                }
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag directionsTag = new CompoundTag();
        for (Map.Entry<String, CountryDirection> entry : directions.entrySet()) {
            directionsTag.putString(entry.getKey(), entry.getValue().commandName());
        }
        tag.put(DIRECTIONS_TAG, directionsTag);
        return tag;
    }

    public CountryDirection get(String countryName) {
        return directions.get(countryName);
    }

    public boolean has(String countryName) {
        return directions.containsKey(countryName);
    }

    public void set(String countryName, CountryDirection direction) {
        directions.put(countryName, direction);
        setDirty();
    }

    public void remove(String countryName) {
        if (directions.remove(countryName) != null) {
            setDirty();
        }
    }
}
