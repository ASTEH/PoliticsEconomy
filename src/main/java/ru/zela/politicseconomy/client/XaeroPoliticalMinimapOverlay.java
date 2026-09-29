package ru.zela.politicseconomy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Political chunk borders for Xaero's Minimap.
 *
 * <p>The integration intentionally avoids a hard compile-time dependency on
 * Xaero. The optional mixin supplies the live renderer instance and this
 * class discovers the HUD rectangle/zoom reflectively. If a particular Xaero
 * build does not expose those values, it falls back to Xaero's default
 * top-right minimap placement.</p>
 */
public final class XaeroPoliticalMinimapOverlay {
    private static final int DEFAULT_SIZE = 128;
    private static final int BORDER_THICKNESS = 2;

    private XaeroPoliticalMinimapOverlay() {}

    public static void render(GuiGraphics graphics, Object renderer) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        Map<Long, String> claims = PoliticalClaimsClientState.snapshot();
        if (claims.isEmpty()) {
            return;
        }

        Layout layout = readLayout(renderer, mc);
        if (layout == null || layout.size <= 0) {
            return;
        }

        long currentChunk = player.chunkPosition().toLong();
        String currentOwner = claims.get(currentChunk);

        graphics.pose().pushPose();
        graphics.pose().translate(layout.centerX, layout.centerY, 0.0F);

        // Xaero's default orientation follows the player. Rotating the political
        // layer by the player's yaw keeps the chunk square aligned with the map.
        graphics.pose().mulPoseMatrix(new Matrix4f().rotateZ((float) Math.toRadians(-player.getYRot())));

        drawVisibleBorders(
            graphics,
            claims,
            player.chunkPosition().x,
            player.chunkPosition().z,
            currentOwner,
            layout.pixelsPerBlock
        );

