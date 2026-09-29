package ru.zela.politicseconomy.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Explicit server request telling the client to open the economy dashboard. */
public record EconomyOpenPayload() implements CustomPacketPayload {
    public static final Type<EconomyOpenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("politicseconomy", "economy_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EconomyOpenPayload> STREAM_CODEC =
        StreamCodec.unit(new EconomyOpenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
