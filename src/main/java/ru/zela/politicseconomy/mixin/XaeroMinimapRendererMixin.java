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

    @Inject(method = "renderMinimap", at = @At("RETURN"), remap = false)
    private void politicseconomy$renderBorders(
        CallbackInfo ci,
        @Local(argsOnly = true) GuiGraphics graphics
    ) {
        XaeroPoliticalMinimapOverlay.render(graphics, this);
    }
}
