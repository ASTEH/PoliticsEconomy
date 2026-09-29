package ru.zela.politicseconomy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import ru.zela.politicseconomy.network.EconomyNetwork;
import ru.zela.politicseconomy.network.EconomySnapshotPayload;
import ru.zela.politicseconomy.research.CountryResearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CountryTechnologyScreen extends Screen {
    private static final int BG = 0xFF0B1016;
    private static final int PANEL = 0xFF151C25;
    private static final int PANEL_2 = 0xFF1B2530;
    private static final int BORDER = 0xFF314050;
    private static final int TEXT = 0xFFEAF1F8;
    private static final int MUTED = 0xFF91A0B1;
    private static final int POSITIVE = 0xFF59D18B;
    private static final int NEGATIVE = 0xFFFF6969;
    private static final int GOLD = 0xFFFFC857;
    private static final int BLUE = 0xFF58A8FF;
    private static final int BLUE_DARK = 0xFF173956;
    private static final int DISABLED = 0xFF4C5865;
    private static final int NODE_W = 126;
    private static final int NODE_H = 72;
    private static final int ROW_GAP = 104;

    private EconomySnapshotPayload snapshot;
    private String selectedId;
    private double scroll;

    public CountryTechnologyScreen(EconomySnapshotPayload snapshot) {
        super(Component.literal("Технологии"));
        this.snapshot = snapshot;
    }

    public void applySnapshot(EconomySnapshotPayload payload) {
        this.snapshot = payload;
        if (selectedId == null || technology(selectedId) == null) {
            selectFirst();
        }
    }

    @Override
    protected void init() {
        super.init();
        selectFirst();
    }

    private void selectFirst() {
        String[] rows = snapshot == null ? new String[0] : snapshot.researchRows();
        if (rows.length > 0) {
            selectedId = split(rows[0], 0);
        }
    }

    private CountryResearch technology(String id) {
        return CountryResearch.byId(id);
    }

    private String status(String id) {
        for (String row : snapshot.researchRows()) {
            if (id.equals(split(row, 0))) return split(row, 3);
        }
        return "LOCKED";
    }

    private String rowValue(String id, int index) {
        for (String row : snapshot.researchRows()) {
            if (id.equals(split(row, 0))) return split(row, index);
        }
        return "";
    }

    private static String split(String row, int index) {
        String[] parts = row.split("\\|", -1);
        return index >= 0 && index < parts.length ? parts[index] : "";
    }

    private int parse(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, BG);

        drawHeader(g);
        drawTree(g, mouseX, mouseY);

        if (selectedId != null) {
            drawDetails(g, mouseX, mouseY);
        }

        drawBackButton(g, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphics g) {
        g.fill(16, 14, width - 16, 70, PANEL);
        outline(g, 16, 14, width - 16, 70, BORDER);

        g.drawString(font, "ДЕРЕВО ТЕХНОЛОГИЙ", 34, 24, TEXT, true);
        g.drawString(font, snapshot.countryName(), 34, 43, MUTED, false);
        g.drawString(font, snapshot.direction(), 210, 24, directionColor(), true);
        g.drawString(font, "Экономика " + snapshot.developmentLevel() + " • " + snapshot.developmentTitle(),
            210, 43, MUTED, false);

        g.drawString(font, "ОЧКИ", width - 148, 24, MUTED, true);
        g.drawString(font, Integer.toString(snapshot.researchPoints()), width - 148, 40, GOLD, true);
    }

    private void drawTree(GuiGraphics g, int mouseX, int mouseY) {
        int treeLeft = 24;
        int treeRight = width - 330;
        int treeTop = 82;
        int treeBottom = height - 18;

        g.fill(treeLeft, treeTop, treeRight, treeBottom, PANEL);
        outline(g, treeLeft, treeTop, treeRight, treeBottom, BORDER);

        g.enableScissor(treeLeft + 1, treeTop + 1, treeRight - 1, treeBottom - 1);

        List<CountryResearch> nodes = directionNodes();
        for (CountryResearch node : nodes) {
            for (String prereqId : node.prerequisites()) {
                CountryResearch prereq = technology(prereqId);
                if (prereq == null || prereq.direction() != node.direction()) continue;
                drawConnection(g, prereq, node);
            }
        }

        for (CountryResearch node : nodes) {
            drawNode(g, node, mouseX, mouseY);
        }

        g.disableScissor();

        double maxScroll = Math.max(0, 82 + 5 * ROW_GAP + 20 - (height - 18));
        if (maxScroll > 0) {
            double ratio = Math.min(1.0, Math.max(0.0, scroll / maxScroll));
            int barTop = treeTop + 8;
            int barBottom = treeBottom - 8;
            int barH = Math.max(24, (int)((barBottom - barTop) * ((double)(barBottom - barTop) / Math.max(1, (barBottom - barTop) + maxScroll))));
            int y = barTop + (int)((barBottom - barTop - barH) * ratio);
            g.fill(treeRight - 7, barTop, treeRight - 3, barBottom, 0xFF202A34);
            g.fill(treeRight - 7, y, treeRight - 3, y + barH, BLUE);
        }
    }

    private List<CountryResearch> directionNodes() {
        List<CountryResearch> result = new ArrayList<>();
        if (snapshot == null) return result;
        for (CountryResearch node : CountryResearch.values()) {
            if (snapshot.direction().equals(node.direction().displayName())) {
                result.add(node);
            }
        }
        return result;
    }

    private int centerX(CountryResearch node, int left, int right) {
        int available = right - left - 36;
        int columns = 5;
        int spacing = Math.max(108, Math.min(154, available / columns));
        return left + 18 + ((node.column() + 2) * spacing) + NODE_W / 2;
    }

    private int centerY(CountryResearch node) {
        return 122 + node.row() * ROW_GAP - (int) scroll;
    }

    private void drawConnection(GuiGraphics g, CountryResearch from, CountryResearch to) {
        int treeLeft = 24;
        int treeRight = width - 330;
        int x1 = centerX(from, treeLeft, treeRight);
        int y1 = centerY(from) + NODE_H / 2;
        int x2 = centerX(to, treeLeft, treeRight);
        int y2 = centerY(to) - NODE_H / 2;

        int midY = (y1 + y2) / 2;
        line(g, x1, y1, x1, midY, status(from.id()).equals("COMPLETED") ? POSITIVE : DISABLED);
        line(g, x1, midY, x2, midY, status(from.id()).equals("COMPLETED") ? POSITIVE : DISABLED);
        line(g, x2, midY, x2, y2, status(from.id()).equals("COMPLETED") ? POSITIVE : DISABLED);
    }

    private void drawNode(GuiGraphics g, CountryResearch node, int mouseX, int mouseY) {
        int treeLeft = 24;
        int treeRight = width - 330;
        int cx = centerX(node, treeLeft, treeRight);
        int cy = centerY(node);
        int left = cx - NODE_W / 2;
        int top = cy - NODE_H / 2;
        int right = left + NODE_W;
        int bottom = top + NODE_H;

        if (bottom < 80 || top > height - 18) return;

        String state = status(node.id());
        boolean selected = node.id().equals(selectedId);
        boolean hover = mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;

        int fill = switch (state) {
            case "COMPLETED" -> 0xFF173628;
            case "AVAILABLE" -> 0xFF17344D;
            case "MONEY", "POINTS", "PREREQUISITE", "LEVEL" -> 0xFF2D2B22;
            default -> 0xFF202831;
        };

        if (hover) fill = brighten(fill);
        g.fill(left, top, right, bottom, fill);
        outline(g, left, top, right, bottom,
            selected ? GOLD : state.equals("COMPLETED") ? POSITIVE : state.equals("AVAILABLE") ? BLUE : BORDER);

        ItemStack icon = itemStack(node.icon());
        if (!icon.isEmpty()) g.renderItem(icon, left + 8, top + 10);

        g.drawString(font, "T" + (node.row() + 1), left + 34, top + 8, MUTED, true);
        String title = clip(node.title(), 86);
        g.drawString(font, title, left + 34, top + 24, TEXT, true);

        String stateText = switch (state) {
            case "COMPLETED" -> "ОТКРЫТО";
            case "AVAILABLE" -> "ДОСТУПНО";
            case "LEVEL" -> "НУЖЕН УР. " + rowValue(node.id(), 6);
            case "PREREQUISITE" -> "НУЖНА СВЯЗЬ";
            case "POINTS" -> "НУЖНЫ ОЧКИ";
            case "MONEY" -> "НУЖНЫ ДЕНЬГИ";
            default -> "ЗАБЛОКИРОВАНО";
        };
        g.drawString(font, stateText, left + 8, bottom - 16,
            state.equals("COMPLETED") ? POSITIVE : state.equals("AVAILABLE") ? BLUE : MUTED, true);
    }

    private void drawDetails(GuiGraphics g, int mouseX, int mouseY) {
        int left = width - 314;
        int right = width - 16;
        int top = 82;
        int bottom = height - 18;

        g.fill(left, top, right, bottom, PANEL);
        outline(g, left, top, right, bottom, BORDER);

        CountryResearch node = technology(selectedId);
        if (node == null) return;

        ItemStack icon = itemStack(node.icon());
        if (!icon.isEmpty()) g.renderItem(icon, left + 16, top + 16);

        g.drawString(font, node.title(), left + 54, top + 18, TEXT, true);
        g.drawString(font, "Уровень " + node.minLevel(), left + 54, top + 36, MUTED, false);

        int y = top + 70;
        for (String line : wrap(node.description(), right - left - 28, 24)) {
            g.drawString(font, line, left + 14, y, MUTED, false);
            y += 14;
        }

        y += 8;
        g.drawString(font, "ТРЕБОВАНИЯ", left + 14, y, TEXT, true);
        y += 18;
        g.drawString(font, node.researchCost() + " очк. исследований", left + 14, y, GOLD, false);
        y += 16;
        g.drawString(font, "$" + node.moneyCost(), left + 14, y, GOLD, false);
        y += 24;

        g.drawString(font, "ОТКРЫВАЕТ", left + 14, y, TEXT, true);
        y += 18;
        for (String line : wrap(node.contentSummary(), right - left - 28, 24)) {
            g.drawString(font, line, left + 14, y, BLUE, false);
            y += 14;
        }

        y += 10;
        g.drawString(font, "КОНТЕНТ", left + 14, y, TEXT, true);
        y += 18;
        for (String rule : node.contentRules()) {
            g.drawString(font, "• " + clip(rule, 30), left + 14, y, MUTED, false);
            y += 14;
            if (y > bottom - 90) break;
        }

        String state = status(node.id());
        int buttonTop = bottom - 54;
        boolean available = "AVAILABLE".equals(state);
        int color = available ? BLUE_DARK : 0xFF252C34;
        g.fill(left + 14, buttonTop, right - 14, buttonTop + 36, color);
        outline(g, left + 14, buttonTop, right - 14, buttonTop + 36, available ? BLUE : BORDER);

        String buttonText = switch (state) {
            case "COMPLETED" -> "ИССЛЕДОВАНО";
            case "AVAILABLE" -> "ИССЛЕДОВАТЬ";
            case "LEVEL" -> "НУЖЕН УРОВЕНЬ";
            case "PREREQUISITE" -> "НУЖНО ПРЕДЫДУЩЕЕ";
            case "POINTS" -> "НЕДОСТАТОЧНО ОЧКОВ";
            case "MONEY" -> "НЕДОСТАТОЧНО ДЕНЕГ";
            default -> "ЗАБЛОКИРОВАНО";
        };
        g.drawCenteredString(font, buttonText, (left + right) / 2, buttonTop + 12,
            available ? TEXT : MUTED);
        if (available && mouseX >= left + 14 && mouseX <= right - 14 && mouseY >= buttonTop && mouseY <= buttonTop + 36) {
            g.fill(left + 15, buttonTop + 1, right - 15, buttonTop + 35, 0x223FFFFFF);
        }
    }

    private void drawBackButton(GuiGraphics g, int mouseX, int mouseY) {
        int left = 16, top = 14, right = 30, bottom = 28;
        boolean hover = mouseX >= left && mouseX <= 92 && mouseY >= top && mouseY <= 44;
        g.fill(left, top, 104, 44, hover ? PANEL_2 : PANEL);
        outline(g, left, top, 104, 44, hover ? BLUE : BORDER);
        g.drawString(font, "← Государство", 28, 28, hover ? TEXT : MUTED, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (mouseY >= 14 && mouseY <= 44 && mouseX >= 16 && mouseX <= 104) {
            Minecraft.getInstance().setScreen(new EconomyScreen(snapshot));
            return true;
        }

        if (mouseX >= width - 314 && mouseX <= width - 16 && mouseY >= height - 72 && mouseY <= height - 18) {
            if ("AVAILABLE".equals(status(selectedId))) {
                EconomyNetwork.sendAction("research", selectedId);
                return true;
            }
        }

        int treeLeft = 24;
        int treeRight = width - 330;
        for (CountryResearch node : directionNodes()) {
            int cx = centerX(node, treeLeft, treeRight);
            int cy = centerY(node);
            int left = cx - NODE_W / 2;
            int top = cy - NODE_H / 2;
            if (mouseX >= left && mouseX <= left + NODE_W && mouseY >= top && mouseY <= top + NODE_H) {
                selectedId = node.id();
                return true;
            }
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int treeLeft = 24;
        int treeRight = width - 330;
        if (mouseX < treeRight) {
            double max = Math.max(0, 82 + 5 * ROW_GAP + 20 - (height - 18));
            scroll = Math.max(0, Math.min(max, scroll - scrollY * 28));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new EconomyScreen(snapshot));
    }

    private int directionColor() {
        String value = snapshot.direction().toLowerCase(Locale.ROOT);
        if (value.contains("торгов")) return 0xFFFFA94D;
        if (value.contains("ресурс")) return 0xFF69C27D;
        return BLUE;
    }

    private ItemStack itemStack(String id) {
        try {
            ResourceLocation location = ResourceLocation.parse(id);
            if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(location)) return ItemStack.EMPTY;
            return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(location));
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private void line(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        if (x1 == x2) {
            g.fill(x1, Math.min(y1, y2), x1 + 2, Math.max(y1, y2) + 1, color);
        } else {
            g.fill(Math.min(x1, x2), y1, Math.max(x1, x2) + 2, y1 + 2, color);
        }
    }

    private void outline(GuiGraphics g, int left, int top, int right, int bottom, int color) {
        g.fill(left, top, right, top + 1, color);
        g.fill(left, bottom - 1, right, bottom, color);
        g.fill(left, top, left + 1, bottom, color);
        g.fill(right - 1, top, right, bottom, color);
    }

    private int brighten(int color) {
        int r = Math.min(255, ((color >> 16) & 255) + 10);
        int gg = Math.min(255, ((color >> 8) & 255) + 10);
        int b = Math.min(255, (color & 255) + 10);
        return (color & 0xFF000000) | (r << 16) | (gg << 8) | b;
    }

    private String clip(String value, int maxPixels) {
        if (font.width(value) <= maxPixels) return value;
        String current = value;
        while (!current.isEmpty() && font.width(current + "…") > maxPixels) {
            current = current.substring(0, current.length() - 1);
        }
        return current + "…";
    }

    private List<String> wrap(String value, int widthPixels, int maxLines) {
        List<String> result = new ArrayList<>();
        String[] words = value.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String next = current.length() == 0 ? word : current + " " + word;
            if (font.width(next) > widthPixels && current.length() > 0) {
                result.add(current.toString());
                if (result.size() >= maxLines) return result;
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(next);
            }
        }
        if (current.length() > 0 && result.size() < maxLines) result.add(current.toString());
        return result;
    }
}
