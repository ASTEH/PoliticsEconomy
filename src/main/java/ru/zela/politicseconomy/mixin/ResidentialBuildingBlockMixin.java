package ru.zela.politicseconomy.mixin;

import net.krona.politicsmod.ResidentialBuildingBlock;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Residential Building has the same creative-only UI gate in PoliticsMod 0.3.
 * Survival economy gameplay needs citizens to be able to inspect/manage it.
 */
@Mixin(ResidentialBuildingBlock.class)
public abstract class ResidentialBuildingBlockMixin {
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
