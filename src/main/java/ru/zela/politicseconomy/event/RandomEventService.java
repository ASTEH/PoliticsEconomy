package ru.zela.politicseconomy.event;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.zela.politicseconomy.country.CountryDevelopmentService;
import ru.zela.politicseconomy.integration.CountryContext;
import ru.zela.politicseconomy.integration.MilitaryEconomyService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Periodic random national events for player states. */
public final class RandomEventService {
    private static final long CHECK_INTERVAL = 6000L;
    private static final long MIN_DELAY = 7000L;
    private static final long MAX_DELAY = 18000L;
    private static final Random RANDOM = new Random();
    private static final Map<MinecraftServer, List<BurningBlock>> ACTIVE_FIRES = new HashMap<>();
    private static final Map<MinecraftServer, List<MapMarker>> ACTIVE_MARKERS = new HashMap<>();

    private RandomEventService() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (level == null) return;

        long now = level.getGameTime();
        tickFires(server, now);
        tickMarkers(server, now);

        if (now % CHECK_INTERVAL != 0L) return;

        Map<String, ServerPlayer> playerStates = playerStates(server);
        if (playerStates.isEmpty()) return;

        RandomEventSavedData data = RandomEventSavedData.get(server);
        for (Map.Entry<String, ServerPlayer> entry : playerStates.entrySet()) {
            String stateKey = entry.getKey();
            ServerPlayer player = entry.getValue();

            if (data.nextEventTick(stateKey) > now) continue;

            data.setNextEventTick(
                stateKey,
                now + MIN_DELAY + RANDOM.nextLong(Math.max(1L, MAX_DELAY - MIN_DELAY))
            );

            // 42% chance every check. With the delay this produces occasional,
            // noticeable events instead of constant spam.
            if (RANDOM.nextDouble() > 0.42D) continue;

            triggerRandomEvent(server, player, stateKey, now);
        }
    }

    private static Map<String, ServerPlayer> playerStates(MinecraftServer server) {
        Map<String, ServerPlayer> result = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String stateKey = CountryContext.playerStateName(player);
            if (stateKey == null || stateKey.isBlank()) continue;
            result.putIfAbsent(stateKey, player);
        }
        return result;
    }

    private static void triggerRandomEvent(
        MinecraftServer server,
        ServerPlayer player,
        String stateKey,
        long now
    ) {
        double roll = RANDOM.nextDouble();

        if (roll < 0.38D) {
            startFire(server, player, stateKey, now);
            return;
        }

        if (roll < 0.60D) {
            CountryDevelopmentService.addActivity(server, stateKey, 120);
            addMarker(server, player.blockPosition(), "ЭКОНОМИКА", stateKey, now, 900L);
            levelPulse(player.serverLevel(), player.blockPosition(), ParticleTypes.HAPPY_VILLAGER);
            NewsService.add(
                server,
                now,
                "ЭКОНОМИКА",
                displayName(server, stateKey) + ": экономический подъём",
                "Деловая активность выросла. Развитие государства получает импульс."
            );
            return;
        }

        if (roll < 0.80D) {
            if (!stateKey.startsWith("millenaire:")) {
                var politics = ru.zela.politicseconomy.country.CountryPoliticalService.get(server);
                politics.setUnrest(
                    stateKey,
                    Math.min(100, politics.getUnrest(stateKey) + 12)
                );
            }
            addMarker(server, player.blockPosition(), "ОБЩЕСТВО", stateKey, now, 900L);
            levelPulse(player.serverLevel(), player.blockPosition(), ParticleTypes.ANGRY_VILLAGER);
            var military = ru.zela.politicseconomy.integration.MilitaryReadinessSavedData.get(server);
            military.reduceReadiness(stateKey, 4.0D);
            NewsService.add(
                server,
                now,
                "ОБЩЕСТВО",
                displayName(server, stateKey) + ": общественное напряжение",
                "В стране наблюдается рост внутреннего напряжения."
            );
            return;
        }

        addMarker(server, player.blockPosition(), "РЫНОК", stateKey, now, 900L);
        levelPulse(player.serverLevel(), player.blockPosition(), ParticleTypes.COMPOSTER);
        NewsService.add(
            server,
            now,
            "РЫНОК",
            displayName(server, stateKey) + ": изменение рыночной конъюнктуры",
            "Спрос и цены временно меняются. Торговцам стоит учитывать новости."
        );
    }

    private static void startFire(
        MinecraftServer server,
        ServerPlayer player,
        String stateKey,
        long now
    ) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = findFireOrigin(level, player, stateKey);
        if (origin == null) {
            NewsService.add(
                server,
                now,
                "БЕДСТВИЕ",
                displayName(server, stateKey) + ": пожар предотвращён",
                "Возникла угроза пожара, но очаг не удалось сформировать."
            );
            return;
        }

        List<BurningBlock> fire = ACTIVE_FIRES.computeIfAbsent(
            server,
            ignored -> new ArrayList<>()
        );

        int count = 0;
        for (int i = 0; i < 28; i++) {
            int dx = RANDOM.nextInt(11) - 5;
            int dz = RANDOM.nextInt(11) - 5;
            int dy = RANDOM.nextInt(5) - 2;
            BlockPos candidate = origin.offset(dx, dy, dz);

            if (!belongsToState(server, candidate, stateKey)) continue;
            if (!burnable(level, candidate)) continue;

            long due = now + 30L + RANDOM.nextLong(180L);
            fire.add(new BurningBlock(candidate.immutable(), stateKey, due));
            lightFire(level, candidate);
            count++;
        }

        if (count == 0) return;

        addMarker(server, origin, "БЕДСТВИЕ", stateKey, now, 1800L);
        levelPulse(level, origin, ParticleTypes.FLAME);
        NewsService.add(
            server,
            now,
            "БЕДСТВИЕ",
            displayName(server, stateKey) + ": пожар",
            "В одном из районов вспыхнул пожар. Огонь быстро распространяется и уничтожает постройки."
        );
        player.sendSystemMessage(
            net.minecraft.network.chat.Component.literal(
                "§cВ вашей стране начался пожар! Проверьте ближайшие постройки."
            )
        );
    }

    private static BlockPos findFireOrigin(
        ServerLevel level,
        ServerPlayer player,
        String stateKey
    ) {
        BlockPos base = player.blockPosition();

        for (int i = 0; i < 80; i++) {
            int radius = 8 + RANDOM.nextInt(33);
            int dx = RANDOM.nextInt(radius * 2 + 1) - radius;
            int dz = RANDOM.nextInt(radius * 2 + 1) - radius;
            BlockPos column = base.offset(dx, 0, dz);

            if (!belongsToState(level.getServer(), column, stateKey)) continue;

            int y = level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                column.getX(),
                column.getZ()
            );
            BlockPos top = new BlockPos(column.getX(), y - 1, column.getZ());
            if (burnable(level, top)) return top;
        }
        return null;
    }

    private static boolean burnable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        if (!state.getFluidState().isEmpty()) return false;
        if (state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER)
            || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY)) {
            return false;
        }
        return state.getDestroySpeed(level, pos) >= 0.0F;
    }

    private static void lightFire(ServerLevel level, BlockPos block) {
        BlockPos above = block.above();
        if (level.getBlockState(above).isAir()) {
            level.setBlockAndUpdate(above, Blocks.FIRE.defaultBlockState());
        }
    }

    private static void tickFires(MinecraftServer server, long now) {
        List<BurningBlock> fire = ACTIVE_FIRES.get(server);
        if (fire == null || fire.isEmpty()) return;

        List<BurningBlock> next = new ArrayList<>();
        ServerLevel level = server.overworld();
        for (BurningBlock burning : fire) {
            if (burning.dueTick() > now) {
                next.add(burning);
                continue;
            }

            if (!belongsToState(server, burning.pos(), burning.stateKey())) {
                continue;
            }

            if (burnable(level, burning.pos())) {
                level.setBlockAndUpdate(
                    burning.pos(),
                    Blocks.AIR.defaultBlockState()
                );

                // Spread to a few nearby blocks. This bypasses vanilla
                // flammability, so wood, stone, Create blocks and most other
                // normal build blocks can become fire victims.
                for (int i = 0; i < 3; i++) {
                    BlockPos neighbour = burning.pos().offset(
                        RANDOM.nextInt(3) - 1,
                        RANDOM.nextInt(3) - 1,
                        RANDOM.nextInt(3) - 1
                    );

                    if (!belongsToState(server, neighbour, burning.stateKey())) continue;
                    if (!burnable(level, neighbour)) continue;

                    long spreadAt = now + 20L + RANDOM.nextLong(100L);
                    next.add(new BurningBlock(
                        neighbour.immutable(),
                        burning.stateKey(),
                        spreadAt
                    ));
                    lightFire(level, neighbour);
                }
            }
        }

        ACTIVE_FIRES.put(server, next);
    }

    public static String[] mapRows(MinecraftServer server) {
        if (server == null) return new String[0];

        List<MapMarker> markers = ACTIVE_MARKERS.get(server);
        if (markers == null || markers.isEmpty()) return new String[0];

        long now = server.overworld().getGameTime();
        List<String> rows = new ArrayList<>();
        for (MapMarker marker : markers) {
            if (marker.expiresAt() <= now) continue;
            rows.add(
                safe(marker.type()) + "|" +
                safe(displayName(server, marker.stateKey())) + "|" +
                marker.pos().getX() + "|" +
                marker.pos().getZ() + "|" +
                marker.expiresAt()
            );
            if (rows.size() >= 64) break;
        }
        return rows.toArray(String[]::new);
    }

    private static void addMarker(
        MinecraftServer server,
        BlockPos pos,
        String type,
        String stateKey,
        long now,
        long duration
    ) {
        ACTIVE_MARKERS.computeIfAbsent(server, ignored -> new ArrayList<>())
            .add(new MapMarker(pos.immutable(), type, stateKey, now + duration));
    }

    private static void tickMarkers(MinecraftServer server, long now) {
        List<MapMarker> markers = ACTIVE_MARKERS.get(server);
        if (markers == null) return;

        markers.removeIf(marker -> marker.expiresAt() <= now);
        if (markers.isEmpty()) ACTIVE_MARKERS.remove(server);
    }

    private static void levelPulse(
        ServerLevel level,
        BlockPos pos,
        net.minecraft.core.particles.ParticleOptions particle
    ) {
        if (level == null || pos == null) return;
        level.sendParticles(
            particle,
            pos.getX() + 0.5D,
            pos.getY() + 1.0D,
            pos.getZ() + 0.5D,
            24,
            1.2D,
            1.0D,
            1.2D,
            0.03D
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace("|", "/");
    }

    private static boolean belongsToState(
        MinecraftServer server,
        BlockPos pos,
        String stateKey
    ) {
        if (stateKey.startsWith("millenaire:")) {
            var village = ru.zela.politicseconomy.integration.MillenaireIntegration
                .snapshotAtChunk(server, new ChunkPos(pos));
            return village != null && stateKey.equals(village.stateKey());
        }

        PoliticsManager politics = PoliticsManager.get(server.overworld());
        Country country = politics == null ? null : politics.getCountry(stateKey);
        if (country == null) return false;
        Country owner = politics.getCountryAt(new ChunkPos(pos));
        return owner != null && stateKey.equals(owner.getName());
    }

    private static String displayName(MinecraftServer server, String stateKey) {
        if (stateKey.startsWith("millenaire:")) {
            return ru.zela.politicseconomy.integration.MillenaireIntegration
                .displayName(server, stateKey);
        }
        return stateKey;
    }

    private record BurningBlock(BlockPos pos, String stateKey, long dueTick) {}
    private record MapMarker(BlockPos pos, String type, String stateKey, long expiresAt) {}
}
