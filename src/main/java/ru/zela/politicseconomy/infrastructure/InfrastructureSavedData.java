package ru.zela.politicseconomy.infrastructure;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent registry of blocks that were explicitly placed by players.
 *
 * <p>Natural blocks are never inserted here. A later economy pass can use
 * these exact positions to determine which country currently owns them.</p>
 */
public final class InfrastructureSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_player_infrastructure";
    private static final String DIMENSIONS_TAG = "dimensions";
    private static final String CHUNKS_TAG = "chunks";
    private static final String BLOCKS_TAG = "blocks";
    private static final String OWNERS_TAG = "owners";

    /** dimension -> chunkLong -> blockPosLong -> block registry id */
    private final Map<String, Map<Long, Map<Long, String>>> blocks = new HashMap<>();
    /** dimension -> chunkLong -> blockPosLong -> owning country name at placement time */
    private final Map<String, Map<Long, Map<Long, String>>> owners = new HashMap<>();

    public static InfrastructureSavedData create() {
        return new InfrastructureSavedData();
    }

    public static InfrastructureSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        InfrastructureSavedData data = create();

        if (!tag.contains(DIMENSIONS_TAG, Tag.TAG_COMPOUND)) {
            return data;
        }

        CompoundTag dimensionsTag = tag.getCompound(DIMENSIONS_TAG);
        for (String dimensionId : dimensionsTag.getAllKeys()) {
            CompoundTag dimensionTag = dimensionsTag.getCompound(dimensionId);
            if (!dimensionTag.contains(CHUNKS_TAG, Tag.TAG_COMPOUND)) {
                continue;
            }

            Map<Long, Map<Long, String>> dimensionMap = new HashMap<>();
            CompoundTag chunksTag = dimensionTag.getCompound(CHUNKS_TAG);

            for (String chunkKey : chunksTag.getAllKeys()) {
                long chunkLong;
                try {
                    chunkLong = Long.parseLong(chunkKey);
                } catch (NumberFormatException ignored) {
                    continue;
                }

                CompoundTag chunkTag = chunksTag.getCompound(chunkKey);
                if (!chunkTag.contains(BLOCKS_TAG, Tag.TAG_COMPOUND)) {
                    continue;
                }

                CompoundTag blockTag = chunkTag.getCompound(BLOCKS_TAG);
                Map<Long, String> chunkMap = new HashMap<>();

                for (String posKey : blockTag.getAllKeys()) {
                    long posLong;
                    try {
                        posLong = Long.parseLong(posKey);
                    } catch (NumberFormatException ignored) {
                        continue;
                    }
                    String blockId = blockTag.getString(posKey);
                    if (!blockId.isBlank()) {
                        chunkMap.put(posLong, blockId);
                    }
                }

                if (!chunkMap.isEmpty()) {
                    dimensionMap.put(chunkLong, chunkMap);
                }
            }

            if (dimensionMap.isEmpty()) {
                continue;
            }

            data.blocks.put(dimensionId, dimensionMap);

            // Owner data was added after the original block registry. Keep old
            // worlds compatible: missing owners simply remain unknown.
            if (dimensionTag.contains(OWNERS_TAG, Tag.TAG_COMPOUND)) {
                CompoundTag ownersTag = dimensionTag.getCompound(OWNERS_TAG);
                Map<Long, Map<Long, String>> ownerDimension = new HashMap<>();
                for (String chunkKey : ownersTag.getAllKeys()) {
                    try {
                        long chunkLong = Long.parseLong(chunkKey);
                        CompoundTag ownerChunk = ownersTag.getCompound(chunkKey);
                        Map<Long, String> ownerMap = new HashMap<>();
                        for (String posKey : ownerChunk.getAllKeys()) {
                            try {
                                long posLong = Long.parseLong(posKey);
                                String owner = ownerChunk.getString(posKey);
                                if (!owner.isBlank()) {
                                    ownerMap.put(posLong, owner);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                        if (!ownerMap.isEmpty()) {
                            ownerDimension.put(chunkLong, ownerMap);
                        }
                    } catch (NumberFormatException ignored) {}
                }
                if (!ownerDimension.isEmpty()) {
                    data.owners.put(dimensionId, ownerDimension);
                }
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag dimensionsTag = new CompoundTag();

        for (Map.Entry<String, Map<Long, Map<Long, String>>> dimensionEntry : blocks.entrySet()) {
            CompoundTag dimensionTag = new CompoundTag();
            CompoundTag chunksTag = new CompoundTag();

            for (Map.Entry<Long, Map<Long, String>> chunkEntry : dimensionEntry.getValue().entrySet()) {
                if (chunkEntry.getValue().isEmpty()) {
                    continue;
                }

                CompoundTag blockTag = new CompoundTag();
                for (Map.Entry<Long, String> blockEntry : chunkEntry.getValue().entrySet()) {
                    blockTag.putString(Long.toString(blockEntry.getKey()), blockEntry.getValue());
                }

                CompoundTag chunkTag = new CompoundTag();
                chunkTag.put(BLOCKS_TAG, blockTag);
                chunksTag.put(Long.toString(chunkEntry.getKey()), chunkTag);
            }

            dimensionTag.put(CHUNKS_TAG, chunksTag);

            Map<Long, Map<Long, String>> ownerDimension = owners.get(dimensionEntry.getKey());
            if (ownerDimension != null && !ownerDimension.isEmpty()) {
                CompoundTag ownersTag = new CompoundTag();
                for (Map.Entry<Long, Map<Long, String>> ownerChunkEntry : ownerDimension.entrySet()) {
                    if (ownerChunkEntry.getValue().isEmpty()) {
                        continue;
                    }
                    CompoundTag ownerChunk = new CompoundTag();
                    for (Map.Entry<Long, String> ownerEntry : ownerChunkEntry.getValue().entrySet()) {
                        if (ownerEntry.getValue() != null && !ownerEntry.getValue().isBlank()) {
                            ownerChunk.putString(Long.toString(ownerEntry.getKey()), ownerEntry.getValue());
                        }
                    }
                    if (!ownerChunk.isEmpty()) {
                        ownersTag.put(Long.toString(ownerChunkEntry.getKey()), ownerChunk);
                    }
                }
                dimensionTag.put(OWNERS_TAG, ownersTag);
            }

            dimensionsTag.put(dimensionEntry.getKey(), dimensionTag);
        }

        tag.put(DIMENSIONS_TAG, dimensionsTag);
        return tag;
    }

    public void add(ServerLevel level, BlockPos pos, BlockState state, String ownerCountry) {
        String dimensionId = level.dimension().location().toString();
        long chunkLong = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        long posLong = pos.asLong();
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        blocks
            .computeIfAbsent(dimensionId, ignored -> new HashMap<>())
            .computeIfAbsent(chunkLong, ignored -> new HashMap<>())
            .put(posLong, blockId);

        if (ownerCountry != null && !ownerCountry.isBlank()) {
            owners
                .computeIfAbsent(dimensionId, ignored -> new HashMap<>())
                .computeIfAbsent(chunkLong, ignored -> new HashMap<>())
                .put(posLong, ownerCountry);
        }

        setDirty();
    }

    public boolean remove(ServerLevel level, BlockPos pos) {
        String dimensionId = level.dimension().location().toString();
        long chunkLong = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);

        Map<Long, Map<Long, String>> dimensionMap = blocks.get(dimensionId);
        if (dimensionMap == null) {
            return false;
        }

        Map<Long, String> chunkMap = dimensionMap.get(chunkLong);
        if (chunkMap == null) {
            return false;
        }

        boolean removed = chunkMap.remove(pos.asLong()) != null;
        if (!removed) {
            return false;
        }

        Map<Long, Map<Long, String>> ownerDimension = owners.get(dimensionId);
        if (ownerDimension != null) {
            Map<Long, String> ownerChunk = ownerDimension.get(chunkLong);
            if (ownerChunk != null) {
                ownerChunk.remove(pos.asLong());
                if (ownerChunk.isEmpty()) {
                    ownerDimension.remove(chunkLong);
                }
            }
            if (ownerDimension.isEmpty()) {
                owners.remove(dimensionId);
            }
        }

        if (chunkMap.isEmpty()) {
            dimensionMap.remove(chunkLong);
        }
        if (dimensionMap.isEmpty()) {
            blocks.remove(dimensionId);
        }

        setDirty();
        return true;
    }

    public void reassignChunkOwner(ServerLevel level, ChunkPos chunk, String ownerCountry) {
        if (level == null || chunk == null || ownerCountry == null || ownerCountry.isBlank()) {
            return;
        }
        String dimensionId = level.dimension().location().toString();
        long chunkLong = ChunkPos.asLong(chunk.x, chunk.z);

        Map<Long, Map<Long, String>> dimensionMap = owners.computeIfAbsent(
            dimensionId,
            ignored -> new HashMap<>()
        );
        Map<Long, String> ownerMap = dimensionMap.computeIfAbsent(
            chunkLong,
            ignored -> new HashMap<>()
        );

        Map<Long, Map<Long, String>> blockDimension = blocks.get(dimensionId);
        Map<Long, String> blockMap = blockDimension == null ? null : blockDimension.get(chunkLong);
        if (blockMap != null) {
            for (Long posLong : blockMap.keySet()) {
                ownerMap.put(posLong, ownerCountry);
            }
        }
        if (ownerMap.isEmpty()) {
            dimensionMap.remove(chunkLong);
        }
        if (dimensionMap.isEmpty()) {
            owners.remove(dimensionId);
        }
        setDirty();
    }

    public String getOwnerCountry(ServerLevel level, BlockPos pos) {
        String dimensionId = level.dimension().location().toString();
        long chunkLong = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        Map<Long, Map<Long, String>> dimensionMap = owners.get(dimensionId);
        if (dimensionMap == null) {
            return null;
        }
        Map<Long, String> chunkMap = dimensionMap.get(chunkLong);
        if (chunkMap == null) {
            return null;
        }
        return chunkMap.get(pos.asLong());
    }

    public Map<Long, String> getChunk(ServerLevel level, ChunkPos chunkPos) {
        String dimensionId = level.dimension().location().toString();
        Map<Long, Map<Long, String>> dimensionMap = blocks.get(dimensionId);
        if (dimensionMap == null) {
            return Map.of();
        }

        Map<Long, String> chunkMap = dimensionMap.get(ChunkPos.asLong(chunkPos.x, chunkPos.z));
        if (chunkMap == null) {
            return Map.of();
        }
        return Map.copyOf(chunkMap);
    }

    public Map<Long, Map<Long, String>> getDimension(ServerLevel level) {
        String dimensionId = level.dimension().location().toString();
        Map<Long, Map<Long, String>> dimensionMap = blocks.get(dimensionId);
        if (dimensionMap == null) {
            return Map.of();
        }

        Map<Long, Map<Long, String>> result = new HashMap<>();
        for (Map.Entry<Long, Map<Long, String>> entry : dimensionMap.entrySet()) {
            result.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return Map.copyOf(result);
    }
}
