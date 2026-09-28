package ru.zela.politicseconomy.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client request to change a country setting through the country dashboard. */
public record CountrySettingsActionPayload(
    String action,
    String value
) implements CustomPacketPayload {
    public static final Type<CountrySettingsActionPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("politicseconomy", "country_settings_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CountrySettingsActionPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public CountrySettingsActionPayload decode(RegistryFriendlyByteBuf buf) {
                return new CountrySettingsActionPayload(
                    buf.readUtf(32),
                    buf.readUtf(64)
                );
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, CountrySettingsActionPayload value) {
                buf.writeUtf(limit(value.action, 32), 32);
                buf.writeUtf(limit(value.value, 64), 64);
            }
        };

    private static String limit(String value, int max) {
        if (value == null || value.isEmpty()) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
