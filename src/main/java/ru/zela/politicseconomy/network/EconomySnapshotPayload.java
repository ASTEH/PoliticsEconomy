package ru.zela.politicseconomy.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Immutable server snapshot used by the client-side economy dashboard. */
public record EconomySnapshotPayload(
    String countryName,
    String direction,
    String government,
    String religion,
    int population,
    double populationWorkforceModifier,
    String policySummary,
    int treasury,
    double infrastructureCost,
    double moneyDebt,
    double dieselModifier,
    double totalMaterialPerCycle,
    String[] materialIds,
    String[] materialNames,
    int[] materialStockpile,
    int[] materialDebt,
    double[] materialPerCycle,
    int developmentLevel,
    int developmentPoints,
    int developmentNextThreshold,
    String developmentPerk,
    String developmentNextPerk
) implements CustomPacketPayload {
    public static final Type<EconomySnapshotPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("politicseconomy", "economy_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EconomySnapshotPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public EconomySnapshotPayload decode(RegistryFriendlyByteBuf buf) {
                String countryName = buf.readUtf(128);
                String direction = buf.readUtf(64);
                String government = buf.readUtf(64);
                String religion = buf.readUtf(64);
                int population = buf.readVarInt();
                double populationWorkforceModifier = buf.readDouble();
                String policySummary = buf.readUtf(512);
                int treasury = buf.readInt();
                double infrastructureCost = buf.readDouble();
                double moneyDebt = buf.readDouble();
                double dieselModifier = buf.readDouble();
                double totalMaterialPerCycle = buf.readDouble();
                String[] materialIds = readStringArray(buf, 128);
                String[] materialNames = readStringArray(buf, 256);
                int[] materialStockpile = readArray(buf);
                int[] materialDebt = readArray(buf);
                double[] materialPerCycle = readDoubleArray(buf);
                int developmentLevel = buf.readVarInt();
                int developmentPoints = buf.readVarInt();
                int developmentNextThreshold = buf.readVarInt();
                String developmentPerk = buf.readUtf(256);
                String developmentNextPerk = buf.readUtf(256);
                return new EconomySnapshotPayload(countryName, direction, government, religion, population,
                    populationWorkforceModifier, policySummary, treasury, infrastructureCost, moneyDebt,
                    dieselModifier, totalMaterialPerCycle, materialIds, materialNames, materialStockpile, materialDebt,
                    materialPerCycle, developmentLevel, developmentPoints, developmentNextThreshold, developmentPerk,
                    developmentNextPerk);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, EconomySnapshotPayload value) {
                buf.writeUtf(limitUtf(value.countryName, 128), 128);
                buf.writeUtf(limitUtf(value.direction, 64), 64);
                buf.writeUtf(limitUtf(value.government, 64), 64);
                buf.writeUtf(limitUtf(value.religion, 64), 64);
                buf.writeVarInt(Math.max(0, value.population));
                buf.writeDouble(value.populationWorkforceModifier);
                buf.writeUtf(limitUtf(value.policySummary, 512), 512);
                buf.writeInt(value.treasury);
                buf.writeDouble(value.infrastructureCost);
                buf.writeDouble(value.moneyDebt);
                buf.writeDouble(value.dieselModifier);
                buf.writeDouble(value.totalMaterialPerCycle);
                writeStringArray(buf, value.materialIds, MAX_MATERIALS, 120);
                writeStringArray(buf, value.materialNames, MAX_MATERIALS, 240);
                writeArray(buf, value.materialStockpile);
                writeArray(buf, value.materialDebt);
                writeDoubleArray(buf, value.materialPerCycle);
                buf.writeVarInt(Math.max(1, value.developmentLevel));
                buf.writeVarInt(Math.max(0, value.developmentPoints));
                buf.writeVarInt(Math.max(0, value.developmentNextThreshold));
                buf.writeUtf(limitUtf(value.developmentPerk, 256), 256);
                buf.writeUtf(limitUtf(value.developmentNextPerk, 256), 256);
            }
        };


    private static final int MAX_MATERIALS = 32;

    private static void writeArray(FriendlyByteBuf buf, int[] values) {
        int size = Math.min(MAX_MATERIALS, values == null ? 0 : values.length);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buf.writeVarInt(Math.max(0, values[i]));
        }
    }

    private static int[] readArray(FriendlyByteBuf buf) {
        int size = Math.min(MAX_MATERIALS, Math.max(0, buf.readVarInt()));
        int[] values = new int[size];
        for (int i = 0; i < size; i++) {
            values[i] = Math.max(0, buf.readVarInt());
        }
        return values;
    }

    private static void writeStringArray(FriendlyByteBuf buf, String[] values, int maxSize, int maxChars) {
        int size = Math.min(maxSize, values == null ? 0 : values.length);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            String value = values[i] == null ? "" : values[i];
            buf.writeUtf(limitUtf(value, maxChars), maxChars);
        }
    }

    private static String limitUtf(String value, int maxChars) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    private static String[] readStringArray(FriendlyByteBuf buf, int maxChars) {
        int size = Math.min(MAX_MATERIALS, Math.max(0, buf.readVarInt()));
        String[] values = new String[size];
        for (int i = 0; i < size; i++) {
            values[i] = buf.readUtf(maxChars);
        }
        return values;
    }

    private static void writeDoubleArray(FriendlyByteBuf buf, double[] values) {
        int size = Math.min(MAX_MATERIALS, values == null ? 0 : values.length);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buf.writeDouble(Math.max(0.0D, values[i]));
        }
    }

    private static double[] readDoubleArray(FriendlyByteBuf buf) {
        int size = Math.min(MAX_MATERIALS, Math.max(0, buf.readVarInt()));
        double[] values = new double[size];
        for (int i = 0; i < size; i++) {
            values[i] = Math.max(0.0D, buf.readDouble());
        }
        return values;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
