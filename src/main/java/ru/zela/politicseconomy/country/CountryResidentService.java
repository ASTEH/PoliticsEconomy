package ru.zela.politicseconomy.country;

import net.krona.politicsmod.PoliticsManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * Physical citizen layer for Politics Economy.
 *
 * Residents are ordinary Minecraft villagers. Vanilla navigation, schedules,
 * beds, workstations and trading remain intact; Politics Economy adds country
 * identity and synchronization with the economic population.
 */
public final class CountryResidentService {
    private static final String RESIDENT_TAG = "politicseconomy_resident";
    private static final int STARTER_RESIDENTS = 4;
    private static final int TICK_INTERVAL = 20;
    private static final int BRAIN_INTERVAL = 100;
    private static final int MAX_SPAWNS_PER_PASS = 4;

    private static final String[] FIRST_NAMES = {
        "Иван", "Анна", "Алексей", "Мария",
        "Дмитрий", "Ольга", "Сергей", "Елена",
        "Никита", "Дарья", "Михаил", "София"
    };

    private CountryResidentService() {}

    public static CountryResidentSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                CountryResidentSavedData::create,
                CountryResidentSavedData::load,
                null
            ),
            CountryResidentSavedData.DATA_NAME
        );
    }

    public static void syncResidents(MinecraftServer server, String countryName) {
        if (server == null || countryName == null || countryName.isBlank()) return;
        if (ru.zela.politicseconomy.integration.MillenaireIntegration.isStateKey(countryName)) return;

        CountryPopulationSavedData populationData = CountryPopulationService.get(server);
        syncResidents(server, countryName, populationData.getResidents(countryName));
    }

    public static void syncResidents(
        MinecraftServer server,
        String countryName,
        int targetResidents
    ) {
        if (server == null || countryName == null || countryName.isBlank()) return;
        if (targetResidents <= 0 || !hasOnlineCountryPlayer(server, countryName)) return;

        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null || politics.getCountry(countryName) == null) return;

        CountryResidentSavedData data = get(server);
        Map<UUID, String> registered = data.snapshot(countryName);

        int missing = Math.max(0, targetResidents - registered.size());
        int spawnCount = Math.min(missing, MAX_SPAWNS_PER_PASS);

        for (int i = 0; i < spawnCount; i++) {
            String role = chooseRole(server, countryName, registered.size());
            Villager villager = spawnResident(server, countryName, role, registered.size());
            if (villager == null) break;

            data.addResident(countryName, villager.getUUID(), role);
            registered = data.snapshot(countryName);
        }

        if (registered.size() > targetResidents) {
            removeExcessResidents(server, countryName, targetResidents, registered);
        }

        if (server.overworld().getGameTime() % BRAIN_INTERVAL == 0L) {
            maintainResidentTerritory(server, countryName, data);
        }

        data.setDirty();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null || server.overworld() == null) return;

        long tick = server.overworld().getGameTime();
        if (tick % TICK_INTERVAL != 0L) return;

        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null) return;

        CountryPopulationSavedData populationData = CountryPopulationService.get(server);
        for (var country : politics.getCountries().values()) {
            int residents = populationData.getResidents(country.getName());
            if (residents > 0) {
                syncResidents(server, country.getName(), residents);
            }
        }
    }

    public static void ensureStarterResidents(MinecraftServer server, String countryName) {
        if (server == null || countryName == null || countryName.isBlank()) return;

        CountryPopulationSavedData data = CountryPopulationService.get(server);
        int target = Math.max(STARTER_RESIDENTS, data.getResidents(countryName));
        syncResidents(server, countryName, target);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        PoliticsManager politics = PoliticsManager.get(player.serverLevel());
        if (politics == null) return;

        String countryName = politics.getPlayerCountry(player.getUUID());
        if (countryName != null && !countryName.isBlank()) {
            ensureStarterResidents(player.getServer(), countryName);
        }
    }

    public static void onResidentDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (!villager.getTags().contains(RESIDENT_TAG)) return;
        if (!(villager.level() instanceof ServerLevel level)) return;

        MinecraftServer server = level.getServer();
        CountryResidentSavedData data = get(server);
        String countryName = null;
        PoliticsManager politics = PoliticsManager.get(level);
        if (politics != null) {
            for (var country : politics.getCountries().values()) {
                if (data.residentIds(country.getName()).contains(villager.getUUID())) {
                    countryName = country.getName();
                    break;
                }
            }
        }

        data.removeResidentEverywhere(villager.getUUID());

        if (countryName != null) {
            CountryPopulationSavedData population = CountryPopulationService.get(server);
            int current = population.getResidents(countryName);
            if (current > 1) {
                population.setResidents(countryName, current - 1);
                population.setStarvationCycles(countryName, 0);

                ru.zela.politicseconomy.event.NewsService.add(
                    server,
                    server.overworld().getGameTime(),
                    "ОБЩЕСТВО",
                    countryName + ": гибель жителя",
                    "Население сократилось до " + (current - 1) + "."
                );
            }
        }

        data.setDirty();
    }

    private static Villager spawnResident(
        MinecraftServer server,
        String countryName,
        String role,
        int residentIndex
    ) {
        ServerPlayer anchorPlayer = findAnchorPlayer(server, countryName);
        if (anchorPlayer == null) return null;

        ServerLevel level = server.overworld();
        BlockPos spawnPos = findSpawnPosition(level, anchorPlayer.blockPosition());
        if (spawnPos == null) return null;

        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) return null;

        VillagerData data = villager.getVillagerData()
            .setProfession(professionForRole(role))
            .setLevel(1);
        villager.setVillagerData(data);

        villager.moveTo(
            spawnPos.getX() + 0.5D,
            spawnPos.getY(),
            spawnPos.getZ() + 0.5D,
            anchorPlayer.getRandom().nextFloat() * 360.0F,
            0.0F
        );
        villager.setPersistenceRequired();
        villager.addTag(RESIDENT_TAG);
        villager.addTag("politicseconomy_country:" + countryName);
        villager.addTag("politicseconomy_role:" + role);
        villager.setCustomName(
            Component.literal(
                FIRST_NAMES[Math.floorMod(residentIndex, FIRST_NAMES.length)]
                    + " • " + roleDisplayName(role)
            ).withStyle(ChatFormatting.WHITE)
        );

        if (!level.addFreshEntity(villager)) return null;
        return villager;
    }

    private static String chooseRole(
        MinecraftServer server,
        String countryName,
        int residentIndex
    ) {
        var ledger = ru.zela.politicseconomy.economy.NationalMaterialConsumptionService
            .getLedger(server);
        int bread = ledger.getStockpile(countryName, "minecraft:bread");
        int population = Math.max(1, CountryPopulationService.get(server).getResidents(countryName));

        if (bread < population * 0.5D) {
            return "FARMER";
        }

        return switch (Math.floorMod(residentIndex, 4)) {
            case 0 -> "FARMER";
            case 1 -> "TOOLSMITH";
            case 2 -> "LIBRARIAN";
            default -> "ARMORER";
        };
    }

    private static VillagerProfession professionForRole(String role) {
        return switch (role) {
            case "FARMER" -> VillagerProfession.FARMER;
            case "TOOLSMITH" -> VillagerProfession.TOOLSMITH;
            case "LIBRARIAN" -> VillagerProfession.LIBRARIAN;
            case "ARMORER" -> VillagerProfession.ARMORER;
            default -> VillagerProfession.NONE;
        };
    }

    private static String roleDisplayName(String role) {
        return switch (role) {
            case "FARMER" -> "фермер";
            case "TOOLSMITH" -> "рабочий";
            case "LIBRARIAN" -> "служащий";
            case "ARMORER" -> "воин";
            default -> "житель";
        };
    }

    private static ServerPlayer findAnchorPlayer(
        MinecraftServer server,
        String countryName
    ) {
        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null) return null;

        ServerPlayer fallback = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!countryName.equals(politics.getPlayerCountry(player.getUUID()))) {
                continue;
            }

            if (countryName.equals(
                politics.getCountryNameAt(player.chunkPosition())
            )) {
                return player;
            }

            if (fallback == null) {
                fallback = player;
            }
        }

        return fallback;
    }

    private static boolean hasOnlineCountryPlayer(
        MinecraftServer server,
        String countryName
    ) {
        return findAnchorPlayer(server, countryName) != null;
    }

    private static BlockPos findSpawnPosition(ServerLevel level, BlockPos origin) {
        for (int radius = 0; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;

                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

                    if (y <= level.getMinBuildHeight() + 1) continue;

                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).isAir()
                        || !level.getBlockState(pos.above()).isAir()
                        || !level.getFluidState(pos).isEmpty()
                        || !level.getFluidState(pos.above()).isEmpty()
                        || !level.getBlockState(pos.below()).isSolid()) {
                        continue;
                    }

                    AABB box = new AABB(pos).inflate(1.0D);
                    if (!level.getEntitiesOfClass(Villager.class, box).isEmpty()) {
                        continue;
                    }

                    return pos;
                }
            }
        }

        return null;
    }

    private static void removeExcessResidents(
        MinecraftServer server,
        String countryName,
        int targetResidents,
        Map<UUID, String> registered
    ) {
        CountryResidentSavedData data = get(server);
        int excess = registered.size() - targetResidents;
        if (excess <= 0) return;

        for (UUID uuid : new ArrayList<>(registered.keySet())) {
            if (excess <= 0) break;

            for (ServerLevel level : server.getAllLevels()) {
                var entity = level.getEntity(uuid);
                if (!(entity instanceof Villager villager)) continue;

                villager.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                data.removeResident(countryName, uuid);
                excess--;
                break;
            }
        }
    }

    private static void maintainResidentTerritory(
        MinecraftServer server,
        String countryName,
        CountryResidentSavedData data
    ) {
        PoliticsManager politics = PoliticsManager.get(server.overworld());
        ServerPlayer anchor = findAnchorPlayer(server, countryName);
        if (politics == null || anchor == null) return;

        for (UUID uuid : data.residentIds(countryName)) {
            var entity = server.overworld().getEntity(uuid);
            if (!(entity instanceof Villager villager)) continue;

            ChunkPos chunk = villager.chunkPosition();
            String owner = politics.getCountryNameAt(chunk);
            if (countryName.equals(owner)) continue;

            BlockPos safe = findSpawnPosition(server.overworld(), anchor.blockPosition());
            if (safe == null) continue;

            villager.teleportTo(
                safe.getX() + 0.5D,
                safe.getY(),
                safe.getZ() + 0.5D
            );
            villager.getNavigation().stop();
        }
    }
}
