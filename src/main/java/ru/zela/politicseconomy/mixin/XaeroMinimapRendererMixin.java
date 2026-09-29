package ru.zela.politicseconomy.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import ru.zela.politicseconomy.client.XaeroPoliticalMinimapOverlay;

@Mixin(targets = "xaero.common.minimap.render.MinimapRenderer", remap = false)
public abstract class XaeroMinimapRendererMixin {

    @Unique private static double politicseconomy$renderX;
    @Unique private static double politicseconomy$renderZ;
    @Unique private static double politicseconomy$zoom;
    @Unique private static int politicseconomy$mapX;
    @Unique private static int politicseconomy$mapY;
    @Unique private static int politicseconomy$specW;
    @Unique private static int politicseconomy$specH;
    @Unique private static boolean politicseconomy$circle;
    @Unique private static float politicseconomy$scale;

    @ModifyArgs(
        method = "renderMinimap",
        at = @At(
            value = "INVOKE",
            target = "Lxaero/hud/minimap/element/render/over/MinimapElementOverMapRendererHandler;prepareRender(DDDIIIIZF)V"
        ),
        remap = false
    )
    private void politicseconomy$captureXaeroTransform(Args args) {
        politicseconomy$renderX = (double) args.get(0);
        politicseconomy$renderZ = (double) args.get(1);
        politicseconomy$zoom = (double) args.get(2);
        politicseconomy$mapX = (int) args.get(3);
        politicseconomy$mapY = (int) args.get(4);
        politicseconomy$specW = (int) args.get(5);
        politicseconomy$specH = (int) args.get(6);
        politicseconomy$circle = (boolean) args.get(7);
        politicseconomy$scale = (float) args.get(8);
    }

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
        @Local(argsOnly = true) GuiGraphics graphics
    ) {
        XaeroPoliticalMinimapOverlay.renderBorders(
            graphics.pose(),
            politicseconomy$renderX,
            politicseconomy$renderZ,
            politicseconomy$zoom,
            politicseconomy$mapX,
            politicseconomy$mapY,
            politicseconomy$specW,
            politicseconomy$specH,
            politicseconomy$circle,
            politicseconomy$scale
        );
    }
}
