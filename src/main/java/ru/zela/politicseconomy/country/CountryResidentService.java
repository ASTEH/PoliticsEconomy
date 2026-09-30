package ru.zela.politicseconomy.country;

import net.krona.politicsmod.PoliticsManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Physical citizen layer for Politics Economy.
 *
 * A villager becomes part of a country when its vanilla HOME memory points
 * to a bed belonging to that country. Population is therefore the number of
 * unique occupied beds, not the number of bed blocks placed in the world.
 */
public final class CountryResidentService {
    private static final String RESIDENT_TAG = "politicseconomy_resident";
    private static final String COUNTRY_TAG_PREFIX = "politicseconomy_country:";
    private static final String ROLE_TAG_PREFIX = "politicseconomy_role:";

    private static final int STARTER_RESIDENTS = 4;
    private static final int TICK_INTERVAL = 20;
    private static final int BRAIN_INTERVAL = 100;
    private static final int MAX_SPAWNS_PER_PASS = 4;

    private static final Map<MinecraftServer, Long> LAST_RECONCILE_TICK =
        new java.util.WeakHashMap<>();

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

    /**
     * Returns the actual resident population: unique beds selected by
     * villagers through their HOME brain memory.
     */
    public static int population(MinecraftServer server, String countryName) {
        if (server == null || countryName == null || countryName.isBlank()) return 0;
        if (ru.zela.politicseconomy.integration.MillenaireIntegration.isStateKey(countryName)) {
            return ru.zela.politicseconomy.integration.MillenaireIntegration.population(server, countryName);
        }

        reconcileIfNeeded(server);

        return get(server).occupiedBedCount(countryName);
    }

    public static int populationAtChunk(MinecraftServer server, ChunkPos chunk) {
        if (server == null || chunk == null) return 0;
        reconcileIfNeeded(server);

        CountryResidentSavedData data = get(server);
        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null) return 0;

        String country = politics.getCountryNameAt(chunk);
        if (country == null) return 0;

