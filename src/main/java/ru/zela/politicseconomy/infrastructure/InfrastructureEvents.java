package ru.zela.politicseconomy.infrastructure;

import net.krona.politicsmod.PoliticsManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.event.level.BlockEvent;
import ru.zela.politicseconomy.country.CountryPopulationService;
import ru.zela.politicseconomy.country.CountryWorkplaceService;
import ru.zela.politicseconomy.integration.EnterpriseMaterialControlService;
import ru.zela.politicseconomy.territory.TerritoryService;

/** Records explicitly placed infrastructure and keeps bed-population counters current. */
public final class InfrastructureEvents {
    private InfrastructureEvents() {}

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            String blockId = BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()).toString();
            if ((blockId.equals("politicsmod:founding_stone") || blockId.equals("politicsmod:city_stone"))
                && !TerritoryService.canPlaceAdministrativeBlock(player, new net.minecraft.world.level.ChunkPos(event.getPos()), blockId)) {
                event.setCanceled(true);
                return;
            }
        }

        PoliticsManager politics = PoliticsManager.get(level);
        String ownerCountry = politics == null
            ? null
            : politics.getPlayerCountry(event.getEntity().getUUID());

        InfrastructureManager.add(level, event.getPos(), event.getPlacedBlock(), ownerCountry);
        CountryWorkplaceService.invalidate(level.getServer());

        if (event.getPlacedBlock().getBlock() instanceof BedBlock) {
            CountryPopulationService.onBedPlaced(
                level,
                event.getPos(),
                (ServerPlayer) event.getEntity()
            );
        }
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        boolean wasBed = event.getState().getBlock() instanceof BedBlock;

        EnterpriseMaterialControlService.remove(level, event.getPos());
        InfrastructureManager.remove(level, event.getPos());
        CountryWorkplaceService.invalidate(level.getServer());

        if (wasBed) {
            CountryPopulationService.onBedChange(level, event.getPos());
        }
    }
}
