package ru.zela.politicseconomy.economy;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.market.MarketService;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.MarketListing;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.zela.politicseconomy.country.CountryDirection;
import ru.zela.politicseconomy.country.CountryDirectionBonusService;
import ru.zela.politicseconomy.country.CountryDirectionManager;
import ru.zela.politicseconomy.country.CountryPopulationService;
import ru.zela.politicseconomy.country.CountryPolicyManager;
import ru.zela.politicseconomy.country.GovernmentType;
import ru.zela.politicseconomy.country.ReligionType;
import ru.zela.politicseconomy.integration.PoliticsModIntegration;
import ru.zela.politicseconomy.infrastructure.InfrastructureManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Internal population market.
 *
 * Population creates concrete item demand every economic cycle. Players can
 * sell demanded items directly to their country's population and receive
 * personal market money. Once the local market is still undersupplied halfway
 * through the cycle, the country can automatically import matching listings
 * from other countries through PoliticsMod's international market.
 */
public final class PopulationMarketService {
    private static final int MIN_CYCLE_TICKS = 20;

    /** Population gets half a cycle before automatic imports start. */
    private static final double IMPORT_PHASE = 0.50D;

    /** International procurement never pays more than this multiple of local market value. */
    private static final double MAX_IMPORT_PRICE_MULTIPLIER = 2.0D;

    /** Base internal market tax before country trade modifiers. */
    private static final double BASE_PLAYER_SALE_TAX = 10.0D;

    private PopulationMarketService() {}

