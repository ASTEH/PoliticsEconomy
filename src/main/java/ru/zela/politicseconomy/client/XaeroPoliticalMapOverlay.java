package ru.zela.politicseconomy.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Real chunk-aligned political overlay for Xaero's fullscreen World Map.
 *
 * Unlike map elements, this renders each claimed chunk from its actual
 * world-space corners:
 *   (chunkX * 16, chunkZ * 16)
 *   ((chunkX + 1) * 16, (chunkZ + 1) * 16)
 *
 * Xaero's current map converts those world coordinates into screen coordinates,
 * so the overlay follows zoom/pan exactly.
 */
public final class XaeroPoliticalMapOverlay {
    private static final String GUI_MAP_CLASS = "xaero.map.gui.GuiMap";

    private XaeroPoliticalMapOverlay() {}

    public static void render(Screen screen, GuiGraphics graphics) {
        if (!ModList.get().isLoaded("xaeroworldmap")) return;
        if (screen == null || !GUI_MAP_CLASS.equals(screen.getClass().getName())) return;
        if (Minecraft.getInstance().level == null) return;

        MapState state = readMapState(screen);
        if (state == null) return;
        if (!"minecraft:overworld".equals(state.dimension())) return;

        Map<Long, String> claims = PoliticalClaimsClientState.snapshot();
        if (claims.isEmpty()) return;

        int minChunkX = floorDiv(state.screenToBlockX(0), 16) - 1;
        int maxChunkX = floorDiv(state.screenToBlockX(screen.width), 16) + 1;
        int minChunkZ = floorDiv(state.screenToBlockZ(0), 16) - 1;
        int maxChunkZ = floorDiv(state.screenToBlockZ(screen.height), 16) + 1;

        List<Quad> quads = new ArrayList<>();
        List<BorderLine> borders = new ArrayList<>();

        for (Map.Entry<Long, String> entry : claims.entrySet()) {
            long packed = entry.getKey();
            int chunkX = PoliticalClaimsClientState.chunkX(packed);
            int chunkZ = PoliticalClaimsClientState.chunkZ(packed);

            if (chunkX < minChunkX || chunkX > maxChunkX || chunkZ < minChunkZ || chunkZ > maxChunkZ) {
                continue;
            }

            String country = entry.getValue();
            int left = state.worldToScreenX((double) chunkX * 16.0D);
            int right = state.worldToScreenX((double) (chunkX + 1) * 16.0D);
            int top = state.worldToScreenY((double) chunkZ * 16.0D);
            int bottom = state.worldToScreenY((double) (chunkZ + 1) * 16.0D);

            if (right <= 0 || left >= screen.width || bottom <= 0 || top >= screen.height) continue;

            if (right == left) right = left + (state.scale < 1.0D ? 1 : 2);
            if (bottom == top) bottom = top + (state.scale < 1.0D ? 1 : 2);

            quads.add(new Quad(
                left, top, right, bottom,
                countryFill(country)
            ));

            int border = countryBorder(country);
            long westKey = chunkKey(chunkX - 1, chunkZ);
            long eastKey = chunkKey(chunkX + 1, chunkZ);
            long northKey = chunkKey(chunkX, chunkZ - 1);
            long southKey = chunkKey(chunkX, chunkZ + 1);

            if (!country.equals(claims.get(westKey))) {
                borders.add(new BorderLine(left, top, left, bottom, border));
            }
            if (!country.equals(claims.get(eastKey))) {
                borders.add(new BorderLine(right - 1, top, right - 1, bottom, border));
            }
            if (!country.equals(claims.get(northKey))) {
                borders.add(new BorderLine(left, top, right, top, border));
            }
            if (!country.equals(claims.get(southKey))) {
                borders.add(new BorderLine(left, bottom - 1, right, bottom - 1, border));
            }
        }

        drawQuads(graphics, quads);
        drawBorders(graphics, borders);
    }

    private static void drawQuads(GuiGraphics graphics, List<Quad> quads) {
        if (quads.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);

        var matrix = graphics.pose().last().pose();
        var buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_COLOR
        );

