package ru.zela.politicseconomy.country;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.zela.politicseconomy.economy.NationalMaterialConsumptionService;
import ru.zela.politicseconomy.economy.NationalMaterialLedgerSavedData;
import ru.zela.politicseconomy.event.NewsService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Slow, population-aware migration between Politics Economy countries.
 *
 * Migration is deliberately gradual:
 * - checked once per minute;
 * - at most two residents can migrate during one pass on the whole server;
 * - a source country keeps at least four occupied-bed residents;
 * - a move happens only when another country is meaningfully more attractive;
 * - the villager is physically teleported to the target settlement.
 *
 * A migrated villager is registered in the target country immediately, but its
 * population does not increase until vanilla AI assigns it a target country's
 * bed through HOME memory. This preserves the "occupied bed = population" rule.
 */
public final class CountryMigrationService {
    private static final long MIGRATION_INTERVAL_TICKS = 1200L; // 60 seconds
    private static final int MIN_SOURCE_POPULATION = 6;
    private static final int MIN_REMAINING_POPULATION = 4;
    private static final int MAX_MIGRATIONS_PER_PASS = 2;
    private static final long RESIDENT_MIGRATION_COOLDOWN_TICKS = 12000L; // 10 minutes

    private static final Map<MinecraftServer, Map<UUID, Long>> LAST_MIGRATION_TICK =
        new WeakHashMap<>();

    private static final double MIN_ATTRACTIVENESS_ADVANTAGE = 12.0D;
    private static final double BASE_MIGRATION_CHANCE = 0.05D;
    private static final double MAX_MIGRATION_CHANCE = 0.20D;

    private static final int FOOD_SCORE_CAP = 25;
    private static final int HOUSING_SCORE_CAP = 25;
    private static final int DEVELOPMENT_SCORE_CAP = 40;
    private static final int DEBT_PENALTY = 25;

