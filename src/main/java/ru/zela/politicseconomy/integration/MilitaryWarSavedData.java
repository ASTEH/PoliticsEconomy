package ru.zela.politicseconomy.integration;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MilitaryWarSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_military_wars";
    private static final String WARS = "wars";
    private static final String DECISIONS = "decisions";

    private final Map<String, War> wars = new HashMap<>();
    private final Map<String, Long> nextDecisionTick = new HashMap<>();

    public static MilitaryWarSavedData create() {
        return new MilitaryWarSavedData();
    }

    public static MilitaryWarSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        MilitaryWarSavedData data = create();

        if (tag.contains(WARS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(WARS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                War war = War.load(list.getCompound(i));
                if (!war.attacker().isBlank() && !war.defender().isBlank()
                    && !war.attacker().equals(war.defender())) {
                    data.wars.put(pairKey(war.attacker(), war.defender()), war);
                }
            }
        }

        if (tag.contains(DECISIONS, Tag.TAG_COMPOUND)) {
            CompoundTag decisions = tag.getCompound(DECISIONS);
            for (String stateKey : decisions.getAllKeys()) {
                data.nextDecisionTick.put(stateKey, decisions.getLong(stateKey));
            }
        }

        return data;
    }

    public static MilitaryWarSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                MilitaryWarSavedData::create,
                MilitaryWarSavedData::load,
                null
            ),
            DATA_NAME
        );
    }

    public boolean isAtWar(String a, String b) {
        return find(a, b) != null;
    }

    public War find(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) return null;
        return wars.get(pairKey(a, b));
    }

    public void startWar(
        String attacker,
        String defender,
        WarType type,
        WarCause cause,
        ChunkPos targetChunk,
        long startedTick
    ) {
        if (attacker == null || defender == null
            || attacker.isBlank() || defender.isBlank()
            || attacker.equals(defender)
            || isAtWar(attacker, defender)) {
            return;
        }

        wars.put(
            pairKey(attacker, defender),
            new War(
                attacker,
                defender,
                type == null ? WarType.GROUND : type,
                cause == null ? WarCause.BORDER_CONFLICT : cause,
                targetChunk == null ? 0L : targetChunk.toLong(),
                startedTick
            )
        );
        setDirty();
    }

    public boolean endWar(String a, String b) {
        War removed = wars.remove(pairKey(a, b));
        if (removed == null) return false;
        setDirty();
        return true;
    }

    public List<War> wars() {
        return List.copyOf(new ArrayList<>(wars.values()));
    }

    public long nextDecisionTick(String stateKey) {
        return nextDecisionTick.getOrDefault(stateKey, 0L);
    }

    public void setNextDecisionTick(String stateKey, long tick) {
        if (stateKey == null || stateKey.isBlank()) return;
        nextDecisionTick.put(stateKey, Math.max(0L, tick));
        setDirty();
    }

    private static String pairKey(String a, String b) {
        return a.compareTo(b) <= 0
            ? a + "\u0000" + b
            : b + "\u0000" + a;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (War war : wars.values()) list.add(war.save());
        tag.put(WARS, list);

        CompoundTag decisions = new CompoundTag();
        for (Map.Entry<String, Long> entry : nextDecisionTick.entrySet()) {
            decisions.putLong(entry.getKey(), entry.getValue());
        }
        tag.put(DECISIONS, decisions);
        return tag;
    }

    public enum WarType {
        GROUND
    }

    public enum WarCause {
        BORDER_CONFLICT,
        RESOURCE_SHORTAGE,
        TERRITORIAL_EXPANSION,
        RETALIATION,
        STRATEGIC_OPPORTUNITY
    }

    public record War(
        String attacker,
        String defender,
        WarType type,
        WarCause cause,
        long targetChunk,
        long startedTick
    ) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("attacker", attacker);
            tag.putString("defender", defender);
            tag.putString("type", type.name());
            tag.putString("cause", cause.name());
            tag.putLong("targetChunk", targetChunk);
            tag.putLong("startedTick", startedTick);
            return tag;
        }

        public static War load(CompoundTag tag) {
            WarType type;
            WarCause cause;
            try {
                type = WarType.valueOf(tag.getString("type"));
            } catch (IllegalArgumentException ignored) {
                type = WarType.GROUND;
            }
            try {
                cause = WarCause.valueOf(tag.getString("cause"));
            } catch (IllegalArgumentException ignored) {
                cause = WarCause.BORDER_CONFLICT;
            }

            return new War(
                tag.getString("attacker"),
                tag.getString("defender"),
                type,
                cause,
                tag.getLong("targetChunk"),
                tag.getLong("startedTick")
            );
        }

        public ChunkPos targetChunk() {
            return new ChunkPos(targetChunk);
        }
    }
}
