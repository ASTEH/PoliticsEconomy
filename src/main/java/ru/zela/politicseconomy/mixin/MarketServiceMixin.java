package ru.zela.politicseconomy.mixin;

import net.krona.politicsmod.PoliticsManager;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.zela.politicseconomy.country.CountryDirection;
import ru.zela.politicseconomy.country.CountryDirectionBonusService;

/**
 * Connects the country's trade direction to the actual PoliticsMod market fee.
 * PoliticsMod charges the fee to the seller, so the seller's direction is the
 * direction that modifies the fee.
 */
@Mixin(value = net.krona.politicsmod.market.MarketService.class, remap = false)
public abstract class MarketServiceMixin {
    @Inject(method = "feeFor", at = @At("HEAD"), cancellable = true, remap = false)
    private static void politicseconomy$modifyFee(
        PoliticsManager manager,
        String buyer,
        String seller,
        int price,
        CallbackInfoReturnable<Integer> cir
    ) {
        if (manager.isAllied(buyer, seller)) {
            cir.setReturnValue(0);
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        int effectivePercent = CountryDirectionBonusService.effectiveTradeFeePercent(server, seller);
        int fee = price * effectivePercent / 100;
        cir.setReturnValue(Math.max(0, fee));
    }
}
