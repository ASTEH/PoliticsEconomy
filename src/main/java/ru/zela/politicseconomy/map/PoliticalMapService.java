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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-side synchronization of PoliticsMod country claims to clients.
 *
 * The client only needs the claims around areas it is currently exploring.
 * This keeps packets bounded while allowing the cache to grow naturally as
 * players travel around the world.
 */
public final class PoliticalMapService {
    private static final int SYNC_INTERVAL_TICKS = 20; // 1 second safety sync

    private static long lastSyncTick = Long.MIN_VALUE;
    private static long lastMillenaireTerritoryFingerprint = Long.MIN_VALUE;

    private PoliticalMapService() {}

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        sync(player);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long time = server.overworld().getGameTime();

        long millenaireFingerprint =
            ru.zela.politicseconomy.integration.MillenaireIntegration
                .territoryFingerprint(server);

        boolean millenaireChanged =
            millenaireFingerprint != lastMillenaireTerritoryFingerprint;

        if (!millenaireChanged && time - lastSyncTick < SYNC_INTERVAL_TICKS) {
            return;
        }

        lastSyncTick = time;
        lastMillenaireTerritoryFingerprint = millenaireFingerprint;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension() == Level.OVERWORLD) {
                sync(player);
            }
        }
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) return;
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

        Map<String, Integer> countryIndices = new LinkedHashMap<>();
        List<String> countries = new ArrayList<>();
        List<Long> chunks = new ArrayList<>();
        List<Integer> owners = new ArrayList<>();
        Set<Long> occupiedChunks = new HashSet<>();

        // PoliticsMod already exposes its complete persisted claim set.
        // Send all known claims so every player can see every country's territory,
        // not only the territory close to their own position.
        politics.forEachClaim((pos, color) -> {
            String countryName = politics.getCountryNameAt(pos);
            if (countryName == null || countryName.isBlank()) return;

            Integer index = countryIndices.get(countryName);
            if (index == null) {
                index = countries.size();
                countryIndices.put(countryName, index);
                countries.add(countryName);
            }

            chunks.add(pos.toLong());
            owners.add(index);
            occupiedChunks.add(pos.toLong());
        });

        // Millénaire villages are autonomous states. Their own territory is
        // appended only where PoliticsMod has no claim, preserving the player
        // country's existing political ownership on overlapping chunks.
        for (ru.zela.politicseconomy.integration.MillenaireIntegration.VillageSnapshot village
            : ru.zela.politicseconomy.integration.MillenaireIntegration.snapshots(player.getServer())) {
            String stateName = "Millénaire: " + village.name();
            Integer index = countryIndices.get(stateName);
            if (index == null) {
                index = countries.size();
                countryIndices.put(stateName, index);
                countries.add(stateName);
            }

            for (ChunkPos chunk : village.territory()) {
                long packed = chunk.toLong();
                if (!occupiedChunks.add(packed)) continue;
                chunks.add(packed);
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
                player.chunkPosition().x,
                player.chunkPosition().z,
                128,
                countries.toArray(String[]::new),
                chunkArray,
                ownerArray
            )
        );
    }

}
