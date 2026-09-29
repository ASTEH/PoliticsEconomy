package ru.zela.politicseconomy.mixin;

import net.krona.politicsmod.network.CreateCountryPayload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zela.politicseconomy.territory.TerritoryService;

@Mixin(value = CreateCountryPayload.class, remap = false)
public abstract class CreateCountryPayloadMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private static void politicseconomy$handle(
        CreateCountryPayload payload,
        dev.architectury.networking.NetworkManager.PacketContext context,
        CallbackInfo ci
    ) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            ci.cancel();
            return;
        }
        context.queue(() -> TerritoryService.handleCreateCountry(player, payload.pos(), payload.countryName()));
        ci.cancel();
    }
}
