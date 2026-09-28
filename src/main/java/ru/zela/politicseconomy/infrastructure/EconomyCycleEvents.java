package ru.zela.politicseconomy.infrastructure;

import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Hooks maintenance to the same configurable cycle length used by PoliticsMod. */
public final class EconomyCycleEvents {
    private EconomyCycleEvents() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MaintenanceService.onServerTick(event.getServer());
    }
}
