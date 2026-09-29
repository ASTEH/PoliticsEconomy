package ru.zela.politicseconomy.client;

import net.neoforged.fml.ModList;
import xaero.common.HudMod;
import xaero.map.WorldMap;
import xaero.map.mods.minimap.element.MinimapElementRendererWrapper;

/**
 * Optional Xaero World Map integration.
 *
 * This class is only loaded when Xaero's World Map is actually present.
 */
public final class XaeroPoliticalMapCompat {
    private static boolean registered;
    private static PoliticalChunkElementRenderer renderer;

    private XaeroPoliticalMapCompat() {}

    public static boolean tryRegister() {
        if (registered) return true;
        if (!ModList.get().isLoaded("xaeroworldmap") || !ModList.get().isLoaded("xaerominimap")) return false;
        if (WorldMap.mapElementRenderHandler == null || HudMod.INSTANCE == null) return false;

        if (renderer == null) {
            renderer = PoliticalChunkElementRenderer.create();
        }

        WorldMap.mapElementRenderHandler.add(
            MinimapElementRendererWrapper.Builder
                .begin(renderer)
                .setModMain(HudMod.INSTANCE)
                .setShouldRenderSupplier(() -> true)
                .setOrder(0)
                .build()
        );

        registered = true;
        return true;
    }

    public static void reset() {
        // Xaero owns the renderer registry for the lifetime of the client.
        // We only clear our local reference so a future world session can
        // reinitialize safely if Xaero recreates its handler.
        renderer = null;
        registered = false;
    }
}
