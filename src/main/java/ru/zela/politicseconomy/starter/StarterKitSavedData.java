package ru.zela.politicseconomy.starter;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Persists the players who have already received the starter kit. */
public final class StarterKitSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_starter_kit";
    private static final String PLAYERS = "players";

    private final Set<UUID> players = new HashSet<>();

    public static StarterKitSavedData create() {
        return new StarterKitSavedData();
    }

    public static StarterKitSavedData load(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        StarterKitSavedData data = create();

        if (tag.contains(PLAYERS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(PLAYERS, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                try {
                    data.players.add(UUID.fromString(list.getString(i)));
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed player UUIDs.
                }
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        ListTag list = new ListTag();
        for (UUID uuid : players) {
            list.add(net.minecraft.nbt.StringTag.valueOf(uuid.toString()));
        }
        tag.put(PLAYERS, list);
        return tag;
    }

    public boolean hasReceived(UUID playerId) {
        return players.contains(playerId);
    }

    public void markReceived(UUID playerId) {
        if (players.add(playerId)) {
            setDirty();
        }
    }
}
