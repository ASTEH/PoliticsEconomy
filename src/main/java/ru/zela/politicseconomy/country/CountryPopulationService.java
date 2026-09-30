package ru.zela.politicseconomy.country;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Country population model.
 *
 * Beds are housing capacity only. Actual population is an independent
 * persisted resident count that grows from food, housing and successful
 * economic cycles. Legacy worlds migrate their old bed-derived population once.
 */
public final class CountryPopulationService {
    private static final int RESIDENTS_PER_BED_LEGACY = 2;
    private static final int INITIAL_RESIDENTS = 4;
    private static final int GROWTH_FED_CYCLES = 3;
    private static final int STARVATION_CYCLES_TO_LOSE_RESIDENT = 3;
    private static final int DEVELOPMENT_ACTIVITY_CYCLES = 6;
    private static final double FOOD_PER_RESIDENT_PER_CYCLE = 0.25D;
    private static final List<String> FOOD_ITEMS = List.of(
        "minecraft:bread",
        "minecraft:baked_potato",
        "minecraft:potato",
        "minecraft:carrot",
        "minecraft:beetroot",
        "minecraft:wheat",
        "minecraft:cooked_beef",
        "minecraft:cooked_chicken",
        "minecraft:cooked_porkchop",
        "minecraft:cooked_cod",
        "minecraft:cooked_salmon"
    );
    private static final Map<MinecraftServer, Cache> CACHE = new WeakHashMap<>();

    private CountryPopulationService() {}

