package ru.zela.politicseconomy.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Network bridge between the server economy model and the client dashboard. */
public final class EconomyNetwork {
    private EconomyNetwork() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(EconomyNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
            EconomySnapshotPayload.TYPE,
            EconomySnapshotPayload.STREAM_CODEC,
            EconomyNetwork::handleClient
        );
    }

    private static void handleClient(EconomySnapshotPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> {
            try {
                Class<?> handlerClass = Class.forName(
                    "ru.zela.politicseconomy.client.EconomyClientPayloadHandler"
                );
                handlerClass.getMethod("handle", EconomySnapshotPayload.class).invoke(null, payload);
            } catch (ReflectiveOperationException exception) {
                throw new RuntimeException("Failed to open Politics Economy dashboard", exception);
            }
        });
    }

    public static void send(ServerPlayer player, EconomySnapshotPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
