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

/**
 * Persistent registry of physical villagers associated with Politics Economy.
 *
 * A resident may exist without housing, but only a villager with a valid HOME
 * memory pointing at a bed contributes to the country's population.
 */
public final class CountryResidentSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_country_residents";
    private static final String COUNTRIES = "countries";
    private static final String UUID_KEY = "uuid";
    private static final String ROLE_KEY = "role";
    private static final String BED_KEY = "bed";

    private final Map<String, Map<UUID, String>> residentsByCountry = new HashMap<>();
    private final Map<String, Map<UUID, Long>> bedByResidentByCountry = new HashMap<>();

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
            Map<UUID, Long> beds = new HashMap<>();

            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                String rawUuid = entry.getString(UUID_KEY);
                if (rawUuid.isBlank()) continue;

                try {
                    UUID uuid = UUID.fromString(rawUuid);
                    String role = entry.getString(ROLE_KEY);
                    residents.put(uuid, role.isBlank() ? "CITIZEN" : role);

                    if (entry.contains(BED_KEY, Tag.TAG_LONG)) {
                        beds.put(uuid, entry.getLong(BED_KEY));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }

            if (!residents.isEmpty()) {
                data.residentsByCountry.put(countryName, residents);
                if (!beds.isEmpty()) {
                    data.bedByResidentByCountry.put(countryName, beds);
                }
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
            Map<UUID, Long> beds =
                bedByResidentByCountry.getOrDefault(countryEntry.getKey(), Map.of());

            for (Map.Entry<UUID, String> resident : countryEntry.getValue().entrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putString(UUID_KEY, resident.getKey().toString());
                entry.putString(ROLE_KEY, resident.getValue());

                Long bed = beds.get(resident.getKey());
                if (bed != null) {
                    entry.putLong(BED_KEY, bed);
                }

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
        return new HashSet<>(
            residentsByCountry.getOrDefault(countryName, Map.of()).keySet()
        );
    }

    public Long getResidentBed(String countryName, UUID uuid) {
        Map<UUID, Long> beds = bedByResidentByCountry.get(countryName);
        return beds == null ? null : beds.get(uuid);
    }

    public void setResidentBed(String countryName, UUID uuid, Long bedPos) {
        if (countryName == null || countryName.isBlank() || uuid == null) return;

        Map<UUID, Long> beds = bedByResidentByCountry.computeIfAbsent(
            countryName,
            ignored -> new HashMap<>()
        );

        if (bedPos == null) {
            beds.remove(uuid);
            if (beds.isEmpty()) bedByResidentByCountry.remove(countryName);
        } else {
            beds.put(uuid, bedPos);
        }

        setDirty();
    }

    /**
     * Number of unique beds currently associated with resident HOME memories.
     * This is the actual country population used by the economy.
     */
    public int occupiedBedCount(String countryName) {
        Set<Long> occupied = new HashSet<>();
        for (UUID uuid : residentIds(countryName)) {
            Long bed = getResidentBed(countryName, uuid);
            if (bed != null) occupied.add(bed);
        }
        return occupied.size();
    }

    public int occupiedBedCountInChunk(String countryName, long chunkPos) {
        Set<Long> occupied = new HashSet<>();
        for (UUID uuid : residentIds(countryName)) {
            Long bed = getResidentBed(countryName, uuid);
            if (bed != null
                && new net.minecraft.world.level.ChunkPos(
                    net.minecraft.core.BlockPos.of(bed)
                ).toLong() == chunkPos) {
                occupied.add(bed);
            }
        }
        return occupied.size();
    }

    public Set<Long> occupiedBedPositions(String countryName) {
        Set<Long> occupied = new HashSet<>();
        for (UUID uuid : residentIds(countryName)) {
            Long bed = getResidentBed(countryName, uuid);
            if (bed != null) occupied.add(bed);
        }
        return occupied;
    }

    public String findCountry(UUID uuid) {
        if (uuid == null) return null;

        for (Map.Entry<String, Map<UUID, String>> entry : residentsByCountry.entrySet()) {
            if (entry.getValue().containsKey(uuid)) {
                return entry.getKey();
            }
        }

        return null;
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

        Map<UUID, Long> beds = bedByResidentByCountry.get(countryName);
        if (beds != null) {
            beds.remove(uuid);
            if (beds.isEmpty()) bedByResidentByCountry.remove(countryName);
        }

        if (residents.isEmpty()) {
            residentsByCountry.remove(countryName);
        }
        setDirty();
    }

    public void moveResident(UUID uuid, String fromCountry, String toCountry, String role) {
        if (uuid == null || toCountry == null || toCountry.isBlank()) return;

        Long bed = fromCountry == null ? null : getResidentBed(fromCountry, uuid);

        if (fromCountry != null && !fromCountry.equals(toCountry)) {
            removeResident(fromCountry, uuid);
        }

        addResident(toCountry, uuid, role);

        if (bed != null && fromCountry != null && !fromCountry.equals(toCountry)) {
            setResidentBed(toCountry, uuid, bed);
        }
    }

    public void removeResidentEverywhere(UUID uuid) {
        if (uuid == null) return;

        boolean changed = false;

        for (String countryName : new HashSet<>(residentsByCountry.keySet())) {
            Map<UUID, String> residents = residentsByCountry.get(countryName);
            if (residents == null) continue;

            if (residents.remove(uuid) != null) {
                changed = true;
            }

            Map<UUID, Long> beds = bedByResidentByCountry.get(countryName);
            if (beds != null) {
                changed |= beds.remove(uuid) != null;
                if (beds.isEmpty()) {
                    bedByResidentByCountry.remove(countryName);
                }
            }

            if (residents.isEmpty()) {
                residentsByCountry.remove(countryName);
            }
        }

        if (changed) setDirty();
    }
}
