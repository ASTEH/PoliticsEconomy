package ru.zela.politicseconomy.infrastructure;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent accounting state for infrastructure maintenance.
 *
 * <p>Because PoliticsMod stores balances as integer dollars while our
 * maintenance calculation can be fractional, we keep the fractional amount
 * as a pending value and only charge whole dollars from the treasury.</p>
 */
public final class MaintenanceLedgerSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_maintenance_ledger";

    private static final String DEBT_TAG = "debt";
    private static final String PENDING_TAG = "pending";
    private static final String LAST_CYCLE_TAG = "lastCycle";
    private static final String INITIALIZED_TAG = "initialized";

    private final Map<String, Double> debt = new HashMap<>();
    private final Map<String, Double> pending = new HashMap<>();
    private long lastProcessedCycle = -1L;
    private boolean initialized = false;

    public static MaintenanceLedgerSavedData create() {
        return new MaintenanceLedgerSavedData();
    }

    public static MaintenanceLedgerSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        MaintenanceLedgerSavedData data = create();

        if (tag.contains(DEBT_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag debtTag = tag.getCompound(DEBT_TAG);
            for (String country : debtTag.getAllKeys()) {
                data.debt.put(country, Math.max(0.0, debtTag.getDouble(country)));
            }
        }

        if (tag.contains(PENDING_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag pendingTag = tag.getCompound(PENDING_TAG);
            for (String country : pendingTag.getAllKeys()) {
                data.pending.put(country, Math.max(0.0, pendingTag.getDouble(country)));
            }
        }

        data.lastProcessedCycle = tag.contains(LAST_CYCLE_TAG, Tag.TAG_LONG)
            ? tag.getLong(LAST_CYCLE_TAG)
            : -1L;
        data.initialized = tag.getBoolean(INITIALIZED_TAG);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag debtTag = new CompoundTag();
        for (Map.Entry<String, Double> entry : debt.entrySet()) {
            if (entry.getValue() > 0.0) {
                debtTag.putDouble(entry.getKey(), entry.getValue());
            }
        }
        tag.put(DEBT_TAG, debtTag);

        CompoundTag pendingTag = new CompoundTag();
        for (Map.Entry<String, Double> entry : pending.entrySet()) {
            if (entry.getValue() > 0.0) {
                pendingTag.putDouble(entry.getKey(), entry.getValue());
            }
        }
        tag.put(PENDING_TAG, pendingTag);
        tag.putLong(LAST_CYCLE_TAG, lastProcessedCycle);
        tag.putBoolean(INITIALIZED_TAG, initialized);
        return tag;
    }

    public double getDebt(String countryName) {
        return debt.getOrDefault(countryName, 0.0);
    }

    public void setDebt(String countryName, double value) {
        if (value <= 0.0) {
            debt.remove(countryName);
        } else {
            debt.put(countryName, value);
        }
        setDirty();
    }

    public double getPending(String countryName) {
        return pending.getOrDefault(countryName, 0.0);
    }

    public void setPending(String countryName, double value) {
        if (value <= 0.0) {
            pending.remove(countryName);
        } else {
            pending.put(countryName, value);
        }
        setDirty();
    }

    public long lastProcessedCycle() {
        return lastProcessedCycle;
    }

    public void setLastProcessedCycle(long cycle) {
        this.lastProcessedCycle = cycle;
        setDirty();
    }

    public boolean initialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
        setDirty();
    }
}
