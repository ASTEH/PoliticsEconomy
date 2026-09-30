package ru.zela.politicseconomy.event;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Persistent in-game news feed. */
public final class NewsSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_news";
    private static final String ITEMS = "items";
    private static final int MAX_ITEMS = 80;

    private final List<Item> items = new ArrayList<>();

    public static NewsSavedData create() {
        return new NewsSavedData();
    }

    public static NewsSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        NewsSavedData data = create();
        if (!tag.contains(ITEMS, Tag.TAG_LIST)) return data;

        ListTag list = tag.getList(ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            data.items.add(new Item(
                item.getLong("tick"),
                item.getString("category"),
                item.getString("title"),
                item.getString("body")
            ));
        }
        return data;
    }

    public static NewsSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(NewsSavedData::create, NewsSavedData::load, null),
            DATA_NAME
        );
    }

    public void add(long tick, String category, String title, String body) {
        items.add(new Item(tick, safe(category), safe(title), safe(body)));
        while (items.size() > MAX_ITEMS) items.remove(0);
        setDirty();
    }

    public List<Item> recent(int max) {
        int from = Math.max(0, items.size() - Math.max(1, max));
        List<Item> result = new ArrayList<>(items.subList(from, items.size()));
        java.util.Collections.reverse(result);
        return List.copyOf(result);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Item item : items) {
            CompoundTag value = new CompoundTag();
            value.putLong("tick", item.tick());
            value.putString("category", item.category());
            value.putString("title", item.title());
            value.putString("body", item.body());
            list.add(value);
        }
        tag.put(ITEMS, list);
        return tag;
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace("|", "/");
    }

    public record Item(long tick, String category, String title, String body) {}
}
