package ru.zela.politicseconomy.client;

import icyllis.modernui.mc.MuiModApi;
import net.minecraft.client.Minecraft;
import ru.zela.politicseconomy.network.EconomySnapshotPayload;

/** Opens the Modern UI dashboard on the client after a server snapshot arrives. */
public final class EconomyClientPayloadHandler {
    private EconomyClientPayloadHandler() {}

    public static void handle(EconomySnapshotPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.level == null) {
                return;
            }
            minecraft.setScreen(
                MuiModApi.get().createScreen(
                    new EconomyFragment(payload),
                    null,
                    minecraft.screen
                )
            );
        });
    }
}
