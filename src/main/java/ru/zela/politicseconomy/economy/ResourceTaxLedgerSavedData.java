package ru.zela.politicseconomy.economy;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent per-player/item rounding remainder for extraction losses. */
public final class ResourceTaxLedgerSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_resource_tax_ledger";
    private static final String PLAYERS_TAG = "players";

    private final Map<UUID, Map<String, Double>> remainders = new HashMap<>();

    public static ResourceTaxLedgerSavedData create() {
        return new ResourceTaxLedgerSavedData();
    }

    public static ResourceTaxLedgerSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ResourceTaxLedgerSavedData data = create();
        if (!tag.contains(PLAYERS_TAG, Tag.TAG_COMPOUND)) {
            return data;
        }
        CompoundTag players = tag.getCompound(PLAYERS_TAG);
        for (String uuidKey : players.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(uuidKey);
                CompoundTag items = players.getCompound(uuidKey);
                Map<String, Double> map = new HashMap<>();
                for (String itemId : items.getAllKeys()) {
                    map.put(itemId, items.getDouble(itemId));
                }
                if (!map.isEmpty()) {
                    data.remainders.put(uuid, map);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed persisted UUIDs.
            }
        }
        return data;
    }

    public static ResourceTaxLedgerSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                ResourceTaxLedgerSavedData::create,
                ResourceTaxLedgerSavedData::load,
                null
            ),
            DATA_NAME
        );
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, Map<String, Double>> playerEntry : remainders.entrySet()) {
            CompoundTag items = new CompoundTag();
            for (Map.Entry<String, Double> itemEntry : playerEntry.getValue().entrySet()) {
                if (Math.abs(itemEntry.getValue()) > 1.0E-9) {
                    items.putDouble(itemEntry.getKey(), itemEntry.getValue());
                }
            }
            if (!items.isEmpty()) {
                players.put(playerEntry.getKey().toString(), items);
            }
        }
        tag.put(PLAYERS_TAG, players);
        return tag;
    }

    public double getRemainder(UUID playerId, String itemId) {
        return remainders.getOrDefault(playerId, Map.of()).getOrDefault(itemId, 0.0D);
    }

    public double getRemainder(UUID playerId, ResourceExtractionCategory category, String itemId) {
        String key = key(category, itemId);
        return remainders.getOrDefault(playerId, Map.of()).getOrDefault(key, 0.0D);
    }

    public void setRemainder(UUID playerId, String itemId, double value) {
        setRemainderInternal(playerId, itemId, value);
    }

    public void setRemainder(UUID playerId, ResourceExtractionCategory category, String itemId, double value) {
        setRemainderInternal(playerId, key(category, itemId), value);
    }

    private void setRemainderInternal(UUID playerId, String key, double value) {
        if (Math.abs(value) < 1.0E-9) {
            Map<String, Double> items = remainders.get(playerId);
            if (items != null) {
                items.remove(key);
                if (items.isEmpty()) {
                    remainders.remove(playerId);
                }
            }
        } else {
            remainders.computeIfAbsent(playerId, ignored -> new HashMap<>()).put(key, value);
        }
        setDirty();
    }

    private static String key(ResourceExtractionCategory category, String itemId) {
        return category.name() + "|" + itemId;
    }
}
