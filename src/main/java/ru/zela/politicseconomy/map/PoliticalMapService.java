package ru.zela.politicseconomy.map;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.zela.politicseconomy.network.EconomyNetwork;
import ru.zela.politicseconomy.network.PoliticalClaimsPayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side synchronization of PoliticsMod country claims to clients.
 *
 * The client only needs the claims around areas it is currently exploring.
 * This keeps packets bounded while allowing the cache to grow naturally as
 * players travel around the world.
 */
public final class PoliticalMapService {
    private static final int SYNC_INTERVAL_TICKS = 100; // 5 seconds
    private static final int SYNC_RADIUS_CHUNKS = 64;

    private static long lastSyncTick = Long.MIN_VALUE;

    private PoliticalMapService() {}

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        sync(player);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long time = server.overworld().getGameTime();

        if (time - lastSyncTick < SYNC_INTERVAL_TICKS) {
            return;
        }
        lastSyncTick = time;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension() == Level.OVERWORLD) {
                sync(player);
            }
        }
    }

    public static void sync(ServerPlayer player) {
        if (player.level().dimension() != Level.OVERWORLD) {
            return;
        }

        PoliticsManager politics = PoliticsManager.get(player.serverLevel());
        if (politics == null) {
            return;
        }

        int centerX = player.chunkPosition().x;
        int centerZ = player.chunkPosition().z;

        Map<String, Integer> countryIndices = new LinkedHashMap<>();
        List<String> countries = new ArrayList<>();
        List<Long> chunks = new ArrayList<>();
        List<Integer> owners = new ArrayList<>();

        int minX = centerX - SYNC_RADIUS_CHUNKS;
        int maxX = centerX + SYNC_RADIUS_CHUNKS;
        int minZ = centerZ - SYNC_RADIUS_CHUNKS;
        int maxZ = centerZ + SYNC_RADIUS_CHUNKS;

        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                Country owner = politics.getCountryAt(new ChunkPos(chunkX, chunkZ));
                if (owner == null) continue;

                String countryName = owner.getName();
                if (countryName == null || countryName.isBlank()) continue;

                Integer index = countryIndices.get(countryName);
                if (index == null) {
                    index = countries.size();
                    countryIndices.put(countryName, index);
                    countries.add(countryName);
                }

                chunks.add(ChunkPos.asLong(chunkX, chunkZ));
                owners.add(index);
            }
        }

        long[] chunkArray = new long[chunks.size()];
        int[] ownerArray = new int[owners.size()];

        for (int i = 0; i < chunks.size(); i++) {
            chunkArray[i] = chunks.get(i);
            ownerArray[i] = owners.get(i);
        }

        EconomyNetwork.send(
            player,
            new PoliticalClaimsPayload(
                centerX,
                centerZ,
                SYNC_RADIUS_CHUNKS,
                countries.toArray(String[]::new),
                chunkArray,
                ownerArray
            )
        );
    }
}
