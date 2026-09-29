package ru.zela.politicseconomy.mixin;

import net.krona.politicsmod.PoliticsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zela.politicseconomy.territory.TerritoryService;

@Mixin(value = PoliticsManager.class, remap = false)
public abstract class PoliticsManagerTerritoryMixin {
    @Inject(method = "createCountry", at = @At("HEAD"), cancellable = true, remap = false)
    private static void politicseconomy$guardCountryCreation(
        Level level,
        BlockPos center,
        Player owner,
        String name,
        CallbackInfo ci
    ) {
        if (owner instanceof ServerPlayer player
            && !TerritoryService.canCreateCountry(player, center)) {
            ci.cancel();
        }
    }

    @Inject(method = "foundNewCity", at = @At("HEAD"), cancellable = true, remap = false)
    private void politicseconomy$guardCityCreation(
        BlockPos center,
        Player player,
        String cityName,
        CallbackInfo ci
    ) {
        if (player instanceof ServerPlayer serverPlayer
            && !TerritoryService.canFoundCity(serverPlayer, center, cityName)) {
            ci.cancel();
        }
    }

    @Inject(method = "createCountry", at = @At("TAIL"), remap = false)
    private static void politicseconomy$syncCountryMap(
        Level level,
        BlockPos center,
        Player owner,
        String name,
        CallbackInfo ci
    ) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ru.zela.politicseconomy.map.PoliticalMapService.syncAll(server);
        }
    }

    @Inject(method = "foundNewCity", at = @At("TAIL"), remap = false)
    private void politicseconomy$syncCityMap(
        BlockPos center,
        Player player,
        String cityName,
        CallbackInfo ci
    ) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ru.zela.politicseconomy.map.PoliticalMapService.syncAll(server);
        }
    }

    @Inject(method = "claimChunk", at = @At("TAIL"), remap = false)
    private void politicseconomy$syncClaimMap(
        ChunkPos center,
        int radius,
        String country,
        String city,
        CallbackInfo ci
    ) {
        if (country != null && !country.isBlank()) {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ru.zela.politicseconomy.map.PoliticalMapService.syncAll(server);
            }
        }
    }

    @Inject(method = "claimChunk", at = @At("HEAD"), cancellable = true, remap = false)
    private void politicseconomy$guardClaim(
        ChunkPos center,
        int radius,
        String country,
        String city,
        CallbackInfo ci
    ) {
        if (country == null || country.isBlank() || center == null) {
            return;
        }

        PoliticsManager manager = (PoliticsManager)(Object)this;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                ChunkPos target = new ChunkPos(center.x + x, center.z + z);
                String owner = manager.getCountryNameAt(target);
                if (owner != null && !country.equals(owner)) {
                    ci.cancel();
                    return;
                }
            }
        }

        if (radius == 0
            && (city == null || city.isBlank())
            && manager.getCountryNameAt(center) == null
            && !hasNeighbor(manager, center, country)) {
            ci.cancel();
        }
    }

    private static boolean hasNeighbor(PoliticsManager manager, ChunkPos chunk, String country) {
        return country.equals(manager.getCountryNameAt(new ChunkPos(chunk.x + 1, chunk.z)))
            || country.equals(manager.getCountryNameAt(new ChunkPos(chunk.x - 1, chunk.z)))
            || country.equals(manager.getCountryNameAt(new ChunkPos(chunk.x, chunk.z + 1)))
            || country.equals(manager.getCountryNameAt(new ChunkPos(chunk.x, chunk.z - 1)));
    }
}
