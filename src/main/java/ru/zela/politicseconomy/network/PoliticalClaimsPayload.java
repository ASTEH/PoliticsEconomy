package ru.zela.politicseconomy.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;

/**
 * Server -> client snapshot of country-owned chunks around a player.
 *
 * The server sends a bounded area instead of attempting to synchronize the
 * entire world at once. Clients keep previously discovered chunks cached.
 */
public record PoliticalClaimsPayload(
    int centerChunkX,
    int centerChunkZ,
    int radius,
    String[] countries,
    long[] chunks,
    int[] countryIndices
) implements CustomPacketPayload {
    public static final Type<PoliticalClaimsPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("politicseconomy", "political_claims"));

    private static final int MAX_COUNTRIES = 256;
    private static final int MAX_CHUNKS = 12_000;
    private static final int MAX_COUNTRY_NAME = 128;

    public PoliticalClaimsPayload {
        countries = countries == null ? new String[0] : Arrays.copyOf(countries, countries.length);
        chunks = chunks == null ? new long[0] : Arrays.copyOf(chunks, chunks.length);
        countryIndices = countryIndices == null ? new int[0] : Arrays.copyOf(countryIndices, countryIndices.length);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, PoliticalClaimsPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public PoliticalClaimsPayload decode(RegistryFriendlyByteBuf buf) {
                int centerX = buf.readInt();
                int centerZ = buf.readInt();
                int radius = Math.max(0, Math.min(128, buf.readVarInt()));

                int countryCount = Math.max(0, Math.min(MAX_COUNTRIES, buf.readVarInt()));
                String[] countries = new String[countryCount];
                for (int i = 0; i < countryCount; i++) {
                    countries[i] = buf.readUtf(MAX_COUNTRY_NAME);
                }

                int chunkCount = Math.max(0, Math.min(MAX_CHUNKS, buf.readVarInt()));
                long[] chunks = new long[chunkCount];
                int[] indices = new int[chunkCount];

                for (int i = 0; i < chunkCount; i++) {
                    chunks[i] = buf.readLong();
                    indices[i] = Math.max(0, Math.min(countryCount - 1, buf.readVarInt()));
                }

                return new PoliticalClaimsPayload(
                    centerX,
                    centerZ,
                    radius,
                    countries,
                    chunks,
                    indices
                );
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, PoliticalClaimsPayload value) {
                buf.writeInt(value.centerChunkX());
                buf.writeInt(value.centerChunkZ());
                buf.writeVarInt(Math.max(0, Math.min(128, value.radius())));

                int countryCount = Math.min(MAX_COUNTRIES, value.countries().length);
                buf.writeVarInt(countryCount);
                for (int i = 0; i < countryCount; i++) {
                    buf.writeUtf(limit(value.countries()[i], MAX_COUNTRY_NAME), MAX_COUNTRY_NAME);
                }

                int count = Math.min(
                    Math.min(MAX_CHUNKS, value.chunks().length),
                    value.countryIndices().length
                );
                buf.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    int index = value.countryIndices()[i];
                    if (countryCount <= 0) {
                        index = 0;
                    } else {
                        index = Math.max(0, Math.min(countryCount - 1, index));
                    }
                    buf.writeLong(value.chunks()[i]);
                    buf.writeVarInt(index);
                }
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static String limit(String value, int max) {
        if (value == null || value.isEmpty()) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
