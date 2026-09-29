package ru.zela.politicseconomy.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import ru.zela.politicseconomy.client.PoliticalClaimsClientState;
import xaero.common.HudMod;
import xaero.common.IXaeroMinimap;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.common.minimap.element.render.MinimapElementReader;
import xaero.common.minimap.element.render.MinimapElementRenderLocation;
import xaero.common.minimap.element.render.MinimapElementRenderProvider;
import xaero.common.minimap.element.render.MinimapElementRenderer;
import xaero.common.minimap.render.MinimapRendererHelper;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Native Xaero World Map element renderer for Politics Economy country claims.
 *
 * Each occupied chunk is drawn as a semi-transparent 16x16 world-space cell,
 * using a stable color derived from its country name.
 */
public final class PoliticalChunkElementRenderer
    extends MinimapElementRenderer<PoliticalChunkElementRenderer.ChunkClaim, PoliticalChunkElementRenderer.Context> {

    public static final class Context {}

    private PoliticalChunkElementRenderer(
        Reader reader,
        Provider provider,
        Context context
    ) {
        super(reader, provider, context);
    }

    public static PoliticalChunkElementRenderer create() {
        return new PoliticalChunkElementRenderer(new Reader(), new Provider(), new Context());
    }

    @Override
    public boolean shouldRender(int location) {
        return location == MinimapElementRenderLocation.WORLD_MAP;
    }

    @Override
    public void preRender(
        int location,
        Entity renderEntity,
        Player player,
        double renderX,
        double renderY,
        double renderZ,
        IXaeroMinimap modMain,
        MultiBufferSource.BufferSource bufferSource,
        MultiTextureRenderTypeRendererProvider rendererProvider
    ) {}

    @Override
    public void postRender(
        int location,
        Entity renderEntity,
        Player player,
        double renderX,
        double renderY,
        double renderZ,
        IXaeroMinimap modMain,
        MultiBufferSource.BufferSource bufferSource,
        MultiTextureRenderTypeRendererProvider rendererProvider
    ) {}

    @Override
    public boolean renderElement(
        int location,
        boolean hovered,
        boolean outOfBounds,
        GuiGraphics graphics,
        MultiBufferSource.BufferSource bufferSource,
        Font font,
        RenderTarget framebuffer,
        MinimapRendererHelper helper,
        Entity renderEntity,
        Player player,
        double renderX,
        double renderY,
        double renderZ,
        int elementIndex,
        double optionalDepth,
        float optionalScale,
        ChunkClaim element,
        double partialX,
        double partialY,
        boolean cave,
        float partialTicks
    ) {
        if (location != MinimapElementRenderLocation.WORLD_MAP) {
            return false;
        }

        float scale = Math.max(0.08F, Math.min(4.0F, optionalScale));
        int half = Math.max(1, Math.round(8.0F * scale));

        int fillColor = countryFill(element.country());
        int borderColor = countryBorder(element.country());

        PoseStack pose = graphics.pose();
        pose.pushPose();

        graphics.fill(
            -half,
            -half,
            half,
            half,
            fillColor
        );

        // Thin border. It becomes a subtle national frontier at normal zoom.
        graphics.fill(-half, -half, half, Math.min(half, -half + 1), borderColor);
        graphics.fill(-half, Math.max(-half, half - 1), half, half, borderColor);
        graphics.fill(-half, -half, Math.min(half, -half + 1), half, borderColor);
        graphics.fill(Math.max(-half, half - 1), -half, half, half, borderColor);

        pose.popPose();
        graphics.flush();
        return false;
    }

    private static int countryFill(String country) {
        return palette(country, 0x62);
    }

    private static int countryBorder(String country) {
        return palette(country, 0xB8);
    }

    private static int palette(String country, int alpha) {
        int[] colors = {
            0x4A90E2,
            0xE05656,
            0x55B86A,
            0x9B6BDB,
            0xE19A3A,
            0x42B7B7,
            0xD66D9A,
            0x7E9CCF,
            0x8DBF48,
            0xC86F3B,
            0x5C86C7,
            0xAA6FB5
        };

        int hash = country == null ? 0 : country.hashCode();
        hash ^= hash >>> 16;
        int rgb = colors[Math.floorMod(hash, colors.length)];
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    public record ChunkClaim(long chunk, String country) {
        public double x() {
            return PoliticalClaimsClientState.chunkX(chunk) * 16.0D + 8.0D;
        }

        public double z() {
            return PoliticalClaimsClientState.chunkZ(chunk) * 16.0D + 8.0D;
        }
    }

    public static final class Provider
        extends MinimapElementRenderProvider<ChunkClaim, Context> {

        private Iterator<ChunkClaim> iterator;

        @Override
        public void begin(int location, Context context) {
            List<ChunkClaim> claims = PoliticalClaimsClientState.snapshot().entrySet().stream()
                .map(entry -> new ChunkClaim(entry.getKey(), entry.getValue()))
                .toList();
            iterator = claims.iterator();
        }

        @Override
        public boolean hasNext(int location, Context context) {
            return iterator != null && iterator.hasNext();
        }

        @Override
        public ChunkClaim getNext(int location, Context context) {
            return iterator.next();
        }

        @Override
        public void end(int location, Context context) {
            iterator = null;
        }
    }

    public static final class Reader
        extends MinimapElementReader<ChunkClaim, Context> {

        @Override
        public boolean isHidden(ChunkClaim element, Context context) {
            return false;
        }

        @Override
        public double getRenderX(ChunkClaim element, Context context, float partialTicks) {
            return element.x();
        }

        @Override
        public double getRenderY(ChunkClaim element, Context context, float partialTicks) {
            return 0.0D;
        }

        @Override
        public double getRenderZ(ChunkClaim element, Context context, float partialTicks) {
            return element.z();
        }

        @Override
        public int getInteractionBoxLeft(ChunkClaim element, Context context, float partialTicks) {
            return -8;
        }

        @Override
        public int getInteractionBoxRight(ChunkClaim element, Context context, float partialTicks) {
            return 8;
        }

        @Override
        public int getInteractionBoxTop(ChunkClaim element, Context context, float partialTicks) {
            return -8;
        }

        @Override
        public int getInteractionBoxBottom(ChunkClaim element, Context context, float partialTicks) {
            return 8;
        }

        @Override
        public int getRenderBoxLeft(ChunkClaim element, Context context, float partialTicks) {
            return -12;
        }

        @Override
        public int getRenderBoxRight(ChunkClaim element, Context context, float partialTicks) {
            return 12;
        }

        @Override
        public int getRenderBoxTop(ChunkClaim element, Context context, float partialTicks) {
            return -12;
        }

        @Override
        public int getRenderBoxBottom(ChunkClaim element, Context context, float partialTicks) {
            return 12;
        }

        @Override
        public int getLeftSideLength(ChunkClaim element, Minecraft mc) {
            return 0;
        }

        @Override
        public String getMenuName(ChunkClaim element) {
            return element.country();
        }

        @Override
        public String getFilterName(ChunkClaim element) {
            return element.country();
        }

        @Override
        public int getMenuTextFillLeftPadding(ChunkClaim element) {
            return 0;
        }

        @Override
        public int getRightClickTitleBackgroundColor(ChunkClaim element) {
            return 0xAA000000;
        }

        @Override
        public boolean shouldScaleBoxWithOptionalScale() {
            return true;
        }

        @Override
        public boolean isInteractable(
            xaero.hud.minimap.element.render.MinimapElementRenderLocation location,
            ChunkClaim element
        ) {
            return false;
        }
    }
}
