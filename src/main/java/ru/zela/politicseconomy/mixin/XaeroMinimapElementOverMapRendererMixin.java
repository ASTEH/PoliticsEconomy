package ru.zela.politicseconomy.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zela.politicseconomy.client.XaeroPoliticalMinimapOverlay;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.common.minimap.element.render.over.MinimapElementOverMapRendererHandler;
import xaero.common.minimap.render.MinimapRendererHelper;

@Mixin(MinimapElementOverMapRendererHandler.class)
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
        boolean cave,
        float partialTicks,
        RenderTarget framebuffer,
        @Coerce Object modMain,
        MinimapRendererHelper helper,
        MultiBufferSource.BufferSource renderTypeBuffers,
        Font font,
        MultiTextureRenderTypeRendererProvider multiTextureRenderTypeRenderers,
        int specW,
        int specH,
        int halfViewW,
        int halfViewH,
        boolean circle,
        float minimapScale,
        CallbackInfo ci
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
