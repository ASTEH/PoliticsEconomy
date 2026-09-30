package ru.zela.politicseconomy.country;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CountryResidentSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_country_residents";
    private static final String COUNTRIES = "countries";
    private static final String UUID_KEY = "uuid";
    private static final String ROLE_KEY = "role";

    private final Map<String, Map<UUID, String>> residentsByCountry = new HashMap<>();

    public static CountryResidentSavedData create() {
        return new CountryResidentSavedData();
    }

    public static CountryResidentSavedData load(
        CompoundTag tag,
        net.minecraft.core.HolderLookup.Provider registries
    ) {
        CountryResidentSavedData data = create();
        if (!tag.contains(COUNTRIES, Tag.TAG_COMPOUND)) return data;

        CompoundTag countries = tag.getCompound(COUNTRIES);
        for (String countryName : countries.getAllKeys()) {
            ListTag list = countries.getList(countryName, Tag.TAG_COMPOUND);
            Map<UUID, String> residents = new HashMap<>();

            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                String rawUuid = entry.getString(UUID_KEY);
                if (rawUuid.isBlank()) continue;

                try {
                    UUID uuid = UUID.fromString(rawUuid);
                    String role = entry.getString(ROLE_KEY);
                    residents.put(uuid, role.isBlank() ? "CITIZEN" : role);
                } catch (IllegalArgumentException ignored) {
                }
            }

            if (!residents.isEmpty()) {
                data.residentsByCountry.put(countryName, residents);
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(
        CompoundTag tag,
        net.minecraft.core.HolderLookup.Provider registries
    ) {
        CompoundTag countries = new CompoundTag();

        for (Map.Entry<String, Map<UUID, String>> countryEntry : residentsByCountry.entrySet()) {
            ListTag list = new ListTag();

            for (Map.Entry<UUID, String> resident : countryEntry.getValue().entrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putString(UUID_KEY, resident.getKey().toString());
                entry.putString(ROLE_KEY, resident.getValue());
                list.add(entry);
            }

            countries.put(countryEntry.getKey(), list);
        }

        tag.put(COUNTRIES, countries);
        return tag;
    }

    public Map<UUID, String> snapshot(String countryName) {
        Map<UUID, String> residents = residentsByCountry.get(countryName);
        if (residents == null || residents.isEmpty()) return Map.of();
        return Map.copyOf(residents);
    }

    public Set<UUID> residentIds(String countryName) {
        return new HashSet<>(residentsByCountry.getOrDefault(countryName, Map.of()).keySet());
    }

    public void addResident(String countryName, UUID uuid, String role) {
        if (countryName == null || countryName.isBlank() || uuid == null) return;

        residentsByCountry
            .computeIfAbsent(countryName, ignored -> new HashMap<>())
            .put(uuid, role == null || role.isBlank() ? "CITIZEN" : role);
        setDirty();
    }

    public void removeResident(String countryName, UUID uuid) {
        Map<UUID, String> residents = residentsByCountry.get(countryName);
        if (residents == null) return;

        residents.remove(uuid);
        if (residents.isEmpty()) residentsByCountry.remove(countryName);
        setDirty();
    }

    public void removeResidentEverywhere(UUID uuid) {
        if (uuid == null) return;

        boolean changed = false;
        for (Map<UUID, String> residents : residentsByCountry.values()) {
            changed |= residents.remove(uuid) != null;
        }

        residentsByCountry.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (changed) setDirty();
    }
}
