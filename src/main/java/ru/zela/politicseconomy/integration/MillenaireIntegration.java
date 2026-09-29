package ru.zela.politicseconomy.integration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.zela.politicseconomy.country.CountryDirection;
import ru.zela.politicseconomy.country.CountryDirectionManager;
import ru.zela.politicseconomy.country.CountryDevelopmentService;
import ru.zela.politicseconomy.country.CountryPolicyManager;
import ru.zela.politicseconomy.country.GovernmentType;
import ru.zela.politicseconomy.country.ReligionType;
import ru.zela.politicseconomy.country.WorkforceSector;
import ru.zela.politicseconomy.country.CountryWorkplaceService;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Optional runtime bridge to Millénaire.
 *
 * <p>The mod does not depend on the Millénaire JAR at compile time. When
 * Millénaire is present, every Village becomes an economic state with its own
 * persistent state key. Live population, buildings, workers, territory,
 * relations and warehouse contents are read directly from Millénaire's
 * Village/VillageSavedData objects.</p>
 */
public final class MillenaireIntegration {
    private static final String VILLAGE_CLASS = "org.millenaire.village.Village";
    private static final String SAVED_DATA_CLASS = "org.millenaire.village.VillageSavedData";
    private static final String STATE_PREFIX = "millenaire:";
    private static final long SYNC_INTERVAL = 40L;
    private static Boolean available;
    private static MinecraftServer cachedServer;
    private static long cachedSecond = Long.MIN_VALUE;
    private static List<VillageSnapshot> cachedSnapshots = List.of();

    private MillenaireIntegration() {}

    public static boolean isAvailable() {
        if (available != null) return available;
        try {
            Class.forName(VILLAGE_CLASS);
            Class.forName(SAVED_DATA_CLASS);
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
        return available;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!isAvailable()) return;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (level == null || level.getGameTime() % SYNC_INTERVAL != 0L) return;

        try {
            for (VillageSnapshot snapshot : snapshots(server)) {
                MillenaireStateSavedData data = MillenaireStateSavedData.get(server);
                data.ensureState(snapshot.villageId(), snapshot.name(), server.getTickCount());
                ensurePolicyDefaults(server, snapshot);
            }
            dataSetDirty(server);
        } catch (Throwable ignored) {
            // Millénaire is optional. One incompatible village/API must never
            // bring down the main PoliticsEconomy loop.
        }
    }

    private static void dataSetDirty(MinecraftServer server) {
        MillenaireStateSavedData.get(server).setDirty();
    }

    public static void ensureState(MinecraftServer server, String stateKey) {
        if (!isStateKey(stateKey)) return;
        UUID id = villageIdFromStateKey(stateKey);
        if (id == null) return;

        Object village = findVillageById(server.overworld(), id);
        if (village == null) return;

        VillageSnapshot snapshot = snapshot(server.overworld(), village);
        MillenaireStateSavedData.get(server).ensureState(
            snapshot.villageId(), snapshot.name(), server.getTickCount()
        );
        ensurePolicyDefaults(server, snapshot);
    }

    private static void ensurePolicyDefaults(MinecraftServer server, VillageSnapshot snapshot) {
        String key = snapshot.stateKey();
        if (CountryDirectionManager.getDirection(server, key) == null) {
            CountryDirectionManager.setDirection(server, key, defaultDirection(snapshot));
        }
        if (CountryPolicyManager.getGovernment(server, key) == null) {
            CountryPolicyManager.setGovernment(server, key, defaultGovernment(snapshot));
        }
        if (CountryPolicyManager.getReligion(server, key) == null) {
            CountryPolicyManager.setReligion(server, key, defaultReligion(snapshot));
        }
    }

    private static CountryDirection defaultDirection(VillageSnapshot snapshot) {
        String type = snapshot.villageType().toLowerCase(Locale.ROOT);
        if (type.contains("trade") || type.contains("market")) {
            return CountryDirection.TRADE;
        }
        if (type.contains("military") || type.contains("fort")) {
            return CountryDirection.INDUSTRIAL;
        }
        return CountryDirection.RESOURCE;
    }

