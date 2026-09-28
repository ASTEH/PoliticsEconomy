package ru.zela.politicseconomy.integration;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persists the material dependencies known for Create/Create-addon enterprises.
 * A dependency is a canonical material-choice key such as "minecraft:iron_ingot"
 * or "minecraft:oak_planks|minecraft:spruce_planks".
 */
public final class EnterpriseMaterialLedgerSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_enterprise_materials";
    private static final String ENTERPRISES = "enterprises";
    private static final String DEPENDENCIES = "dependencies";

    private final Map<Long, Set<String>> dependenciesByPosition = new HashMap<>();

    public static EnterpriseMaterialLedgerSavedData create() {
        return new EnterpriseMaterialLedgerSavedData();
    }

    public static EnterpriseMaterialLedgerSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        EnterpriseMaterialLedgerSavedData data = create();
        if (!tag.contains(ENTERPRISES, Tag.TAG_COMPOUND)) {
            return data;
        }

        CompoundTag enterprises = tag.getCompound(ENTERPRISES);
        for (String posKey : enterprises.getAllKeys()) {
            long pos;
            try {
                pos = Long.parseLong(posKey);
            } catch (NumberFormatException ignored) {
                continue;
            }

            ListTag list = enterprises.getList(DEPENDENCIES, Tag.TAG_STRING);
            Set<String> dependencies = new HashSet<>();
            for (int i = 0; i < list.size(); i++) {
                String value = list.getString(i);
                if (value != null && !value.isBlank()) {
                    dependencies.add(value);
                }
            }
            if (!dependencies.isEmpty()) {
                data.dependenciesByPosition.put(pos, dependencies);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag enterprises = new CompoundTag();
        for (Map.Entry<Long, Set<String>> entry : dependenciesByPosition.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            ListTag list = new ListTag();
            for (String dependency : entry.getValue()) {
                list.add(StringTag.valueOf(dependency));
            }
            CompoundTag enterprise = new CompoundTag();
            enterprise.put(DEPENDENCIES, list);
            enterprises.put(Long.toString(entry.getKey()), enterprise);
        }
        tag.put(ENTERPRISES, enterprises);
        return tag;
    }

    public Set<String> getDependencies(long pos) {
        return Set.copyOf(dependenciesByPosition.getOrDefault(pos, Set.of()));
    }

    public void setDependencies(long pos, Set<String> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            dependenciesByPosition.remove(pos);
        } else {
            dependenciesByPosition.put(pos, new HashSet<>(dependencies));
        }
        setDirty();
    }

    public void remove(long pos) {
        if (dependenciesByPosition.remove(pos) != null) {
            setDirty();
        }
    }

    public boolean hasDependencies(long pos) {
        return !dependenciesByPosition.getOrDefault(pos, Set.of()).isEmpty();
    }
}
