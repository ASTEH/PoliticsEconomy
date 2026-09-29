package ru.zela.politicseconomy.economy;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent state for the player-facing internal market. */
public final class PopulationMarketSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_population_market";

    private static final String LAST_CYCLE = "last_cycle";
    private static final String LAST_IMPORT_CYCLE = "last_import_cycle";
    private static final String PLAYERS = "players";
    private static final String COUNTRIES = "countries";

    private long lastCycle = Long.MIN_VALUE;
    private long lastImportCycle = Long.MIN_VALUE;

    private final Map<UUID, Long> wallets = new HashMap<>();
    private final Map<String, Map<String, Integer>> baseDemand = new HashMap<>();
    private final Map<String, Map<String, Integer>> remainingDemand = new HashMap<>();
    private final Map<String, Map<String, Integer>> sold = new HashMap<>();
    private final Map<String, Map<String, Integer>> imported = new HashMap<>();

    public static PopulationMarketSavedData create() {
        return new PopulationMarketSavedData();
    }

    public static PopulationMarketSavedData load(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        PopulationMarketSavedData data = create();
        data.lastCycle = tag.getLong(LAST_CYCLE);
        data.lastImportCycle = tag.getLong(LAST_IMPORT_CYCLE);

        if (tag.contains(PLAYERS, Tag.TAG_COMPOUND)) {
            CompoundTag players = tag.getCompound(PLAYERS);
            for (String key : players.getAllKeys()) {
                try {
                    data.wallets.put(UUID.fromString(key), Math.max(0L, players.getLong(key)));
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed UUIDs.
                }
            }
        }

        if (tag.contains(COUNTRIES, Tag.TAG_COMPOUND)) {
            CompoundTag countries = tag.getCompound(COUNTRIES);
            for (String countryName : countries.getAllKeys()) {
                CompoundTag country = countries.getCompound(countryName);
                data.baseDemand.put(countryName, readIntMap(country, "base"));
                data.remainingDemand.put(countryName, readIntMap(country, "remaining"));
                data.sold.put(countryName, readIntMap(country, "sold"));
                data.imported.put(countryName, readIntMap(country, "imported"));
            }
        }

        return data;
    }

    private static Map<String, Integer> readIntMap(CompoundTag parent, String key) {
        Map<String, Integer> result = new HashMap<>();
        if (!parent.contains(key, Tag.TAG_COMPOUND)) return result;
        CompoundTag values = parent.getCompound(key);
        for (String itemId : values.getAllKeys()) {
            result.put(itemId, Math.max(0, values.getInt(itemId)));
        }
        return result;
    }

    @Override
    public CompoundTag save(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        tag.putLong(LAST_CYCLE, lastCycle);
        tag.putLong(LAST_IMPORT_CYCLE, lastImportCycle);

        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, Long> entry : wallets.entrySet()) {
            players.putLong(entry.getKey().toString(), Math.max(0L, entry.getValue()));
        }
        tag.put(PLAYERS, players);

        CompoundTag countries = new CompoundTag();
        for (String countryName : countryNames()) {
            CompoundTag country = new CompoundTag();
            writeIntMap(country, "base", baseDemand.get(countryName));
            writeIntMap(country, "remaining", remainingDemand.get(countryName));
            writeIntMap(country, "sold", sold.get(countryName));
            writeIntMap(country, "imported", imported.get(countryName));
            countries.put(countryName, country);
        }
        tag.put(COUNTRIES, countries);

        return tag;
    }

    private static void writeIntMap(
        CompoundTag parent,
        String key,
        Map<String, Integer> values
    ) {
        CompoundTag result = new CompoundTag();
        if (values != null) {
            for (Map.Entry<String, Integer> entry : values.entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0) {
                    result.putInt(entry.getKey(), entry.getValue());
                }
            }
        }
        parent.put(key, result);
    }

    private java.util.Set<String> countryNames() {
        java.util.Set<String> result = new java.util.HashSet<>(baseDemand.keySet());
        result.addAll(remainingDemand.keySet());
        result.addAll(sold.keySet());
        result.addAll(imported.keySet());
        return result;
    }

    public long lastCycle() {
        return lastCycle;
    }

    public void setLastCycle(long value) {
        if (lastCycle != value) {
            lastCycle = value;
            setDirty();
        }
    }

    public long lastImportCycle() {
        return lastImportCycle;
    }

    public void setLastImportCycle(long value) {
        if (lastImportCycle != value) {
            lastImportCycle = value;
            setDirty();
        }
    }

    public long wallet(UUID playerId) {
        return Math.max(0L, wallets.getOrDefault(playerId, 0L));
    }

    public void addWallet(UUID playerId, long amount) {
        if (amount <= 0) return;
        wallets.put(playerId, Math.max(0L, wallet(playerId) + amount));
        setDirty();
    }

    public Map<String, Integer> baseDemand(String countryName) {
        return Map.copyOf(baseDemand.getOrDefault(countryName, Map.of()));
    }

    public int baseDemand(String countryName, String itemId) {
        return baseDemand.getOrDefault(countryName, Map.of()).getOrDefault(itemId, 0);
    }

    public int remainingDemand(String countryName, String itemId) {
        return remainingDemand.getOrDefault(countryName, Map.of()).getOrDefault(itemId, 0);
    }

    public void setRemainingDemand(String countryName, String itemId, int amount) {
        remainingDemand
            .computeIfAbsent(countryName, ignored -> new HashMap<>())
            .put(itemId, Math.max(0, amount));
        setDirty();
    }

    public void setCycleDemand(String countryName, Map<String, Integer> demand) {
        baseDemand.put(countryName, new HashMap<>(demand));
        remainingDemand.put(countryName, new HashMap<>(demand));
        sold.put(countryName, new HashMap<>());
        imported.put(countryName, new HashMap<>());
        setDirty();
    }

    public void addSold(String countryName, String itemId, int amount) {
        if (amount <= 0) return;
        sold.computeIfAbsent(countryName, ignored -> new HashMap<>())
            .merge(itemId, amount, Integer::sum);
        setDirty();
    }

    public void addImported(String countryName, String itemId, int amount) {
        if (amount <= 0) return;
        imported.computeIfAbsent(countryName, ignored -> new HashMap<>())
            .merge(itemId, amount, Integer::sum);
        setDirty();
    }

    public int sold(String countryName, String itemId) {
        return sold.getOrDefault(countryName, Map.of()).getOrDefault(itemId, 0);
    }

    public int imported(String countryName, String itemId) {
        return imported.getOrDefault(countryName, Map.of()).getOrDefault(itemId, 0);
    }
}
