package ru.zela.politicseconomy.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zela.politicseconomy.client.XaeroPoliticalMinimapOverlay;

@Mixin(targets = "xaero.common.minimap.render.MinimapRenderer", remap = false)
public abstract class XaeroMinimapRendererMixin {

    @Inject(
        method = "renderMinimap",
        at = @At(
            value = "INVOKE",
            target = "Lxaero/hud/minimap/element/render/over/MinimapElementOverMapRendererHandler;prepareRender(DDDIIIIZF)V",
            shift = At.Shift.AFTER
        ),
        remap = false
    )
    private void politicseconomy$renderBorders(
        CallbackInfo ci,
        @Local(argsOnly = true) GuiGraphics graphics,
        @Local(ordinal = 0) double renderX,
        @Local(ordinal = 1) double renderZ,
        @Local(ordinal = 2) double zoom,
        @Local(ordinal = 0) int mapX,
        @Local(ordinal = 1) int mapY,
        @Local(ordinal = 2) int specW,
        @Local(ordinal = 3) int specH,
        @Local(ordinal = 0) boolean circle,
        @Local(ordinal = 0) float scale
    ) {
        XaeroPoliticalMinimapOverlay.renderBorders(
            graphics.pose(),
            renderX, renderZ, zoom,
            mapX, mapY, specW, specH, circle, scale
        );
    }
}
