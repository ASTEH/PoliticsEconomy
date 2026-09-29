package ru.zela.politicseconomy.mixin;

import dev.architectury.networking.NetworkManager;
import net.krona.politicsmod.network.ClaimLandPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zela.politicseconomy.territory.TerritoryService;

@Mixin(value = ClaimLandPayload.class, remap = false)
public abstract class ClaimLandPayloadMixin {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private static void politicseconomy$handleClaim(
        ClaimLandPayload payload,
        NetworkManager.PacketContext context,
        CallbackInfo ci
    ) {
        context.queue(() -> TerritoryService.handleClaimPacket(
            (net.minecraft.server.level.ServerPlayer) context.getPlayer(),
            payload.center(),
            payload.countryName(),
            payload.cityName(),
            payload.isCityClaim()
        ));
        ci.cancel();
    }
}
