package ru.zela.politicseconomy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.zela.politicseconomy.network.EconomyNetwork;
import ru.zela.politicseconomy.network.EconomySnapshotPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Standalone full-screen war declaration UI. */
public final class WarDeclarationScreen extends Screen {
    private static final int BG = 0xFF090C11;
    private static final int PANEL = 0xFF151B23;
    private static final int PANEL_2 = 0xFF1B222C;
    private static final int PANEL_3 = 0xFF232D38;
    private static final int BORDER = 0xFF303B49;
    private static final int TEXT = 0xFFE7EDF5;
    private static final int MUTED = 0xFF929EAD;
    private static final int ACCENT = 0xFF4BA3FF;
    private static final int ACCENT_DARK = 0xFF173A5B;
    private static final int NEGATIVE = 0xFFFF6B6B;
    private static final int NEGATIVE_DARK = 0xFF4A2124;
    private static final int GOLD = 0xFFFFC857;

    private EconomySnapshotPayload snapshot;
    private final Screen parent;
    private EditBox search;

    private String selectedName;
    private String selectedType;
    private boolean dropdownOpen;
    private int dropdownScroll;

    private final List<RowHit> rows = new ArrayList<>();

    public WarDeclarationScreen(EconomySnapshotPayload snapshot, Screen parent) {
        super(Component.literal("Объявление войны"));
        this.snapshot = snapshot;
        this.parent = parent;
    }

    public void applySnapshot(EconomySnapshotPayload payload) {
        this.snapshot = payload;
    }

    @Override
    protected void init() {
        super.init();

        search = new EditBox(
            font,
            0,
            0,
            300,
            20,
            Component.literal("Поиск страны")
        );
        search.setMaxLength(128);
        search.setTextColor(TEXT);
        search.setTextColorUneditable(MUTED);
        search.setBordered(true);
        addRenderableWidget(search);

        layout();
    }

