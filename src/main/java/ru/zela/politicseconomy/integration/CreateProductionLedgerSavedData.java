package ru.zela.politicseconomy.integration;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** Persistent fractional remainders for Create production modifiers. */
public final class CreateProductionLedgerSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_create_production_ledger";
    private static final String VALUES_TAG = "values";

    private final Map<String, Double> remainders = new HashMap<>();

    public static CreateProductionLedgerSavedData create() {
        return new CreateProductionLedgerSavedData();
    }

    public static CreateProductionLedgerSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CreateProductionLedgerSavedData data = create();
        if (tag.contains(VALUES_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag values = tag.getCompound(VALUES_TAG);
            for (String key : values.getAllKeys()) {
                data.remainders.put(key, values.getDouble(key));
            }
        }
        return data;
    }

    public static CreateProductionLedgerSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                CreateProductionLedgerSavedData::create,
                CreateProductionLedgerSavedData::load,
                null
            ),
            DATA_NAME
        );
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag values = new CompoundTag();
        for (Map.Entry<String, Double> entry : remainders.entrySet()) {
            values.putDouble(entry.getKey(), entry.getValue());
        }
        tag.put(VALUES_TAG, values);
        return tag;
    }

    public double getRemainder(String key) {
        return remainders.getOrDefault(key, 0.0D);
    }

    public void setRemainder(String key, double value) {
        if (value <= 1.0E-9D) {
            remainders.remove(key);
        } else {
            remainders.put(key, value);
        }
        setDirty();
    }
}
