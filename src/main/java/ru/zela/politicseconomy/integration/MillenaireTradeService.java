package ru.zela.politicseconomy.integration;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.zela.politicseconomy.economy.PopulationMarketService;
import ru.zela.politicseconomy.economy.PopulationMarketSavedData;

import java.util.List;
import java.util.Map;

/**
 * Autonomous trade between Millénaire states.
 *
 * <p>This is intentionally separate from player-operated physical trade:
 * Millénaire villages can exchange their existing warehouse goods according
 * to population demand, bilateral relations and treasury. Items are actually
 * removed from one village's inventories and inserted into another's.</p>
 */
public final class MillenaireTradeService {
    private static final int MIN_CYCLE_TICKS = 20;
    private static final int MAX_GOODS_PER_DEAL = 32;
    private static final int MIN_RELATION = 20;
    private static final double SELLER_RESERVE_FRACTION = 0.25D;
    private static final long MINIMUM_TREASURY_FOR_TRADE = 5L;

    private MillenaireTradeService() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (level == null || !MillenaireIntegration.isAvailable()) return;

        int cycleTicks = Math.max(
            MIN_CYCLE_TICKS,
            net.krona.politicsmod.config.PoliticsConfig.get().economyCycleTicks
        );
        long cycle = level.getGameTime() / cycleTicks;

        MillenaireTradeSavedData stateData = MillenaireTradeSavedData.get(server);
        if (cycle <= stateData.lastProcessedCycle()) return;
        stateData.setLastProcessedCycle(cycle);

        PopulationMarketSavedData market =
            PopulationMarketService.get(server);
        ensureMarketCycle(server, market, cycle);

        List<MillenaireIntegration.VillageSnapshot> states =
            MillenaireIntegration.snapshots(server);

        for (MillenaireIntegration.VillageSnapshot buyer : states) {
            if (buyer.population() <= 0) continue;

            List<PopulationMarketService.DemandLine> needs =
                PopulationMarketService.demandLines(server, buyer.stateKey());

            for (PopulationMarketService.DemandLine need : needs) {
                if (need.remaining() <= 0) continue;
                tradeGood(server, states, buyer, need, market);
            }
        }

        stateData.setDirty();
    }

    private static void ensureMarketCycle(
        MinecraftServer server,
        PopulationMarketSavedData market,
        long cycle
    ) {
        if (market.lastCycle() >= cycle) return;

        net.krona.politicsmod.PoliticsManager politics =
            net.krona.politicsmod.PoliticsManager.get(server.overworld());
        if (politics == null) return;

        for (MillenaireIntegration.VillageSnapshot state
            : MillenaireIntegration.snapshots(server)) {
            market.setCycleDemand(
                state.stateKey(),
                buildDemand(server, state.stateKey())
            );
        }
        market.setLastCycle(cycle);
    }

    private static Map<String, Integer> buildDemand(
        MinecraftServer server,
        String stateKey
    ) {
        return PopulationMarketService.demandLines(server, stateKey).stream()
            .collect(java.util.stream.Collectors.toMap(
                PopulationMarketService.DemandLine::itemId,
                PopulationMarketService.DemandLine::baseDemand,
                Integer::sum,
                java.util.LinkedHashMap::new
            ));
    }

    private static void tradeGood(
        MinecraftServer server,
        List<MillenaireIntegration.VillageSnapshot> states,
        MillenaireIntegration.VillageSnapshot buyer,
        PopulationMarketService.DemandLine need,
        PopulationMarketSavedData market
    ) {
        for (MillenaireIntegration.VillageSnapshot seller : states) {
            if (seller.villageId().equals(buyer.villageId())) continue;

            int relation = buyer.relations().getOrDefault(seller.villageId(), 0);
            if (relation < MIN_RELATION) continue;

            int stock = seller.warehouse().getOrDefault(need.itemId(), 0);
            if (stock <= 0) continue;

            int reserve = Math.max(
                1,
                (int) Math.ceil(stock * SELLER_RESERVE_FRACTION)
            );
            int availableForExport = Math.max(0, stock - reserve);
            if (availableForExport <= 0) continue;

            MillenaireStateSavedData finances =
                MillenaireStateSavedData.get(server);

            long buyerTreasury = finances.treasury(buyer.villageId());
            if (buyerTreasury < MINIMUM_TREASURY_FOR_TRADE) continue;

            int maxAffordable = (int) Math.min(
                Integer.MAX_VALUE,
                buyerTreasury / Math.max(1, need.pricePerUnit())
            );
            int amount = Math.min(
                MAX_GOODS_PER_DEAL,
                Math.min(
                    need.remaining(),
                    Math.min(availableForExport, maxAffordable)
                )
            );
            if (amount <= 0) continue;

            int unitPrice = Math.max(1, need.pricePerUnit());
            long gross = (long) amount * unitPrice;
            int feePercent = Math.max(
                2,
                Math.min(
                    25,
                    ru.zela.politicseconomy.country.CountryDirectionBonusService
                        .effectiveTradeFeePercent(server, buyer.stateKey())
                )
            );
            long fee = Math.round(gross * feePercent / 100.0D);
            long sellerPayment = Math.max(0L, gross - fee);

            int removed = MillenaireIntegration.consumeFromWarehouse(
                server,
                seller.stateKey(),
                List.of(need.itemId()),
                amount
            );
            if (removed <= 0) continue;

            int added = MillenaireIntegration.addToWarehouse(
                server,
                buyer.stateKey(),
                need.itemId(),
                removed
            );

            if (added < removed) {
                MillenaireIntegration.addToWarehouse(
                    server,
                    seller.stateKey(),
                    need.itemId(),
                    removed - added
                );
            }

            if (added <= 0) continue;

            long actualGross = (long) added * unitPrice;
            long actualFee = Math.round(actualGross * feePercent / 100.0D);
            long actualPayment = Math.max(0L, actualGross - actualFee);

            finances.addTreasury(
                buyer.villageId(),
                -Math.min(buyerTreasury, actualGross)
            );
            finances.addTreasury(
                seller.villageId(),
                actualPayment
            );

            market.setRemainingDemand(
                buyer.stateKey(),
                need.itemId(),
                Math.max(
                    0,
                    market.remainingDemand(buyer.stateKey(), need.itemId()) - added
                )
            );
            market.addImported(buyer.stateKey(), need.itemId(), added);

            int activity = Math.min(
                6,
                1 + added / 16
            );
            ru.zela.politicseconomy.country.CountryDevelopmentService.addActivity(
                server,
                buyer.stateKey(),
                activity
            );
            ru.zela.politicseconomy.country.CountryDevelopmentService.addActivity(
                server,
                seller.stateKey(),
                activity
            );
            break;
        }
    }
}
