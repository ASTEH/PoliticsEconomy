package ru.zela.politicseconomy.mixin;

import net.krona.politicsmod.FoundingStoneBlock;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Allows the PoliticsMod Founding Stone to open in survival.
 * The actual nation-creation validation remains in PoliticsMod.
 */
@Mixin(FoundingStoneBlock.class)
public abstract class FoundingStoneBlockMixin {
    @Redirect(
        method = "useWithoutItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;isCreative()Z"
        )
    )
    private boolean politicseconomy$allowSurvivalUse(Player player) {
        return true;
    }
}