        return data.occupiedBedCountInChunk(country, chunk.toLong());
    }

    public static void ensureStarterResidents(MinecraftServer server, String countryName) {
        if (server == null || countryName == null || countryName.isBlank()) return;
        if (!hasOnlineCountryPlayer(server, countryName)) return;

        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null || politics.getCountry(countryName) == null) return;

        CountryResidentSavedData data = get(server);
        int existingPhysical = data.residentIds(countryName).size();
        int missing = Math.max(0, STARTER_RESIDENTS - existingPhysical);

        spawnResidents(server, countryName, missing, false);
        reconcileIfNeeded(server);
    }

    /**
     * Called when economic growth creates enough food and housing for another
     * household member. The villager is created physically; population starts
     * counting it only after the villager claims a bed.
     */
    public static boolean spawnResidentForGrowth(MinecraftServer server, String countryName) {
        if (server == null || countryName == null || countryName.isBlank()) return false;
        return spawnResidents(server, countryName, 1, true) > 0;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null || server.overworld() == null) return;

        long tick = server.overworld().getGameTime();
        if (tick % TICK_INTERVAL != 0L) return;

        reconcileAll(server);

        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null) return;

        for (var country : politics.getCountries().values()) {
            if (hasOnlineCountryPlayer(server, country.getName())) {
                ensureStarterResidents(server, country.getName());
            }
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        PoliticsManager politics = PoliticsManager.get(player.serverLevel());
        if (politics == null) return;

        String countryName = politics.getPlayerCountry(player.getUUID());
        if (countryName != null && !countryName.isBlank()) {
            ensureStarterResidents(player.getServer(), countryName);
            reconcileIfNeeded(player.getServer());
        }
    }

    public static void onResidentDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (!villager.getTags().contains(RESIDENT_TAG)) return;
        if (!(villager.level() instanceof ServerLevel level)) return;

        MinecraftServer server = level.getServer();
        CountryResidentSavedData data = get(server);
        data.removeResidentEverywhere(villager.getUUID());
        data.setDirty();

        String countryName = data.findCountry(villager.getUUID());
        if (countryName != null) {
            ru.zela.politicseconomy.event.NewsService.add(
                server,
                server.overworld().getGameTime(),
                "ОБЩЕСТВО",
                countryName + ": гибель жителя",
                "Житель погиб. Свободное жильё может принять нового жителя."
            );
        }
    }

    /**
     * Removes one physical resident when a prolonged food shortage causes
     * population decline. The actual population change is reflected through
     * the occupied-bed counter after the resident is removed.
     */
    public static boolean removeOneResident(MinecraftServer server, String countryName) {
        if (server == null || countryName == null || countryName.isBlank()) return false;

        CountryResidentSavedData data = get(server);
        for (UUID uuid : new ArrayList<>(data.residentIds(countryName))) {
            Long bed = data.getResidentBed(countryName, uuid);

            for (ServerLevel level : server.getAllLevels()) {
                var entity = level.getEntity(uuid);
                if (entity instanceof Villager villager) {
                    villager.releasePoi(MemoryModuleType.HOME);
                    villager.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                    data.removeResident(countryName, uuid);
                    data.setDirty();
                    return true;
                }
            }

            // If the entity is currently unloaded, remove the persisted
            // residence record. When it becomes loaded again, vanilla HOME
            // data can attach it back to its actual country.
            if (bed != null) {
                data.removeResident(countryName, uuid);
                data.setDirty();
                return true;
            }
        }

        return false;
    }

    /**
     * Reconciles all claimed country chunks with the villagers currently
     * loaded in those chunks. This is what allows a villager brought from
     * another settlement to become a citizen once it selects a local bed.
     */
    public static void reconcileAll(MinecraftServer server) {
        if (server == null || server.overworld() == null) return;

        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null) return;

        for (var country : politics.getCountries().values()) {
            reconcileCountry(server, country.getName());
        }

        LAST_RECONCILE_TICK.put(server, server.overworld().getGameTime());
    }

    private static void reconcileIfNeeded(MinecraftServer server) {
        long tick = server.overworld().getGameTime();
        if (!Long.valueOf(tick).equals(LAST_RECONCILE_TICK.get(server))) {
            reconcileAll(server);
        }
    }

    private static void reconcileCountry(MinecraftServer server, String countryName) {
        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null || politics.getCountry(countryName) == null) return;

        CountryResidentSavedData data = get(server);
        ServerLevel level = server.overworld();
        Set<UUID> seen = new HashSet<>();

        // First validate residents whose entities are currently loaded.
        for (UUID uuid : new ArrayList<>(data.residentIds(countryName))) {
            var entity = level.getEntity(uuid);
            if (entity instanceof Villager villager) {
                seen.add(uuid);
                reconcileLoadedVillager(server, politics, data, villager, countryName);
            }
        }

        // Then discover ordinary villagers living in this country, including
        // villagers imported from a different settlement.
        politics.forEachClaim((chunk, color) -> {
            String owner = politics.getCountryNameAt(chunk);
            if (!countryName.equals(owner)) return;

            int minX = chunk.getMinBlockX();
            int minZ = chunk.getMinBlockZ();

            AABB box = new AABB(
                minX,
                level.getMinBuildHeight(),
                minZ,
                minX + 16.0D,
                level.getMaxBuildHeight(),
                minZ + 16.0D
            );

            for (Villager villager : level.getEntitiesOfClass(Villager.class, box)) {
                if (!seen.add(villager.getUUID())) {
                    continue;
                }

                reconcileLoadedVillager(server, politics, data, villager, countryName);
            }
        });

        data.setDirty();
    }

    private static void reconcileLoadedVillager(
        MinecraftServer server,
        PoliticsManager politics,
        CountryResidentSavedData data,
        Villager villager,
        String targetCountry
    ) {
        GlobalPos home = villager.getBrain()
            .getMemory(MemoryModuleType.HOME)
            .orElse(null);

        String homeCountry = countryCountryAtHome(server, politics, home);

        // A villager that is physically inside the target country but still
        // remembers a bed in another country gets its old POI released. Vanilla
        // AI can then choose an available local bed normally.
        if (homeCountry != null && !targetCountry.equals(homeCountry)) {
            if (politics.getCountryNameAt(villager.chunkPosition()) != null
                && targetCountry.equals(
                    politics.getCountryNameAt(villager.chunkPosition())
                )) {
                villager.releasePoi(MemoryModuleType.HOME);
            }
            return;
        }

        if (!targetCountry.equals(homeCountry)) {
            return;
        }

        BlockPos bedPos = home.pos();
        if (!isValidBed(server.overworld(), bedPos)) {
            villager.releasePoi(MemoryModuleType.HOME);
            data.setResidentBed(targetCountry, villager.getUUID(), null);
            return;
        }

        String currentCountry = data.findCountry(villager.getUUID());
        if (!targetCountry.equals(currentCountry)) {
            if (currentCountry != null) {
                data.removeResident(currentCountry, villager.getUUID());
            }

            String role = roleFromVillager(villager);
            data.addResident(targetCountry, villager.getUUID(), role);
            setCountryTags(villager, targetCountry, role);
        } else if (!data.residentIds(targetCountry).contains(villager.getUUID())) {
            data.addResident(targetCountry, villager.getUUID(), roleFromVillager(villager));
        }

        data.setResidentBed(
            targetCountry,
            villager.getUUID(),
            bedPos.asLong()
        );
    }

    private static String countryCountryAtHome(
        MinecraftServer server,
        PoliticsManager politics,
        GlobalPos home
    ) {
        if (home == null || !home.dimension().equals(Level.OVERWORLD)) {
            return null;
        }

        BlockPos pos = home.pos();
        if (!isValidBed(server.overworld(), pos)) {
            return null;
        }

        return politics.getCountryNameAt(new ChunkPos(pos));
    }

    private static boolean isValidBed(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock()
            instanceof net.minecraft.world.level.block.BedBlock;
    }

    private static int spawnResidents(
        MinecraftServer server,
        String countryName,
        int count,
        boolean growth
    ) {
        if (count <= 0) return 0;

        ServerPlayer anchor = findAnchorPlayer(server, countryName);
        if (anchor == null) return 0;

        CountryResidentSavedData data = get(server);
        int spawned = 0;

        for (int i = 0; i < Math.min(MAX_SPAWNS_PER_PASS, count); i++) {
            String role = chooseRole(server, countryName, data.residentIds(countryName).size());
            Villager villager = spawnResident(server, countryName, role, data.residentIds(countryName).size());
            if (villager == null) break;

            data.addResident(countryName, villager.getUUID(), role);
            setCountryTags(villager, countryName, role);
            spawned++;
        }

        if (spawned > 0) {
            data.setDirty();
            anchor.sendSystemMessage(
                Component.literal(
                    growth
                        ? "Новый житель появился в государстве " + countryName + ". Он должен найти свободное жильё."
                        : "В государстве " + countryName + " появились новые жители."
                ).withStyle(ChatFormatting.GREEN)
            );
        }

        return spawned;
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

        VillagerData villagerData = villager.getVillagerData()
            .setProfession(professionForRole(role))
            .setLevel(1);
        villager.setVillagerData(villagerData);

        villager.moveTo(
            spawnPos.getX() + 0.5D,
            spawnPos.getY(),
            spawnPos.getZ() + 0.5D,
            anchorPlayer.getRandom().nextFloat() * 360.0F,
            0.0F
        );
        villager.setPersistenceRequired();
        villager.addTag(RESIDENT_TAG);
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
        if (bread < 4) {
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

    private static String roleFromVillager(Villager villager) {
        return switch (villager.getVillagerData().profession().toString()) {
            case "minecraft:farmer" -> "FARMER";
            case "minecraft:toolsmith" -> "TOOLSMITH";
            case "minecraft:librarian" -> "LIBRARIAN";
            case "minecraft:armorer" -> "ARMORER";
            default -> "CITIZEN";
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

    private static void setCountryTags(
        Villager villager,
        String countryName,
        String role
    ) {
        for (String tag : new ArrayList<>(villager.getTags())) {
            if (tag.startsWith(COUNTRY_TAG_PREFIX)
                || tag.startsWith(ROLE_TAG_PREFIX)) {
                villager.removeTag(tag);
            }
        }

        villager.addTag(RESIDENT_TAG);
        villager.addTag(COUNTRY_TAG_PREFIX + countryName);
        villager.addTag(ROLE_TAG_PREFIX + role);
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

            if (countryName.equals(politics.getCountryNameAt(player.chunkPosition()))) {
                return player;
            }

            if (fallback == null) fallback = player;
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
                    int y = level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        x,
                        z
                    );

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
}
