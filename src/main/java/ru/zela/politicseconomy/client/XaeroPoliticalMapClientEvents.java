package ru.zela.politicseconomy.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import ru.zela.politicseconomy.PoliticsEconomy;

/** Client lifecycle hooks for the optional Xaero political map layer. */
@EventBusSubscriber(modid = PoliticsEconomy.MOD_ID, value = Dist.CLIENT)
public final class XaeroPoliticalMapClientEvents {
    private static boolean wasInWorld;

    private XaeroPoliticalMapClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean inWorld = mc.level != null && mc.player != null;

        if (!inWorld) {
            wasInWorld = false;
            return;
        }

        wasInWorld = true;
        XaeroPoliticalMapCompat.tryRegister();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PoliticalClaimsClientState.clear();
        XaeroPoliticalMapCompat.reset();
        wasInWorld = false;
    }
}
