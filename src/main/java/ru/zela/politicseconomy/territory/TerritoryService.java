package ru.zela.politicseconomy.territory;

import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.zela.politicseconomy.infrastructure.InfrastructureManager;
import ru.zela.politicseconomy.map.PoliticalMapService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class TerritoryService {
    private static final String DATA_NAME = "politicseconomy_territory";
    private static final int CAPTURE_TICKS = 20 * 120;
    private static final int ACTIONBAR_INTERVAL = 10;
    private static long lastMapBroadcastTick = Long.MIN_VALUE;

    private TerritoryService() {}

    public static TerritorySavedData getData(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(TerritorySavedData::create, TerritorySavedData::load, null),
            DATA_NAME
        );
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        processOccupations(event.getServer());
    }

    public static boolean buyFreeChunk(ServerPlayer player) {
        if (player == null || player.serverLevel().dimension() != Level.OVERWORLD) return false;
        PoliticsManager politics = PoliticsManager.get(player.serverLevel());
        if (politics == null) return false;

        String countryName = politics.getPlayerCountry(player.getUUID());
        if (countryName == null) return fail(player, "Сначала вступи или создай страну.");
        Country country = politics.getCountry(countryName);
        if (country == null) return false;

        ChunkPos target = player.chunkPosition();
        if (politics.getCountryNameAt(target) != null) {
            return fail(player, "Этот чанк уже принадлежит государству.");
        }
        if (!hasOwnedNeighbor(politics, target, countryName)) {
            return fail(player, "Можно покупать только соседний со своей территорией чанк.");
        }

        int price = claimPrice(player.getServer(), countryName);
        int ownedBefore = claimedChunkCount(player.getServer(), countryName);
        int cycleUpkeepAfter = territoryUpkeep(player.getServer(), countryName, ownedBefore + 1);
        if (!player.isCreative() && country.balance < price) {
            return fail(player, "Недостаточно денег в казне. Нужно $" + price + ".");
        }

        if (!player.isCreative()) country.balance -= price;
        politics.claimChunk(target, 0, countryName, null);
        politics.saveData();

        player.sendSystemMessage(Component.literal(
            "Чанк " + target.x + ", " + target.z + " присоединён за $" + price
                + ". Содержание территории теперь: $" + cycleUpkeepAfter + "/цикл."
        ).withStyle(ChatFormatting.GREEN));
        PoliticalMapService.syncAll(player.getServer());
        return true;
    }

    public static void handleClaimPacket(
        ServerPlayer player,
        BlockPos payloadCenter,
        String payloadCountry,
        String payloadCity,
        boolean isCityClaim
    ) {
        PoliticsManager politics = PoliticsManager.get(player.serverLevel());
        if (politics == null || player.serverLevel().dimension() != Level.OVERWORLD) return;

        String ownCountry = politics.getPlayerCountry(player.getUUID());
        if (ownCountry == null || !ownCountry.equals(payloadCountry)) {
            fail(player, "Нельзя изменять территорию чужого государства.");
            return;
        }
        if (!player.chunkPosition().equals(new ChunkPos(payloadCenter))) {
            fail(player, "Территорию можно менять только в текущем чанке.");
            return;
        }

        if (!isCityClaim) {
            buyFreeChunk(player);
            return;
        }

        Country country = politics.getCountry(ownCountry);
        if (country == null) return;
        CountryRole role = country.getRole(player.getUUID());
        if (role != CountryRole.LEADER && role != CountryRole.MAYOR && !player.isCreative()) {
            fail(player, "Добавлять чанк к городу может лидер или мэр.");
            return;
        }
        if (payloadCity == null || payloadCity.isBlank() || !politics.hasCity(ownCountry, payloadCity)) {
            fail(player, "Такого города нет.");
            return;
        }

        ChunkPos chunk = player.chunkPosition();
        if (!ownCountry.equals(politics.getCountryNameAt(chunk))) {
            fail(player, "Городской чанк должен находиться внутри твоего государства.");
            return;
        }
        if (politics.getCityAt(chunk) != null) {
            fail(player, "Этот чанк уже относится к городу.");
            return;
        }

        int price = PoliticsConfig.get().cityChunkPrice;
        if (!player.isCreative() && country.balance < price) {
            fail(player, "Недостаточно денег в казне. Нужно $" + price + ".");
            return;
        }
        if (!player.isCreative()) country.balance -= price;

        politics.claimChunk(chunk, 0, ownCountry, payloadCity);
        politics.saveData();
        player.sendSystemMessage(Component.literal(
            "Чанк добавлен к городу " + payloadCity + " за $" + price + "."
        ).withStyle(ChatFormatting.GREEN));
        PoliticalMapService.syncAll(player.getServer());
    }

    public static boolean canCreateCountry(ServerPlayer player, BlockPos payloadCenter) {
        if (player == null || payloadCenter == null) return false;
        PoliticsManager politics = PoliticsManager.get(player.serverLevel());
        if (politics == null) return false;

        if (!player.chunkPosition().equals(new ChunkPos(payloadCenter))) {
            return fail(player, "Столицу можно основать только в текущем чанке.");
        }

        ChunkPos center = new ChunkPos(payloadCenter);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (politics.getCountryNameAt(new ChunkPos(center.x + x, center.z + z)) != null) {
                    return fail(player, "Нельзя основать государство поверх чужой территории.");
                }
            }
        }
        return true;
    }

    public static boolean canFoundCity(ServerPlayer player, BlockPos payloadCenter, String cityName) {
        if (player == null || payloadCenter == null) return false;
        PoliticsManager politics = PoliticsManager.get(player.serverLevel());
        if (politics == null) return false;

        String countryName = politics.getCountryByOwner(player.getUUID());
        if (countryName == null) {
            return fail(player, "Город может основать только лидер государства.");
        }
        if (!player.chunkPosition().equals(new ChunkPos(payloadCenter))) {
            return fail(player, "Город можно основать только в текущем чанке.");
        }

        ChunkPos center = new ChunkPos(payloadCenter);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (!countryName.equals(
                    politics.getCountryNameAt(new ChunkPos(center.x + x, center.z + z))
                )) {
                    return fail(player, "Все 3x3 чанка будущего города должны уже принадлежать твоему государству.");
                }
            }
        }
        return true;
    }

    public static void handleCreateCountry(ServerPlayer player, BlockPos payloadCenter, String countryName) {
        if (!canCreateCountry(player, payloadCenter)) return;

        PoliticsManager.createCountry(player.serverLevel(), payloadCenter, player, countryName);

        PoliticsManager politics = PoliticsManager.get(player.serverLevel());
        String createdCountry = politics == null
            ? null
            : (politics.getCountry(countryName) != null
                ? countryName
                : politics.getPlayerCountry(player.getUUID()));

        if (createdCountry != null && !createdCountry.isBlank()) {
            ru.zela.politicseconomy.country.CountryPopulationService.initializeCountry(
                player.getServer(), createdCountry
            );

            ItemStack beds = new ItemStack(net.minecraft.world.item.Items.WHITE_BED, 4);
            if (!player.getInventory().add(beds)) {
                player.drop(beds, false);
            }

            player.sendSystemMessage(Component.literal(
                "Государство основано: 4 жителя зарегистрированы. "
                    + "Тебе выданы 4 кровати — они определяют доступное жильё."
            ).withStyle(ChatFormatting.GREEN));
        }

        PoliticalMapService.syncAll(player.getServer());
    }

    public static void handleFoundCity(ServerPlayer player, BlockPos payloadCenter, String cityName) {
        if (!canFoundCity(player, payloadCenter, cityName)) return;
        PoliticsManager politics = PoliticsManager.get(player.serverLevel());
        if (politics == null) return;
        politics.foundNewCity(payloadCenter, player, cityName);
        PoliticalMapService.syncAll(player.getServer());
    }

    public static boolean canPlaceAdministrativeBlock(ServerPlayer player, ChunkPos chunk, String blockId) {
        PoliticsManager politics = PoliticsManager.get(player.serverLevel());
        if (politics == null || blockId == null) return true;

        String owner = politics.getCountryNameAt(chunk);
        String playerCountry = politics.getPlayerCountry(player.getUUID());

        if (blockId.endsWith("founding_stone")) {
            if (owner != null && (playerCountry == null || !owner.equals(playerCountry))) {
                fail(player, "Камень основания нельзя устанавливать на территории другого государства.");
                return false;
            }
        }

        if (blockId.endsWith("city_stone")) {
            if (owner == null) return fail(player, "Камень города можно устанавливать только на своей территории.");
            if (playerCountry == null || !owner.equals(playerCountry)) {
                return fail(player, "Камень города нельзя устанавливать на территории другого государства.");
            }
        }

        return true;
    }

    /**
     * Returns the number of chunks currently owned by the country.
     * PoliticsMod counts every claimed chunk for territory upkeep, so this is
     * the same population of territory used by the vanilla PoliticsMod cycle.
     */
    public static int claimedChunkCount(MinecraftServer server, String countryName) {
        if (server == null || countryName == null || countryName.isBlank()) return 0;
        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null) return 0;

        final int[] count = {0};
        politics.forEachClaim((pos, color) -> {
            if (countryName.equals(politics.getCountryNameAt(pos))) count[0]++;
        });
        return count[0];
    }

    /**
     * Dynamic price for the next free expansion chunk.
     * Every already-owned chunk adds one percentage point to the base price.
     * Example with a $500 base price: 25 chunks => $625 for the next one,
     * 50 chunks => $750, 100 chunks => $1000.
     */
    public static int claimPrice(MinecraftServer server, String countryName) {
        int owned = claimedChunkCount(server, countryName);
        double multiplier = 1.0D + owned * 0.01D;
        return Math.max(1, (int) Math.round(PoliticsConfig.get().countryChunkPrice * multiplier));
    }

    /**
     * Total territory upkeep for the supplied number of owned chunks.
     * The actual deduction remains in PoliticsMod.processEconomyCycle(); this
     * helper only exposes the amount to the Politics Economy UI/commands.
     */
    public static int territoryUpkeep(MinecraftServer server, String countryName) {
        int owned = claimedChunkCount(server, countryName);
        return territoryUpkeep(server, countryName, owned);
    }

    public static int territoryUpkeep(MinecraftServer server, String countryName, int ownedChunks) {
        long upkeep = (long) Math.max(0, ownedChunks) * Math.max(0, PoliticsConfig.get().upkeepPerChunk);
        return upkeep > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) upkeep;
    }

    public static String occupationProgressText(MinecraftServer server, ChunkPos chunk) {
        Occupation occupation = getData(server).getOccupation(chunk.toLong());
        if (occupation == null) return null;
        int seconds = occupation.progressTicks / 20;
        return "Оккупация: " + seconds + " / " + (CAPTURE_TICKS / 20) + " сек.";
    }

    private static void processOccupations(MinecraftServer server) {
        ServerLevel level = server.overworld();
        PoliticsManager politics = PoliticsManager.get(level);
        if (politics == null) return;

        TerritorySavedData data = getData(server);
        boolean dirty = false;
        boolean capturedAny = false;

        for (Occupation occupation : data.snapshot().values()) {
            ChunkPos chunk = new ChunkPos(occupation.chunkLong);
            String currentOwner = politics.getCountryNameAt(chunk);

            if (currentOwner == null
                || !occupation.defenderCountry.equals(currentOwner)
                || politics.getCountry(occupation.attackerCountry) == null
                || politics.getCountry(occupation.defenderCountry) == null
                || !politics.isAtWar(occupation.attackerCountry, occupation.defenderCountry)
                || !hasOwnedNeighbor(politics, chunk, occupation.attackerCountry)) {
                data.removeOccupation(occupation.chunkLong);
                dirty = true;
                continue;
            }

            int attackers = 0;
            int defenders = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.serverLevel() != level || !player.chunkPosition().equals(chunk)) continue;
                String pc = politics.getPlayerCountry(player.getUUID());
                if (occupation.attackerCountry.equals(pc)) attackers++;
                if (occupation.defenderCountry.equals(pc)) defenders++;
            }

            int delta = Math.max(-2, Math.min(2, attackers - defenders));
            if (delta == 0 && attackers == 0) delta = -1;
            occupation.progressTicks = Math.max(0, Math.min(CAPTURE_TICKS, occupation.progressTicks + delta));
            dirty = true;

            if (attackers > 0 && server.overworld().getGameTime() % ACTIONBAR_INTERVAL == 0) {
                int percent = (int) Math.round(occupation.progressTicks * 100.0D / CAPTURE_TICKS);
                sendProgress(server, chunk, occupation, percent);
            }

            if (occupation.progressTicks >= CAPTURE_TICKS) {
                captureChunk(server, politics, occupation, chunk);
                data.removeOccupation(occupation.chunkLong);
                dirty = true;
                capturedAny = true;
            }
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.serverLevel() != level) continue;
            String attacker = politics.getPlayerCountry(player.getUUID());
            if (attacker == null) continue;

            ChunkPos chunk = player.chunkPosition();
            String defender = politics.getCountryNameAt(chunk);
            if (defender == null || attacker.equals(defender)) continue;
            if (!politics.isAtWar(attacker, defender)) continue;
            if (!hasOwnedNeighbor(politics, chunk, attacker)) continue;

            long key = chunk.toLong();
            if (data.getOccupation(key) == null) {
                data.putOccupation(new Occupation(key, attacker, defender, 0));
                dirty = true;
            }
        }

        if (dirty) data.setDirty();
        if (capturedAny && server.overworld().getGameTime() - lastMapBroadcastTick > 5) {
            lastMapBroadcastTick = server.overworld().getGameTime();
            PoliticalMapService.syncAll(server);
        }
    }

    private static void sendProgress(MinecraftServer server, ChunkPos chunk, Occupation occupation, int percent) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.serverLevel() != server.overworld() || !player.chunkPosition().equals(chunk)) continue;
            String pc = PoliticsManager.get(player.serverLevel()).getPlayerCountry(player.getUUID());
            if (occupation.attackerCountry.equals(pc) || occupation.defenderCountry.equals(pc)) {
                player.displayClientMessage(
                    Component.literal("Оккупация: " + percent + "%")
                        .withStyle(percent >= 75 ? ChatFormatting.GOLD : ChatFormatting.YELLOW),
                    true
                );
            }
        }
    }

    private static void captureChunk(MinecraftServer server, PoliticsManager politics, Occupation occupation, ChunkPos chunk) {
        politics.transferChunk(chunk, occupation.attackerCountry);
        transferCountryAssets(server.overworld(), politics, chunk, occupation.attackerCountry);
        politics.saveData();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.serverLevel() == server.overworld() && player.chunkPosition().equals(chunk)) {
                player.sendSystemMessage(Component.literal(
                    "Чанк " + chunk.x + ", " + chunk.z + " завоёван государством " + occupation.attackerCountry + "."
                ).withStyle(ChatFormatting.GREEN));
            }
        }
    }

    private static void transferCountryAssets(ServerLevel level, PoliticsManager politics, ChunkPos chunk, String newCountry) {
        Set<Long> positions = new HashSet<>();
        for (Country country : politics.getCountries().values()) {
            collectAndRemove(country.taxBlocks, chunk, positions);
            collectAndRemove(country.embassyBlocks, chunk, positions);
            collectAndRemove(country.radarBlocks, chunk, positions);
            collectAndRemove(country.vaultBlocks, chunk, positions);
        }

        Country target = politics.getCountry(newCountry);
        if (target != null) {
            for (long posLong : positions) {
                BlockPos pos = BlockPos.of(posLong);
                String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(pos).getBlock()).toString();
                if (blockId.equals("politicsmod:tax_block")) target.taxBlocks.add(posLong);
                if (blockId.equals("politicsmod:embassy")) target.embassyBlocks.add(posLong);
                if (blockId.equals("politicsmod:radar")) target.radarBlocks.add(posLong);
                if (blockId.equals("politicsmod:vault")) target.vaultBlocks.add(posLong);
            }
        }

        InfrastructureManager.reassignChunkOwner(level, chunk, newCountry);
    }

    private static void collectAndRemove(java.util.List<Long> list, ChunkPos chunk, Set<Long> out) {
        for (int i = list.size() - 1; i >= 0; i--) {
            long pos = list.get(i);
            if (new ChunkPos(BlockPos.of(pos)).equals(chunk)) {
                out.add(pos);
                list.remove(i);
            }
        }
    }

    private static boolean hasOwnedNeighbor(PoliticsManager politics, ChunkPos chunk, String country) {
        return country.equals(politics.getCountryNameAt(new ChunkPos(chunk.x + 1, chunk.z)))
            || country.equals(politics.getCountryNameAt(new ChunkPos(chunk.x - 1, chunk.z)))
            || country.equals(politics.getCountryNameAt(new ChunkPos(chunk.x, chunk.z + 1)))
            || country.equals(politics.getCountryNameAt(new ChunkPos(chunk.x, chunk.z - 1)));
    }

    private static boolean fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
        return false;
    }

    public static final class Occupation {
        private final long chunkLong;
        private final String attackerCountry;
        private final String defenderCountry;
        private int progressTicks;

        private Occupation(long chunkLong, String attackerCountry, String defenderCountry, int progressTicks) {
            this.chunkLong = chunkLong;
            this.attackerCountry = attackerCountry;
            this.defenderCountry = defenderCountry;
            this.progressTicks = progressTicks;
        }
    }

    public static final class TerritorySavedData extends SavedData {
        private static final String OCCUPATIONS = "occupations";
        private final Map<Long, Occupation> occupations = new HashMap<>();

        public static TerritorySavedData create() {
            return new TerritorySavedData();
        }

        public static TerritorySavedData load(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
            TerritorySavedData data = create();
            for (net.minecraft.nbt.Tag raw : tag.getList(OCCUPATIONS, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                net.minecraft.nbt.CompoundTag entry = (net.minecraft.nbt.CompoundTag) raw;
                String attacker = entry.getString("attacker");
                String defender = entry.getString("defender");
                if (attacker.isBlank() || defender.isBlank()) continue;
                long chunk = entry.getLong("chunk");
                int progress = Math.max(0, Math.min(CAPTURE_TICKS, entry.getInt("progress")));
                data.occupations.put(chunk, new Occupation(chunk, attacker, defender, progress));
            }
            return data;
        }

        @Override
        public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (Occupation occupation : occupations.values()) {
                net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
                entry.putLong("chunk", occupation.chunkLong);
                entry.putString("attacker", occupation.attackerCountry);
                entry.putString("defender", occupation.defenderCountry);
                entry.putInt("progress", occupation.progressTicks);
                list.add(entry);
            }
            tag.put(OCCUPATIONS, list);
            return tag;
        }

        public Occupation getOccupation(long chunkLong) {
            return occupations.get(chunkLong);
        }

        public void putOccupation(Occupation occupation) {
            occupations.put(occupation.chunkLong, occupation);
        }

        public void removeOccupation(long chunkLong) {
            occupations.remove(chunkLong);
        }

        public Map<Long, Occupation> snapshot() {
            return Map.copyOf(occupations);
        }
    }
}