    private CountryMigrationService() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null || server.overworld() == null) return;

        long tick = server.overworld().getGameTime();
        if (tick <= 0L || tick % MIGRATION_INTERVAL_TICKS != 0L) return;

        processMigrationPass(server);
    }

    private static void processMigrationPass(MinecraftServer server) {
        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null) return;

        List<String> countries = new ArrayList<>();
        for (Country country : politics.getCountries().values()) {
            if (country != null && country.getName() != null && !country.getName().isBlank()) {
                countries.add(country.getName());
            }
        }

        if (countries.size() < 2) return;

        Collections.shuffle(countries, new java.util.Random(server.overworld().getGameTime()));

        int migrations = 0;
        for (String sourceCountry : countries) {
            if (migrations >= MAX_MIGRATIONS_PER_PASS) break;

            int sourcePopulation = CountryResidentService.population(server, sourceCountry);
            if (sourcePopulation < MIN_SOURCE_POPULATION
                || sourcePopulation <= MIN_REMAINING_POPULATION) {
                continue;
            }

            Villager villager = findLoadedResident(server, sourceCountry);
            if (villager == null) continue;

            MigrationTarget target = findBestTarget(server, sourceCountry, countries);
            if (target == null) continue;

            double advantage = target.score() - attractiveness(server, sourceCountry);
            if (advantage < MIN_ATTRACTIVENESS_ADVANTAGE) continue;

            double chance = Math.min(
                MAX_MIGRATION_CHANCE,
                BASE_MIGRATION_CHANCE + advantage * 0.005D
            );

            if (server.overworld().getRandom().nextDouble() >= chance) continue;

            long lastMigration = LAST_MIGRATION_TICK
                .getOrDefault(server, Map.of())
                .getOrDefault(villager.getUUID(), Long.MIN_VALUE);
            long now = server.overworld().getGameTime();
            if (lastMigration != Long.MIN_VALUE
                && now - lastMigration < RESIDENT_MIGRATION_COOLDOWN_TICKS) {
                continue;
            }

            if (migrateVillager(server, villager, sourceCountry, target.countryName())) {
                migrations++;
            }
        }
    }

    private static MigrationTarget findBestTarget(
        MinecraftServer server,
        String sourceCountry,
        List<String> countries
    ) {
        MigrationTarget best = null;

        for (String targetCountry : countries) {
            if (targetCountry.equals(sourceCountry)) continue;

            int population = CountryResidentService.population(server, targetCountry);
            int housing = CountryPopulationService.housingCapacity(server, targetCountry);
            int freeHousing = Math.max(0, housing - population);

            // There must be at least one free bed in the target country.
            // The villager will still need to claim it through normal HOME AI.
            if (freeHousing <= 0) continue;

            // Do not move villagers into countries with no physical settlement
            // anchor. An online player or an already loaded resident gives us a
            // safe location for the physical teleport.
            if (findTargetAnchor(server, targetCountry) == null) continue;

            double score = attractiveness(server, targetCountry);
            if (best == null || score > best.score()) {
                best = new MigrationTarget(targetCountry, score);
            }
        }

        return best;
    }

    private static double attractiveness(MinecraftServer server, String countryName) {
        int population = CountryResidentService.population(server, countryName);
        int housing = CountryPopulationService.housingCapacity(server, countryName);
        int freeHousing = Math.max(0, housing - population);

        double housingRatio = population <= 0
            ? 1.0D
            : Math.min(1.0D, freeHousing / (double) Math.max(1, population));

        double housingScore = Math.min(
            HOUSING_SCORE_CAP,
            housingRatio * HOUSING_SCORE_CAP
        );

        double foodStock = foodStockpile(server, countryName);
        double foodPerResident = foodStock / (double) Math.max(1, population);
        double foodScore = Math.min(FOOD_SCORE_CAP, foodPerResident * 1.5D);

        int developmentLevel = CountryDevelopmentService.level(server, countryName);
        double developmentScore = Math.min(
            DEVELOPMENT_SCORE_CAP,
            Math.max(0, developmentLevel - 1) * 10.0D
        );

        NationalMaterialLedgerSavedData ledger =
            NationalMaterialConsumptionService.getLedger(server);

        double debtPenalty = ledger.hasAnyDebt(countryName)
            ? Math.min(DEBT_PENALTY, 8.0D + ledger.totalDebt(countryName) * 0.02D)
            : 0.0D;

        // Mild overcrowding pressure prevents very large countries from
        // automatically attracting everyone just because they are developed.
        double overcrowdingPenalty = population > 0 && housing > 0 && population >= housing
            ? 12.0D
            : 0.0D;

        return housingScore + foodScore + developmentScore
            - debtPenalty - overcrowdingPenalty;
    }

    private static double foodStockpile(MinecraftServer server, String countryName) {
        NationalMaterialLedgerSavedData ledger =
            NationalMaterialConsumptionService.getLedger(server);

        String[] foods = {
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
        };

        long total = 0L;
        for (String food : foods) {
            total += Math.max(0, ledger.getStockpile(countryName, food));
            if (total >= 10000L) return 10000.0D;
        }

        return total;
    }

    private static Villager findLoadedResident(
        MinecraftServer server,
        String countryName
    ) {
        CountryResidentSavedData data = CountryResidentService.get(server);

        for (UUID uuid : data.residentIds(countryName)) {
            Long bed = data.getResidentBed(countryName, uuid);
            if (bed == null) continue;

            var entity = server.overworld().getEntity(uuid);
            if (entity instanceof Villager villager && !villager.isRemoved()) {
                return villager;
            }
        }

        return null;
    }

    private static Vec3 findTargetAnchor(
        MinecraftServer server,
        String countryName
    ) {
        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null) return null;

        for (var player : server.getPlayerList().getPlayers()) {
            if (countryName.equals(politics.getPlayerCountry(player.getUUID()))) {
                return player.position();
            }
        }

        CountryResidentSavedData data = CountryResidentService.get(server);
        for (UUID uuid : data.residentIds(countryName)) {
            var entity = server.overworld().getEntity(uuid);
            if (entity instanceof Villager villager && !villager.isRemoved()) {
                return villager.position();
            }
        }

        return null;
    }

    private static boolean migrateVillager(
        MinecraftServer server,
        Villager villager,
        String sourceCountry,
        String targetCountry
    ) {
        CountryResidentSavedData data = CountryResidentService.get(server);

        Vec3 targetAnchor = findTargetAnchor(server, targetCountry);
        if (targetAnchor == null) return false;

        UUID uuid = villager.getUUID();
        String role = data.snapshot(sourceCountry).getOrDefault(
            uuid,
            "CITIZEN"
        );

        // Release the source bed first so source population immediately drops
        // through the occupied-bed model.
        villager.releasePoi(MemoryModuleType.HOME);
        data.removeResident(sourceCountry, uuid);
        data.addResident(targetCountry, uuid, role);
        data.setResidentBed(targetCountry, uuid, null);
        data.setDirty();

        double angle = server.overworld().getRandom().nextDouble() * Math.PI * 2.0D;
        double distance = 2.0D + server.overworld().getRandom().nextDouble() * 3.0D;

        double targetX = targetAnchor.x + Math.cos(angle) * distance;
        double targetY = targetAnchor.y;
        double targetZ = targetAnchor.z + Math.sin(angle) * distance;

        villager.teleportTo(targetX, targetY, targetZ);
        villager.getBrain().eraseMemory(MemoryModuleType.HOME);
        setCountryTags(villager, targetCountry, role);
        LAST_MIGRATION_TICK
            .computeIfAbsent(server, ignored -> new HashMap<>())
            .put(uuid, server.overworld().getGameTime());

        String message =
            "Житель из страны «" + sourceCountry
                + "» решил, что в стране «" + targetCountry
                + "» ему будет лучше, и переехал.";

        Component chatMessage = Component.literal("[Миграция] ")
            .withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(message).withStyle(ChatFormatting.WHITE));

        server.getPlayerList().broadcastSystemMessage(chatMessage, false);

        NewsService.add(
            server,
            server.overworld().getGameTime(),
            "ОБЩЕСТВО",
            "Миграция: " + sourceCountry + " → " + targetCountry,
            message
        );

        return true;
    }

    private static void setCountryTags(
        Villager villager,
        String countryName,
        String role
    ) {
        String countryPrefix = "politicseconomy_country:";
        String rolePrefix = "politicseconomy_role:";

        for (String tag : new ArrayList<>(villager.getTags())) {
            if (tag.startsWith(countryPrefix) || tag.startsWith(rolePrefix)) {
                villager.removeTag(tag);
            }
        }

        villager.addTag("politicseconomy_resident");
        villager.addTag(countryPrefix + countryName);
        villager.addTag(rolePrefix + role);
    }

    private record MigrationTarget(String countryName, double score) {}
}
