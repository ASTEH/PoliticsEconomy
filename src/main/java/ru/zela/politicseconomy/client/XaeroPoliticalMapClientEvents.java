package ru.zela.politicseconomy.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import ru.zela.politicseconomy.PoliticsEconomy;

/**
 * Client lifecycle for the optional Xaero World Map political overlay.
 */
@EventBusSubscriber(modid = PoliticsEconomy.MOD_ID, value = Dist.CLIENT)
public final class XaeroPoliticalMapClientEvents {
    private XaeroPoliticalMapClientEvents() {}

    @SubscribeEvent
    public static void onMapRender(ScreenEvent.Render.Post event) {
        XaeroPoliticalMapOverlay.render(event.getScreen(), event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PoliticalClaimsClientState.clear();
    }
}
