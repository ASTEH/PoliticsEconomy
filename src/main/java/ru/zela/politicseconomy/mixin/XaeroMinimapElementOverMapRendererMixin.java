package ru.zela.politicseconomy.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zela.politicseconomy.client.XaeroPoliticalMinimapOverlay;

@Mixin(
    targets = "xaero.common.minimap.element.render.over.MinimapElementOverMapRendererHandler",
    remap = false
)
public abstract class XaeroMinimapElementOverMapRendererMixin {

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void politicseconomy$renderBorders(
        PoseStack poseStack,
        Entity renderEntity,
        Player player,
        double renderX,
        double renderY,
        double renderZ,
        double ps,
        double pc,
        double zoom,
        CallbackInfo ci,
        @Local(argsOnly = true, ordinal = 0) int specW,
        @Local(argsOnly = true, ordinal = 1) int specH,
        @Local(argsOnly = true, ordinal = 1) boolean circle
    ) {
        if (player == null) {
            return;
        }

        XaeroPoliticalMinimapOverlay.renderBorders(
            poseStack,
            renderX,
            renderZ,
            ps,
            pc,
            zoom,
            specW,
            specH,
            circle
        );
    }
}
