package ru.zela.politicseconomy.client;

import net.minecraft.client.Minecraft;
import ru.zela.politicseconomy.network.EconomySnapshotPayload;

/** Opens or updates the native Minecraft economy dashboard. */
public final class EconomyClientPayloadHandler {
    private static boolean openRequested;

    private EconomyClientPayloadHandler() {}

    public static void requestOpen() {
        openRequested = true;
    }

    public static void handle(EconomySnapshotPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.level == null) {
                return;
            }

            if (minecraft.screen instanceof EconomyScreen screen) {
                screen.applySnapshot(payload);
                return;
            }

            if (minecraft.screen instanceof CountryTechnologyScreen screen) {
                screen.applySnapshot(payload);
                return;
            }

            if (openRequested) {
                openRequested = false;
                minecraft.setScreen(new EconomyScreen(payload));
            }
        });
    }
}
