package ru.zela.politicseconomy.country;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class CountryPoliticalSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_country_politics";
    private static final String COUNTRIES = "countries";
    private final Map<String, EnumMap<GovernmentType, Integer>> support = new HashMap<>();
    private final Map<String, Integer> unrest = new HashMap<>();
    private final Map<String, String> demands = new HashMap<>();
    private final Map<String, Long> reformLocks = new HashMap<>();

    public static CountryPoliticalSavedData create() { return new CountryPoliticalSavedData(); }

    public static CountryPoliticalSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CountryPoliticalSavedData data = create();
        if (!tag.contains(COUNTRIES, Tag.TAG_LIST)) return data;
        ListTag list = tag.getList(COUNTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            String name = c.getString("name");
            if (name.isBlank()) continue;
            EnumMap<GovernmentType, Integer> values = new EnumMap<>(GovernmentType.class);
            for (GovernmentType type : GovernmentType.values()) values.put(type, Math.max(0, Math.min(100, c.getInt("support_" + type.commandName()))));
            data.support.put(name, normalize(values));
            data.unrest.put(name, Math.max(0, Math.min(100, c.getInt("unrest"))));
            if (c.contains("demand")) data.demands.put(name, c.getString("demand"));
            data.reformLocks.put(name, Math.max(0L, c.getLong("reform_lock")));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (String name : countryNames()) {
            CompoundTag c = new CompoundTag(); c.putString("name", name);
            EnumMap<GovernmentType, Integer> values = support.get(name);
            if (values != null) for (GovernmentType type : GovernmentType.values()) c.putInt("support_" + type.commandName(), values.getOrDefault(type, 0));
            c.putInt("unrest", getUnrest(name));
            String demand = demands.get(name); if (demand != null && !demand.isBlank()) c.putString("demand", demand);
            c.putLong("reform_lock", reformLocks.getOrDefault(name, 0L));
            list.add(c);
        }
        tag.put(COUNTRIES, list); return tag;
    }

    public boolean hasSupport(String name) { return support.containsKey(name); }
    public EnumMap<GovernmentType, Integer> getSupport(String name) {
        EnumMap<GovernmentType, Integer> source = support.get(name), out = new EnumMap<>(GovernmentType.class);
        for (GovernmentType type : GovernmentType.values()) out.put(type, source == null ? 0 : source.getOrDefault(type, 0));
        return out;
    }
    public void setSupport(String name, Map<GovernmentType, Integer> values) { support.put(name, normalize(values)); setDirty(); }
    public int getSupport(String name, GovernmentType type) { return getSupport(name).getOrDefault(type, 0); }
    public int getUnrest(String name) { return Math.max(0, Math.min(100, unrest.getOrDefault(name, 0))); }
    public void setUnrest(String name, int value) { unrest.put(name, Math.max(0, Math.min(100, value))); setDirty(); }
    public String getDemand(String name) { return demands.getOrDefault(name, ""); }
    public void setDemand(String name, String value) { if (value == null || value.isBlank()) demands.remove(name); else demands.put(name, value); setDirty(); }
    public long getReformLock(String name) { return reformLocks.getOrDefault(name, 0L); }
    public void setReformLock(String name, long value) { reformLocks.put(name, Math.max(0L, value)); setDirty(); }
    private java.util.Set<String> countryNames() { java.util.Set<String> s = new java.util.HashSet<>(support.keySet()); s.addAll(unrest.keySet()); s.addAll(demands.keySet()); s.addAll(reformLocks.keySet()); return s; }

    private static EnumMap<GovernmentType, Integer> normalize(Map<GovernmentType, Integer> values) {
        EnumMap<GovernmentType, Integer> out = new EnumMap<>(GovernmentType.class); int sum = 0;
        for (GovernmentType t : GovernmentType.values()) { int v = Math.max(0, Math.min(100, values.getOrDefault(t, 0))); out.put(t, v); sum += v; }
        if (sum <= 0) { int base = 100 / GovernmentType.values().length, extra = 100 - base * GovernmentType.values().length; for (GovernmentType t : GovernmentType.values()) out.put(t, base + (extra-- > 0 ? 1 : 0)); return out; }
        if (sum == 100) return out;
        Map<GovernmentType, Double> frac = new EnumMap<>(GovernmentType.class); int rounded = 0;
        for (GovernmentType t : GovernmentType.values()) { double raw = out.get(t) * 100.0 / sum; int floor = (int)Math.floor(raw); out.put(t, floor); frac.put(t, raw - floor); rounded += floor; }
        for (int left = 100 - rounded; left > 0; left--) { GovernmentType best = GovernmentType.DEMOCRACY; for (GovernmentType t : GovernmentType.values()) if (frac.get(t) > frac.get(best)) best = t; out.put(best, out.get(best) + 1); frac.put(best, -1.0); }
        return out;
    }
}
