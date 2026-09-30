package ru.zela.politicseconomy.mixin;

import net.krona.politicsmod.network.CreateCountryPayload;
import dev.architectury.networking.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zela.politicseconomy.territory.TerritoryService;

/**
 * Replaces PoliticsMod's raw country-creation packet handler so the server
 * always validates and uses the player's real current position.
 *
 * This also makes Politics Economy's population bootstrap run immediately
 * after the country is created.
 */
@Mixin(value = CreateCountryPayload.class, remap = false)
public abstract class CreateCountryPayloadMixin {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private static void politicseconomy$handleCreateCountry(
        CreateCountryPayload payload,
        NetworkManager.PacketContext context,
        CallbackInfo ci
    ) {
        context.queue(() -> {
            if (!(context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)) return;

            TerritoryService.handleCreateCountry(
                player,
                payload.pos(),
                payload.countryName()
            );
        });
        ci.cancel();
    }
}
