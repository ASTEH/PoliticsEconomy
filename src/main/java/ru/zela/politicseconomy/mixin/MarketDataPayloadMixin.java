package ru.zela.politicseconomy.mixin;

import net.krona.politicsmod.network.MarketDataPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.zela.politicseconomy.country.CountryDirectionBonusService;

/** Keeps the Trade Warehouse UI in sync with the fee actually charged on the server. */
@Mixin(value = MarketDataPayload.class, remap = false)
public abstract class MarketDataPayloadMixin {
    @Inject(method = "build", at = @At("RETURN"), cancellable = true, remap = false)
    private static void politicseconomy$modifyDisplayedFee(
        ServerPlayer player,
        boolean open,
        CallbackInfoReturnable<MarketDataPayload> cir
    ) {
        MarketDataPayload payload = cir.getReturnValue();
        if (payload == null || payload.myCountry().isBlank()) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        int effective = CountryDirectionBonusService.effectiveTradeFeePercent(server, payload.myCountry());
        if (effective == payload.feePercent()) {
            return;
        }

        cir.setReturnValue(new MarketDataPayload(
            payload.open(),
            payload.myCountry(),
            payload.myRole(),
            payload.treasury(),
            effective,
            payload.maxPrice(),
            payload.entries()
        ));
    }
}
