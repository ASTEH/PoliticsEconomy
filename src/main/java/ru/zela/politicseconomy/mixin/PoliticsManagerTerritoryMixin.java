package ru.zela.politicseconomy.mixin;

import net.krona.politicsmod.PoliticsManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zela.politicseconomy.territory.TerritoryService;

@Mixin(value = PoliticsManager.class, remap = false)
public abstract class PoliticsManagerTerritoryMixin {
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

        // Never allow claimChunk to overwrite another country's territory.
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

        // Country expansion through claimChunk must be adjacent to the existing
        // country. City chunks are already-owned chunks and do not use this rule.
        if (radius == 0 && (city == null || city.isBlank())
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
