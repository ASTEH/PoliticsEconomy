package ru.zela.politicseconomy.territory;

import net.krona.politicsmod.PoliticsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Heightmap;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/**
 * Lightweight in-world visualization of country borders.
 *
 * <p>Only borders close to online players are rendered. The effect is made
 * from colored dust particles positioned at the surface height, so the line
 * follows hills and terrain instead of floating at a fixed Y level.</p>
 */
public final class TerritoryBorderVisualService {
    private static final int TICK_INTERVAL = 10;
    private static final double VIEW_DISTANCE = 64.0D;
    private static final double SAMPLE_STEP = 4.0D;
    private static long lastTick = Long.MIN_VALUE;

    private TerritoryBorderVisualService() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long gameTime = server.overworld().getGameTime();

        if (gameTime - lastTick < TICK_INTERVAL) {
            return;
        }
        lastTick = gameTime;

        ServerLevel level = server.overworld();
        PoliticsManager politics = PoliticsManager.get(level);
        if (politics == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.serverLevel() != level) {
                continue;
            }
            renderNearbyBorders(level, politics, player);
        }
    }

    private static void renderNearbyBorders(
        ServerLevel level,
        PoliticsManager politics,
        ServerPlayer player
    ) {
        ChunkPos center = player.chunkPosition();
        Set<Long> renderedEdges = new HashSet<>();

        ChunkPos[] nearbyChunks = {
            center,
            new ChunkPos(center.x + 1, center.z),
            new ChunkPos(center.x - 1, center.z),
            new ChunkPos(center.x, center.z + 1),
            new ChunkPos(center.x, center.z - 1)
        };

        for (ChunkPos chunk : nearbyChunks) {
            String owner = politics.getCountryNameAt(chunk);
            if (owner == null || owner.isBlank()) {
                continue;
            }

            int borderColor = countryColor(owner);

            renderEdgeIfNeeded(
                level, politics, player, chunk, owner, borderColor,
                Direction.WEST, renderedEdges
            );
            renderEdgeIfNeeded(
                level, politics, player, chunk, owner, borderColor,
                Direction.EAST, renderedEdges
            );
            renderEdgeIfNeeded(
                level, politics, player, chunk, owner, borderColor,
                Direction.NORTH, renderedEdges
            );
            renderEdgeIfNeeded(
                level, politics, player, chunk, owner, borderColor,
                Direction.SOUTH, renderedEdges
            );
        }
    }

    private static void renderEdgeIfNeeded(
        ServerLevel level,
        PoliticsManager politics,
        ServerPlayer player,
        ChunkPos chunk,
        String owner,
        int color,
        Direction direction,
        Set<Long> renderedEdges
    ) {
        ChunkPos neighbor = switch (direction) {
            case WEST -> new ChunkPos(chunk.x - 1, chunk.z);
            case EAST -> new ChunkPos(chunk.x + 1, chunk.z);
            case NORTH -> new ChunkPos(chunk.x, chunk.z - 1);
            case SOUTH -> new ChunkPos(chunk.x, chunk.z + 1);
        };

        String neighborOwner = politics.getCountryNameAt(neighbor);
        if (owner.equals(neighborOwner)) {
            return;
        }

        long edgeKey = edgeKey(chunk, direction);
        if (!renderedEdges.add(edgeKey)) {
            return;
        }

        double edgeDistance = switch (direction) {
            case WEST -> Math.abs(player.getX() - chunk.x * 16.0D);
            case EAST -> Math.abs(player.getX() - (chunk.x + 1) * 16.0D);
            case NORTH -> Math.abs(player.getZ() - chunk.z * 16.0D);
            case SOUTH -> Math.abs(player.getZ() - (chunk.z + 1) * 16.0D);
        };

        if (edgeDistance > VIEW_DISTANCE) {
            return;
        }

        DustParticleOptions particle = dust(color);

        for (double offset = 2.0D; offset < 16.0D; offset += SAMPLE_STEP) {
            double x;
            double z;

            switch (direction) {
                case WEST, EAST -> {
                    x = direction == Direction.WEST
                        ? chunk.x * 16.0D + 0.12D
                        : (chunk.x + 1) * 16.0D - 0.12D;
                    z = chunk.z * 16.0D + offset;
                }
                case NORTH, SOUTH -> {
                    x = chunk.x * 16.0D + offset;
                    z = direction == Direction.NORTH
                        ? chunk.z * 16.0D + 0.12D
                        : (chunk.z + 1) * 16.0D - 0.12D;
                }
                default -> throw new IllegalStateException("Unexpected border direction");
            }

            if (player.distanceToSqr(x, player.getY(), z) > VIEW_DISTANCE * VIEW_DISTANCE) {
                continue;
            }

            int blockX = Mth.floor(x);
            int blockZ = Mth.floor(z);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);

            level.sendParticles(
                player,
                particle,
                false,
                false,
                x,
                Math.max(level.getMinBuildHeight() + 1, y) + 0.08D,
                z,
                1,
                0.0D,
                0.015D,
                0.0D,
                0.0D
            );
        }
    }

    private static long edgeKey(ChunkPos chunk, Direction direction) {
        if (direction == Direction.WEST || direction == Direction.EAST) {
            int edgeX = direction == Direction.WEST ? chunk.x : chunk.x + 1;
            // Vertical border: both sides of the same line share this key.
            return ChunkPos.asLong(edgeX, chunk.z) * 31L;
        }

        int edgeZ = direction == Direction.NORTH ? chunk.z : chunk.z + 1;
        // Horizontal border: both sides of the same line share this key.
        return ChunkPos.asLong(chunk.x, edgeZ) * 31L + 1L;
    }

    private static DustParticleOptions dust(int rgb) {
        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;
        return new DustParticleOptions(new Vector3f(red, green, blue), 1.05F);
    }

    private static int countryColor(String country) {
        int[] colors = {
            0x4A90E2,
            0xE05656,
            0x55B86A,
            0x9B6BDB,
            0xE19A3A,
            0x42B7B7,
            0xD66D9A,
            0x7E9CCF,
            0x8DBF48,
            0xC86F3B,
            0x5C86C7,
            0xAA6FB5
        };

        int hash = country == null ? 0 : country.hashCode();
        hash ^= hash >>> 16;
        return colors[Math.floorMod(hash, colors.length)];
    }

    private enum Direction {
        WEST,
        EAST,
        NORTH,
        SOUTH
    }
}
