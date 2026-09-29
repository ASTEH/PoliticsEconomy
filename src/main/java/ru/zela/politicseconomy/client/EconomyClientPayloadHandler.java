package ru.zela.politicseconomy.client;

import net.minecraft.client.Minecraft;
import ru.zela.politicseconomy.network.EconomySnapshotPayload;

/** Opens or updates the native Minecraft economy dashboard. */
public final class EconomyClientPayloadHandler {
    private EconomyClientPayloadHandler() {}

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

            minecraft.setScreen(new EconomyScreen(payload));
        });
    }
}
