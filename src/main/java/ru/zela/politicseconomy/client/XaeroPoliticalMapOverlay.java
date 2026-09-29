package ru.zela.politicseconomy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

            if (right < 0 || left > screen.width || bottom < 0 || top > screen.height) {
                continue;
            }

            if (right <= left) right = left + 1;
            if (bottom <= top) bottom = top + 1;

            int fill = countryFill(country);
            graphics.fill(left, top, right, bottom, fill);

            drawExternalBorders(
                screen,
                graphics,
                claims,
                chunkX,
                chunkZ,
                country,
                left,
                right,
                top,
                bottom
            );
        }
    }

    private static void drawExternalBorders(
        Screen screen,
        GuiGraphics graphics,
        Map<Long, String> claims,
        int chunkX,
        int chunkZ,
        String country,
        int left,
        int right,
        int top,
        int bottom
    ) {
        int border = countryBorder(country);

        // Draw only borders where the adjacent chunk is empty or belongs to another state.
        // This keeps a country as one continuous colored area instead of a visible grid.
        String west = claims.get(chunkKey(chunkX - 1, chunkZ));
        String east = claims.get(chunkKey(chunkX + 1, chunkZ));
        String north = claims.get(chunkKey(chunkX, chunkZ - 1));
        String south = claims.get(chunkKey(chunkX, chunkZ + 1));

        if (!country.equals(west)) {
            graphics.fill(left, top, Math.min(left + 1, right), bottom, border);
        }
        if (!country.equals(east)) {
            graphics.fill(Math.max(right - 1, left), top, right, bottom, border);
        }
        if (!country.equals(north)) {
            graphics.fill(left, top, right, Math.min(top + 1, bottom), border);
        }
        if (!country.equals(south)) {
            graphics.fill(left, Math.max(bottom - 1, top), right, bottom, border);
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
