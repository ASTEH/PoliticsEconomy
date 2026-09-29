package ru.zela.politicseconomy.mixin;

import net.krona.politicsmod.network.FoundCityPayload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zela.politicseconomy.territory.TerritoryService;

@Mixin(value = FoundCityPayload.class, remap = false)
public abstract class FoundCityPayloadMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private static void politicseconomy$handle(
        FoundCityPayload payload,
        dev.architectury.networking.NetworkManager.PacketContext context,
        CallbackInfo ci
    ) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            ci.cancel();
            return;
        }
        context.queue(() -> TerritoryService.handleFoundCity(player, payload.pos(), payload.cityName()));
        ci.cancel();
    }
}