    public static PopulationMarketSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                PopulationMarketSavedData::create,
                PopulationMarketSavedData::load,
                null
            ),
            PopulationMarketSavedData.DATA_NAME
        );
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        int cycleTicks = Math.max(
            MIN_CYCLE_TICKS,
            net.krona.politicsmod.config.PoliticsConfig.get().economyCycleTicks
        );

        long cycle = overworld.getGameTime() / cycleTicks;
        PopulationMarketSavedData data = get(server);

        if (cycle > data.lastCycle()) {
            startCycle(server, data, cycle);
        }

        long phaseTick = overworld.getGameTime() % cycleTicks;
        long importStart = Math.max(
            1L,
            (long) Math.floor(cycleTicks * IMPORT_PHASE)
        );

        if (phaseTick >= importStart && data.lastImportCycle() < cycle) {
            importMissingGoods(server, data);
            data.setLastImportCycle(cycle);
        }
    }

    private static void startCycle(
        MinecraftServer server,
        PopulationMarketSavedData data,
        long cycle
    ) {
        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null) return;

        for (Country country : politics.getCountries().values()) {
            Map<String, Integer> demand = calculateCountryDemand(
                server,
                country.getName()
            );
            data.setCycleDemand(country.getName(), demand);
        }

        data.setLastCycle(cycle);
        // Give local players the entire first half of the cycle before importing.
        data.setLastImportCycle(cycle - 1);
    }

    private static Map<String, Integer> calculateCountryDemand(
        MinecraftServer server,
        String countryName
    ) {
        int population = CountryPopulationService.population(server, countryName);
        if (population <= 0) return Map.of();

        CountryDirection direction =
            CountryDirectionManager.getDirection(server, countryName);
        GovernmentType government =
            CountryPolicyManager.getGovernment(server, countryName);
        ReligionType religion =
            CountryPolicyManager.getReligion(server, countryName);

        java.util.LinkedHashMap<String, Integer> result = new java.util.LinkedHashMap<>();
        for (PopulationDemandCatalog.Good good : PopulationDemandCatalog.goods()) {
            int amount = PopulationDemandCatalog.demandFor(
                good,
                population,
                direction,
                government,
                religion
            );
            if (amount > 0) {
                result.put(good.itemId(), amount);
            }
        }
        return result;
    }

    public static SellResult sellToPopulation(
        ServerPlayer player,
        String rawItemId,
        int requestedAmount
    ) {
        if (requestedAmount <= 0) {
            return new SellResult(false, 0, 0, "Количество должно быть больше нуля.");
        }

        Country country = PoliticsModIntegration.playerCountry(player).orElse(null);
        if (country == null) {
            return new SellResult(false, 0, 0, "Ты не состоишь ни в одной стране.");
        }

        String itemId;
        try {
            itemId = ResourceLocation.parse(rawItemId).toString();
        } catch (IllegalArgumentException exception) {
            return new SellResult(false, 0, 0, "Неверный item id: " + rawItemId);
        }

        PopulationDemandCatalog.Good good = PopulationDemandCatalog.find(itemId);
        if (good == null) {
            return new SellResult(false, 0, 0, "Этот товар сейчас не входит во внутренний спрос.");
        }

        PoliticsManager politics = PoliticsManager.get(player.level());
        if (politics == null
            || !country.getName().equals(
                politics.getCountryNameAt(player.chunkPosition())
            )) {
            return new SellResult(
                false,
                0,
                0,
                "Продавать населению можно только находясь на территории своего государства."
            );
        }

        MinecraftServer server = player.getServer();
        PopulationMarketSavedData data = get(server);

        ensureCurrentCycle(server, data, country.getName());

        int remaining = data.remainingDemand(country.getName(), itemId);
        if (remaining <= 0) {
            return new SellResult(
                false,
                0,
                0,
                "Население уже закупило весь объём этого товара в текущем цикле."
            );
        }

        Item item = BuiltInRegistries.ITEM.getOptional(
            ResourceLocation.parse(itemId)
        ).orElse(null);
        if (item == null) {
            return new SellResult(false, 0, 0, "Предмет не найден: " + itemId);
        }

        int available = countItem(player, item);
        if (available <= 0) {
            return new SellResult(false, 0, 0, "В инвентаре нет " + itemId + ".");
        }

        int sold = Math.min(requestedAmount, Math.min(remaining, available));
        if (sold <= 0) {
            return new SellResult(false, 0, 0, "Нечего продавать.");
        }

        int pricePerUnit = currentPrice(data, country.getName(), good);
        long gross = (long) pricePerUnit * sold;

        double taxPercent = playerSaleTax(server, country.getName());
        long tax = Math.max(
            0L,
            Math.min(gross, Math.round(gross * taxPercent / 100.0D))
        );
        long payout = gross - tax;

        removeItems(player, item, sold);

        data.setRemainingDemand(
            country.getName(),
            itemId,
            remaining - sold
        );
        data.addSold(country.getName(), itemId, sold);

        if (tax > 0) {
            long newBalance = Math.min(
                Integer.MAX_VALUE,
                (long) country.balance + tax
            );
            country.balance = (int) newBalance;
            politics.setDirty();
        }

        data.addWallet(player.getUUID(), payout);

        String itemName = PopulationDemandCatalog.shortName(itemId);
        String message = "Продано населению: "
            + sold + " " + itemName
            + " × $" + pricePerUnit
            + " = $" + gross
            + " • тебе $" + payout
            + " • налог государству $" + tax;

        if (sold < requestedAmount) {
            message += " • фактически продано только " + sold;
        }

        return new SellResult(true, sold, payout, message);
    }

    private static void ensureCurrentCycle(
        MinecraftServer server,
        PopulationMarketSavedData data,
        String countryName
    ) {
        long cycleTicks = Math.max(
            MIN_CYCLE_TICKS,
            net.krona.politicsmod.config.PoliticsConfig.get().economyCycleTicks
        );
        long cycle = server.overworld().getGameTime() / cycleTicks;

        if (cycle > data.lastCycle()) {
            startCycle(server, data, cycle);
            return;
        }

        if (!data.baseDemand(countryName).isEmpty()) {
            return;
        }

        Country country = PoliticsManager.get(server.overworld()).getCountry(countryName);
        if (country != null) {
            data.setCycleDemand(
                countryName,
                calculateCountryDemand(server, countryName)
            );
        }
    }

    private static void importMissingGoods(
        MinecraftServer server,
        PopulationMarketSavedData data
    ) {
        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null) return;

        for (Country buyer : politics.getCountries().values()) {
            if (!hasTradeWarehouse(server, politics, buyer.getName())) {
                continue;
            }

            List<Map.Entry<String, Integer>> needs =
                new ArrayList<>(data.remainingDemand(buyer.getName()).entrySet());

            for (Map.Entry<String, Integer> need : needs) {
                if (need.getValue() <= 0) continue;

                int base = data.baseDemand(buyer.getName(), need.getKey());
                if (base <= 0 || need.getValue() * 2 < base) {
                    // Local players have supplied at least half the cycle's demand.
                    continue;
                }

                PopulationDemandCatalog.Good good =
                    PopulationDemandCatalog.find(need.getKey());
                if (good == null) continue;

                int referencePrice = currentPrice(
                    data,
                    buyer.getName(),
                    good
                );
                buyListingsForDemand(
                    politics,
                    buyer,
                    need.getKey(),
                    need.getValue(),
                    referencePrice,
                    data
                );
            }
        }
    }

    private static void buyListingsForDemand(
        PoliticsManager politics,
        Country buyer,
        String itemId,
        int wanted,
        int referencePrice,
        PopulationMarketSavedData data
    ) {
        List<MarketListing> listings = politics.getListings().stream()
            .filter(listing -> listing.stack != null && !listing.stack.isEmpty())
            .filter(listing -> BuiltInRegistries.ITEM.getKey(listing.stack.getItem()).toString().equals(itemId))
            .filter(listing -> !listing.seller.equals(buyer.getName()))
            .filter(listing -> politics.getCountry(listing.seller) != null)
            .filter(listing -> !politics.isAtWar(buyer.getName(), listing.seller))
            .sorted(Comparator.comparingDouble(
                listing -> listing.price / (double) Math.max(1, listing.stack.getCount())
            ))
            .toList();

        long maxUnitPrice = Math.max(
            1L,
            Math.round(referencePrice * MAX_IMPORT_PRICE_MULTIPLIER)
        );

        int remaining = wanted;
        for (MarketListing listing : listings) {
            if (remaining <= 0) break;

            int available = listing.stack.getCount();
            if (available <= 0) continue;

            long unitPrice = (listing.price + available - 1L) / available;
            if (unitPrice > maxUnitPrice) continue;

            int amount = Math.min(remaining, available);
            long totalPrice = Math.max(
                1L,
                (listing.price * (long) amount + available - 1L) / available
            );

            if (totalPrice > buyer.balance) {
                continue;
            }

            Country seller = politics.getCountry(listing.seller);
            if (seller == null) continue;

            int fee = MarketService.feeFor(
                politics,
                buyer.getName(),
                seller.getName(),
                (int) Math.min(Integer.MAX_VALUE, totalPrice)
            );

            politics.removeListing(listing.id);

            if (amount < available) {
                ItemStack remainder = listing.stack.copy();
                remainder.setCount(available - amount);
                int remainderPrice = Math.max(
                    1,
                    listing.price - (int) Math.min(
                        Integer.MAX_VALUE,
                        totalPrice
                    )
                );
                politics.addListing(
                    listing.seller,
                    listing.listedBy,
                    remainder,
                    remainderPrice
                );
            }

            buyer.balance -= (int) totalPrice;
            seller.balance += Math.max(0, (int) totalPrice - fee);
            politics.setDirty();

            data.setRemainingDemand(
                buyer.getName(),
                itemId,
                data.remainingDemand(buyer.getName(), itemId) - amount
            );
            data.addImported(buyer.getName(), itemId, amount);

            remaining -= amount;
        }
    }

    private static boolean hasTradeWarehouse(
        MinecraftServer server,
        PoliticsManager politics,
        String countryName
    ) {
        ServerLevel level = server.overworld();
        Map<Long, Map<Long, String>> chunks =
            InfrastructureManager.get(server).getDimension(level);

        for (Map.Entry<Long, Map<Long, String>> chunk : chunks.entrySet()) {
            var owner = politics.getCountryAt(
                new net.minecraft.world.level.ChunkPos(chunk.getKey())
            );
            if (owner == null || !countryName.equals(owner.getName())) {
                continue;
            }

            for (String blockId : chunk.getValue().values()) {
                if ("politicsmod:trade_warehouse".equals(blockId)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static List<DemandLine> demandLines(
        MinecraftServer server,
        String countryName
    ) {
        PopulationMarketSavedData data = get(server);
        ensureCurrentCycle(server, data, countryName);

        List<DemandLine> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry :
            data.baseDemand(countryName).entrySet()) {

            PopulationDemandCatalog.Good good =
                PopulationDemandCatalog.find(entry.getKey());
            if (good == null) continue;

            int remaining = data.remainingDemand(countryName, entry.getKey());
            result.add(new DemandLine(
                good.itemId(),
                entry.getValue(),
                remaining,
                data.sold(countryName, good.itemId()),
                data.imported(countryName, good.itemId()),
                currentPrice(data, countryName, good)
            ));
        }

        result.sort(
            Comparator.comparingDouble(
                line -> -((line.baseDemand() <= 0)
                    ? 0.0D
                    : line.remaining() / (double) line.baseDemand())
            ).thenComparing(DemandLine::itemId)
        );

        return List.copyOf(result);
    }

    public static long wallet(MinecraftServer server, UUID playerId) {
        return get(server).wallet(playerId);
    }

    private static int currentPrice(
        PopulationMarketSavedData data,
        String countryName,
        PopulationDemandCatalog.Good good
    ) {
        int base = Math.max(1, data.baseDemand(countryName, good.itemId()));
        int remaining = Math.max(0, data.remainingDemand(countryName, good.itemId()));

        double shortageRatio = Math.min(
            1.0D,
            remaining / (double) base
        );

        double multiplier = 0.65D + 0.85D * shortageRatio;
        return Math.max(
            1,
            (int) Math.round(good.basePrice() * multiplier)
        );
    }

    private static double playerSaleTax(
        MinecraftServer server,
        String countryName
    ) {
        var profile = CountryDirectionBonusService.profile(
            server,
            countryName
        );
        double tradeModifier = profile == null ? 0.0D : profile.tradeFee();
        return Math.max(
            2.0D,
            Math.min(
                25.0D,
                BASE_PLAYER_SALE_TAX + tradeModifier
            )
        );
    }

    private static int countItem(ServerPlayer player, Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void removeItems(
        ServerPlayer player,
        Item item,
        int amount
    ) {
        int remaining = amount;

        for (int i = 0;
             i < player.getInventory().getContainerSize() && remaining > 0;
             i++) {

            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || stack.getItem() != item) continue;

            int used = Math.min(remaining, stack.getCount());
            stack.shrink(used);
            remaining -= used;
        }

        player.getInventory().setChanged();
    }

    public record SellResult(
        boolean success,
        int sold,
        long payout,
        String message
    ) {}

    public record DemandLine(
        String itemId,
        int baseDemand,
        int remaining,
        int sold,
        int imported,
        int pricePerUnit
    ) {}
}
