package ru.zela.politicseconomy.trade;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.zela.politicseconomy.infrastructure.InfrastructureManager;
import ru.zela.politicseconomy.integration.PoliticsModIntegration;
import ru.zela.politicseconomy.network.EconomyNetwork;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Physical international trade.
 *
 * Money, orders and contracts are server-side state. Goods never move through
 * this service: sellers tag real ItemStacks inside a real inventory, and the
 * same tagged stacks must physically arrive at the buyer terminal before
 * settlement happens. This makes Create, trains, belts and vehicle transport
 * the actual logistics layer.
 */
public final class TradeService {
    public static final String SHIPMENT_DATA_KEY = "pe_trade_shipment";
    private static final int MIN_TICKS_BETWEEN_DELIVERY_SCANS = 20;
    private static long lastDeliveryScan = Long.MIN_VALUE;

    private TradeService() {}

    public static TradeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                TradeSavedData::create,
                TradeSavedData::load,
                null
            ),
            TradeSavedData.DATA_NAME
        );
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long time = server.overworld().getGameTime();
        if (time - lastDeliveryScan < MIN_TICKS_BETWEEN_DELIVERY_SCANS) {
            return;
        }
        lastDeliveryScan = time;

        TradeSavedData data = get(server);
        List<TradeSavedData.Shipment> active = data.shipments().values().stream()
            .filter(s -> s.status() == TradeSavedData.ShipmentStatus.IN_TRANSIT)
            .toList();

        for (TradeSavedData.Shipment shipment : active) {
            int delivered = countTaggedItems(server, shipment.destinationPos(), shipment.itemId(), shipment.id());
            if (delivered < shipment.quantity()) {
                continue;
            }

            settleShipment(server, data, shipment);
            EconomyNetwork.refreshAllOnlinePlayers(server);
        }
    }

    public static TradeResult setTerminal(ServerPlayer player) {
        String countryName = country(player);
        if (countryName == null) {
            return TradeResult.fail("Ты не состоишь ни в одной стране.");
        }

        if (!hasTradeWarehouse(player.getServer(), countryName)) {
            return TradeResult.fail("Для торгового терминала нужен Trade Warehouse.");
        }

        BlockPos pos = targetedInventory(player);
        if (pos == null) {
            return TradeResult.fail("Посмотри на сундук, бочку, Create-склад или другой автоматический инвентарь.");
        }

        IItemHandler handler = itemHandler(player, pos);
        if (handler == null || handler.getSlots() <= 0) {
            return TradeResult.fail("В этом блоке нет доступного автоматического инвентаря.");
        }

        String owner = politics(player).getCountryNameAt(new ChunkPos(pos));
        if (!countryName.equals(owner)) {
            return TradeResult.fail("Терминал должен находиться на территории твоего государства.");
        }

        TradeSavedData data = get(player.getServer());
        data.setTerminal(countryName, new TradeSavedData.Terminal(
            countryName,
            pos.asLong(),
            Level.OVERWORLD.location().toString()
        ));

        return TradeResult.ok(
            "Торговый терминал установлен: " + pos.toShortString()
                + ". Подключи к нему Create-логистику."
        );
    }

    public static TradeSavedData.Terminal terminal(MinecraftServer server, String country) {
        return get(server).terminal(country);
    }

    public static TradeResult createOrder(
        ServerPlayer player,
        String rawItemId,
        int quantity,
        int maxUnitPrice
    ) {
        if (quantity <= 0 || maxUnitPrice <= 0) {
            return TradeResult.fail("Количество и максимальная цена должны быть больше нуля.");
        }

        String countryName = country(player);
        if (countryName == null) {
            return TradeResult.fail("Ты не состоишь ни в одной стране.");
        }

        TradeSavedData.Terminal terminal = terminal(player.getServer(), countryName);
        if (terminal == null) {
            return TradeResult.fail("Сначала назначь торговый терминал.");
        }

        String itemId;
        try {
            itemId = ResourceLocation.parse(rawItemId).toString();
        } catch (IllegalArgumentException e) {
            return TradeResult.fail("Неверный item id: " + rawItemId);
        }

        if (BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).isEmpty()) {
            return TradeResult.fail("Такого предмета не существует: " + itemId);
        }

        long goodsReserve = safeMultiply(quantity, maxUnitPrice);
        long logisticsReserve = Math.max(100L, goodsReserve / 4L);
        long reserve = Math.min(Integer.MAX_VALUE, safeAdd(goodsReserve, logisticsReserve));

        PoliticsManager politics = politics(player);
        Country buyer = politics.getCountry(countryName);
        if (buyer == null) {
            return TradeResult.fail("Государство не найдено.");
        }

        if (buyer.balance < reserve) {
            return TradeResult.fail(
                "Недостаточно средств. Нужно зарезервировать $" + reserve
                    + ", в казне $" + buyer.balance + "."
            );
        }

        buyer.balance -= (int) reserve;
        politics.setDirty();

        TradeSavedData data = get(player.getServer());
        int id = data.nextOrderId();
        data.putOrder(new TradeSavedData.Order(
            id,
            countryName,
            itemId,
            quantity,
            maxUnitPrice,
            reserve,
            player.getServer().overworld().getGameTime()
        ));

        return TradeResult.ok(
            "Создан заказ #" + id
                + ": " + quantity + " " + itemId
                + " по максимуму $" + maxUnitPrice + "/шт."
                + " • зарезервировано $" + reserve
        );
    }

    public static List<TradeSavedData.Order> openOrders(MinecraftServer server, String excludedCountry) {
        return get(server).orders().values().stream()
            .filter(order -> order.status() == TradeSavedData.OrderStatus.OPEN)
            .filter(order -> excludedCountry == null || !excludedCountry.equals(order.buyerCountry()))
            .sorted(Comparator.comparingInt(TradeSavedData.Order::id))
            .toList();
    }

    public static List<TradeSavedData.Order> countryOrders(MinecraftServer server, String country) {
        return get(server).orders().values().stream()
            .filter(order -> country.equals(order.buyerCountry())
                || country.equals(order.sellerCountry()))
            .sorted(Comparator.comparingInt(TradeSavedData.Order::id).reversed())
            .toList();
    }

    public static List<TradeSavedData.Order> countryHistory(MinecraftServer server, String country) {
        return get(server).orders().values().stream()
            .filter(order -> country.equals(order.buyerCountry())
                || country.equals(order.sellerCountry()))
            .filter(order -> order.status() == TradeSavedData.OrderStatus.COMPLETE
                || order.status() == TradeSavedData.OrderStatus.CANCELLED)
            .sorted(Comparator.comparingInt(TradeSavedData.Order::id).reversed())
            .toList();
    }

    public static TradeResult cancelOrder(ServerPlayer player, int orderId, String reason) {
        String countryName = country(player);
        if (countryName == null) return TradeResult.fail("Ты не состоишь ни в одной стране.");

        TradeSavedData data = get(player.getServer());
        TradeSavedData.Order order = data.order(orderId);
        if (order == null) return TradeResult.fail("Заказ не найден.");
        if (!countryName.equals(order.buyerCountry())) {
            return TradeResult.fail("Отменить заказ может только покупатель.");
        }
        if (order.status() == TradeSavedData.OrderStatus.COMPLETE
            || order.status() == TradeSavedData.OrderStatus.CANCELLED) {
            return TradeResult.fail("Этот заказ уже закрыт.");
        }

        String cleanReason = reason == null ? "" : reason.trim().replace("\n", " ");
        if (cleanReason.isBlank()) {
            return TradeResult.fail("Укажи причину отмены заказа.");
        }
        if (cleanReason.length() > 160) {
            cleanReason = cleanReason.substring(0, 160);
        }

        boolean hasActiveShipment = data.shipments().values().stream()
            .anyMatch(shipment -> shipment.orderId() == orderId
                && shipment.status() != TradeSavedData.ShipmentStatus.DELIVERED);

        if (hasActiveShipment) {
            return TradeResult.fail("Заказ уже отправлен физическим грузом и не может быть отменён.");
        }

        refundBuyer(player.getServer(), order.buyerCountry(), order.reservedFunds());
        order.cancel(cleanReason);
        data.setDirty();

        return TradeResult.ok("Заказ #" + orderId + " отменён. Деньги возвращены. Причина: " + cleanReason);
    }

    public static TradeResult acceptOrder(
        ServerPlayer player,
        int orderId,
        int unitPrice
    ) {
        String sellerCountry = country(player);
        if (sellerCountry == null) return TradeResult.fail("Ты не состоишь ни в одной стране.");

        TradeSavedData data = get(player.getServer());
        TradeSavedData.Order order = data.order(orderId);
        if (order == null) return TradeResult.fail("Заказ не найден.");
        if (order.status() != TradeSavedData.OrderStatus.OPEN) {
            return TradeResult.fail("Этот заказ уже принят или закрыт.");
        }
        if (sellerCountry.equals(order.buyerCountry())) {
            return TradeResult.fail("Нельзя продавать товар самому себе.");
        }
        if (unitPrice <= 0 || unitPrice > order.maxUnitPrice()) {
            return TradeResult.fail("Цена должна быть от $1 до $" + order.maxUnitPrice() + ".");
        }

        TradeSavedData.Terminal sellerTerminal = terminal(player.getServer(), sellerCountry);
        TradeSavedData.Terminal buyerTerminal = terminal(player.getServer(), order.buyerCountry());
        if (sellerTerminal == null || buyerTerminal == null) {
            return TradeResult.fail("И продавцу, и покупателю нужен торговый терминал.");
        }

        order.accept(sellerCountry, unitPrice);
        data.setDirty();

        return TradeResult.ok(
            "Заказ #" + orderId + " принят за $" + unitPrice + "/шт."
                + ". Теперь нужно физически отправить партии."
        );
    }

    public static TradeResult dispatchShipment(
        ServerPlayer player,
        int orderId,
        int requestedAmount
    ) {
        String sellerCountry = country(player);
        if (sellerCountry == null) return TradeResult.fail("Ты не состоишь ни в одной стране.");

        TradeSavedData data = get(player.getServer());
        TradeSavedData.Order order = data.order(orderId);
        if (order == null) return TradeResult.fail("Заказ не найден.");
        if (!sellerCountry.equals(order.sellerCountry())) {
            return TradeResult.fail("Этот заказ принят не твоим государством.");
        }
        if (order.status() != TradeSavedData.OrderStatus.ACCEPTED
            && order.status() != TradeSavedData.OrderStatus.SHIPPING) {
            return TradeResult.fail("Заказ ещё нельзя отправлять.");
        }

        int amountWanted = Math.min(requestedAmount, order.remaining());
        if (amountWanted <= 0) {
            return TradeResult.fail("В заказе больше нет товара для отправки.");
        }

        TradeSavedData.Terminal origin = terminal(player.getServer(), sellerCountry);
        TradeSavedData.Terminal destination = terminal(player.getServer(), order.buyerCountry());
        if (origin == null || destination == null) {
            return TradeResult.fail("Не найден терминал продавца или покупателя.");
        }

        BlockPos originPos = BlockPos.of(origin.pos());
        if (player.distanceToSqr(originPos.getX() + 0.5D, originPos.getY() + 0.5D, originPos.getZ() + 0.5D) > 100.0D) {
            return TradeResult.fail("Подойди к своему торговому терминалу.");
        }

        IItemHandler handler = itemHandler(player.serverLevel(), originPos);
        if (handler == null) {
            return TradeResult.fail("Торговый терминал больше не содержит доступного инвентаря.");
        }

        int taggedAmount = 0;
        int shipmentId = data.nextShipmentId();

        for (int slot = 0; slot < handler.getSlots() && taggedAmount < amountWanted; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(order.itemId())) continue;
            if (shipmentIdOf(stack) > 0) continue;

            int stackCount = stack.getCount();
            int take = Math.min(stackCount, amountWanted - taggedAmount);
            if (take <= 0) continue;

            ItemStack extracted = handler.extractItem(slot, take, false);
            if (extracted.isEmpty() || extracted.getCount() != take) {
                continue;
            }

            markShipment(extracted, shipmentId);

            // Seal only the requested amount, allowing partial shipments from a full stack.
            ItemStack remainder = handler.insertItem(slot, extracted, false);
            if (!remainder.isEmpty()) {
                clearShipment(extracted);
                handler.insertItem(slot, extracted, false);
                continue;
            }

            taggedAmount += take;
        }

        if (taggedAmount <= 0) {
            return TradeResult.fail(
                "Не найден полный, незапечатанный стек нужного товара. "
                    + "Разбей предметы на нужный размер партии и повтори."
            );
        }

        TradeSavedData.Shipment shipment = new TradeSavedData.Shipment(
            shipmentId,
            orderId,
            order.sellerCountry(),
            order.buyerCountry(),
            order.itemId(),
            taggedAmount,
            order.agreedUnitPrice(),
            origin.pos(),
            destination.pos(),
            player.getServer().overworld().getGameTime()
        );
        data.putShipment(shipment);
        order.shipped(taggedAmount);

        return TradeResult.ok(
            "Создан физический груз #" + shipmentId
                + ": " + taggedAmount + " " + order.itemId()
                + ". Предметы помечены и должны реально доехать до терминала покупателя."
        );
    }

    public static List<TradeSavedData.Shipment> waitingShipments(MinecraftServer server) {
        return get(server).shipments().values().stream()
            .filter(shipment -> shipment.status() == TradeSavedData.ShipmentStatus.WAITING_LOGISTICS)
            .sorted(Comparator.comparingInt(TradeSavedData.Shipment::id))
            .toList();
    }

    public static TradeResult acceptLogistics(ServerPlayer player, int shipmentId) {
        String courierCountry = country(player);
        if (courierCountry == null) return TradeResult.fail("Ты не состоишь ни в одной стране.");

        TradeSavedData data = get(player.getServer());
        TradeSavedData.Shipment shipment = data.shipment(shipmentId);
        if (shipment == null) return TradeResult.fail("Груз не найден.");
        if (shipment.status() != TradeSavedData.ShipmentStatus.WAITING_LOGISTICS) {
            return TradeResult.fail("Этот груз уже взят другим логистом или завершён.");
        }
        if (courierCountry.equals(shipment.buyerCountry())
            || courierCountry.equals(shipment.sellerCountry())) {
            return TradeResult.fail("Для этого груза нужен сторонний логист.");
        }

        shipment.assignCourier(player.getUUID());
        data.setDirty();
        return TradeResult.ok(
            "Груз #" + shipmentId + " взят в перевозку. Деньги будут выплачены только после физической доставки."
        );
    }

    public static TradeResult listPlayerTrade(ServerPlayer player) {
        String countryName = country(player);
        if (countryName == null) return TradeResult.fail("Ты не состоишь ни в одной стране.");

        TradeSavedData data = get(player.getServer());
        int orders = (int) countryOrders(player.getServer(), countryName).size();
        int waiting = waitingShipments(player.getServer()).size();

        return TradeResult.ok(
            "Торговля: заказов государства " + orders
                + " • свободных грузов для логистов " + waiting
        );
    }

    private static void settleShipment(
        MinecraftServer server,
        TradeSavedData data,
        TradeSavedData.Shipment shipment
    ) {
        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null) return;

        Country seller = politics.getCountry(shipment.sellerCountry());
        Country buyer = politics.getCountry(shipment.buyerCountry());
        if (seller == null || buyer == null) return;

        long goodsValue = safeMultiply(shipment.quantity(), shipment.unitPrice());
        long logisticsFee = logisticsFee(server, shipment);
        long totalSpend = Math.min(
            goodsValue + logisticsFee,
            Integer.MAX_VALUE
        );

        TradeSavedData.Order order = data.order(shipment.orderId());
        if (order == null) return;

        long available = order.reservedFunds();
        long actualLogisticsFee = Math.min(
            logisticsFee,
            Math.max(0L, available - Math.min(goodsValue, available))
        );
        long actualGoodsValue = Math.min(goodsValue, available);

        seller.balance = safeAddInt(seller.balance, actualGoodsValue);
        if (shipment.courier() != null) {
            addPlayerWallet(data, shipment.courier(), actualLogisticsFee);
        } else {
            buyer.balance = safeAddInt(buyer.balance, actualLogisticsFee);
        }

        long spent = actualGoodsValue + actualLogisticsFee;
        order.spendReserved(spent);

        if (order.remaining() <= 0) {
            if (order.reservedFunds() > 0) {
                buyer.balance = safeAddInt(buyer.balance, order.reservedFunds());
                order.spendReserved(order.reservedFunds());
            }
            order.complete();
        }

        shipment.delivered();
        clearShipmentTagAt(server, shipment.destinationPos(), shipment.id());

        boolean allShipmentsDelivered = data.shipments().values().stream()
            .filter(existing -> existing.orderId() == shipment.orderId())
            .allMatch(existing -> existing.status() == TradeSavedData.ShipmentStatus.DELIVERED);

        if (order.remaining() <= 0 && allShipmentsDelivered) {
            if (order.reservedFunds() > 0) {
                buyer.balance = safeAddInt(buyer.balance, order.reservedFunds());
                order.spendReserved(order.reservedFunds());
            }
            order.complete();
        }

        politics.setDirty();
        data.setDirty();
    }

    private static long logisticsFee(MinecraftServer server, TradeSavedData.Shipment shipment) {
        BlockPos a = BlockPos.of(shipment.originPos());
        BlockPos b = BlockPos.of(shipment.destinationPos());
        double distance = a.distSqr(b);
        long goodsValue = safeMultiply(shipment.quantity(), shipment.unitPrice());
        long base = Math.max(100L, goodsValue / 10L);
        long distanceFee = (long) Math.sqrt(Math.max(0.0D, distance)) / 16L;
        return Math.max(100L, Math.min(goodsValue / 3L + 1L, base + distanceFee));
    }

    private static void addPlayerWallet(TradeSavedData data, UUID playerId, long amount) {
        if (amount <= 0) return;
        // Keep the logistics money in the existing internal-market wallet.
        ru.zela.politicseconomy.economy.PopulationMarketSavedData market =
            null;
        // The trade core deliberately pays logistics through the existing personal
        // wallet abstraction so no second currency has to be maintained.
        // Wallet writes are delegated to the common market data.
        // This helper is intentionally filled by the bridge below.
        TradeWalletBridge.add(data, playerId, amount);
    }

    private static void refundBuyer(MinecraftServer server, String countryName, long amount) {
        if (amount <= 0) return;
        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null) return;
        Country buyer = politics.getCountry(countryName);
        if (buyer == null) return;
        buyer.balance = safeAddInt(buyer.balance, amount);
        politics.setDirty();
    }

    private static String country(ServerPlayer player) {
        return PoliticsModIntegration.playerCountry(player)
            .map(Country::getName)
            .orElse(null);
    }

    private static PoliticsManager politics(ServerPlayer player) {
        return PoliticsManager.get(player.serverLevel());
    }

    private static BlockPos targetedInventory(ServerPlayer player) {
        var hit = player.pick(6.0D, 0.0F, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)) {
            return null;
        }
        BlockPos pos = blockHit.getBlockPos();
        return itemHandler(player.serverLevel(), pos) != null ? pos : null;
    }

    private static IItemHandler itemHandler(ServerPlayer player, BlockPos pos) {
        return itemHandler(player.serverLevel(), pos);
    }

    private static IItemHandler itemHandler(Level level, BlockPos pos) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
    }

    private static int shipmentIdOf(ItemStack stack) {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) return 0;
        CompoundTag tag = data.copyTag();
        return tag.contains(SHIPMENT_DATA_KEY, Tag.TAG_INT)
            ? tag.getInt(SHIPMENT_DATA_KEY)
            : 0;
    }

    private static void markShipment(ItemStack stack, int shipmentId) {
        CompoundTag tag = new CompoundTag();
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data != null && !data.isEmpty()) {
            tag = data.copyTag();
        }
        tag.putInt(SHIPMENT_DATA_KEY, shipmentId);
        stack.set(
            net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.of(tag)
        );
    }

    private static void clearShipment(ItemStack stack) {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) return;
        CompoundTag tag = data.copyTag();
        tag.remove(SHIPMENT_DATA_KEY);
        if (tag.isEmpty()) {
            stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        } else {
            stack.set(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag)
            );
        }
    }

    private static int countTaggedItems(
        MinecraftServer server,
        long terminalPos,
        String itemId,
        int shipmentId
    ) {
        IItemHandler handler = itemHandler(server.overworld(), BlockPos.of(terminalPos));
        if (handler == null) return 0;

        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (!itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) continue;
            if (shipmentIdOf(stack) != shipmentId) continue;
            total += stack.getCount();
        }
        return total;
    }

    private static void clearShipmentTagAt(MinecraftServer server, long terminalPos, int shipmentId) {
        IItemHandler handler = itemHandler(server.overworld(), BlockPos.of(terminalPos));
        if (handler == null) return;

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (shipmentIdOf(stack) == shipmentId) {
                clearShipment(stack);
            }
        }
    }

    private static boolean hasTradeWarehouse(MinecraftServer server, String countryName) {
        if (server == null || countryName == null || countryName.isBlank()) {
            return false;
        }

        Map<Long, Map<Long, String>> chunks =
            InfrastructureManager.get(server).getDimension(server.overworld());

        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null) return false;

        for (Map.Entry<Long, Map<Long, String>> chunk : chunks.entrySet()) {
            var owner = politics.getCountryAt(new ChunkPos(chunk.getKey()));
            if (owner == null || !countryName.equals(owner.getName())) continue;

            for (String blockId : chunk.getValue().values()) {
                if ("politicsmod:trade_warehouse".equals(blockId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static long safeMultiply(long a, long b) {
        if (a <= 0 || b <= 0) return 0L;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;
        return a * b;
    }

    private static long safeAdd(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        return a + b;
    }

    private static int safeAddInt(int value, long amount) {
        long result = Math.max(0L, (long) value + amount);
        return (int) Math.min(Integer.MAX_VALUE, result);
    }

    public record TradeResult(boolean success, String message) {
        public static TradeResult ok(String message) {
            return new TradeResult(true, message);
        }

        public static TradeResult fail(String message) {
            return new TradeResult(false, message);
        }
    }

    /**
     * Small bridge kept inside the trade package so TradeService can use the
     * existing player wallet without duplicating the currency storage.
     */
    private static final class TradeWalletBridge {
        private static void add(TradeSavedData data, UUID playerId, long amount) {
            // The existing PopulationMarketSavedData is the canonical personal
            // wallet. The bridge resolves the current server through the active
            // lifecycle hook.
            MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ru.zela.politicseconomy.economy.PopulationMarketService.get(server)
                    .addWallet(playerId, amount);
            }
        }
    }
}