    public static CountryPopulationSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                CountryPopulationSavedData::create,
                CountryPopulationSavedData::load,
                null
            ),
            CountryPopulationSavedData.DATA_NAME
        );
    }

    public static int population(MinecraftServer server, String countryName) {
        if (server == null || countryName == null || countryName.isBlank()) return 0;
        if (ru.zela.politicseconomy.integration.MillenaireIntegration.isStateKey(countryName)) {
            return ru.zela.politicseconomy.integration.MillenaireIntegration.population(server, countryName);
        }
        ensureCountryBootstrap(server, countryName);

        CountryPopulationSavedData data = get(server);
        Cache cache = cache(server);
        return cache.byCountry.computeIfAbsent(
            countryName,
            name -> data.getResidents(name)
        );
    }

    public static int cityPopulation(
        MinecraftServer server,
        String countryName,
        String cityName
    ) {
        if (server == null
            || countryName == null || countryName.isBlank()
            || cityName == null || cityName.isBlank()) {
            return 0;
        }

        Cache cache = cache(server);
        String key = countryName + "\n" + cityName;
        return cache.byCity.computeIfAbsent(
            key,
            ignored -> calculateCity(server, countryName, cityName)
        );
    }

    /** Population represented by a specific claimed chunk. */
    public static int populationAtChunk(
        MinecraftServer server,
        ChunkPos chunk
    ) {
        if (server == null || chunk == null) return 0;
        return get(server).getBeds(chunk.toLong());
    }

    /**
     * Boots a PoliticsMod country into the resident system even when the
     * country was created directly by PoliticsMod rather than our helper.
     * This runs idempotently and also grants the one-time four-bed starter pack.
     */
    public static void ensureCountryBootstrap(
        MinecraftServer server,
        String countryName
    ) {
        if (server == null || countryName == null || countryName.isBlank()) return;
        if (ru.zela.politicseconomy.integration.MillenaireIntegration.isStateKey(countryName)) return;

        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null || politics.getCountry(countryName) == null) return;

        CountryPopulationSavedData data = get(server);

        int legacyBeds = 0;
        for (Map.Entry<Long, Integer> entry : data.snapshot().entrySet()) {
            Country owner = politics.getCountryAt(new ChunkPos(entry.getKey()));
            if (owner != null && countryName.equals(owner.getName())) {
                legacyBeds += Math.max(0, entry.getValue());
            }
        }

        // A zero-resident record can exist from an older migration pass.
        // Treat such a country as uninitialized until its one-time starter
        // package has been delivered.
        if (!data.hasResidents(countryName)
            || (data.getResidents(countryName) <= 0 && !data.starterBedsGiven(countryName))) {

            data.setResidents(
                countryName,
                legacyBeds > 0
                    ? legacyBeds * RESIDENTS_PER_BED_LEGACY
                    : INITIAL_RESIDENTS
            );
            data.setFedCycles(countryName, 0);
            data.setStarvationCycles(countryName, 0);
            data.setDevelopmentProgress(countryName, 0);

            var ledger = ru.zela.politicseconomy.economy.NationalMaterialConsumptionService
                .getLedger(server);
            ledger.initializeCountry(countryName);

            if (legacyBeds == 0 && ledger.getStockpile(countryName, "minecraft:bread") <= 0) {
                ledger.addStockpile(countryName, "minecraft:bread", 8);
            }
        }

        if (!data.starterBedsGiven(countryName)) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!countryName.equals(politics.getPlayerCountry(player.getUUID()))) {
                    continue;
                }

                ItemStack beds = new ItemStack(Items.WHITE_BED, 4);
                if (!player.getInventory().add(beds)) {
                    player.drop(beds, false);
                }

                player.sendSystemMessage(
                    Component.literal(
                        "Государство " + countryName
                            + " получило 4 стартовых жителя и 4 кровати."
                    ).withStyle(ChatFormatting.GREEN)
                );
                data.markStarterBedsGiven(countryName);
                break;
            }
        }

        // The resident layer uses normal Minecraft villagers as the physical
        // representation of the persisted population. Calling this here makes
        // newly founded countries receive their four residents immediately.
        CountryResidentService.ensureStarterResidents(server, countryName);

        data.setDirty();
        invalidate(server);
    }

    public static void initializeCountry(MinecraftServer server, String countryName) {
        if (server == null || countryName == null || countryName.isBlank()) return;

        ensureCountryBootstrap(server, countryName);
    }

    public static int housingCapacity(MinecraftServer server, String countryName) {
        if (server == null || countryName == null || countryName.isBlank()) return 0;
        if (ru.zela.politicseconomy.integration.MillenaireIntegration.isStateKey(countryName)) {
            return ru.zela.politicseconomy.integration.MillenaireIntegration.population(server, countryName);
        }

        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null) return 0;

        int beds = 0;
        for (Map.Entry<Long, Integer> entry : get(server).snapshot().entrySet()) {
            Country owner = politics.getCountryAt(new ChunkPos(entry.getKey()));
            if (owner != null && countryName.equals(owner.getName())) {
                beds += Math.max(0, entry.getValue());
            }
        }
        return beds;
    }

    public static void processCycle(MinecraftServer server, String countryName) {
        if (server == null || countryName == null || countryName.isBlank()) return;
        if (ru.zela.politicseconomy.integration.MillenaireIntegration.isStateKey(countryName)) return;

        ensureCountryBootstrap(server, countryName);

        CountryPopulationSavedData data = get(server);
        int residents = data.getResidents(countryName);
        if (residents <= 0) return;

        int housing = housingCapacity(server, countryName);
        double requiredRaw =
            data.getFoodRemainder(countryName)
                + residents * FOOD_PER_RESIDENT_PER_CYCLE;
        int requiredFood = Math.max(0, (int) Math.floor(requiredRaw + 1.0E-9D));
        double nextRemainder = Math.max(0.0D, requiredRaw - requiredFood);

        int consumed = ru.zela.politicseconomy.economy.NationalMaterialConsumptionService
            .getLedger(server)
            .consumeAccepted(countryName, FOOD_ITEMS, requiredFood);

        if (consumed >= requiredFood) {
            data.setFoodRemainder(countryName, nextRemainder);
            data.setStarvationCycles(countryName, 0);

            int fedCycles = data.getFedCycles(countryName) + 1;
            data.setFedCycles(countryName, fedCycles);

            if (housing > residents && fedCycles >= GROWTH_FED_CYCLES) {
                residents++;
                data.setResidents(countryName, residents);
                data.setFedCycles(countryName, 0);

                ru.zela.politicseconomy.event.NewsService.add(
                    server,
                    server.overworld().getGameTime(),
                    "ОБЩЕСТВО",
                    countryName + ": рост населения",
                    "Благодаря достатку еды и свободному жилью население выросло до " + residents + "."
                );
            }

            int developmentProgress = data.getDevelopmentProgress(countryName) + 1;
            if (developmentProgress >= DEVELOPMENT_ACTIVITY_CYCLES) {
                developmentProgress = 0;
                CountryDevelopmentService.addActivity(server, countryName, 1);
            }
            data.setDevelopmentProgress(countryName, developmentProgress);
        } else {
            data.setFoodRemainder(countryName, nextRemainder);
            data.setFedCycles(countryName, 0);

            int starvationCycles = data.getStarvationCycles(countryName) + 1;
            data.setStarvationCycles(countryName, starvationCycles);

            if (starvationCycles >= STARVATION_CYCLES_TO_LOSE_RESIDENT) {
                residents = Math.max(1, residents - 1);
                data.setResidents(countryName, residents);
                data.setStarvationCycles(countryName, 0);

                ru.zela.politicseconomy.event.NewsService.add(
                    server,
                    server.overworld().getGameTime(),
                    "ОБЩЕСТВО",
                    countryName + ": нехватка продовольствия",
                    "Запасов еды не хватило для населения. Численность снизилась до " + residents + "."
                );
            }
        }

        // Keep the visible villager population synchronized with the same
        // number that drives the economic system. Growth creates a resident;
        // starvation removes one from the physical registry.
        CountryResidentService.syncResidents(server, countryName);

        data.setDirty();
        invalidate(server);
    }

    private static void migrateLegacyPopulation(
        MinecraftServer server,
        String countryName,
        CountryPopulationSavedData data
    ) {
        PoliticsManager politics = PoliticsManager.get(server.overworld());
        int legacyBeds = 0;

        if (politics != null) {
            for (Map.Entry<Long, Integer> entry : data.snapshot().entrySet()) {
                Country owner = politics.getCountryAt(new ChunkPos(entry.getKey()));
                if (owner != null && countryName.equals(owner.getName())) {
                    legacyBeds += Math.max(0, entry.getValue());
                }
            }
        }

        data.setResidents(
            countryName,
            legacyBeds > 0
                ? legacyBeds * RESIDENTS_PER_BED_LEGACY
                : INITIAL_RESIDENTS
        );
        data.setFedCycles(countryName, 0);
        data.setStarvationCycles(countryName, 0);
        data.setDevelopmentProgress(countryName, 0);
    }

    /** Detects newly created PoliticsMod countries and initializes their residents. */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null || server.overworld() == null) return;
        if (server.overworld().getGameTime() % 20L != 0L) return;

        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null) return;

        for (Country country : politics.getCountries().values()) {
            ensureCountryBootstrap(server, country.getName());
        }
    }

    /** Rebuilds a loaded chunk's bed count after the chunk is loaded. */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        refreshChunk(level, chunk);
    }

    /**
     * Refreshes a bed's chunk and its neighboring chunks after a block change.
     * The task is delayed until after the break/place operation is complete.
     */
    public static void onBedChange(ServerLevel level, BlockPos pos) {
        level.getServer().execute(() -> refreshAround(level, pos));
    }

    /** Called after a player places a bed so invalid housing gets an immediate on-screen warning. */
    public static void onBedPlaced(ServerLevel level, BlockPos pos, ServerPlayer player) {
        level.getServer().execute(() -> {
            BlockPos head = findBedHead(level, pos);
            if (head != null && !isValidBed(level, head)) {
                player.displayClientMessage(
                    Component.literal("Кровать не засчитана: над ней нет крыши в пределах 5 блоков. Население не добавится.")
                        .withStyle(ChatFormatting.YELLOW),
                    true
                );
            } else if (head != null
                && PoliticsManager.get(level) != null
                && PoliticsManager.get(level).getCountryAt(new ChunkPos(head)) == null) {
                player.displayClientMessage(
                    Component.literal("Кровать не засчитана: этот чанк не принадлежит государству. Население не добавится.")
                        .withStyle(ChatFormatting.YELLOW),
                    true
                );
            }
            refreshAround(level, pos);
        });
    }

    /** Resolves either half of a bed to its HEAD block. */
    private static BlockPos findBedHead(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof BedBlock) {
            if (state.getValue(BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
                return pos;
            }
            Direction facing = state.getValue(BedBlock.FACING);
            BlockPos candidate = pos.relative(facing);
            var candidateState = level.getBlockState(candidate);
            if (candidateState.getBlock() instanceof BedBlock
                && candidateState.getValue(BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
                return candidate;
            }
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = pos.relative(direction);
            var candidateState = level.getBlockState(candidate);
            if (candidateState.getBlock() instanceof BedBlock
                && candidateState.getValue(BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
                return candidate;
            }
        }
        return null;
    }

    private static void refreshAround(ServerLevel level, BlockPos pos) {
        int centerX = pos.getX() >> 4;
        int centerZ = pos.getZ() >> 4;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                refreshChunk(level, level.getChunk(centerX + dx, centerZ + dz));
            }
        }
    }

    public static void refreshChunk(ServerLevel level, LevelChunk chunk) {
        int beds = countBeds(level, chunk);
        get(level.getServer()).setBeds(chunk.getPos().toLong(), beds);
        invalidate(level.getServer());
    }

    /** Counts every valid bed head; beds may be stacked vertically at the same X/Z. */
    private static int countBeds(ServerLevel level, LevelChunk chunk) {
        int validBeds = 0;

        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    var state = chunk.getBlockState(pos);
                    if (state.getBlock() instanceof BedBlock
                        && state.getValue(BedBlock.PART)
                        == net.minecraft.world.level.block.state.properties.BedPart.HEAD
                        && hasRoof(level, pos)) {
                        validBeds++;
                    }
                }
            }
        }

        return validBeds;
    }

    /** A bed is housing only when there is a solid roof within five blocks above its head. */
    private static boolean isValidBed(ServerLevel level, BlockPos bedHead) {
        return hasRoof(level, bedHead);
    }

    /**
     * Uses the same practical roof definition as PoliticsMod's old residential
     * scanner: any solid-rendering block within five blocks above the bed head.
     */
    private static boolean hasRoof(ServerLevel level, BlockPos bedHead) {
        for (int i = 1; i <= 5; i++) {
            BlockPos above = bedHead.above(i);
            var state = level.getBlockState(above);
            if (state.isSolidRender(level, above)) {
                return true;
            }
        }
        return false;
    }

    private static int calculateCountry(
        MinecraftServer server,
        String countryName
    ) {
        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null || politics.getCountry(countryName) == null) return 0;

        int totalBeds = 0;
        for (Map.Entry<Long, Integer> entry : get(server).snapshot().entrySet()) {
            Country owner = politics.getCountryAt(new ChunkPos(entry.getKey()));
            if (owner != null && countryName.equals(owner.getName())) {
                totalBeds += Math.max(0, entry.getValue());
            }
        }
        return population(server, countryName);
    }

    private static int calculateCity(
        MinecraftServer server,
        String countryName,
        String cityName
    ) {
        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null || politics.getCountry(countryName) == null) return 0;

        int totalBeds = 0;
        for (Map.Entry<Long, Integer> entry : get(server).snapshot().entrySet()) {
            ChunkPos chunk = new ChunkPos(entry.getKey());
            Country owner = politics.getCountryAt(chunk);
            if (owner == null || !countryName.equals(owner.getName())) continue;
            if (!cityName.equals(politics.getCityAt(chunk))) continue;
            totalBeds += Math.max(0, entry.getValue());
        }
        return totalBeds;
    }

    private static Cache cache(MinecraftServer server) {
        long second = server.overworld().getGameTime() / 20L;
        Cache cache = CACHE.get(server);
        if (cache == null || cache.second != second) {
            cache = new Cache(second);
            CACHE.put(server, cache);
        }
        return cache;
    }

    public static void invalidate(MinecraftServer server) {
        CACHE.remove(server);
    }

    private static final class Cache {
        private final long second;
        private final Map<String, Integer> byCountry = new HashMap<>();
        private final Map<String, Integer> byCity = new HashMap<>();

        private Cache(long second) {
            this.second = second;
        }
    }
}
