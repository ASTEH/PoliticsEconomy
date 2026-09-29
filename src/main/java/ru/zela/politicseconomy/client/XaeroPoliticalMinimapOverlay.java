package ru.zela.politicseconomy.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.Objects;

/**
 * Political borders rendered in Xaero's own minimap render space.
 *
 * The minimap renderer already provides the exact map transform (rotation,
 * zoom and visible half-size), so this code does not guess the HUD rectangle.
 * Rendering here also means the overlay belongs to the minimap framebuffer
 * instead of being drawn over the whole Minecraft HUD.
 */
public final class XaeroPoliticalMinimapOverlay {
    private static final int DARK_COLOR = 0xD9000000;
    private static final float OUTER_THICKNESS = 3.0F;
    private static final float INNER_THICKNESS = 1.6F;

    private XaeroPoliticalMinimapOverlay() {}

    public static void renderBorders(
        PoseStack poseStack,
        double renderX,
        double renderZ,
        double ps,
        double pc,
        double zoom,
        int specW,
        int specH,
        boolean circle
    ) {
        if (specW <= 0 || specH <= 0 || zoom <= 0.0D) {
            return;
        }

        Map<Long, String> claims = PoliticalClaimsClientState.snapshot();
        if (claims.isEmpty()) {
            return;
        }

        int radiusChunks = calculateChunkRadius(zoom, specW, specH);
        int centerChunkX = Mth.floor(renderX) >> 4;
        int centerChunkZ = Mth.floor(renderZ) >> 4;

        BufferBuilder outer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_COLOR
        );
        BufferBuilder inner = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_COLOR
        );

        Matrix4f matrix = poseStack.last().pose();
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.0D, -980.0D);

        boolean drew = false;

        int minX = centerChunkX - radiusChunks;
        int maxX = centerChunkX + radiusChunks;
        int minZ = centerChunkZ - radiusChunks;
        int maxZ = centerChunkZ + radiusChunks;

        // Vertical chunk edges. Each edge is processed exactly once.
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                String leftOwner = claims.get(chunkKey(chunkX, chunkZ));
                String rightOwner = claims.get(chunkKey(chunkX + 1, chunkZ));

                if (Objects.equals(leftOwner, rightOwner)
                    || (leftOwner == null && rightOwner == null)) {
                    continue;
                }

                String owner = leftOwner != null ? leftOwner : rightOwner;
                double worldX = chunkX * 16.0D;
                double worldZ1 = chunkZ * 16.0D;
                double worldZ2 = worldZ1 + 16.0D;

                Segment segment = transformSegment(
                    worldX, worldZ1,
                    worldX, worldZ2,
                    renderX, renderZ,
                    ps, pc, zoom,
                    specW, specH,
                    circle
                );

                if (segment != null) {
                    int color = countryColor(owner, 0xF0);
                    addLineQuad(outer, matrix, segment, OUTER_THICKNESS, DARK_COLOR);
                    addLineQuad(inner, matrix, segment, INNER_THICKNESS, color);
                    drew = true;
                }
            }
        }

        // Horizontal chunk edges.
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                String topOwner = claims.get(chunkKey(chunkX, chunkZ));
                String bottomOwner = claims.get(chunkKey(chunkX, chunkZ + 1));

                if (Objects.equals(topOwner, bottomOwner)
                    || (topOwner == null && bottomOwner == null)) {
                    continue;
                }

                String owner = topOwner != null ? topOwner : bottomOwner;
                double worldZ = chunkZ * 16.0D;
                double worldX1 = chunkX * 16.0D;
                double worldX2 = worldX1 + 16.0D;

                Segment segment = transformSegment(
                    worldX1, worldZ,
                    worldX2, worldZ,
                    renderX, renderZ,
                    ps, pc, zoom,
                    specW, specH,
                    circle
                );

                if (segment != null) {
                    int color = countryColor(owner, 0xF0);
                    addLineQuad(outer, matrix, segment, OUTER_THICKNESS, DARK_COLOR);
                    addLineQuad(inner, matrix, segment, INNER_THICKNESS, color);
                    drew = true;
                }
            }
        }

        poseStack.popPose();

        if (!drew) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        try {
            BufferUploader.drawWithShader(outer.buildOrThrow());
            BufferUploader.drawWithShader(inner.buildOrThrow());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    private static int calculateChunkRadius(double zoom, int specW, int specH) {
        double visibleBlocksX = specW / zoom;
        double visibleBlocksZ = specH / zoom;
        double maxVisibleBlocks = Math.max(visibleBlocksX, visibleBlocksZ);

        return Mth.clamp((int) Math.ceil(maxVisibleBlocks / 16.0D) + 2, 2, 32);
    }

    private static Segment transformSegment(
        double worldX1,
        double worldZ1,
        double worldX2,
        double worldZ2,
        double renderX,
        double renderZ,
        double ps,
        double pc,
        double zoom,
        double specW,
        double specH,
        boolean circle
    ) {
        Point a = transformPoint(
            worldX1, worldZ1, renderX, renderZ, ps, pc, zoom
        );
        Point b = transformPoint(
            worldX2, worldZ2, renderX, renderZ, ps, pc, zoom
        );

        double radiusX = Math.max(1.0D, specW);
        double radiusZ = Math.max(1.0D, specH);

        if (circle) {
            double radius = Math.min(radiusX, radiusZ);
            Segment clipped = clipToCircle(a, b, radius);
            if (clipped == null) {
                return null;
            }
            return clipped;
        }

        return clipToRectangle(a, b, -radiusX, radiusX, -radiusZ, radiusZ);
    }

    private static Point transformPoint(
        double worldX,
        double worldZ,
        double renderX,
        double renderZ,
        double ps,
        double pc,
        double zoom
    ) {
        double offX = worldX - renderX;
        double offZ = worldZ - renderZ;

        double y = (pc * offX + ps * offZ) * zoom;
        double x = (ps * offX - pc * offZ) * zoom;

        return new Point(x, y);
    }

    private static Segment clipToRectangle(
        Point a,
        Point b,
        double minX,
        double maxX,
        double minY,
        double maxY
    ) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double t0 = 0.0D;
        double t1 = 1.0D;

        double[] p = {-dx, dx, -dy, dy};
        double[] q = {a.x - minX, maxX - a.x, a.y - minY, maxY - a.y};

        for (int i = 0; i < 4; i++) {
            if (Math.abs(p[i]) < 1.0E-9D) {
                if (q[i] < 0.0D) {
                    return null;
                }
                continue;
            }

            double r = q[i] / p[i];

            if (p[i] < 0.0D) {
                if (r > t1) return null;
                if (r > t0) t0 = r;
            } else {
                if (r < t0) return null;
                if (r < t1) t1 = r;
            }
        }

        return segmentAt(a, b, t0, t1);
    }

    private static Segment clipToCircle(Point a, Point b, double radius) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;

        double aa = dx * dx + dy * dy;
        double bb = 2.0D * (a.x * dx + a.y * dy);
        double cc = a.x * a.x + a.y * a.y - radius * radius;

        if (cc <= 0.0D && distanceSquared(b) <= radius * radius) {
            return new Segment(a, b);
        }

        if (aa < 1.0E-9D) {
            return cc <= 0.0D ? new Segment(a, b) : null;
        }

        double discriminant = bb * bb - 4.0D * aa * cc;
        double t0 = 0.0D;
        double t1 = 1.0D;

        if (discriminant < 0.0D) {
            if (distanceSquared(a) > radius * radius || distanceSquared(b) > radius * radius) {
                return null;
            }
        } else {
            double root = Math.sqrt(discriminant);
            double r0 = (-bb - root) / (2.0D * aa);
            double r1 = (-bb + root) / (2.0D * aa);

            if (r0 > r1) {
                double tmp = r0;
                r0 = r1;
                r1 = tmp;
            }

            if (r0 > t0) t0 = r0;
            if (r1 < t1) t1 = r1;
        }

        if (t1 < 0.0D || t0 > 1.0D) {
            return null;
        }

        t0 = Mth.clamp((float) t0, 0.0F, 1.0F);
        t1 = Mth.clamp((float) t1, 0.0F, 1.0F);

        return segmentAt(a, b, t0, t1);
    }

    private static double distanceSquared(Point point) {
        return point.x * point.x + point.y * point.y;
    }

    private static Segment segmentAt(Point a, Point b, double t0, double t1) {
        if (t1 < t0) {
            return null;
        }

        return new Segment(
            new Point(
                Mth.lerp((float) t0, (float) a.x, (float) b.x),
                Mth.lerp((float) t0, (float) a.y, (float) b.y)
            ),
            new Point(
                Mth.lerp((float) t1, (float) a.x, (float) b.x),
                Mth.lerp((float) t1, (float) a.y, (float) b.y)
            )
        );
    }

    private static void addLineQuad(
        BufferBuilder builder,
        Matrix4f matrix,
        Segment segment,
        float thickness,
        int color
    ) {
        double dx = segment.b.x - segment.a.x;
        double dy = segment.b.y - segment.a.y;
        double length = Math.sqrt(dx * dx + dy * dy);

        if (length < 0.001D) {
            return;
        }

        double half = thickness * 0.5D;
        double nx = -dy / length * half;
        double ny = dx / length * half;

        builder.addVertex(matrix, (float) (segment.a.x + nx), (float) (segment.a.y + ny), 0.0F)
            .setColor(color);
        builder.addVertex(matrix, (float) (segment.a.x - nx), (float) (segment.a.y - ny), 0.0F)
            .setColor(color);
        builder.addVertex(matrix, (float) (segment.b.x - nx), (float) (segment.b.y - ny), 0.0F)
            .setColor(color);
        builder.addVertex(matrix, (float) (segment.b.x + nx), (float) (segment.b.y + ny), 0.0F)
            .setColor(color);
    }

    private static long chunkKey(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }

    private static int countryColor(String country, int alpha) {
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
        return (alpha << 24) | rgb;
    }

    private record Point(double x, double y) {}
    private record Segment(Point a, Point b) {}
}