        graphics.pose().popPose();
    }

    private static void drawVisibleBorders(
        GuiGraphics graphics,
        Map<Long, String> claims,
        int centerChunkX,
        int centerChunkZ,
        String currentOwner,
        double pixelsPerBlock
    ) {
        int radius = Math.max(2, (int) Math.ceil((DEFAULT_SIZE / 16.0D) / Math.max(0.25D, pixelsPerBlock)));
        radius = Math.min(radius, 8);

        int cell = Math.max(2, (int) Math.round(16.0D * pixelsPerBlock));
        int halfRadius = radius * cell;

        for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
            for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
                long key = chunkKey(chunkX, chunkZ);
                String owner = claims.get(key);
                if (owner == null) {
                    continue;
                }

                int left = (chunkX - centerChunkX) * cell;
                int top = (chunkZ - centerChunkZ) * cell;
                int right = left + cell;
                int bottom = top + cell;
                int color = countryBorder(owner);

                if (!owner.equals(claims.get(chunkKey(chunkX - 1, chunkZ)))) {
                    drawVertical(graphics, left, top, bottom, color, halfRadius);
                }
                if (!owner.equals(claims.get(chunkKey(chunkX + 1, chunkZ)))) {
                    drawVertical(graphics, right - BORDER_THICKNESS, top, bottom, color, halfRadius);
                }
                if (!owner.equals(claims.get(chunkKey(chunkX, chunkZ - 1)))) {
                    drawHorizontal(graphics, left, right, top, color, halfRadius);
                }
                if (!owner.equals(claims.get(chunkKey(chunkX, chunkZ + 1)))) {
                    drawHorizontal(graphics, left, right, bottom - BORDER_THICKNESS, color, halfRadius);
                }
            }
        }

        // Make the currently occupied country's border slightly brighter so the
        // player can immediately distinguish their own frontier on the minimap.
        if (currentOwner != null) {
            int highlight = countryHighlight(currentOwner);
            int currentLeft = -cell / 2;
            int currentTop = -cell / 2;
            int currentRight = currentLeft + cell;
            int currentBottom = currentTop + cell;

            if (!currentOwner.equals(claims.get(chunkKey(centerChunkX - 1, centerChunkZ)))) {
                drawVertical(graphics, currentLeft, currentTop, currentBottom, highlight, halfRadius);
            }
            if (!currentOwner.equals(claims.get(chunkKey(centerChunkX + 1, centerChunkZ)))) {
                drawVertical(graphics, currentRight - BORDER_THICKNESS, currentTop, currentBottom, highlight, halfRadius);
            }
            if (!currentOwner.equals(claims.get(chunkKey(centerChunkX, centerChunkZ - 1)))) {
                drawHorizontal(graphics, currentLeft, currentRight, currentTop, highlight, halfRadius);
            }
            if (!currentOwner.equals(claims.get(chunkKey(centerChunkX, centerChunkZ + 1)))) {
                drawHorizontal(graphics, currentLeft, currentRight, currentBottom - BORDER_THICKNESS, highlight, halfRadius);
            }
        }
    }

    private static void drawVertical(
        GuiGraphics graphics,
        int x,
        int top,
        int bottom,
        int color,
        int limit
    ) {
        int clippedTop = Mth.clamp(top, -limit, limit);
        int clippedBottom = Mth.clamp(bottom, -limit, limit);
        if (clippedBottom > clippedTop) {
            graphics.fill(x, clippedTop, x + BORDER_THICKNESS, clippedBottom, color);
        }
    }

    private static void drawHorizontal(
        GuiGraphics graphics,
        int left,
        int right,
        int y,
        int color,
        int limit
    ) {
        int clippedLeft = Mth.clamp(left, -limit, limit);
        int clippedRight = Mth.clamp(right, -limit, limit);
        if (clippedRight > clippedLeft) {
            graphics.fill(clippedLeft, y, clippedRight, y + BORDER_THICKNESS, color);
        }
    }

    private static Layout readLayout(Object renderer, Minecraft mc) {
        Object minimap = findMinimap(renderer);
        int size = firstInt(
            minimap, renderer,
            "getEffectiveMinimapSize",
            "getMinimapSize",
            "getSize"
        );
        if (size <= 0) {
            size = DEFAULT_SIZE;
        }
        size = Mth.clamp(size, 64, 512);

        Integer centerX = firstIntNullable(
            minimap, renderer,
            "getCenterX",
            "getMinimapCenterX",
            "getScreenCenterX"
        );
        Integer centerY = firstIntNullable(
            minimap, renderer,
            "getCenterY",
            "getMinimapCenterY",
            "getScreenCenterY"
        );

        if (centerX == null) {
            Integer x = firstIntNullable(minimap, renderer, "getX", "getLeft", "getScreenX", "getMinimapX");
            centerX = x == null ? mc.getWindow().getGuiScaledWidth() - size / 2 - 4 : x + size / 2;
        }
        if (centerY == null) {
            Integer y = firstIntNullable(minimap, renderer, "getY", "getTop", "getScreenY", "getMinimapY");
            centerY = y == null ? size / 2 + 4 : y + size / 2;
        }

        double zoom = firstDouble(
            minimap, renderer,
            "getZoom",
            "getMinimapZoom",
            "getScale"
        );
        if (!(zoom > 0.0D)) {
            zoom = 1.0D;
        }

        // Xaero zoom values are scale-like; clamp pathological reflection hits.
        zoom = Mth.clamp((float) zoom, 0.25F, 4.0F);

        return new Layout(centerX, centerY, size, zoom);
    }

    private static Object findMinimap(Object renderer) {
        if (renderer == null) {
            return null;
        }

        Class<?> type = renderer.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType().getName().equals("xaero.hud.minimap.Minimap")) {
                    try {
                        field.setAccessible(true);
                        return field.get(renderer);
                    } catch (ReflectiveOperationException ignored) {
                        return null;
                    }
                }
            }
            type = type.getSuperclass();
        }

        return null;
    }

    private static int firstInt(Object primary, Object secondary, String... names) {
        Integer value = firstIntNullable(primary, secondary, names);
        return value == null ? 0 : value;
    }

    private static Integer firstIntNullable(Object primary, Object secondary, String... names) {
        for (String name : names) {
            Integer a = invokeInt(primary, name);
            if (a != null) return a;
            Integer b = invokeInt(secondary, name);
            if (b != null) return b;
            a = fieldInt(primary, name);
            if (a != null) return a;
            b = fieldInt(secondary, name);
            if (b != null) return b;
        }
        return null;
    }

    private static double firstDouble(Object primary, Object secondary, String... names) {
        for (String name : names) {
            Double a = invokeDouble(primary, name);
            if (a != null) return a;
            Double b = invokeDouble(secondary, name);
            if (b != null) return b;
            a = fieldDouble(primary, name);
            if (a != null) return a;
            b = fieldDouble(secondary, name);
            if (b != null) return b;
        }
        return 0.0D;
    }

    private static Integer invokeInt(Object target, String name) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(name);
            if (method.getReturnType() == int.class || method.getReturnType() == Integer.class) {
                method.setAccessible(true);
                return ((Number) method.invoke(target)).intValue();
            }
        } catch (ReflectiveOperationException ignored) {}
        return null;
    }

    private static Double invokeDouble(Object target, String name) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(name);
            if (method.getReturnType() == double.class
                || method.getReturnType() == float.class
                || method.getReturnType() == Double.class
                || method.getReturnType() == Float.class) {
                method.setAccessible(true);
                return ((Number) method.invoke(target)).doubleValue();
            }
        } catch (ReflectiveOperationException ignored) {}
        return null;
    }

    private static Integer fieldInt(Object target, String name) {
        if (target == null) return null;
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                if (field.getType() != int.class && field.getType() != Integer.class) return null;
                field.setAccessible(true);
                return field.getInt(target);
            } catch (ReflectiveOperationException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static Double fieldDouble(Object target, String name) {
        if (target == null) return null;
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                if (!(field.getType() == double.class
                    || field.getType() == float.class
                    || field.getType() == Double.class
                    || field.getType() == Float.class)) return null;
                field.setAccessible(true);
                return field.getDouble(target);
            } catch (ReflectiveOperationException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static long chunkKey(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }

    private static int countryBorder(String country) {
        return palette(country, 0xE8);
    }

    private static int countryHighlight(String country) {
        return palette(country, 0xFF);
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

    private record Layout(int centerX, int centerY, int size, double pixelsPerBlock) {}
}
