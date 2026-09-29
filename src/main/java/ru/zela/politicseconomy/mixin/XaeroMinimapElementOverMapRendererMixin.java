package ru.zela.politicseconomy.mixin;

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
            readSpecWidth(),
            readSpecHeight(),
            readCircle()
        );
    }

    /*
     * The render method's later arguments are deliberately not referenced
     * directly. The defaults below match Xaero's normal minimap geometry and
     * keep this optional integration independent from Xaero's implementation
     * classes. The actual map transform (renderX/renderZ/ps/pc/zoom) still
     * comes directly from Xaero.
     */
    private int readSpecWidth() {
        return 128;
    }

    private int readSpecHeight() {
        return 128;
    }

    private boolean readCircle() {
        return false;
    }
}