        for (Quad quad : quads) {
            buffer.addVertex(matrix, quad.left(), quad.top(), 0.0F).setColor(quad.color());
            buffer.addVertex(matrix, quad.right(), quad.top(), 0.0F).setColor(quad.color());
            buffer.addVertex(matrix, quad.right(), quad.bottom(), 0.0F).setColor(quad.color());
            buffer.addVertex(matrix, quad.left(), quad.bottom(), 0.0F).setColor(quad.color());
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void drawBorders(GuiGraphics graphics, List<BorderLine> borders) {
        if (borders.isEmpty()) return;

        for (BorderLine line : borders) {
            graphics.fill(
                line.x1(),
                line.y1(),
                line.x2() == line.x1() ? line.x1() + 1 : line.x2(),
                line.y2() == line.y1() ? line.y1() + 1 : line.y2(),
                line.color()
            );
        }
    }

    private static long chunkKey(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }

    private static int countryFill(String country) {
        return palette(country, 0x55);
    }

    private static int countryBorder(String country) {
        return palette(country, 0xC8);
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

    private static MapState readMapState(Screen screen) {
        try {
            double cameraX = readDouble(screen, "cameraX");
            double cameraZ = readDouble(screen, "cameraZ");

            double scale;
            try {
                scale = readDouble(screen, "scale");
            } catch (ReflectiveOperationException ignored) {
                scale = readStaticDouble(screen.getClass(), "destScale");
            }

            if (!(scale > 0.0D)) return null;

            Minecraft mc = Minecraft.getInstance();
            double guiScale = Math.max(1.0D, mc.getWindow().getGuiScale());
            int windowWidth = mc.getWindow().getWidth();
            int windowHeight = mc.getWindow().getHeight();

            Object processor = invokeNoArgs(screen, "getMapProcessor");
            String dimension = resolveDimension(processor);

            return new MapState(
                cameraX,
                cameraZ,
                scale,
                guiScale,
                windowWidth,
                windowHeight,
                dimension
            );
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String resolveDimension(Object processor) {
        try {
            Object value = invokeNoArgs(processor, "getCurrentDimension");
            if (value instanceof String string && !string.isBlank()) {
                return normalizeDimension(string);
            }
        } catch (ReflectiveOperationException ignored) {}

        try {
            Object value = invokeNoArgs(processor, "getCurrentDimId");
            if (value instanceof String string && !string.isBlank()) {
                return normalizeDimension(string);
            }
        } catch (ReflectiveOperationException ignored) {}

        return "minecraft:overworld";
    }

    private static String normalizeDimension(String value) {
        if (value.contains(":")) return value;
        return "minecraft:" + value;
    }

    private static double readDouble(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static double readStaticDouble(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.getDouble(null);
    }

    private static Object invokeNoArgs(Object target, String name) throws ReflectiveOperationException {
        if (target == null) return null;
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static int floorDiv(int value, int divisor) {
        return Math.floorDiv(value, divisor);
    }

    private record Quad(int left, int top, int right, int bottom, int color) {}

    private record BorderLine(int x1, int y1, int x2, int y2, int color) {}

    private record MapState(
        double cameraX,
        double cameraZ,
        double scale,
        double guiScale,
        int windowWidth,
        int windowHeight,
        String dimension
    ) {
        int worldToScreenX(double blockX) {
            return (int) (((blockX - cameraX) * scale + windowWidth / 2.0D) / guiScale);
        }

        int worldToScreenY(double blockZ) {
            return (int) (((blockZ - cameraZ) * scale + windowHeight / 2.0D) / guiScale);
        }

        int screenToBlockX(double screenX) {
            return (int) Math.floor((screenX * guiScale - windowWidth / 2.0D) / scale + cameraX);
        }

        int screenToBlockZ(double screenY) {
            return (int) Math.floor((screenY * guiScale - windowHeight / 2.0D) / scale + cameraZ);
        }
    }
}
