package ru.zela.politicseconomy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import ru.zela.politicseconomy.country.CountryDirection;
import ru.zela.politicseconomy.country.CountryReformCostTable;
import ru.zela.politicseconomy.country.GovernmentType;
import ru.zela.politicseconomy.country.ReligionType;
import ru.zela.politicseconomy.country.WorkforceSector;
import ru.zela.politicseconomy.network.EconomyNetwork;
import ru.zela.politicseconomy.network.EconomySnapshotPayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class EconomyScreen extends Screen {
    private enum Page {
        OVERVIEW("Обзор", "minecraft:emerald"),
        COUNTRY("Государство", "minecraft:compass"),
        EFFECTS("Эффекты", "minecraft:redstone"),
        CITIES("Города", "minecraft:bricks"),
        MARKET("Рынок", "minecraft:emerald"),
        TRADE("Торговля", "minecraft:chest");

        final String title;
        final String iconId;

        Page(String title, String iconId) {
            this.title = title;
            this.iconId = iconId;
        }
    }

    private EconomySnapshotPayload snapshot;
    private Page page = Page.OVERVIEW;
    private double scroll;

    private final List<ClickTarget> targets = new ArrayList<>();
    private String modalAction;
    private String modalCommand;
    private String modalTitle;

    private EditBox tradeItem;
    private EditBox tradeAmount;
    private EditBox tradeMaxPrice;
    private EditBox tradeAcceptPrice;
    private EditBox tradeDispatchAmount;
    private String selectedTradeItemId = "minecraft:iron_ingot";
    private List<TradeItemOption> tradeItemOptions = List.of();

    // Coordinates are calculated from the actual trade cards every frame.
    private int tradeOrderTop;
    private int tradeOwnOrdersTop;
    private int tradeOwnOrdersLeft;
    private int tradeOwnOrdersRight;

    private static final int BG = 0xFF0D1117;
    private static final int PANEL = 0xFF151B23;
    private static final int PANEL_2 = 0xFF1B222C;
    private static final int PANEL_3 = 0xFF232D38;
    private static final int BORDER = 0xFF303B49;
    private static final int TEXT = 0xFFE7EDF5;
    private static final int MUTED = 0xFF929EAD;
    private static final int ACCENT = 0xFF4BA3FF;
    private static final int ACCENT_DARK = 0xFF173A5B;
    private static final int POSITIVE = 0xFF5FCB84;
    private static final int POSITIVE_DARK = 0xFF173D2A;
    private static final int NEGATIVE = 0xFFFF6B6B;
    private static final int NEGATIVE_DARK = 0xFF4A2124;
    private static final int GOLD = 0xFFFFC857;

    public EconomyScreen(EconomySnapshotPayload snapshot) {
        super(Component.literal("Politics Economy"));
        this.snapshot = snapshot;
    }

    public void applySnapshot(EconomySnapshotPayload payload) {
        this.snapshot = payload;
    }

    @Override
    protected void init() {
        super.init();

        tradeItem = new EditBox(font, 0, 0, 182, 20, Component.literal("Предмет"));
        tradeAmount = new EditBox(font, 0, 0, 72, 20, Component.literal("Количество"));
        tradeMaxPrice = new EditBox(font, 0, 0, 88, 20, Component.literal("Цена"));
        tradeAcceptPrice = new EditBox(font, 0, 0, 72, 20, Component.literal("Цена"));
        tradeDispatchAmount = new EditBox(font, 0, 0, 72, 20, Component.literal("Партия"));

        buildTradeItemOptions();
        tradeItem.setValue(tradeItemDisplayName(selectedTradeItemId));
        tradeAmount.setValue("64");
        tradeMaxPrice.setValue("20");
        tradeAcceptPrice.setValue("15");
        tradeDispatchAmount.setValue("64");

        for (EditBox box : List.of(
            tradeItem, tradeAmount, tradeMaxPrice, tradeAcceptPrice, tradeDispatchAmount
        )) {
            box.setTextColor(TEXT);
            box.setTextColorUneditable(MUTED);
            box.setBordered(true);
            box.setMaxLength(120);
            addRenderableWidget(box);
        }

        layoutTradeInputs();
        updateTradeInputVisibility();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String item = tradeItem == null ? tradeItemDisplayName(selectedTradeItemId) : tradeItem.getValue();
        String amount = tradeAmount == null ? "64" : tradeAmount.getValue();
        String max = tradeMaxPrice == null ? "20" : tradeMaxPrice.getValue();
        String accept = tradeAcceptPrice == null ? "15" : tradeAcceptPrice.getValue();
        String dispatch = tradeDispatchAmount == null ? "64" : tradeDispatchAmount.getValue();

        super.resize(minecraft, width, height);

        if (tradeItem != null) {
            tradeItem.setValue(item);
            tradeAmount.setValue(amount);
            tradeMaxPrice.setValue(max);
            tradeAcceptPrice.setValue(accept);
            tradeDispatchAmount.setValue(dispatch);
            layoutTradeInputs();
            updateTradeInputVisibility();
        }
    }

    private void layoutTradeInputs() {
        // drawTrade() receives an already scroll-adjusted Y coordinate.
        // Subtracting scroll here again caused the text fields to "fly" while scrolling.
        int orderTop = tradeOrderTop;
        tradeItem.setX(contentLeft() + 14);
        tradeItem.setY(orderTop + 39);
        tradeItem.setWidth(260);

        tradeAmount.setX(contentLeft() + 284);
        tradeAmount.setY(orderTop + 39);
        tradeAmount.setWidth(88);

        tradeMaxPrice.setX(contentLeft() + 382);
        tradeMaxPrice.setY(orderTop + 39);
        tradeMaxPrice.setWidth(98);

        int ownTop = tradeOwnOrdersTop;
        tradeAcceptPrice.setX(tradeOwnOrdersLeft + 108);
        tradeAcceptPrice.setY(ownTop + 14);
        tradeAcceptPrice.setWidth(70);

        tradeDispatchAmount.setX(tradeOwnOrdersLeft + 265);
        tradeDispatchAmount.setY(ownTop + 14);
        tradeDispatchAmount.setWidth(70);
    }

    private void updateTradeInputVisibility() {
        boolean visible = page == Page.TRADE && modalAction == null;
        if (tradeItem == null) return;

        // Keep widgets disabled outside the content viewport so their vanilla
        // hitboxes cannot remain active when their visual rows are scrolled away.
        int top = contentTop();
        int bottom = height - 12;

        tradeItem.visible = visible && inViewport(tradeItem.getY(), tradeItem.getHeight(), top, bottom);
        tradeAmount.visible = visible && inViewport(tradeAmount.getY(), tradeAmount.getHeight(), top, bottom);
        tradeMaxPrice.visible = visible && inViewport(tradeMaxPrice.getY(), tradeMaxPrice.getHeight(), top, bottom);
        tradeAcceptPrice.visible = visible && inViewport(tradeAcceptPrice.getY(), tradeAcceptPrice.getHeight(), top, bottom);
        tradeDispatchAmount.visible = visible && inViewport(tradeDispatchAmount.getY(), tradeDispatchAmount.getHeight(), top, bottom);
    }

    private static boolean inViewport(int y, int h, int top, int bottom) {
        return y + h > top && y < bottom;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        targets.clear();

        graphics.fill(0, 0, width, height, BG);
        graphics.fill(0, 0, 5, height, ACCENT);

        drawHeader(graphics, mouseX, mouseY);
        drawSidebar(graphics, mouseX, mouseY);

        int left = contentLeft();
        int right = width - 16;
        int top = contentTop();
        int bottom = height - 12;

        graphics.enableScissor(left, top, right, bottom);
        int end = top - (int) scroll;

        switch (page) {
            case OVERVIEW -> end = drawOverview(graphics, end, left, right, mouseX, mouseY);
            case COUNTRY -> end = drawCountry(graphics, end, left, right, mouseX, mouseY);
            case EFFECTS -> end = drawEffects(graphics, end, left, right);
            case CITIES -> end = drawCities(graphics, end, left, right);
            case MARKET -> end = drawMarket(graphics, end, left, right, mouseX, mouseY);
            case TRADE -> end = drawTrade(graphics, end, left, right, mouseX, mouseY);
        }
        if (page == Page.TRADE && modalAction == null) {
            layoutTradeInputs();
            updateTradeInputVisibility();
            tradeItem.render(graphics, mouseX, mouseY, partialTick);
            tradeAmount.render(graphics, mouseX, mouseY, partialTick);
            tradeMaxPrice.render(graphics, mouseX, mouseY, partialTick);
            tradeAcceptPrice.render(graphics, mouseX, mouseY, partialTick);
            tradeDispatchAmount.render(graphics, mouseX, mouseY, partialTick);
            drawTradeItemDropdown(graphics, mouseX, mouseY);
        }

        graphics.disableScissor();

        drawCloseButton(graphics, mouseX, mouseY);

        double maxScroll = Math.max(0, end - bottom);
        if (maxScroll > 0) {
            drawScrollBar(graphics, right + 5, top, bottom, maxScroll);
        }

        if (modalAction != null) {
            drawModal(graphics, mouseX, mouseY);
        }
    }

    private void drawHeader(GuiGraphics g, int mouseX, int mouseY) {
        int left = 12;
        int right = width - 16;

        panel(g, left, 12, right, 64);
        g.drawString(font, "POLITICS ECONOMY", 28, 22, TEXT, true);
        g.drawString(font, snapshot.countryName().toUpperCase(Locale.ROOT), 28, 39, MUTED, false);

        g.drawString(font, "КАЗНА", right - 183, 19, MUTED, true);
        g.drawString(font, "$" + formatDouble(snapshot.treasury()), right - 183, 34, GOLD, true);
        g.drawString(font, format(snapshot.population()) + " населения", right - 183, 49, TEXT, false);

        drawPill(g, snapshot.direction(), right - 360, 27, ACCENT_DARK, ACCENT);

        boolean hover = inside(mouseX, mouseY, right - 31, 20, right - 10, 41);
        g.fill(right - 31, 20, right - 10, 41, hover ? NEGATIVE_DARK : PANEL_2);
        outline(g, right - 31, 20, right - 10, 41, hover ? NEGATIVE : BORDER);
        g.drawCenteredString(font, "×", right - 20, 25, hover ? NEGATIVE : TEXT);
        target(right - 31, 20, right - 10, 41, this::onClose);
    }

    private void drawSidebar(GuiGraphics g, int mouseX, int mouseY) {
        int left = 12;
        int right = 164;
        int top = 76;
        int bottom = height - 12;

        panel(g, left, top, right, bottom);
        g.drawString(font, "УПРАВЛЕНИЕ", left + 13, top + 12, MUTED, true);

        int y = top + 31;
        for (Page item : Page.values()) {
            boolean selected = item == page;
            boolean hover = inside(mouseX, mouseY, left + 7, y - 3, right - 7, y + 22);

            if (selected) {
                g.fill(left + 7, y - 3, right - 7, y + 22, ACCENT_DARK);
                g.fill(left + 7, y - 3, left + 10, y + 22, ACCENT);
            } else if (hover) {
                g.fill(left + 7, y - 3, right - 7, y + 22, PANEL_3);
            }

            ItemStack navIcon = itemStack(item.iconId);
            if (!navIcon.isEmpty()) g.renderItem(navIcon, left + 12, y + 1);
            g.drawString(font, item.title, left + 36, y + 4, selected ? TEXT : MUTED, selected);
            Page next = item;
            target(left + 7, y - 3, right - 7, y + 22, () -> {
                page = next;
                scroll = 0;
                updateTradeInputVisibility();
            });
            y += 29;
        }

        int infoY = bottom - 62;
        g.drawString(font, "РАЗВИТИЕ", left + 13, infoY, MUTED, true);
        g.drawString(font, "Уровень " + snapshot.developmentLevel(), left + 13, infoY + 14, TEXT, false);
        double progress = snapshot.developmentNextThreshold() <= 0 ? 1 :
            snapshot.developmentPoints() / (double) snapshot.developmentNextThreshold();
        progress(g, left + 13, infoY + 31, right - 13, infoY + 37, progress, ACCENT);
        g.drawString(font,
            format(snapshot.developmentPoints()) + " / " + format(snapshot.developmentNextThreshold()),
            left + 13, infoY + 44, MUTED, false);
    }

    private int drawOverview(GuiGraphics g, int y, int left, int right, int mouseX, int mouseY) {
        y = title(g, left, y, "ОБЩАЯ КАРТИНА", "Ключевые показатели государства");

        int gap = 8;
        int w = (right - left - gap * 3) / 4;
        metric(g, left, y, w, "КАЗНА", "$" + formatDouble(snapshot.treasury()), "Государственные деньги", "minecraft:emerald", GOLD);
        metric(g, left + w + gap, y, w, "СОДЕРЖАНИЕ",
            "-$" + formatDouble(snapshot.infrastructureCost()), "Инфраструктура / цикл", "minecraft:anvil", NEGATIVE);
        metric(g, left + 2 * (w + gap), y, w, "НАСЕЛЕНИЕ",
            format(snapshot.population()), "Жители страны", "minecraft:player_head", ACCENT);
        metric(g, left + 3 * (w + gap), y, w, "ДОЛГ",
            "$" + formatDouble(snapshot.moneyDebt()), "Денежная задолженность", "minecraft:redstone",
            snapshot.moneyDebt() > 0 ? NEGATIVE : POSITIVE);
        y += 86;

        panel(g, left, y, right, y + 104);
        g.drawString(font, "ПОЛИТИЧЕСКИЙ ПРОФИЛЬ", left + 14, y + 12, TEXT, true);
        info(g, left + 14, y + 33, right - 14, "Направление", snapshot.direction(), "minecraft:compass");
        info(g, left + 14, y + 52, right - 14, "Правление", snapshot.government(), "minecraft:iron_sword");
        info(g, left + 14, y + 71, right - 14, "Религия", snapshot.religion(), "minecraft:book");
        y += 114;

        panel(g, left, y, right, y + 88);
        ItemStack developmentIcon = itemStack("minecraft:diamond");
        if (!developmentIcon.isEmpty()) g.renderItem(developmentIcon, left + 10, y + 10);
        g.drawString(font, "РАЗВИТИЕ", left + 38, y + 12, TEXT, true);
        g.drawString(font,
            snapshot.developmentLevel() >= 5
                ? "Максимальный уровень"
                : format(snapshot.developmentPoints()) + " / " + format(snapshot.developmentNextThreshold()) + " очков",
            left + 14, y + 31, MUTED, false);
        g.drawString(font, clip(snapshot.developmentPerk(), 60), right - 260, y + 12, GOLD, false);
        double dev = snapshot.developmentNextThreshold() <= 0 ? 1 :
            snapshot.developmentPoints() / (double) snapshot.developmentNextThreshold();
        progress(g, left + 14, y + 54, right - 14, y + 62, dev, ACCENT);
        y += 98;

        int half = (right - left - gap) / 2;
        int workBottom = y + 52 + WorkforceSector.values().length * 38 + 12;
        int matBottom = y + 52 + materialCount() * 38 + 12;

        panel(g, left, y, left + half, workBottom);
        panel(g, left + half + gap, y, right, matBottom);

        g.drawString(font, "РАБОЧАЯ СИЛА", left + 14, y + 12, TEXT, true);
        g.drawString(font,
            format(snapshot.workingPopulation()) + " доступно • " +
                format(snapshot.employedPopulation()) + " занято • " +
                format(snapshot.unemployedPopulation()) + " без места",
            left + 14, y + 31, MUTED, false);

        int wy = y + 52;
        WorkforceSector[] sectors = WorkforceSector.values();
        for (int i = 0; i < sectors.length; i++) {
            String line = clip(sectors[i].displayName(), 15) + "  " +
                valueAt(snapshot.sectorAllocation(), i) + "%  " +
                format(valueAt(snapshot.sectorWorkers(), i)) + "/" +
                format(valueAt(snapshot.workplaceSlots(), i)) + "  " +
                signed(valueAt(snapshot.sectorBonuses(), i));

            ItemStack sectorIcon = itemStack(sectors[i].iconItemId());
            if (!sectorIcon.isEmpty()) g.renderItem(sectorIcon, left + 12, wy - 7);
            g.drawString(font, line, left + 34, wy, TEXT, false);
            final WorkforceSector sector = sectors[i];
            miniButton(g, left + half - 61, wy - 5, "-", mouseX, mouseY, () -> changeWorkforce(sector, -5));
            miniButton(g, left + half - 33, wy - 5, "+", mouseX, mouseY, () -> changeWorkforce(sector, 5));
            wy += 38;
        }

        g.drawString(font, "ГОСУДАРСТВЕННЫЙ СКЛАД", left + half + gap + 14, y + 12, TEXT, true);
        g.drawString(font, "Запасы и ресурсные обязательства",
            left + half + gap + 14, y + 31, MUTED, false);

        int sy = y + 52;
        for (int i = 0; i < materialCount(); i++) {
            int baseX = left + half + gap + 14;
            String name = clip(valueAt(snapshot.materialNames(), i), 18);
            int debt = valueAt(snapshot.materialDebt(), i);
            ItemStack materialIcon = materialIconStack(valueAt(snapshot.materialIds(), i));
            if (!materialIcon.isEmpty()) g.renderItem(materialIcon, baseX, sy - 8);

            g.drawString(font, name, baseX + 24, sy, TEXT, false);
            g.drawString(font,
                valueAt(snapshot.materialStockpile(), i) + "  •  " +
                    String.format(Locale.ROOT, "%.2f/c", valueAt(snapshot.materialPerCycle(), i)) +
                    (debt > 0 ? "  • долг " + debt : ""),
                baseX + 24, sy + 14, debt > 0 ? NEGATIVE : MUTED, false);
            sy += 38;
        }

        if (materialCount() == 0) {
            g.drawString(font, "Склад пока пуст.", left + half + gap + 14, sy, MUTED, false);
        }

        return Math.max(workBottom, matBottom) + 10;
    }

    private int drawCountry(GuiGraphics g, int y, int left, int right, int mouseX, int mouseY) {
        y = title(g, left, y, "ГОСУДАРСТВО", "Направление, форма правления и религия");
        y = choiceSection(g, y, left, right, mouseX, mouseY,
            "ЭКОНОМИЧЕСКОЕ НАПРАВЛЕНИЕ", "direction", List.of(CountryDirection.values()));
        y += 10;
        y = choiceSection(g, y, left, right, mouseX, mouseY,
            "ФОРМА ПРАВЛЕНИЯ", "government", List.of(GovernmentType.values()));
        y += 10;
        y = choiceSection(g, y, left, right, mouseX, mouseY,
            "РЕЛИГИЯ", "religion", List.of(ReligionType.values()));
        return y + 10;
    }

    private int choiceSection(GuiGraphics g, int y, int left, int right,
                              int mouseX, int mouseY, String heading, String action, List<?> values) {
        int rowH = 42;
        int bottom = y + 33 + values.size() * rowH;
        panel(g, left, y, right, bottom);
        String choiceIconId = switch (action) {
            case "direction" -> "minecraft:compass";
            case "government" -> "minecraft:iron_sword";
            default -> "minecraft:book";
        };
        ItemStack choiceIcon = itemStack(choiceIconId);
        if (!choiceIcon.isEmpty()) g.renderItem(choiceIcon, left + 10, y + 7);
        g.drawString(font, heading, left + 38, y + 11, TEXT, true);

        int rowY = y + 30;
        for (Object object : values) {
            String display;
            String command;

            if (object instanceof CountryDirection direction) {
                display = direction.displayName();
                command = direction.commandName();
            } else if (object instanceof GovernmentType government) {
                display = government.displayName();
                command = government.commandName();
            } else {
                ReligionType religion = (ReligionType) object;
                display = religion.displayName();
                command = religion.commandName();
            }

            boolean selected = switch (action) {
                case "direction" -> snapshot.direction().equals(display);
                case "government" -> snapshot.government().equals(display);
                default -> snapshot.religion().equals(display);
            };

            if (inside(mouseX, mouseY, left + 8, rowY, right - 8, rowY + 36)) {
                g.fill(left + 8, rowY, right - 8, rowY + 36, PANEL_3);
            }

            g.drawString(font, display, left + 18, rowY + 6, selected ? TEXT : MUTED, selected);
            g.drawString(font, reformCost(action), left + 18, rowY + 22,
                selected ? POSITIVE : MUTED, false);

            int bx = right - 98;
            drawButton(g, bx, rowY + 7, right - 16, rowY + 29,
                selected ? "ВЫБРАНО" : "ВЫБРАТЬ",
                selected ? POSITIVE_DARK : ACCENT_DARK,
                selected ? POSITIVE : ACCENT, mouseX, mouseY,
                selected ? null : () -> openReformDialog(action, command, display));

            rowY += rowH;
        }
        return bottom;
    }

    private String reformCost(String action) {
        boolean first = switch (action) {
            case "direction" -> "Не выбрано".equals(snapshot.direction());
            case "government" -> "Не выбрано".equals(snapshot.government());
            default -> "Не выбрано".equals(snapshot.religion());
        };
        return first
            ? "Первый выбор • бесплатно"
            : CountryReformCostTable.summary(action, false, snapshot.population(), snapshot.developmentLevel());
    }

    private void openReformDialog(String action, String command, String title) {
        modalAction = action;
        modalCommand = command;
        modalTitle = title;
        updateTradeInputVisibility();
    }

    private int drawEffects(GuiGraphics g, int y, int left, int right) {
        y = title(g, left, y, "ЭФФЕКТЫ", "Итоговые модификаторы страны");

        int gap = 10;
        int half = (right - left - gap) / 2;
        int pos = effectCount(true);
        int neg = effectCount(false);

        int leftBottom = y + 38 + Math.max(1, pos) * 30 + 12;
        int rightBottom = y + 38 + Math.max(1, neg) * 30 + 12;
        panel(g, left, y, left + half, leftBottom);
        panel(g, left + half + gap, y, right, rightBottom);

        g.drawString(font, "ПОЛОЖИТЕЛЬНЫЕ", left + 14, y + 12, POSITIVE, true);
        drawEffectList(g, left + 14, y + 36, left + half - 14, true);
        g.drawString(font, "ОТРИЦАТЕЛЬНЫЕ", left + half + gap + 14, y + 12, NEGATIVE, true);
        drawEffectList(g, left + half + gap + 14, y + 36, right - 14, false);

        return Math.max(leftBottom, rightBottom) + 10;
    }

    private void drawEffectList(GuiGraphics g, int x, int y, int right, boolean positive) {
        int row = 0;
        for (int i = 0; i < snapshot.modifierNames().length; i++) {
            double value = valueAt(snapshot.modifierValues(), i);
            if ((positive && value <= 0.0001) || (!positive && value >= -0.0001)) continue;

            ItemStack effectIcon = itemStack(positive ? "minecraft:emerald" : "minecraft:redstone");
            if (!effectIcon.isEmpty()) g.renderItem(effectIcon, x, y + row * 30 - 7);
            g.drawString(font, clip(snapshot.modifierNames()[i], 31), x + 22, y + row * 30, TEXT, false);
            String valueText = signed(value);
            g.drawString(font, valueText, right - font.width(valueText),
                y + row * 30, positive ? POSITIVE : NEGATIVE, true);
            row++;
        }

        if (row == 0) {
            g.drawString(font, "Нет активных эффектов.", x, y, MUTED, false);
        }
    }

    private int drawCities(GuiGraphics g, int y, int left, int right) {
        y = title(g, left, y, "ГОРОДА", "Экономические показатели зарегистрированных городов");

        for (int i = 0; i < snapshot.cityNames().length; i++) {
            int bottom = y + 94;
            panel(g, left, y, right, bottom);

            String name = (valueAt(snapshot.cityCapitals(), i) ? "СТОЛИЦА • " : "")
                + valueAt(snapshot.cityNames(), i)
                + (valueAt(snapshot.cityMine(), i) ? " • ВАША" : "");

            ItemStack cityIcon = itemStack(valueAt(snapshot.cityMine(), i) ? "minecraft:gold_block" : "minecraft:bricks");
            if (!cityIcon.isEmpty()) g.renderItem(cityIcon, left + 10, y + 8);
            g.drawString(font, clip(name, 47), left + 34, y + 12, TEXT, true);
            g.drawString(font, "Государство: " + clip(valueAt(snapshot.cityCountries(), i), 27),
                left + 34, y + 31, MUTED, false);
            g.drawString(font, "Мэр: " + clip(valueAt(snapshot.cityMayors(), i), 27),
                left + 34, y + 50, MUTED, false);

            int sx = right - 300;
            miniStatIcon(g, sx, y + 12, "Казна", "$" + format(valueAt(snapshot.cityTreasuries(), i)), "minecraft:emerald", GOLD);
            miniStatIcon(g, sx + 96, y + 12, "Доход", "$" + format(valueAt(snapshot.cityIncome(), i)), "minecraft:paper", POSITIVE);
            miniStatIcon(g, sx + 192, y + 12, "Насел.", format(valueAt(snapshot.cityPopulation(), i)), "minecraft:player_head", ACCENT);
            g.drawString(font,
                "Инфра " + format(valueAt(snapshot.cityInfrastructure(), i)) +
                    "  •  Налоговые блоки " + format(valueAt(snapshot.cityTaxBlocks(), i)),
                sx, y + 53, MUTED, false);

            y = bottom + 8;
        }

        if (snapshot.cityNames().length == 0) {
            panel(g, left, y, right, y + 60);
            g.drawString(font, "Зарегистрированных городов нет.", left + 14, y + 22, MUTED, false);
            y += 68;
        }

        return y + 4;
    }

    private int drawMarket(GuiGraphics g, int y, int left, int right, int mouseX, int mouseY) {
        y = title(g, left, y, "РЫНОК", "Население покупает реальные предметы; здесь можно продавать им товары");

        panel(g, left, y, right, y + 78);
        g.drawString(font, "КОШЕЛЁК НАСЕЛЕНИЯ", left + 14, y + 13, MUTED, true);
        g.drawString(font, "$" + formatLong(snapshot.personalWallet()), left + 14, y + 32, GOLD, true);
        g.drawString(font, "Цена растёт при дефиците.", left + 200, y + 25, TEXT, false);
        y += 88;

        for (int i = 0; i < snapshot.marketItemIds().length; i++) {
            int bottom = y + 70;
            panel(g, left, y, right, bottom);

            ItemStack stack = itemStack(valueAt(snapshot.marketItemIds(), i));
            if (!stack.isEmpty()) g.renderItem(stack, left + 12, y + 13);

            g.drawString(font, clip(valueAt(snapshot.marketItemNames(), i), 27),
                left + 48, y + 12, TEXT, true);

            g.drawString(font,
                "Спрос " + format(valueAt(snapshot.marketBaseDemand(), i)) +
                    " • осталось " + format(valueAt(snapshot.marketRemaining(), i)) +
                    " • продано " + format(valueAt(snapshot.marketSold(), i)) +
                    " • импорт " + format(valueAt(snapshot.marketImported(), i)),
                left + 48, y + 31, MUTED, false);

            double fulfilled = valueAt(snapshot.marketBaseDemand(), i) <= 0 ? 1 :
                1 - valueAt(snapshot.marketRemaining(), i) /
                    (double) Math.max(1, valueAt(snapshot.marketBaseDemand(), i));
            progress(g, left + 48, y + 49, right - 215, y + 56, fulfilled, POSITIVE);

            g.drawString(font, "$" + format(valueAt(snapshot.marketPrices(), i)) + "/шт.",
                right - 200, y + 13, GOLD, true);

            int idx = i;
            drawButton(g, right - 142, y + 40, right - 78, y + 63,
                "×1", PANEL_3, ACCENT, mouseX, mouseY, () -> sellMarket(idx, 1));
            drawButton(g, right - 74, y + 40, right - 10, y + 63,
                "×16", ACCENT_DARK, ACCENT, mouseX, mouseY, () -> sellMarket(idx, 16));

            y = bottom + 7;
        }

        return y + 6;
    }

    private void buildTradeItemOptions() {
        List<TradeItemOption> options = new ArrayList<>();
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            var item = entry.getValue();
            if (item == null) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) continue;
            String name = new ItemStack(item).getHoverName().getString();
            if (name == null || name.isBlank()) name = id.getPath().replace('_', ' ');
            options.add(new TradeItemOption(id.toString(), name));
        }

        options.sort(
            Comparator.comparing(TradeItemOption::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(TradeItemOption::id)
        );
        tradeItemOptions = List.copyOf(options);
    }

    private String tradeItemDisplayName(String itemId) {
        if (itemId == null) return "";
        for (TradeItemOption option : tradeItemOptions) {
            if (itemId.equals(option.id())) return option.name();
        }
        try {
            ResourceLocation id = ResourceLocation.parse(itemId);
            return BuiltInRegistries.ITEM.getOptional(id)
                .map(item -> new ItemStack(item).getHoverName().getString())
                .orElse(itemId);
        } catch (Exception ignored) {
            return itemId;
        }
    }

    private List<TradeItemOption> filteredTradeItems() {
        String query = tradeItem == null ? "" : tradeItem.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return tradeItemOptions.stream().limit(8).toList();

        return tradeItemOptions.stream()
            .filter(option ->
                option.name().toLowerCase(Locale.ROOT).contains(query)
                    || option.id().toLowerCase(Locale.ROOT).contains(query))
            .limit(8)
            .toList();
    }

    private String resolveSelectedTradeItemId() {
        String query = tradeItem.getValue().trim();
        if (query.isEmpty()) return null;

        if (query.equalsIgnoreCase(tradeItemDisplayName(selectedTradeItemId))) {
            return selectedTradeItemId;
        }

        for (TradeItemOption option : tradeItemOptions) {
            if (option.name().equalsIgnoreCase(query)
                || option.id().equalsIgnoreCase(query)) {
                selectedTradeItemId = option.id();
                return selectedTradeItemId;
            }
        }

        return null;
    }

    private void drawTradeItemDropdown(GuiGraphics g, int mouseX, int mouseY) {
        if (!tradeItem.isFocused() || modalAction != null) return;

        List<TradeItemOption> matches = filteredTradeItems();
        if (matches.isEmpty()) return;

        int left = tradeItem.getX();
        int top = tradeItem.getY() + tradeItem.getHeight() + 2;
        int right = left + tradeItem.getWidth();
        int maxBottom = height - 12;
        int bottom = Math.min(top + matches.size() * 28 + 2, maxBottom);
        int visibleRows = Math.max(0, (bottom - top - 2) / 28);

        g.fill(left, top, right, bottom, PANEL_2);
        outline(g, left, top, right, bottom, BORDER);

        int rowY = top + 1;
        for (int matchIndex = 0; matchIndex < visibleRows; matchIndex++) {
            TradeItemOption option = matches.get(matchIndex);
            boolean hover = inside(mouseX, mouseY, left + 1, rowY, right - 1, rowY + 27);
            if (hover) {
                g.fill(left + 1, rowY, right - 1, rowY + 27, PANEL_3);
            }

            ItemStack stack = itemStack(option.id());
            if (!stack.isEmpty()) {
                g.renderItem(stack, left + 6, rowY + 5);
            }

            g.drawString(font, clip(option.name(), 28), left + 30, rowY + 5, TEXT, true);
            g.drawString(font, clip(option.id(), 31), left + 30, rowY + 16, MUTED, false);

            target(left + 1, rowY, right - 1, rowY + 27, () -> {
                selectedTradeItemId = option.id();
                tradeItem.setValue(option.name());
                tradeItem.setCursorPosition(0);
                tradeItem.setHighlightPos(0);
                tradeItem.setFocused(false);
            });
            rowY += 28;
        }
    }

    private int drawTrade(GuiGraphics g, int y, int left, int right, int mouseX, int mouseY) {
        y = title(g, left, y, "ТОРГОВЛЯ", "Физические грузы и поставщики — деньги закреплены за реальными поставками");

        panel(g, left, y, right, y + 100);
        ItemStack terminalIcon = itemStack("minecraft:chest");
        if (!terminalIcon.isEmpty()) g.renderItem(terminalIcon, left + 10, y + 10);
        g.drawString(font, "ТОРГОВЫЙ ТЕРМИНАЛ", left + 38, y + 13, TEXT, true);
        g.drawString(font,
            snapshot.tradeTerminalSet()
                ? "Назначен • " + snapshot.tradeTerminalPosition()
                : "Не назначен",
            left + 38, y + 32,
            snapshot.tradeTerminalSet() ? POSITIVE : NEGATIVE, false);
        g.drawString(font, "Используется для физического входа и выхода грузов.",
            left + 38, y + 54, MUTED, false);

        drawButton(g, right - 160, y + 21, right - 14, y + 48,
            snapshot.tradeTerminalSet() ? "ПЕРЕНАЗНАЧИТЬ" : "НАЗНАЧИТЬ",
            snapshot.tradeTerminalSet() ? PANEL_3 : ACCENT_DARK,
            snapshot.tradeTerminalSet() ? TEXT : ACCENT,
            mouseX, mouseY, () -> sendTrade("trade_terminal_set", ""));
        y += 110;

        tradeOrderTop = y;
        int orderBottom = y + 108;
        panel(g, left, y, right, orderBottom);

        g.drawString(font, "СОЗДАТЬ ЗАКУПКУ", left + 14, y + 11, TEXT, true);

        g.drawString(font, "Предмет", left + 14, y + 28, MUTED, false);
        g.drawString(font, "Количество", left + 284, y + 28, MUTED, false);
        g.drawString(font, "Макс. цена / шт.", left + 382, y + 28, MUTED, false);

        g.drawString(font,
            "Поиск по русскому названию или item ID.",
            left + 14, y + 69, MUTED, false);

        boolean selectedItemValid = resolveSelectedTradeItemId() != null;
        drawButton(g, right - 148, y + 68, right - 10, y + 95,
            "СОЗДАТЬ ЗАКАЗ",
            selectedItemValid ? ACCENT_DARK : PANEL_3,
            selectedItemValid ? ACCENT : MUTED,
            mouseX, mouseY,
            selectedItemValid
                ? () -> sendTrade("trade_order_create",
                    selectedTradeItemId + "|" + tradeAmount.getValue() + "|" + tradeMaxPrice.getValue())
                : null);

        g.drawString(font, "Деньги резервируются из казны страны.",
            left + 14, y + 88, MUTED, false);

        y = orderBottom + 10;

        int gap = 10;
        int half = (right - left - gap) / 2;
        tradeOwnOrdersTop = y;
        tradeOwnOrdersLeft = left;
        tradeOwnOrdersRight = left + half;
        int ownEnd = drawTradeOwnOrders(g, y, left, left + half, mouseX, mouseY);
        int openEnd = drawTradeOpenOrders(g, y, left + half + gap, right, mouseX, mouseY);
        y = Math.max(ownEnd, openEnd) + 10;

        return drawTradeShipments(g, y, left, right, mouseX, mouseY);
    }

    private int drawTradeOwnOrders(GuiGraphics g, int y, int left, int right, int mouseX, int mouseY) {
        int rowH = 84;
        int count = Math.max(1, snapshot.tradeOwnOrders().length);
        int bottom = y + 52 + count * rowH;
        panel(g, left, y, right, bottom);
        g.drawString(font, "МОИ ЗАКАЗЫ", left + 14, y + 12, TEXT, true);
        g.drawString(font, "Цена принятия", left + 14, y + 31, MUTED, false);
        g.drawString(font, "Партия", left + 150, y + 31, MUTED, false);
        int rowY = y + 48;

        if (snapshot.tradeOwnOrders().length == 0) {
            g.drawString(font, "Заказов нет.", left + 14, rowY + 10, MUTED, false);
            return bottom;
        }

        for (String raw : snapshot.tradeOwnOrders()) {
            TradeOrderRow row = parseTradeOrder(raw);
            if (row == null) continue;

            g.fill(left + 8, rowY, right - 8, rowY + rowH - 6, PANEL_2);
            ItemStack orderIcon = itemStack(row.itemId());
            if (!orderIcon.isEmpty()) g.renderItem(orderIcon, left + 12, rowY + 14);
            g.drawString(font, "#" + row.id() + " • " + clip(tradeItemName(row.itemId()), 18),
                left + 40, rowY + 8, TEXT, true);
            g.drawString(font,
                format(row.remaining()) + "/" + format(row.quantity()) +
                    " • max $" + row.maxPrice(),
                left + 40, rowY + 26, MUTED, false);
            g.drawString(font, "Статус: " + tradeStatus(row.status()),
                left + 40, rowY + 44, MUTED, false);

            boolean seller = snapshot.countryName().equals(row.seller());
            boolean canDispatch = seller && row.remaining() > 0 &&
                ("ACCEPTED".equals(row.status()) || "SHIPPING".equals(row.status()));
            boolean canCancel = !seller && ("OPEN".equals(row.status()) || "ACCEPTED".equals(row.status()));

            if (canDispatch) {
                drawButton(g, right - 100, rowY + 7, right - 14, rowY + 30,
                    "ОТПРАВИТЬ", ACCENT_DARK, ACCENT, mouseX, mouseY,
                    () -> sendTrade("trade_shipment_dispatch",
                        row.id() + "|" + tradeDispatchAmount.getValue()));
            }
            drawButton(g, right - 100, rowY + 36, right - 14, rowY + 59,
                "ОТМЕНИТЬ", canCancel ? NEGATIVE_DARK : PANEL_3,
                canCancel ? NEGATIVE : MUTED, mouseX, mouseY,
                canCancel ? () -> sendTrade("trade_order_cancel", String.valueOf(row.id())) : null);

            rowY += rowH;
        }
        return bottom;
    }

    private int drawTradeOpenOrders(GuiGraphics g, int y, int left, int right, int mouseX, int mouseY) {
        int rowH = 84;
        int count = Math.max(1, snapshot.tradeOpenOrders().length);
        int bottom = y + 52 + count * rowH;
        panel(g, left, y, right, bottom);
        g.drawString(font, "ДОСТУПНЫЕ ЗАКУПКИ", left + 14, y + 12, TEXT, true);
        g.drawString(font, "Можно стать поставщиком.", left + 14, y + 31, MUTED, false);
        int rowY = y + 48;

        if (snapshot.tradeOpenOrders().length == 0) {
            g.drawString(font, "Открытых заказов нет.", left + 14, rowY + 10, MUTED, false);
            return bottom;
        }

        for (String raw : snapshot.tradeOpenOrders()) {
            TradeOrderOfferRow row = parseTradeOrderOffer(raw);
            if (row == null) continue;

            g.fill(left + 8, rowY, right - 8, rowY + rowH - 6, PANEL_2);
            ItemStack orderIcon = itemStack(row.itemId());
            if (!orderIcon.isEmpty()) g.renderItem(orderIcon, left + 12, rowY + 14);
            g.drawString(font, "#" + row.id() + " • " + clip(tradeItemName(row.itemId()), 16),
                left + 40, rowY + 8, TEXT, true);
            g.drawString(font, "Покупатель: " + clip(row.buyer(), 17),
                left + 40, rowY + 26, MUTED, false);
            g.drawString(font,
                "Нужно " + format(row.remaining()) + " • максимум $" + row.maxPrice() + "/шт",
                left + 40, rowY + 44, MUTED, false);

            drawButton(g, right - 96, rowY + 30, right - 14, rowY + 53,
                "ПРИНЯТЬ", POSITIVE_DARK, POSITIVE, mouseX, mouseY,
                () -> sendTrade("trade_order_accept",
                    row.id() + "|" + tradeAcceptPrice.getValue()));
            rowY += rowH;
        }
        return bottom;
    }

    private int drawTradeShipments(GuiGraphics g, int y, int left, int right, int mouseX, int mouseY) {
        int rowH = 84;
        int count = Math.max(1, snapshot.tradeShipments().length);
        int bottom = y + 52 + count * rowH;
        panel(g, left, y, right, bottom);
        g.drawString(font, "ЛОГИСТИКА", left + 14, y + 12, TEXT, true);
        g.drawString(font, "Логист получает оплату после физической доставки.",
            left + 14, y + 31, MUTED, false);
        int rowY = y + 48;

        if (snapshot.tradeShipments().length == 0) {
            g.drawString(font, "Активных грузов нет.", left + 14, rowY + 10, MUTED, false);
            return bottom;
        }

        for (String raw : snapshot.tradeShipments()) {
            TradeShipmentRow row = parseTradeShipment(raw);
            if (row == null) continue;

            g.fill(left + 8, rowY, right - 8, rowY + rowH - 6, PANEL_2);
            ItemStack shipmentIcon = itemStack(row.itemId());
            if (!shipmentIcon.isEmpty()) g.renderItem(shipmentIcon, left + 12, rowY + 14);
            g.drawString(font,
                "#" + row.id() + " • заказ #" + row.orderId() + " • " + clip(tradeItemName(row.itemId()), 20),
                left + 40, rowY + 8, TEXT, true);
            g.drawString(font,
                row.seller() + " → " + row.buyer() + " • ×" + format(row.quantity()),
                left + 40, rowY + 26, MUTED, false);
            g.drawString(font,
                tradeStatus(row.status()) + " • " + row.originChunk() + " → " + row.destinationChunk(),
                left + 40, rowY + 44, MUTED, false);

            boolean canHaul = "WAITING_LOGISTICS".equals(row.status()) &&
                !snapshot.countryName().equals(row.seller()) &&
                !snapshot.countryName().equals(row.buyer());

            if (canHaul) {
                drawButton(g, right - 116, rowY + 28, right - 14, rowY + 53,
                    "ВЗЯТЬ ГРУЗ", ACCENT_DARK, ACCENT, mouseX, mouseY,
                    () -> sendTrade("trade_shipment_haul", String.valueOf(row.id())));
            }

            rowY += rowH;
        }

        return bottom;
    }

    private void drawModal(GuiGraphics g, int mouseX, int mouseY) {
        // The modal is a true top layer: background panels and text must not remain readable.
        g.fill(0, 0, width, height, 0xFF000000);

        int w = Math.min(520, width - 32);
        int h = 172;
        int left = (width - w) / 2;
        int top = (height - h) / 2;

        panel(g, left, top, left + w, top + h);
        ItemStack reformIcon = itemStack(
            "direction".equals(modalAction) ? "minecraft:compass"
                : "government".equals(modalAction) ? "minecraft:iron_sword"
                : "minecraft:book"
        );
        if (!reformIcon.isEmpty()) g.renderItem(reformIcon, left + 14, top + 13);
        g.drawString(font, "ПОДТВЕРЖДЕНИЕ РЕФОРМЫ", left + 44, top + 17, MUTED, true);
        g.drawString(font, clip(modalTitle, 49), left + 44, top + 39, TEXT, true);

        boolean first = switch (modalAction) {
            case "direction" -> "Не выбрано".equals(snapshot.direction());
            case "government" -> "Не выбрано".equals(snapshot.government());
            default -> "Не выбрано".equals(snapshot.religion());
        };

        g.drawString(font,
            first
                ? "Первый выбор данного параметра бесплатен."
                : "Это полноценная реформа. Сервер проверит деньги и материалы.",
            left + 18, top + 66, MUTED, false);

        String cost = first
            ? "БЕСПЛАТНО"
            : CountryReformCostTable.summary(
                modalAction, false, snapshot.population(), snapshot.developmentLevel());

        g.drawString(font, "Стоимость: " + clip(cost, 55),
            left + 18, top + 88, first ? POSITIVE : GOLD, true);

        drawButtonVisual(g, left + w - 196, top + h - 42, left + w - 104, top + h - 15,
            "ОТМЕНА", PANEL_3, TEXT, mouseX, mouseY);
        drawButtonVisual(g, left + w - 95, top + h - 42, left + w - 18, top + h - 15,
            "ПОДТВЕРДИТЬ", ACCENT_DARK, ACCENT, mouseX, mouseY);
    }

    private void closeModal() {
        modalAction = null;
        modalCommand = null;
        modalTitle = null;
        updateTradeInputVisibility();
    }

    private void drawCloseButton(GuiGraphics g, int mouseX, int mouseY) {
        int right = width - 16;
        boolean hover = inside(mouseX, mouseY, right - 31, 20, right - 10, 41);
        g.fill(right - 31, 20, right - 10, 41, hover ? NEGATIVE_DARK : PANEL_2);
        outline(g, right - 31, 20, right - 10, 41, hover ? NEGATIVE : BORDER);
        g.drawCenteredString(font, "×", right - 20, 25, hover ? NEGATIVE : TEXT);
        target(right - 31, 20, right - 10, 41, this::onClose);
    }

    private int title(GuiGraphics g, int left, int y, String heading, String subtitle) {
        g.drawString(font, heading, left, y + 1, TEXT, true);
        g.drawString(font, subtitle, left, y + 18, MUTED, false);
        return y + 40;
    }

    private void metric(GuiGraphics g, int x, int y, int w, String title, String value, String subtitle, String iconId, int accent) {
        panel(g, x, y, x + w, y + 76);
        g.fill(x, y, x + 3, y + 76, accent);
        ItemStack icon = itemStack(iconId);
        if (!icon.isEmpty()) g.renderItem(icon, x + 9, y + 11);
        g.drawString(font, title, x + 34, y + 10, MUTED, true);
        g.drawString(font, value, x + 34, y + 28, TEXT, true);
        g.drawString(font, clip(subtitle, 19), x + 34, y + 49, MUTED, false);
    }

    private void info(GuiGraphics g, int x, int y, int right, String name, String value, String iconId) {
        ItemStack icon = itemStack(iconId);
        if (!icon.isEmpty()) g.renderItem(icon, x, y - 3);
        int textX = x + 24;
        g.drawString(font, name, textX, y, MUTED, false);
        String v = clip(value, 30);
        g.drawString(font, v, right - font.width(v), y, TEXT, true);
    }

    private void miniStat(GuiGraphics g, int x, int y, String label, String value, int color) {
        g.drawString(font, label.toUpperCase(Locale.ROOT), x, y, MUTED, false);
        g.drawString(font, value, x, y + 14, color, true);
    }

    private void miniStatIcon(GuiGraphics g, int x, int y, String label, String value, String iconId, int color) {
        ItemStack icon = itemStack(iconId);
        if (!icon.isEmpty()) g.renderItem(icon, x, y - 4);
        g.drawString(font, label.toUpperCase(Locale.ROOT), x + 20, y, MUTED, false);
        g.drawString(font, value, x + 20, y + 14, color, true);
    }

    private void miniButton(GuiGraphics g, int x, int y, String label, int mouseX, int mouseY, Runnable action) {
        drawButton(g, x, y, x + 24, y + 20, label, PANEL_3, TEXT, mouseX, mouseY, action);
    }

    private void drawButtonVisual(
        GuiGraphics g, int left, int top, int right, int bottom,
        String text, int fill, int accent, int mouseX, int mouseY
    ) {
        boolean hover = inside(mouseX, mouseY, left, top, right, bottom);
        g.fill(left, top, right, bottom, hover ? brighten(fill) : fill);
        outline(g, left, top, right, bottom, hover ? accent : BORDER);
        int tx = left + Math.max(4, (right - left - font.width(text)) / 2);
        g.drawString(font, text, tx, top + 6, hover ? TEXT : accent, true);
    }

    private void drawButton(GuiGraphics g, int left, int top, int right, int bottom,
                            String text, int fill, int accent,
                            int mouseX, int mouseY, Runnable action) {
        boolean enabled = action != null;
        boolean hover = enabled && inside(mouseX, mouseY, left, top, right, bottom);
        g.fill(left, top, right, bottom, hover ? brighten(fill) : fill);
        outline(g, left, top, right, bottom, hover ? accent : BORDER);
        int tx = left + Math.max(4, (right - left - font.width(text)) / 2);
        g.drawString(font, text, tx, top + 6, enabled ? (hover ? TEXT : accent) : MUTED, true);
        if (enabled) target(left, top, right, bottom, action);
    }

    private void drawPill(GuiGraphics g, String text, int x, int y, int fill, int accent) {
        if (text == null || text.isBlank() || "Не выбрано".equals(text)) return;
        int w = font.width(clip(text, 21)) + 14;
        g.fill(x, y, x + w, y + 17, fill);
        outline(g, x, y, x + w, y + 17, accent);
        g.drawString(font, clip(text, 21), x + 7, y + 4, accent, true);
    }

    private void progress(GuiGraphics g, int left, int top, int right, int bottom, double value, int color) {
        double v = Math.max(0, Math.min(1, value));
        g.fill(left, top, right, bottom, PANEL_3);
        g.fill(left, top, left + (int) ((right - left) * v), bottom, color);
    }

    private void drawScrollBar(GuiGraphics g, int x, int top, int bottom, double maxScroll) {
        int h = bottom - top;
        int thumb = Math.max(24, (int) (h * h / (h + maxScroll)));
        int y = top + (int) ((h - thumb) * (scroll / Math.max(1, maxScroll)));
        g.fill(x, top, x + 3, bottom, PANEL_3);
        g.fill(x, y, x + 3, y + thumb, ACCENT);
    }

    private void panel(GuiGraphics g, int left, int top, int right, int bottom) {
        g.fill(left, top, right, bottom, PANEL);
        outline(g, left, top, right, bottom, BORDER);
    }

    private void target(int left, int top, int right, int bottom, Runnable action) {
        targets.add(new ClickTarget(left, top, right, bottom, action));
    }

    private static boolean inside(double x, double y, int left, int top, int right, int bottom) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    private static int brighten(int color) {
        int a = color >>> 24;
        int r = Math.min(255, ((color >>> 16) & 255) + 12);
        int green = Math.min(255, ((color >>> 8) & 255) + 12);
        int b = Math.min(255, (color & 255) + 12);
        return (a << 24) | (r << 16) | (green << 8) | b;
    }

    private static void outline(GuiGraphics g, int left, int top, int right, int bottom, int color) {
        g.fill(left, top, right, top + 1, color);
        g.fill(left, bottom - 1, right, bottom, color);
        g.fill(left, top, left + 1, bottom, color);
        g.fill(right - 1, top, right, bottom, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (modalAction != null) {
            int w = Math.min(520, width - 32);
            int h = 172;
            int left = (width - w) / 2;
            int top = (height - h) / 2;

            if (inside(mouseX, mouseY, left + w - 196, top + h - 42, left + w - 104, top + h - 15)) {
                closeModal();
                return true;
            }

            if (inside(mouseX, mouseY, left + w - 95, top + h - 42, left + w - 18, top + h - 15)) {
                EconomyNetwork.sendAction(modalAction, modalCommand);
                closeModal();
                return true;
            }

            // Modal owns the complete input layer. Nothing behind it can receive a click.
            return true;
        }

        if (page == Page.TRADE && super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        for (int i = targets.size() - 1; i >= 0; i--) {
            ClickTarget click = targets.get(i);
            if (click.contains(mouseX, mouseY)) {
                click.action.run();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (modalAction != null) return true;
        if (mouseX < contentLeft() || mouseX > width - 16 || mouseY < contentTop() || mouseY > height - 12) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        double max = estimatedMaxScroll();
        scroll = Math.max(0, Math.min(max, scroll - scrollY * 32));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (modalAction != null && keyCode == 256) {
            closeModal();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private double estimatedMaxScroll() {
        int y = contentTop();
        int end;
        switch (page) {
            case OVERVIEW -> end = y + 40 + 86 + 114 + 98 +
                52 + Math.max(WorkforceSector.values().length, materialCount()) * 38 + 20;
            case COUNTRY -> end = y + 40 +
                33 + CountryDirection.values().length * 42 +
                10 + 33 + GovernmentType.values().length * 42 +
                10 + 33 + ReligionType.values().length * 42 + 10;
            case EFFECTS -> end = y + 40 + 38 +
                Math.max(1, Math.max(effectCount(true), effectCount(false))) * 30 + 22;
            case CITIES -> end = y + 40 + Math.max(1, snapshot.cityNames().length) * 102 + 10;
            case MARKET -> end = y + 40 + 88 + Math.max(1, snapshot.marketItemIds().length) * 77 + 10;
            case TRADE -> end = y + 40 + 110 + 118 +
                Math.max(1, Math.max(snapshot.tradeOwnOrders().length, snapshot.tradeOpenOrders().length)) * 84 +
                10 + 52 + Math.max(1, snapshot.tradeShipments().length) * 84 + 10;
            default -> end = y;
        }
        return Math.max(0, end - (height - 12));
    }

    private int contentLeft() {
        return 176;
    }

    private int contentTop() {
        return 76;
    }

    private int materialCount() {
        return Math.min(
            Math.min(snapshot.materialIds().length, snapshot.materialNames().length),
            Math.min(snapshot.materialStockpile().length, snapshot.materialDebt().length)
        );
    }

    private int effectCount(boolean positive) {
        int count = 0;
        for (double value : snapshot.modifierValues()) {
            if ((positive && value > 0.0001) || (!positive && value < -0.0001)) count++;
        }
        return count;
    }

    private void changeWorkforce(WorkforceSector sector, int delta) {
        EconomyNetwork.sendAction("workforce", sector.commandName() + ":" + delta);
    }

    private void sellMarket(int index, int amount) {
        EconomyNetwork.sendAction(
            "market_sell",
            amount + "|" + valueAt(snapshot.marketItemIds(), index)
        );
    }

    private void sendTrade(String action, String value) {
        EconomyNetwork.sendAction(action, value);
    }

    private static ItemStack itemStack(String itemId) {
        try {
            ResourceLocation id = ResourceLocation.parse(itemId);
            return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack materialIconStack(String raw) {
        if (raw == null || raw.isBlank()) return ItemStack.EMPTY;
        for (String candidate : raw.split("\\|")) {
            ItemStack stack = itemStack(candidate);
            if (!stack.isEmpty()) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static String tradeItemName(String itemId) {
        ItemStack stack = itemStack(itemId);
        return stack.isEmpty() ? itemId : stack.getHoverName().getString();
    }

    private String clip(String value, int maxChars) {
        if (value == null) return "";
        if (value.length() <= maxChars) return value;
        return value.substring(0, Math.max(0, maxChars - 1)) + "…";
    }

    private static int formatSafe(int value) {
        return Math.max(0, value);
    }

    private static String format(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String formatLong(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%,.2f", value);
    }

    private static String signed(double value) {
        if (Math.abs(value) < 0.0001) return "0%";
        return String.format(Locale.ROOT, "%+.0f%%", value);
    }

    private static int valueAt(int[] values, int index) {
        return values != null && index >= 0 && index < values.length ? values[index] : 0;
    }

    private static double valueAt(double[] values, int index) {
        return values != null && index >= 0 && index < values.length ? values[index] : 0;
    }

    private static boolean valueAt(boolean[] values, int index) {
        return values != null && index >= 0 && index < values.length && values[index];
    }

    private static String valueAt(String[] values, int index) {
        return values != null && index >= 0 && index < values.length && values[index] != null
            ? values[index]
            : "";
    }

    private static String tradeStatus(String status) {
        return switch (status) {
            case "OPEN" -> "Открыт";
            case "ACCEPTED" -> "Принят поставщиком";
            case "SHIPPING" -> "Частично отправлен";
            case "COMPLETE" -> "Завершён";
            case "CANCELLED" -> "Отменён";
            case "WAITING_LOGISTICS" -> "Ждёт логиста";
            case "IN_TRANSIT" -> "В пути";
            case "DELIVERED" -> "Доставлен";
            default -> status;
        };
    }

    private static TradeOrderRow parseTradeOrder(String raw) {
        String[] p = raw.split("\\|", -1);
        if (p.length != 8) return null;
        try {
            return new TradeOrderRow(
                Integer.parseInt(p[0]), p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                Integer.parseInt(p[4]), p[5], p[6], Long.parseLong(p[7]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static TradeOrderOfferRow parseTradeOrderOffer(String raw) {
        String[] p = raw.split("\\|", -1);
        if (p.length != 7) return null;
        try {
            return new TradeOrderOfferRow(
                Integer.parseInt(p[0]), p[1], p[2], Integer.parseInt(p[3]),
                Integer.parseInt(p[4]), Integer.parseInt(p[5]), p[6]);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static TradeShipmentRow parseTradeShipment(String raw) {
        String[] p = raw.split("\\|", -1);
        if (p.length != 10) return null;
        try {
            return new TradeShipmentRow(
                Integer.parseInt(p[0]), Integer.parseInt(p[1]), p[2], Integer.parseInt(p[3]),
                p[4], p[5], p[6], p[7], p[8], p[9]);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record TradeItemOption(String id, String name) {}

    private record ClickTarget(int left, int top, int right, int bottom, Runnable action) {
        boolean contains(double x, double y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }
    }

    private record TradeOrderRow(
        int id, String itemId, int remaining, int quantity,
        int maxPrice, String seller, String status, long reserved) {}

    private record TradeOrderOfferRow(
        int id, String itemId, String buyer, int remaining,
        int maxPrice, int agreedPrice, String status) {}

    private record TradeShipmentRow(
        int id, int orderId, String itemId, int quantity,
        String seller, String buyer, String status, String courier,
        String originChunk, String destinationChunk) {}
}