    private void layout() {
        int panelWidth = Math.min(760, width - 40);
        int panelLeft = (width - panelWidth) / 2;

        search.setX(panelLeft + 28);
        search.setY(104);
        search.setWidth(panelWidth - 56);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String query = search == null ? "" : search.getValue();
        super.resize(minecraft, width, height);
        if (search != null) {
            search.setValue(query);
            layout();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, BG);

        int panelWidth = Math.min(760, width - 40);
        int panelLeft = (width - panelWidth) / 2;
        int panelTop = 42;
        int panelRight = panelLeft + panelWidth;

        int panelBottom = dropdownOpen
            ? Math.min(height - 28, 540)
            : Math.min(height - 28, 390);

        g.fill(panelLeft, panelTop, panelRight, panelBottom, PANEL);
        outline(g, panelLeft, panelTop, panelRight, panelBottom, BORDER);

        g.fill(panelLeft, panelTop, panelRight, panelTop + 4, NEGATIVE);

        g.drawString(font, "ОБЪЯВЛЕНИЕ ВОЙНЫ", panelLeft + 28, panelTop + 24, TEXT, true);
        g.drawString(
            font,
            "Выберите государство, против которого будет объявлена война.",
            panelLeft + 28,
            panelTop + 43,
            MUTED,
            false
        );

        g.drawString(font, "ПОИСК", panelLeft + 28, 92, MUTED, true);
        search.render(g, mouseX, mouseY, partialTick);

        drawSelector(g, mouseX, mouseY, panelLeft, panelRight);

        if (dropdownOpen) {
            drawDropdown(g, mouseX, mouseY, panelLeft, panelRight);
        }

        int infoTop = dropdownOpen ? 400 : 235;
        drawInfo(g, panelLeft + 28, infoTop, panelRight - 28);

        int buttonsY = panelBottom - 48;
        drawButton(
            g,
            panelLeft + panelWidth - 270,
            buttonsY,
            panelLeft + panelWidth - 150,
            buttonsY + 28,
            "ОТМЕНА",
            PANEL_3,
            TEXT,
            mouseX,
            mouseY
        );

        boolean ready = selectedName != null && selectedType != null;
        drawButton(
            g,
            panelLeft + panelWidth - 140,
            buttonsY,
            panelRight - 28,
            buttonsY + 28,
            "ОБЪЯВИТЬ",
            ready ? NEGATIVE_DARK : PANEL_3,
            ready ? NEGATIVE : MUTED,
            mouseX,
            mouseY
        );

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawSelector(
        GuiGraphics g,
        int mouseX,
        int mouseY,
        int left,
        int right
    ) {
        int top = 145;
        int bottom = 184;
        boolean hover = inside(mouseX, mouseY, left + 28, top, right - 28, bottom);

        g.fill(
            left + 28,
            top,
            right - 28,
            bottom,
            hover ? PANEL_3 : PANEL_2
        );
        outline(
            g,
            left + 28,
            top,
            right - 28,
            bottom,
            selectedName == null ? BORDER : ACCENT
        );

        String label = selectedName == null
            ? "Выберите государство"
            : selectedName + "  •  " + selectedType;

        g.drawString(
            font,
            clip(label, right - left - 118),
            left + 43,
            top + 12,
            selectedName == null ? MUTED : TEXT,
            selectedName != null
        );

        g.drawString(
            font,
            dropdownOpen ? "▲" : "▼",
            right - 58,
            top + 12,
            ACCENT,
            true
        );
    }

    private void drawDropdown(
        GuiGraphics g,
        int mouseX,
        int mouseY,
        int left,
        int right
    ) {
        int top = 190;
        int rowHeight = 34;
        int maxRows = Math.max(1, Math.min(8, (height - top - 120) / rowHeight));

        rows.clear();
        List<CountryOption> options = filteredOptions();
        if (options.isEmpty()) {
            panel(g, left + 28, top, right - 28, top + rowHeight);
            g.drawString(
                font,
                "Подходящих государств не найдено.",
                left + 42,
                top + 11,
                MUTED,
                false
            );
            return;
        }

        int maxScroll = Math.max(0, options.size() - maxRows);
        dropdownScroll = Math.max(0, Math.min(dropdownScroll, maxScroll));

        for (int visible = 0; visible < maxRows; visible++) {
            int index = dropdownScroll + visible;
            if (index >= options.size()) break;

            CountryOption option = options.get(index);
            int rowTop = top + visible * rowHeight;
            int rowBottom = rowTop + rowHeight - 2;
            boolean selected = option.name.equals(selectedName)
                && option.type.equals(selectedType);
            boolean hover = inside(mouseX, mouseY, left + 28, rowTop, right - 28, rowBottom);

            g.fill(
                left + 28,
                rowTop,
                right - 28,
                rowBottom,
                selected ? ACCENT_DARK : hover ? PANEL_3 : PANEL_2
            );
            outline(
                g,
                left + 28,
                rowTop,
                right - 28,
                rowBottom,
                selected ? ACCENT : BORDER
            );

            g.drawString(
                font,
                clip(option.name, right - left - 190),
                left + 42,
                rowTop + 9,
                TEXT,
                selected
            );

            String type = option.type;
            int typeColor = "Millénaire".equals(type) ? 0xFFC77DFF : ACCENT;
            drawPill(
                g,
                type,
                right - 175,
                rowTop + 8,
                type.equals("Millénaire") ? 0xFF433056 : ACCENT_DARK,
                typeColor,
                130
            );

            rows.add(new RowHit(
                left + 28,
                rowTop,
                right - 28,
                rowBottom,
                option
            ));
        }

        if (options.size() > maxRows) {
            int trackTop = top;
            int trackBottom = top + maxRows * rowHeight - 2;
            int thumbHeight = Math.max(22, (trackBottom - trackTop) * maxRows / options.size());
            int thumbY = trackTop + (trackBottom - trackTop - thumbHeight)
                * dropdownScroll / Math.max(1, maxScroll);

            g.fill(right - 39, trackTop, right - 33, trackBottom, PANEL_3);
            g.fill(right - 39, thumbY, right - 33, thumbY + thumbHeight, ACCENT);
        }
    }

    private void drawInfo(GuiGraphics g, int left, int top, int right) {
        panel(g, left, top, right, top + 96);

        g.drawString(font, "ПЕРЕД ОБЪЯВЛЕНИЕМ", left + 14, top + 12, MUTED, true);

        g.drawString(
            font,
            "• Вы должны состоять в государстве.",
            left + 14,
            top + 33,
            TEXT,
            false
        );
        g.drawString(
            font,
            "• Для начала войны требуется военная готовность не менее 35%.",
            left + 14,
            top + 50,
            TEXT,
            false
        );
        g.drawString(
            font,
            "• Непогашенный материальный долг блокирует объявление войны.",
            left + 14,
            top + 67,
            TEXT,
            false
        );
        g.drawString(
            font,
            selectedName == null
                ? "Цель пока не выбрана."
                : "Цель: " + selectedName + " • " + selectedType,
            left + 14,
            top + 84,
            selectedName == null ? MUTED : GOLD,
            selectedName != null
        );
    }

    private List<CountryOption> filteredOptions() {
        String query = search == null
            ? ""
            : search.getValue().trim().toLowerCase(Locale.ROOT);

        List<CountryOption> result = new ArrayList<>();
        for (int i = 0; i < snapshot.cityNames().length; i++) {
            String name = valueAt(snapshot.cityNames(), i);
            String type = valueAt(snapshot.cityCountries(), i);

            if (name.isBlank() || name.equals(snapshot.countryName())) continue;
            if (!query.isBlank()
                && !name.toLowerCase(Locale.ROOT).contains(query)
                && !type.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }

            result.add(new CountryOption(name, type));
        }
        return result;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int panelWidth = Math.min(760, width - 40);
        int left = (width - panelWidth) / 2;
        int right = left + panelWidth;

        int panelBottom = dropdownOpen
            ? Math.min(height - 28, 540)
            : Math.min(height - 28, 390);

        int buttonsY = panelBottom - 48;

        if (inside(mouseX, mouseY, left + 28, 145, right - 28, 184)) {
            dropdownOpen = !dropdownOpen;
            dropdownScroll = 0;
            return true;
        }

        if (dropdownOpen) {
            for (int i = rows.size() - 1; i >= 0; i--) {
                RowHit row = rows.get(i);
                if (!row.contains(mouseX, mouseY)) continue;

                selectedName = row.option.name;
                selectedType = row.option.type;
                dropdownOpen = false;
                return true;
            }
        }

        if (inside(mouseX, mouseY, left + panelWidth - 270, buttonsY,
            left + panelWidth - 150, buttonsY + 28)) {
            closeScreen();
            return true;
        }

        if (inside(mouseX, mouseY, left + panelWidth - 140, buttonsY,
            right - 28, buttonsY + 28)) {
            if (selectedName != null && selectedType != null) {
                EconomyNetwork.sendAction(
                    "war_declare",
                    selectedType + "|" + selectedName
                );
                closeScreen();
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(
        double mouseX,
        double mouseY,
        double scrollX,
        double scrollY
    ) {
        if (!dropdownOpen) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

        int panelWidth = Math.min(760, width - 40);
        int left = (width - panelWidth) / 2;
        int right = left + panelWidth;
        int top = 190;
        int bottom = Math.min(height - 120, top + 8 * 34);

        if (!inside(mouseX, mouseY, left + 28, top, right - 28, bottom)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        int maxScroll = Math.max(0, filteredOptions().size() - 8);
        dropdownScroll = Math.max(
            0,
            Math.min(maxScroll, dropdownScroll - (int) Math.signum(scrollY))
        );
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            closeScreen();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void closeScreen() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private void drawButton(
        GuiGraphics g,
        int left,
        int top,
        int right,
        int bottom,
        String text,
        int fill,
        int accent,
        int mouseX,
        int mouseY
    ) {
        boolean hover = inside(mouseX, mouseY, left, top, right, bottom);
        g.fill(left, top, right, bottom, hover ? brighten(fill) : fill);
        outline(g, left, top, right, bottom, hover ? accent : BORDER);
        int tx = left + Math.max(5, (right - left - font.width(text)) / 2);
        g.drawString(font, text, tx, top + 7, hover ? TEXT : accent, true);
    }

    private void drawPill(
        GuiGraphics g,
        String text,
        int x,
        int y,
        int fill,
        int accent,
        int maxWidth
    ) {
        String value = clip(text, maxWidth - 14);
        int w = Math.min(maxWidth, font.width(value) + 14);
        g.fill(x, y, x + w, y + 17, fill);
        outline(g, x, y, x + w, y + 17, accent);
        g.drawString(font, value, x + 7, y + 4, accent, true);
    }

    private void panel(GuiGraphics g, int left, int top, int right, int bottom) {
        g.fill(left, top, right, bottom, PANEL);
        outline(g, left, top, right, bottom, BORDER);
    }

    private void outline(GuiGraphics g, int left, int top, int right, int bottom, int color) {
        g.fill(left, top, right, top + 1, color);
        g.fill(left, bottom - 1, right, bottom, color);
        g.fill(left, top, left + 1, bottom, color);
        g.fill(right - 1, top, right, bottom, color);
    }

    private static int brighten(int color) {
        int a = color >>> 24;
        int r = Math.min(255, ((color >>> 16) & 255) + 12);
        int green = Math.min(255, ((color >>> 8) & 255) + 12);
        int b = Math.min(255, (color & 255) + 12);
        return (a << 24) | (r << 16) | (green << 8) | b;
    }

    private String clip(String value, int maxPixels) {
        if (value == null) return "";
        if (font.width(value) <= maxPixels) return value;

        String ellipsis = "…";
        int limit = maxPixels - font.width(ellipsis);
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end)) > limit) end--;
        return end <= 0 ? ellipsis : value.substring(0, end) + ellipsis;
    }

    private static boolean inside(double x, double y, int left, int top, int right, int bottom) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    private static String valueAt(String[] values, int index) {
        return values != null && index >= 0 && index < values.length && values[index] != null
            ? values[index]
            : "";
    }

    private record CountryOption(String name, String type) {}
    private record RowHit(int left, int top, int right, int bottom, CountryOption option) {
        boolean contains(double x, double y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }
    }
}