    private static GovernmentType defaultGovernment(VillageSnapshot snapshot) {
        String culture = snapshot.culture().toLowerCase(Locale.ROOT);
        if (culture.contains("indian") || culture.contains("japanese")) {
            return GovernmentType.MONARCHY;
        }
        return GovernmentType.MONARCHY;
    }

    private static ReligionType defaultReligion(VillageSnapshot snapshot) {
        String culture = snapshot.culture().toLowerCase(Locale.ROOT);
        if (culture.contains("byzant") || culture.contains("norman")) {
            return ReligionType.CHRISTIANITY;
        }
        return ReligionType.SECULAR;
    }

    public static boolean isStateKey(String value) {
        return value != null && value.startsWith(STATE_PREFIX);
    }

    public static String stateKey(UUID villageId) {
        return villageId == null ? null : STATE_PREFIX + villageId;
    }

    public static UUID villageIdFromStateKey(String stateKey) {
        if (!isStateKey(stateKey)) return null;
        try {
            return UUID.fromString(stateKey.substring(STATE_PREFIX.length()));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static String stateKey(Object village) {
        UUID id = villageId(village);
        return stateKey(id);
    }

    public static List<VillageSnapshot> snapshots(MinecraftServer server) {
        if (!isAvailable() || server == null || server.overworld() == null) return List.of();
        long second = server.overworld().getGameTime() / 20L;
        if (server != cachedServer || second != cachedSecond) {
            List<VillageSnapshot> result = new ArrayList<>();
            for (Object village : villages(server.overworld())) {
                try {
                    result.add(snapshot(server.overworld(), village));
                } catch (Throwable ignored) {
                }
            }
            cachedServer = server;
            cachedSecond = second;
            cachedSnapshots = List.copyOf(result);
        }
        return cachedSnapshots;
    }

    public static VillageSnapshot snapshotAtChunk(MinecraftServer server, ChunkPos chunk) {
        if (!isAvailable() || server == null || chunk == null) return null;
        for (VillageSnapshot snapshot : snapshots(server)) {
            if (snapshot.territory().contains(chunk)) return snapshot;
        }
        return null;
    }

    public static VillageSnapshot snapshotForStateKey(MinecraftServer server, String stateKey) {
        if (!isStateKey(stateKey) || server == null) return null;
        UUID id = villageIdFromStateKey(stateKey);
        if (id == null) return null;
        for (VillageSnapshot snapshot : snapshots(server)) {
            if (id.equals(snapshot.villageId())) return snapshot;
        }
        return null;
    }

    public static int population(MinecraftServer server, String stateKey) {
        VillageSnapshot snapshot = snapshotForStateKey(server, stateKey);
        return snapshot == null ? 0 : snapshot.adults() + snapshot.children();
    }

    public static int adultPopulation(MinecraftServer server, String stateKey) {
        VillageSnapshot snapshot = snapshotForStateKey(server, stateKey);
        return snapshot == null ? 0 : snapshot.adults();
    }

    public static EnumMap<WorkforceSector, Integer> sectorWorkers(
        MinecraftServer server,
        String stateKey
    ) {
        VillageSnapshot snapshot = snapshotForStateKey(server, stateKey);
        return snapshot == null
            ? emptySectors()
            : new EnumMap<>(snapshot.workersBySector());
    }

    public static CountryWorkplaceService.Snapshot workplaceSnapshot(
        MinecraftServer server,
        String stateKey
    ) {
        VillageSnapshot snapshot = snapshotForStateKey(server, stateKey);
        if (snapshot == null) return CountryWorkplaceService.Snapshot.empty();
        return snapshot.workplaceSnapshot();
    }

    public static Map<String, Integer> warehouse(
        MinecraftServer server,
        String stateKey
    ) {
        VillageSnapshot snapshot = snapshotForStateKey(server, stateKey);
        return snapshot == null ? Map.of() : snapshot.warehouse();
    }

    public static int consumeFromWarehouse(
        MinecraftServer server,
        String stateKey,
        List<String> acceptedItemIds,
        int amount
    ) {
        if (server == null || !isStateKey(stateKey) || amount <= 0 || acceptedItemIds == null || acceptedItemIds.isEmpty()) {
            return 0;
        }

        UUID id = villageIdFromStateKey(stateKey);
        if (id == null) return 0;

        Object village = findVillageById(server.overworld(), id);
        if (village == null) return 0;

        int remaining = amount;
        for (String itemId : acceptedItemIds) {
            if (remaining <= 0) break;
            ResourceLocation resource = parseResource(itemId);
            if (resource == null) continue;

            Item item = BuiltInRegistries.ITEM.getOptional(resource).orElse(null);
            if (item == null) continue;

            Object buildings = invoke(village, "getBuildings");
            if (!(buildings instanceof Iterable<?> iterable)) break;
            for (Object building : iterable) {
                if (remaining <= 0) break;
                Object inventory = invoke(building, "getInventory");
                if (inventory == null) continue;
                int removed = intValue(invoke(inventory, "remove", server.overworld(), item, remaining));
                remaining -= Math.max(0, removed);
            }
        }

        invalidateSnapshotCache();
        return amount - remaining;
    }

    public static int addToWarehouse(
        MinecraftServer server,
        String stateKey,
        String itemId,
        int amount
    ) {
        if (server == null || !isStateKey(stateKey) || amount <= 0 || itemId == null) return 0;

        UUID id = villageIdFromStateKey(stateKey);
        ResourceLocation resource = parseResource(itemId);
        if (id == null || resource == null) return 0;

        Item item = BuiltInRegistries.ITEM.getOptional(resource).orElse(null);
        if (item == null) return 0;

        Object village = findVillageById(server.overworld(), id);
        if (village == null) return 0;

        int remaining = amount;
        Object buildings = invoke(village, "getBuildings");
        if (!(buildings instanceof Iterable<?> iterable)) return 0;

        for (Object building : iterable) {
            if (remaining <= 0) break;
            Object inventory = invoke(building, "getInventory");
            if (inventory == null) continue;

            int added = intValue(invoke(inventory, "add", server.overworld(), item, remaining));
            remaining -= Math.max(0, added);
        }

        invalidateSnapshotCache();
        return amount - remaining;
    }

    private static ResourceLocation parseResource(String raw) {
        try {
            return ResourceLocation.parse(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void invalidateSnapshotCache() {
        cachedSecond = Long.MIN_VALUE;
        cachedSnapshots = List.of();
    }

    public static String displayName(MinecraftServer server, String stateKey) {
        VillageSnapshot snapshot = snapshotForStateKey(server, stateKey);
        return snapshot == null ? stateKey : snapshot.name();
    }

    public static VillageSnapshot snapshot(ServerLevel level, Object village) {
        UUID id = villageId(village);
        String name = stringValue(invoke(village, "getVillageName"));
        ResourceLocation culture = resourceLocation(invoke(village, "getCultureId"));
        ResourceLocation type = resourceLocation(invoke(village, "getVillageTypeId"));
        BlockPos center = (BlockPos) invoke(village, "getCenter");

        int adults = 0;
        int children = 0;
        EnumMap<WorkforceSector, Integer> workers = emptySectors();

        Object villagerMap = invoke(village, "getVillagerRecords");
        if (villagerMap instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                if (value == null) continue;
                boolean killed = booleanValue(invoke(value, "isKilled"));
                if (killed) continue;

                ResourceLocation typeId = resourceLocation(invoke(value, "getVillagerTypeId"));
                String typePath = typeId == null ? "" : typeId.getPath().toLowerCase(Locale.ROOT);
                boolean child = typePath.contains("boy")
                    || typePath.contains("girl")
                    || typePath.contains("child");
                if (child) {
                    children++;
                    continue;
                }

                adults++;
                WorkforceSector sector = classifyVillager(typePath,
                    stringValue(invoke(value, "getRoleName")).toLowerCase(Locale.ROOT));
                if (sector != null) {
                    workers.merge(sector, 1, Integer::sum);
                }
            }
        }

        EnumMap<WorkforceSector, Integer> workplaceCounts = emptySectors();
        EnumMap<WorkforceSector, Integer> workplaceSlots = emptySectors();

        Object buildingList = invoke(village, "getBuildings");
        if (buildingList instanceof Iterable<?> iterable) {
            for (Object building : iterable) {
                if (building == null) continue;
                String status = String.valueOf(invoke(building, "getStatus"));
                boolean operational = booleanValue(invoke(building, "isOperational"));
                if (!operational && !"UPGRADING".equalsIgnoreCase(status)) continue;

                ResourceLocation plan = resourceLocation(invoke(building, "getPlanId"));
                BuildingJob job = classifyBuilding(plan == null ? "" : plan.getPath());
                if (job == null) continue;

                workplaceCounts.merge(job.sector(), 1, Integer::sum);
                workplaceSlots.merge(job.sector(), job.capacity(), Integer::sum);
            }
        }

        Map<String, Integer> warehouse = warehouse(level, village);
        java.util.Set<ChunkPos> territory = new java.util.HashSet<>();
        Object chunks = invoke(village, "computeVillageChunks");
        if (chunks instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                if (value instanceof ChunkPos chunk) territory.add(chunk);
            }
        }

        Map<UUID, Integer> relations = new LinkedHashMap<>();
        Object rels = invoke(village, "getRelations");
        if (rels instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                UUID other = uuidFromVillageId(entry.getKey());
                if (other != null) {
                    relations.put(other, Math.max(-100, Math.min(100, intValue(entry.getValue()))));
                }
            }
        }

        return new VillageSnapshot(
            id,
            stateKey(id),
            name == null || name.isBlank()
                ? (type == null ? id.toString().substring(0, 8) : type.getPath())
                : name,
            culture == null ? "" : culture.toString(),
            type == null ? "" : type.toString(),
            center == null ? BlockPos.ZERO : center,
            adults,
            children,
            workers,
            new CountryWorkplaceService.Snapshot(workplaceCounts, workplaceSlots),
            territory,
            warehouse,
            relations
        );
    }

    private static Map<String, Integer> warehouse(ServerLevel level, Object village) {
        Map<String, Integer> result = new LinkedHashMap<>();
        Object buildings = invoke(village, "getBuildings");
        if (!(buildings instanceof Iterable<?> iterable)) return Map.of();

        for (Object building : iterable) {
            if (building == null) continue;
            Object inventory = invoke(building, "getInventory");
            if (inventory == null) continue;

            Object scanned = invoke(inventory, "scanChests", level);
            Object contents = scanned != null ? scanned : invoke(inventory, "getCachedContents");
            if (!(contents instanceof Map<?, ?> map)) continue;

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof Item item)) continue;
                int amount = intValue(entry.getValue());
                if (amount <= 0) continue;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (id != null) result.merge(id.toString(), amount, Integer::sum);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Object findVillageById(ServerLevel level, UUID id) {
        for (Object village : villages(level)) {
            if (id.equals(villageId(village))) return village;
        }
        return null;
    }

    private static List<Object> villages(ServerLevel level) {
        try {
            Class<?> savedDataClass = Class.forName(SAVED_DATA_CLASS);
            Method get = savedDataClass.getMethod("get", ServerLevel.class);
            Object savedData = get.invoke(null, level);
            Object manager = invoke(savedData, "getVillageManager");
            Object all = invoke(manager, "getAllVillages");
            if (!(all instanceof Iterable<?> iterable)) return List.of();

            List<Object> result = new ArrayList<>();
            for (Object village : iterable) {
                if (village != null) result.add(village);
            }
            return result;
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private static UUID villageId(Object village) {
        return uuidFromVillageId(invoke(village, "getId"));
    }

    private static UUID uuidFromVillageId(Object value) {
        if (value == null) return null;
        Object uuid = invoke(value, "uuid");
        return uuid instanceof UUID u ? u : null;
    }

    private static ResourceLocation resourceLocation(Object value) {
        return value instanceof ResourceLocation r ? r : null;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean b && b;
    }

    private static int intValue(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static Object invoke(Object target, String name, Object... args) {
        if (target == null) return null;
        try {
            Class<?> type = target.getClass();
            for (Method method : type.getMethods()) {
                if (!method.getName().equals(name)) continue;
                if (method.getParameterCount() != args.length) continue;

                Class<?>[] parameterTypes = method.getParameterTypes();
                boolean compatible = true;
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (args[i] == null) continue;
                    if (!wrap(parameterTypes[i]).isInstance(args[i])) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) return method.invoke(target, args);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static WorkforceSector classifyVillager(String type, String role) {
        String text = (type + " " + role).toLowerCase(Locale.ROOT);
        if (containsAny(text, "merchant", "trader", "seller", "shop")) return WorkforceSector.TRADE_LOGISTICS;
        if (containsAny(text, "soldier", "guard", "warrior", "general", "army")) return WorkforceSector.MILITARY;
        if (containsAny(text, "miner", "lumberman", "woodcutter", "hunter", "quarry")) return WorkforceSector.EXTRACTION;
        if (containsAny(text, "smith", "smelter", "toolsmith", "craft", "weaver", "seamster", "dressmaker", "sculptor", "painter")) return WorkforceSector.INDUSTRY;
        if (containsAny(text, "farmer", "farm", "peasant", "fisherman", "fisher", "shepherd", "pastor")) return WorkforceSector.AGRICULTURE;
        if (containsAny(text, "mason", "architect", "worker", "builder", "host", "teacher", "school")) return WorkforceSector.CONSTRUCTION_SERVICES;
        return null;
    }

    private static BuildingJob classifyBuilding(String path) {
        String text = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (containsAny(text, "farm", "field", "grove", "fishfarm", "paddy", "plantation", "sheepfarm")) {
            return new BuildingJob(WorkforceSector.AGRICULTURE, 4);
        }
        if (containsAny(text, "mine", "quarry", "woodcutter", "hunter", "lumber")) {
            return new BuildingJob(WorkforceSector.EXTRACTION, 5);
        }
        if (containsAny(text, "forge", "smelter", "smith", "toolsmith", "workshop", "craftsman", "weaver",
            "seamster", "dressmaker", "sculptor", "mason", "olivepress")) {
            return new BuildingJob(WorkforceSector.INDUSTRY, 5);
        }
        if (containsAny(text, "barracks", "soldier", "armyforge", "watchtower", "fort")) {
            return new BuildingJob(WorkforceSector.MILITARY, 4);
        }
        if (containsAny(text, "market", "trade", "bazaar", "merchant", "shop", "inn", "taverna")) {
            return new BuildingJob(WorkforceSector.TRADE_LOGISTICS, 4);
        }
        if (containsAny(text, "school", "church", "temple", "archives")) {
            return new BuildingJob(WorkforceSector.CONSTRUCTION_SERVICES, 3);
        }
        return null;
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) return true;
        }
        return false;
    }

    private static EnumMap<WorkforceSector, Integer> emptySectors() {
        EnumMap<WorkforceSector, Integer> map = new EnumMap<>(WorkforceSector.class);
        for (WorkforceSector sector : WorkforceSector.values()) map.put(sector, 0);
        return map;
    }

    public record VillageSnapshot(
        UUID villageId,
        String stateKey,
        String name,
        String culture,
        String villageType,
        BlockPos center,
        int adults,
        int children,
        EnumMap<WorkforceSector, Integer> workersBySector,
        CountryWorkplaceService.Snapshot workplaceSnapshot,
        java.util.Set<ChunkPos> territory,
        Map<String, Integer> warehouse,
        Map<UUID, Integer> relations
    ) {
        public VillageSnapshot {
            workersBySector = new EnumMap<>(workersBySector);
            territory = java.util.Set.copyOf(territory);
            warehouse = Map.copyOf(warehouse);
            relations = Map.copyOf(relations);
        }

        public int population() {
            return Math.max(0, adults) + Math.max(0, children);
        }
    }

    private record BuildingJob(WorkforceSector sector, int capacity) {}
}
