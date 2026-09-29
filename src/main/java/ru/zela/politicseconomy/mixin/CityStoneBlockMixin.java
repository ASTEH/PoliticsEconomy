package ru.zela.politicseconomy.mixin;

import net.krona.politicsmod.CityStoneBlock;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * PoliticsMod 0.3 gates the City Stone UI behind creative mode.
 * Politics Economy uses the block as a survival starter/administrative item,
 * so only that gate is relaxed; the original UI/network logic stays intact.
 */
@Mixin(CityStoneBlock.class)
public abstract class CityStoneBlockMixin {
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
