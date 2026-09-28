package ru.zela.politicseconomy.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Decides whether a Create/extension machine must be paused for material debt. */
public final class EconomicMachineControlService {
    private EconomicMachineControlService() {}

    /**
     * Uses remembered enterprise dependencies when available. Unknown machines
     * retain the safe fallback of stopping while their country has any debt.
     */
    public static boolean shouldSuspend(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        return EnterpriseMaterialControlService.shouldSuspend(level, pos);
    }

    public static boolean isInCountry(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        var manager = net.krona.politicsmod.PoliticsManager.get(level);
        return manager != null && manager.getCountryNameAt(new ChunkPos(pos)) != null;
    }
}
