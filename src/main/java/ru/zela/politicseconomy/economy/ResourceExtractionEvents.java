package ru.zela.politicseconomy.economy;

import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** NeoForge bridge for the resource extraction penalty. */
public final class ResourceExtractionEvents {
    private ResourceExtractionEvents() {}

    public static void onBlockDrops(BlockDropsEvent event) {
        if (!event.isCanceled()) {
            ResourceExtractionService.apply(event);
        }
    }
}
