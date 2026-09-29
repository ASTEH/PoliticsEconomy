package ru.zela.politicseconomy.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import ru.zela.politicseconomy.country.CountrySettingsService;
import ru.zela.politicseconomy.country.CountryWorkforceService;
import ru.zela.politicseconomy.economyui.EconomyMenu;
import ru.zela.politicseconomy.integration.PoliticsModIntegration;
import ru.zela.politicseconomy.trade.TradeService;
import ru.zela.politicseconomy.network.PoliticalClaimsPayload;

public final class EconomyNetwork {
    private EconomyNetwork() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(EconomyNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(EconomySnapshotPayload.TYPE, EconomySnapshotPayload.STREAM_CODEC, EconomyNetwork::handleClient);
        registrar.playToClient(
            EconomyOpenPayload.TYPE,
            EconomyOpenPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() ->
                ru.zela.politicseconomy.client.EconomyClientPayloadHandler.requestOpen()
            )
        );
        registrar.playToClient(
            PoliticalClaimsPayload.TYPE,
            PoliticalClaimsPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> handlePoliticalClaimsClient(payload))
        );
        registrar.playToServer(CountrySettingsActionPayload.TYPE, CountrySettingsActionPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) return;

                if ("workforce".equals(payload.action())) {
                    CountryWorkforceService.Result result =
                        CountryWorkforceService.apply(player, payload.value(), player.isCreative() && player.hasPermissions(2));
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(result.message())
                        .withStyle(result.success() ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
                    var country = ru.zela.politicseconomy.integration.PoliticsModIntegration.playerCountry(player).orElse(null);
                    if (country != null) send(player, EconomyMenu.buildSnapshot(player, country));
                    return;
                }

                if (payload.action().startsWith("trade_")) {
                    handleTradeAction(player, payload.action(), payload.value());
                    return;
                }

                if ("market_sell".equals(payload.action())) {
                    String raw = payload.value();
                    int separator = raw.indexOf('|');
                    if (separator > 0 && separator < raw.length() - 1) {
                        try {
                            int amount = Integer.parseInt(raw.substring(0, separator));
                            String itemId = raw.substring(separator + 1);
                            ru.zela.politicseconomy.economy.PopulationMarketService.SellResult result =
                                ru.zela.politicseconomy.economy.PopulationMarketService.sellToPopulation(
                                    player,
                                    itemId,
                                    amount
                                );
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(result.message())
                                .withStyle(result.success() ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
                        } catch (NumberFormatException exception) {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Некорректное количество товара.")
                                .withStyle(net.minecraft.ChatFormatting.RED));
                        }
                    }
                    var country = ru.zela.politicseconomy.integration.PoliticsModIntegration.playerCountry(player).orElse(null);
                    if (country != null) send(player, EconomyMenu.buildSnapshot(player, country));
                    return;
                }

                CountrySettingsService.Result result = CountrySettingsService.apply(player, payload.action(), payload.value());
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(result.message())
                    .withStyle(result.success() ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
                var country = ru.zela.politicseconomy.integration.PoliticsModIntegration.playerCountry(player).orElse(null);
                if (country != null) send(player, EconomyMenu.buildSnapshot(player, country));
            }));
    }

    private static void handleTradeAction(ServerPlayer player, String action, String value) {
        TradeService.TradeResult result;

        try {
            switch (action) {
                case "trade_terminal_set" -> result = TradeService.setTerminal(player);
                case "trade_order_create" -> {
                    String[] parts = value.split("\\|", -1);
                    if (parts.length != 3) {
                        result = TradeService.TradeResult.fail("Некорректные параметры заказа.");
                    } else {
                        result = TradeService.createOrder(
                            player,
                            parts[0],
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2])
                        );
                    }
                }
                case "trade_order_accept" -> {
                    String[] parts = value.split("\\|", -1);
                    if (parts.length != 2) {
                        result = TradeService.TradeResult.fail("Некорректные параметры принятия заказа.");
                    } else {
                        result = TradeService.acceptOrder(
                            player,
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1])
                        );
                    }
                }
                case "trade_order_cancel" ->
                    result = TradeService.cancelOrder(player, Integer.parseInt(value));
                case "trade_shipment_dispatch" -> {
                    String[] parts = value.split("\\|", -1);
                    if (parts.length != 2) {
                        result = TradeService.TradeResult.fail("Некорректные параметры отправки груза.");
                    } else {
                        result = TradeService.dispatchShipment(
                            player,
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1])
                        );
                    }
                }
                case "trade_shipment_haul" ->
                    result = TradeService.acceptLogistics(player, Integer.parseInt(value));
                default ->
                    result = TradeService.TradeResult.fail("Неизвестное торговое действие.");
            }
        } catch (NumberFormatException exception) {
            result = TradeService.TradeResult.fail("Некорректное число в торговой операции.");
        } catch (Exception exception) {
            result = TradeService.TradeResult.fail("Торговая операция не выполнена.");
        }

        player.sendSystemMessage(
            net.minecraft.network.chat.Component.literal(result.message())
                .withStyle(result.success() ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED)
        );

        refreshAllOnlinePlayers(player.getServer());
    }

    public static void refreshAllOnlinePlayers(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            var country = PoliticsModIntegration.playerCountry(online).orElse(null);
            if (country != null) {
                send(online, EconomyMenu.buildSnapshot(online, country));
            }
        }
    }

    private static void handlePoliticalClaimsClient(PoliticalClaimsPayload payload) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        try {
            Class<?> stateClass = Class.forName(
                "ru.zela.politicseconomy.client.PoliticalClaimsClientState"
            );
            stateClass.getMethod("apply", PoliticalClaimsPayload.class).invoke(null, payload);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to apply Politics Economy map claims", exception);
        }
    }

    private static void handleClient(EconomySnapshotPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> {
            try {
                Class<?> handlerClass = Class.forName("ru.zela.politicseconomy.client.EconomyClientPayloadHandler");
                handlerClass.getMethod("handle", EconomySnapshotPayload.class).invoke(null, payload);
            } catch (ReflectiveOperationException exception) {
                throw new RuntimeException("Failed to open Politics Economy dashboard", exception);
            }
        });
    }

    public static void send(ServerPlayer player, EconomySnapshotPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void send(ServerPlayer player, EconomyOpenPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void send(ServerPlayer player, PoliticalClaimsPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendAction(String action, String value) {
        PacketDistributor.sendToServer(new CountrySettingsActionPayload(action, value));
    }
}