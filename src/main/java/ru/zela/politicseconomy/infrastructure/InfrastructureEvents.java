package ru.zela.politicseconomy.infrastructure;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.krona.politicsmod.PoliticsManager;
import ru.zela.politicseconomy.integration.EnterpriseMaterialControlService;

/** Records only blocks that were explicitly placed by a player. */
public final class InfrastructureEvents {
    private InfrastructureEvents() {}

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        PoliticsManager politics = PoliticsManager.get(level);
        String ownerCountry = politics == null ? null : politics.getPlayerCountry(event.getEntity().getUUID());
        InfrastructureManager.add(level, event.getPos(), event.getPlacedBlock(), ownerCountry);
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        EnterpriseMaterialControlService.remove(level, event.getPos());
        InfrastructureManager.remove(level, event.getPos());
    }
}
