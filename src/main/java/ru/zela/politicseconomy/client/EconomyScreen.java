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
        CITIES("Страны", "minecraft:globe"),
        NEWS("Новости", "minecraft:writable_book"),
        MARKET("Рынок", "minecraft:emerald"),
        TRADE("Торговля", "minecraft:chest"),
        TRADE_HISTORY("История заказов", "minecraft:written_book"),
        DEBTS("Долги", "minecraft:iron_block"),
        RESEARCH("Технологии", "minecraft:bookshelf");

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

    private boolean workforceExpanded = true;
    private boolean warehouseExpanded = true;

    private EditBox tradeItem;
    private EditBox tradeAmount;
    private EditBox tradeMaxPrice;
    private EditBox tradeAcceptPrice;
    private EditBox tradeDispatchAmount;
    private EditBox tradeHistorySearch;
    private EditBox tradeCancelReason;
    private Integer cancelOrderId;
    private String selectedTradeItemId = "minecraft:iron_ingot";
    private int selectedCountryIndex;
    private boolean countryDropdownOpen;
    private List<TradeItemOption> tradeItemOptions = List.of();

    // Coordinates are calculated from the actual trade cards every frame.
    private int tradeOrderTop;
    private int tradeOwnOrdersTop;
    private int tradeOwnOrdersLeft;
    private int tradeOwnOrdersRight;
    private int tradeHistoryTop;

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
        tradeHistorySearch = new EditBox(font, 0, 0, 220, 20, Component.literal("Поиск"));
        tradeCancelReason = new EditBox(font, 0, 0, 440, 20, Component.literal("Причина отмены"));

        buildTradeItemOptions();
        tradeItem.setValue(tradeItemDisplayName(selectedTradeItemId));
        tradeAmount.setValue("64");
        tradeMaxPrice.setValue("20");
        tradeAcceptPrice.setValue("15");
        tradeDispatchAmount.setValue("64");
        tradeHistorySearch.setValue("");
        tradeCancelReason.setValue("");

        for (EditBox box : List.of(
            tradeItem, tradeAmount, tradeMaxPrice, tradeAcceptPrice, tradeDispatchAmount,
            tradeHistorySearch, tradeCancelReason
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
        String historySearch = tradeHistorySearch == null ? "" : tradeHistorySearch.getValue();
        String cancelReason = tradeCancelReason == null ? "" : tradeCancelReason.getValue();

        super.resize(minecraft, width, height);

        if (tradeItem != null) {
            tradeItem.setValue(item);
            tradeAmount.setValue(amount);
            tradeMaxPrice.setValue(max);
            tradeAcceptPrice.setValue(accept);
            tradeDispatchAmount.setValue(dispatch);
            tradeHistorySearch.setValue(historySearch);
            tradeCancelReason.setValue(cancelReason);
            layoutTradeInputs();
            updateTradeInputVisibility();
        }
    }

    private void layoutTradeInputs() {
        // drawTrade() receives an already scroll-adjusted Y coordinate.
        int orderTop = tradeOrderTop;
        int contentWidth = width - contentLeft() - 16;

        if (contentWidth < 760) {
            // Compact: one full-width item field, then quantity and price below it.
            int fieldWidth = Math.max(120, contentWidth - 28);
            tradeItem.setX(contentLeft() + 14);
            tradeItem.setY(orderTop + 39);
            tradeItem.setWidth(fieldWidth);

            int secondRowY = orderTop + 75;
            tradeAmount.setX(contentLeft() + 14);
            tradeAmount.setY(secondRowY);
            tradeAmount.setWidth(90);

            tradeMaxPrice.setX(contentLeft() + 116);
            tradeMaxPrice.setY(secondRowY);
            tradeMaxPrice.setWidth(104);
        } else {
            tradeItem.setX(contentLeft() + 14);
            tradeItem.setY(orderTop + 39);
            tradeItem.setWidth(260);

            tradeAmount.setX(contentLeft() + 284);
            tradeAmount.setY(orderTop + 39);
            tradeAmount.setWidth(88);

            tradeMaxPrice.setX(contentLeft() + 382);
            tradeMaxPrice.setY(orderTop + 39);
            tradeMaxPrice.setWidth(98);
        }

        // These inputs belong to the "My orders" header row.
        // Keep them below the panel title so they can never cover it.
        int ownTop = tradeOwnOrdersTop;
        tradeAcceptPrice.setX(tradeOwnOrdersLeft + 55);
        tradeAcceptPrice.setY(ownTop + 24);
        tradeAcceptPrice.setWidth(70);

        tradeDispatchAmount.setX(tradeOwnOrdersLeft + 185);
        tradeDispatchAmount.setY(ownTop + 24);
        tradeDispatchAmount.setWidth(70);
    }

    private void updateTradeInputVisibility() {
        if (tradeItem == null) return;

        int top = contentTop();
        int bottom = height - 12;
        boolean tradeVisible = page == Page.TRADE && modalAction == null;
        boolean historyVisible = page == Page.TRADE_HISTORY && modalAction == null;
        boolean cancelVisible = "trade_cancel".equals(modalAction);

        tradeItem.visible = tradeVisible && inViewport(tradeItem.getY(), tradeItem.getHeight(), top, bottom);
        tradeAmount.visible = tradeVisible && inViewport(tradeAmount.getY(), tradeAmount.getHeight(), top, bottom);
        tradeMaxPrice.visible = tradeVisible && inViewport(tradeMaxPrice.getY(), tradeMaxPrice.getHeight(), top, bottom);
        tradeAcceptPrice.visible = tradeVisible && inViewport(tradeAcceptPrice.getY(), tradeAcceptPrice.getHeight(), top, bottom);
        tradeDispatchAmount.visible = tradeVisible && inViewport(tradeDispatchAmount.getY(), tradeDispatchAmount.getHeight(), top, bottom);

        tradeHistorySearch.visible = historyVisible && inViewport(tradeHistorySearch.getY(), tradeHistorySearch.getHeight(), top, bottom);
        tradeCancelReason.visible = cancelVisible;
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
            case CITIES -> end = drawCities(graphics, end, left, right, mouseX, mouseY);
            case NEWS -> end = drawNews(graphics, end, left, right);
            case MARKET -> end = drawMarket(graphics, end, left, right, mouseX, mouseY);
            case TRADE -> end = drawTrade(graphics, end, left, right, mouseX, mouseY);
            case TRADE_HISTORY -> end = drawTradeHistory(graphics, end, left, right, mouseX, mouseY);
            case DEBTS -> end = drawDebts(graphics, end, left, right, mouseX, mouseY);
            case RESEARCH -> end = drawResearch(graphics, end, left, right, mouseX, mouseY);
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

        if (page == Page.TRADE_HISTORY && modalAction == null) {
            layoutHistorySearch();
            updateTradeInputVisibility();
            tradeHistorySearch.render(graphics, mouseX, mouseY, partialTick);
        }

        graphics.disableScissor();

        drawCloseButton(graphics, mouseX, mouseY);

        double maxScroll = Math.max(0, end - bottom);
        if (maxScroll > 0) {
            drawScrollBar(graphics, right + 5, top, bottom, maxScroll);
        }

        if (modalAction != null) {
            drawModal(graphics, mouseX, mouseY);
            if ("trade_cancel".equals(modalAction)) {
                tradeCancelReason.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private void drawHeader(GuiGraphics g, int mouseX, int mouseY) {
        int left = 12;
        int right = width - 16;

        panel(g, left, 12, right, 64);
        g.drawString(font, "POLITICS ECONOMY", 28, 22, TEXT, true);
        g.drawString(font, clipToWidth(snapshot.countryName().toUpperCase(Locale.ROOT), 250), 28, 39, MUTED, false);

        g.drawString(font, "КАЗНА", right - 183, 19, MUTED, true);
        g.drawString(font, "$" + formatDouble(snapshot.treasury()), right - 183, 34, GOLD, true);
        g.drawString(font, format(snapshot.population()) + " населения", right - 183, 49, TEXT, false);

        drawPill(g, snapshot.direction(), right - 360, 27, ACCENT_DARK, ACCENT, 155);

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
                if (next == Page.RESEARCH) {
                    Minecraft.getInstance().setScreen(new CountryTechnologyScreen(snapshot));
                    return;
                }
                page = next;
                scroll = 0;
                updateTradeInputVisibility();
            });
            y += 29;
        }

        if (height >= 420) {
            int infoY = bottom - 62;
            g.drawString(font, "РАЗВИТИЕ", left + 13, infoY, MUTED, true);
            g.drawString(font, "Уровень " + snapshot.developmentLevel() + " • " + snapshot.developmentTitle(),
                left + 13, infoY + 14, TEXT, false);
            double progress = snapshot.developmentNextThreshold() <= 0 ? 1 :
                snapshot.developmentPoints() / (double) snapshot.developmentNextThreshold();
            progress(g, left + 13, infoY + 31, right - 13, infoY + 37, progress, ACCENT);
            g.drawString(font,
                format(snapshot.developmentPoints()) + " / " + format(snapshot.developmentNextThreshold()),
                left + 13, infoY + 44, MUTED, false);
        }
    }

    private int drawOverview(GuiGraphics g, int y, int left, int right, int mouseX, int mouseY) {
        y = title(g, left, y, "ОБЩАЯ КАРТИНА", "Ключевые показатели государства");

        int gap = 8;
        int overviewWidth = right - left;

        if (overviewWidth < 760) {
            int w = (overviewWidth - gap) / 2;

            metric(g, left, y, w, "КАЗНА",
                "$" + formatDouble(snapshot.treasury()),
                "Государственные деньги", "minecraft:emerald", GOLD);

            metric(g, left + w + gap, y, w, "СОДЕРЖАНИЕ",
                "-$" + formatDouble(snapshot.infrastructureCost()),
                "Инфраструктура / цикл", "minecraft:anvil", NEGATIVE);

            metric(g, left, y + 84, w, "НАСЕЛЕНИЕ",
                format(snapshot.population()),
                "Жители страны", "minecraft:player_head", ACCENT);

            metric(g, left + w + gap, y + 84, w, "ДОЛГ",
                "$" + formatDouble(snapshot.moneyDebt()),
                "Денежная задолженность", "minecraft:redstone",
                snapshot.moneyDebt() > 0 ? NEGATIVE : POSITIVE);

            y += 170;
        } else {
            int w = (overviewWidth - gap * 3) / 4;
            metric(g, left, y, w, "КАЗНА",
                "$" + formatDouble(snapshot.treasury()),
                "Государственные деньги", "minecraft:emerald", GOLD);
            metric(g, left + w + gap, y, w, "СОДЕРЖАНИЕ",
                "-$" + formatDouble(snapshot.infrastructureCost()),
                "Инфраструктура / цикл", "minecraft:anvil", NEGATIVE);
            metric(g, left + 2 * (w + gap), y, w, "НАСЕЛЕНИЕ",
                format(snapshot.population()),
                "Жители страны", "minecraft:player_head", ACCENT);
            metric(g, left + 3 * (w + gap), y, w, "ДОЛГ",
                "$" + formatDouble(snapshot.moneyDebt()),
                "Денежная задолженность", "minecraft:redstone",
                snapshot.moneyDebt() > 0 ? NEGATIVE : POSITIVE);
            y += 86;
        }

        y = drawAttentionPanel(g, y, left, right);

        panel(g, left, y, right, y + 104);
        g.drawString(font, "ПОЛИТИЧЕСКИЙ ПРОФИЛЬ", left + 14, y + 12, TEXT, true);
        info(g, left + 14, y + 33, right - 14, "Направление", snapshot.direction(), "minecraft:compass");
        info(g, left + 14, y + 52, right - 14, "Правление", snapshot.government(), "minecraft:iron_sword");
        info(g, left + 14, y + 71, right - 14, "Религия", snapshot.religion(), "minecraft:book");
        y += 114;

        panel(g, left, y, right, y + 104);
        ItemStack developmentIcon = itemStack("minecraft:diamond");
        if (!developmentIcon.isEmpty()) g.renderItem(developmentIcon, left + 10, y + 10);
        g.drawString(font, "РАЗВИТИЕ", left + 38, y + 12, TEXT, true);
        String developmentProgress = snapshot.developmentLevel() >= 5
            ? "Максимальный уровень"
            : format(snapshot.developmentPoints()) + " / " + format(snapshot.developmentNextThreshold()) + " очков";
        g.drawString(font,
            "Уровень " + snapshot.developmentLevel() + " • " + snapshot.developmentTitle(),
            left + 38, y + 31, TEXT, false);
        g.drawString(font, developmentProgress, left + 38, y + 46, MUTED, false);

        String perk = clipToWidth(snapshot.developmentPerk(), Math.max(100, right - left - 290));
        g.drawString(font, perk, right - 14 - font.width(perk), y + 12, GOLD, false);
        double dev = snapshot.developmentNextThreshold() <= 0 ? 1 :
            snapshot.developmentPoints() / (double) snapshot.developmentNextThreshold();
        progress(g, left + 14, y + 54, right - 14, y + 62, dev, ACCENT);
        g.drawString(font,
            snapshot.developmentLevel() >= 5
                ? "Все этапы развития открыты"
                : "Далее: " + snapshot.developmentNextTitle() + " — " + snapshot.developmentNextPerk(),
            left + 14, y + 76, MUTED, false);
        y += 114;

        // Материалы объединены в единую панель государственного склада.
        // Оба операционных раздела теперь идут в полную ширину, чтобы список ресурсов
        // не сжимался и не обрезался на широких и узких экранах.
        int workBottom = drawWorkforcePanel(g, y, left, right, mouseX, mouseY);
        y = workBottom + 10;
        int warehouseBottom = drawWarehousePanel(g, y, left, right, mouseX, mouseY);
        return warehouseBottom + 10;
    }

    private int drawWorkforcePanel(GuiGraphics g, int y, int left, int right, int mouseX, int mouseY) {
        WorkforceSector[] sectors = WorkforceSector.values();
        int headerH = 34;
        int rowH = 34;
        int bottom = workforceExpanded
            ? y + headerH + 28 + sectors.length * rowH + 8
            : y + headerH + 8;

        panel(g, left, y, right, bottom);

        boolean hoverHeader = inside(mouseX, mouseY, left + 8, y + 5, right - 8, y + headerH - 2);
        if (hoverHeader) g.fill(left + 8, y + 5, right - 8, y + headerH - 2, PANEL_3);

        g.drawString(font, "РАБОЧАЯ СИЛА", left + 14, y + 11, TEXT, true);
        g.drawString(font, workforceExpanded ? "▼" : "▶", right - 28, y + 11, ACCENT, true);
        String workforceSummary =
            format(snapshot.workingPopulation()) + " доступно • " +
            format(snapshot.employedPopulation()) + " занято • " +
            format(snapshot.unemployedPopulation()) + " без места";
        g.drawString(font, clipToWidth(workforceSummary, Math.max(120, right - left - 52)),
            left + 150, y + 11, MUTED, false);
        target(left + 8, y + 4, right - 8, y + headerH,
            () -> {
                workforceExpanded = !workforceExpanded;
                scroll = Math.min(scroll, estimatedMaxScroll());
            });

        if (!workforceExpanded) return bottom;

        int contentTop = y + headerH + 28;
        int iconX = left + 12;
        int nameX = left + 34;
        int workersRight = left + 126;
        int percentRight = left + 175;
        int bonusRight = right - 60;

        g.drawString(font, "РАБОТНИКИ", workersRight - 60, contentTop - 13, MUTED, false);
        g.drawString(font, "ДОЛЯ", percentRight - 4, contentTop - 13, MUTED, false);
        g.drawString(font, "БОНУС", bonusRight - 28, contentTop - 13, MUTED, false);

        int rowY = contentTop;
        for (int i = 0; i < sectors.length; i++) {
            WorkforceSector sector = sectors[i];
            ItemStack icon = itemStack(sector.iconItemId());
            if (!icon.isEmpty()) g.renderItem(icon, iconX, rowY + 7);

            g.drawString(font,
                clipToWidth(sector.displayName(), Math.max(60, workersRight - nameX - 4)),
                nameX, rowY + 10, TEXT, false);

            String workers = format(valueAt(snapshot.sectorWorkers(), i));
            g.drawString(font, workers,
                workersRight - font.width(workers), rowY + 10, TEXT, true);

            String share = valueAt(snapshot.sectorAllocation(), i) + "%";
            g.drawString(font, share,
                percentRight - font.width(share), rowY + 10, ACCENT, true);

            String bonus = signed(valueAt(snapshot.sectorBonuses(), i));
            g.drawString(font, bonus,
                bonusRight - font.width(bonus), rowY + 10,
                valueAt(snapshot.sectorBonuses(), i) >= 0 ? POSITIVE : NEGATIVE, true);

            final WorkforceSector targetSector = sector;
            miniButton(g, right - 54, rowY + 6, "-", mouseX, mouseY,
                () -> changeWorkforce(targetSector, -5));
            miniButton(g, right - 28, rowY + 6, "+", mouseX, mouseY,
                () -> changeWorkforce(targetSector, 5));

            rowY += rowH;
        }

        return bottom;
    }

    private int drawAttentionPanel(GuiGraphics g, int y, int left, int right) {
        List<String> issues = new ArrayList<>();

        int debtMaterials = 0;
        for (int i = 0; i < materialCount(); i++) {
            if (valueAt(snapshot.materialDebt(), i) > 0) debtMaterials++;
        }
        if (debtMaterials > 0) {
            issues.add("Материальный долг: " + debtMaterials + " поз.");
        }
        if (snapshot.moneyDebt() > 0.0001D) {
            issues.add("Денежный долг: $" + formatDouble(snapshot.moneyDebt()));
        }

        int unemployed = Math.max(0, snapshot.unemployedPopulation());
        if (unemployed > 0) {
            issues.add("Без работы: " + format(unemployed) + " жителей");
        }

        if ("Не выбрано".equals(snapshot.direction())) issues.add("Не выбрано экономическое направление");
        if ("Не выбрано".equals(snapshot.government())) issues.add("Не выбрана форма правления");
        if ("Не выбрано".equals(snapshot.religion())) issues.add("Не выбрана религия");

        int lines = Math.min(4, issues.size());
        int bottom = y + (issues.isEmpty() ? 58 : 34 + lines * 18);
        panel(g, left, y, right, bottom);

        int color = issues.isEmpty() ? POSITIVE : GOLD;
        g.drawString(font, issues.isEmpty() ? "СОСТОЯНИЕ" : "ТРЕБУЕТ ВНИМАНИЯ",
            left + 14, y + 12, color, true);

        if (issues.isEmpty()) {
            g.drawString(font, "Срочных проблем по доступным показателям нет.",
                left + 14, y + 31, MUTED, false);
        } else {
            for (int i = 0; i < lines; i++) {
                g.drawString(font, "• " + clipToWidth(issues.get(i), right - left - 28),
                    left + 14, y + 31 + i * 18, TEXT, false);
            }
            if (issues.size() > lines) {
                g.drawString(font, "… и ещё " + (issues.size() - lines),
                    left + 14, y + 31 + lines * 18, MUTED, false);
                bottom += 18;
            }
        }

        return bottom + 10;
    }

    private int drawWarehousePanel(GuiGraphics g, int y, int left, int right, int mouseX, int mouseY) {
        List<Integer> materialOrder = sortedMaterialIndices();
        int rowH = 42;
        int headerH = warehouseExpanded ? 72 : 34;
        int bottom = warehouseExpanded
            ? y + headerH + Math.max(1, materialOrder.size()) * rowH + 8
            : y + headerH + 8;

        panel(g, left, y, right, bottom);

        ItemStack warehouseIcon = itemStack("minecraft:chest");
        if (!warehouseIcon.isEmpty()) g.renderItem(warehouseIcon, left + 10, y + 9);
        g.drawString(font, "ГОСУДАРСТВЕННЫЙ СКЛАД", left + 38, y + 12, TEXT, true);
        g.drawString(font, warehouseExpanded ? "▼" : "▶", right - 28, y + 12, ACCENT, true);

        int positionCount = materialCount();
        String summary = positionCount == 0
            ? "Склад пока пуст"
            : "Позиций: " + positionCount
                + " • дефицит: " + countMaterialDeficits()
                + " • долг: " + countMaterialDebts();
        g.drawString(font,
            clipToWidth(summary, Math.max(140, right - left - 64)),
            left + 150, y + 12, MUTED, false);

        target(left + 8, y + 4, right - 8, y + 30,
            () -> {
                warehouseExpanded = !warehouseExpanded;
                scroll = Math.min(scroll, estimatedMaxScroll());
            });

        if (!warehouseExpanded) {
            return bottom;
        }

        g.drawString(font, "Запасы, потребление и ресурсные обязательства", left + 14, y + 31, MUTED, false);
        g.drawString(font,
            clipToWidth("Сортировка: долг → дефицит → запас", Math.max(120, right - left - 210)),
            left + 14, y + 50, MUTED, false);

        drawButton(g, right - 154, y + 38, right - 14, y + 64,
            "ПОПОЛНИТЬ ИЗ ИНВ.",
            ACCENT_DARK, ACCENT, mouseX, mouseY,
            () -> EconomyNetwork.sendAction("warehouse_deposit_all", ""));

        int rowY = y + headerH - 4;
        for (int i : materialOrder) {
            int baseX = left + 14;
            ItemStack icon = materialIconStack(valueAt(snapshot.materialIds(), i));
            if (!icon.isEmpty()) g.renderItem(icon, baseX, rowY - 7);

            String name = clipToWidth(
                valueAt(snapshot.materialNames(), i),
                Math.max(100, right - baseX - 370)
            );
            g.drawString(font, name, baseX + 24, rowY, TEXT, false);

            double perCycle = valueAt(snapshot.materialPerCycle(), i);
            int stock = valueAt(snapshot.materialStockpile(), i);
            int debt = valueAt(snapshot.materialDebt(), i);
            int deficit = materialCycleDeficit(i);

            String flow = "На складе: " + stock + " шт. • нужно: " +
                String.format(Locale.ROOT, "%.2f/цикл", perCycle);
            g.drawString(font,
                clipToWidth(flow, Math.max(180, right - baseX - 310)),
                baseX + 24, rowY + 14, MUTED, false);

            String state;
            int stateColor;
            if (debt > 0) {
                state = "ДОЛГ " + debt;
                stateColor = NEGATIVE;
            } else if (deficit > 0) {
                state = "НЕ ХВАТАЕТ " + deficit;
                stateColor = GOLD;
            } else {
                state = "ХВАТАЕТ";
                stateColor = POSITIVE;
            }

            int stateWidth = Math.min(150, right - baseX - 200);
            g.drawString(font,
                clipToWidth(state, Math.max(90, stateWidth)),
                right - 14 - Math.min(150, Math.max(90, font.width(state))),
                rowY + 7, stateColor, true);

            rowY += rowH;
        }

        if (materialCount() == 0) {
            g.drawString(font,
                "Сейчас государству не назначено ни одного материального обязательства.",
                left + 14, rowY, MUTED, false);
        }

        return bottom;
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

            int bx = right - 98;
            int textWidth = Math.max(80, bx - (left + 18) - 12);
            g.drawString(font, clipToWidth(display, textWidth), left + 18, rowY + 6, selected ? TEXT : MUTED, selected);
            g.drawString(font, clipToWidth(reformCost(action), textWidth), left + 18, rowY + 22,
                selected ? POSITIVE : MUTED, false);
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

    private void openCancelDialog(int orderId) {
        cancelOrderId = orderId;
        modalAction = "trade_cancel";
        modalCommand = null;
        modalTitle = "Отменить заказ #" + orderId;
        tradeCancelReason.setValue("");
        updateTradeInputVisibility();
        tradeCancelReason.setFocused(true);
    }

    private int drawEffects(GuiGraphics g, int y, int left, int right) {
        y = title(g, left, y, "ЭФФЕКТЫ", "Итоговые модификаторы страны");
        g.drawString(font, "Зелёный = улучшает результат • красный = ухудшает результат",
            left, y - 7, MUTED, false);

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
            if (Math.abs(value) < 0.0001) continue;
            boolean beneficial = isEffectPositive(snapshot.modifierNames()[i], value);
            if (beneficial != positive) continue;

            ItemStack effectIcon = itemStack(beneficial ? "minecraft:emerald" : "minecraft:redstone");
            if (!effectIcon.isEmpty()) g.renderItem(effectIcon, x, y + row * 30 - 7);
            String valueText = signed(value);
            int nameRight = right - font.width(valueText) - 10;
            g.drawString(font,
                clipToWidth(snapshot.modifierNames()[i], Math.max(60, nameRight - (x + 22))),
                x + 22, y + row * 30, TEXT, false);
            g.drawString(font, valueText, right - font.width(valueText),
                y + row * 30, beneficial ? POSITIVE : NEGATIVE, true);
            row++;
        }

        if (row == 0) {
            g.drawString(font, "Нет активных эффектов.", x, y, MUTED, false);
        }
    }

    private int drawCities(GuiGraphics g, int y, int left, int right, int mouseX, int mouseY) {
        y = title(g, left, y, "ТОП СТРАН", "Единый рейтинг PoliticsMod и автономных государств Millénaire");

        int rows = snapshot.cityNames().length;
        if (rows == 0) {
            panel(g, left, y, right, y + 62);
            g.drawString(font, "Государств пока нет.", left + 14, y + 22, MUTED, false);
            return y + 70;
        }

        int dropdownBottom = y + 31;
        panel(g, left, y, right, dropdownBottom);
        g.drawString(font, "СТРАНА", left + 14, y + 10, MUTED, true);

        int selected = Math.max(0, Math.min(selectedCountryIndex, rows - 1));
        selectedCountryIndex = selected;
        String selectedName = valueAt(snapshot.cityNames(), selected);
        String selectedType = valueAt(snapshot.cityCountries(), selected);

        drawButton(
            g,
            left + 96,
            y + 3,
            right - 14,
            y + 27,
            selectedName + " • " + selectedType,
            PANEL_3,
            ACCENT,
            mouseX,
            mouseY,
            () -> countryDropdownOpen = !countryDropdownOpen
        );

        if (countryDropdownOpen) {
            int optionH = 24;
            int maxOptions = Math.min(rows, 10);
            int boxBottom = dropdownBottom + maxOptions * optionH + 6;
            panel(g, left + 96, dropdownBottom + 2, right - 14, boxBottom);
            for (int i = 0; i < maxOptions; i++) {
                int rowY = dropdownBottom + 6 + i * optionH;
                final int index = i;
                boolean hover = inside(mouseX, mouseY, left + 104, rowY, right - 22, rowY + 20);
                if (hover) g.fill(left + 104, rowY, right - 22, rowY + 20, PANEL_2);
                g.drawString(
                    font,
                    (i + 1) + ". " + clipToWidth(valueAt(snapshot.cityNames(), i) + " • " + valueAt(snapshot.cityCountries(), i), right - left - 150),
                    left + 110,
                    rowY + 5,
                    i == selected ? TEXT : MUTED,
                    i == selected
                );
                target(left + 104, rowY, right - 22, rowY + 20, () -> {
                    selectedCountryIndex = index;
                    countryDropdownOpen = false;
                });
            }
            y = boxBottom + 10;
        } else {
            y += 42;
        }

        int rankY = y;
        for (int i = 0; i < rows; i++) {
            int bottom = rankY + 92;
            panel(g, left, rankY, right, bottom);

            boolean selectedRow = i == selected;
            if (selectedRow) {
                g.fill(left + 7, rankY + 7, right - 7, bottom - 7, ACCENT_DARK);
                g.fill(left + 7, rankY + 7, left + 10, bottom - 7, ACCENT);
            }

            String rank = "#" + (i + 1);
            String name = valueAt(snapshot.cityNames(), i);
            String type = valueAt(snapshot.cityCountries(), i);
            double score = parseDoubleSafe(valueAt(snapshot.cityMayors(), i));

            g.drawString(font, rank, left + 15, rankY + 13, GOLD, true);
            g.drawString(font, clipToWidth(name, 260), left + 50, rankY + 12, TEXT, true);
            drawPill(g, type, left + 50, rankY + 31,
                type.equals("Millénaire") ? 0xFF433056 : ACCENT_DARK,
                type.equals("Millénaire") ? 0xFFC77DFF : ACCENT,
                120);

            miniStatIcon(g, right - 290, rankY + 12, "Индекс", formatDouble(score), "minecraft:diamond", GOLD);
            miniStatIcon(g, right - 192, rankY + 12, "Насел.", format(valueAt(snapshot.cityPopulation(), i)), "minecraft:player_head", ACCENT);
            miniStatIcon(g, right - 94, rankY + 12, "Развитие", format(valueAt(snapshot.cityInfrastructure(), i)), "minecraft:diamond_block", POSITIVE);

            g.drawString(
                font,
                "$" + format(valueAt(snapshot.cityTreasuries(), i))
                    + "  •  Военные: " + format(valueAt(snapshot.cityTaxBlocks(), i))
                    + "  •  Готовность: " + formatDouble(
                        score <= 0 ? 0.0D : 0.0D
                    ),
                right - 290,
                rankY + 58,
                MUTED,
                false
            );

            rankY = bottom + 8;
        }

        return rankY + 4;
    }

    private int drawNews(GuiGraphics g, int y, int left, int right) {
        y = title(g, left, y, "НОВОСТИ", "Последние события мира PoliticsEconomy");

        if (snapshot.newsRows().length == 0) {
            panel(g, left, y, right, y + 72);
            g.drawString(font, "Новостей пока нет.", left + 14, y + 24, MUTED, false);
            g.drawString(font, "Когда произойдут события, они появятся здесь.", left + 14, y + 43, TEXT, false);
            return y + 82;
        }

        for (String raw : snapshot.newsRows()) {
            String[] parts = raw.split("\\|", -1);
            if (parts.length < 4) continue;

            String category = parts[1];
            String title = parts[2];
            String body = parts[3];

            int bottom = y + 78;
            panel(g, left, y, right, bottom);

            int categoryColor = switch (category) {
                case "БЕДСТВИЕ" -> NEGATIVE;
                case "ЭКОНОМИКА" -> POSITIVE;
                case "ОБЩЕСТВО" -> GOLD;
                case "ВОЙНА" -> NEGATIVE;
                default -> ACCENT;
            };

            drawPill(g, category, left + 12, y + 9, PANEL_3, categoryColor, 110);
            g.drawString(font, clipToWidth(title, right - left - 150), left + 134, y + 12, TEXT, true);
            g.drawString(font, clipToWidth(body, right - left - 28), left + 14, y + 39, MUTED, false);
            y = bottom + 8;
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

            g.drawString(font,
                clipToWidth(valueAt(snapshot.marketItemNames(), i), Math.max(100, right - left - 275)),
                left + 48, y + 12, TEXT, true);

            String marketInfo =
                "Спрос " + format(valueAt(snapshot.marketBaseDemand(), i)) +
                    " • осталось " + format(valueAt(snapshot.marketRemaining(), i)) +
                    " • продано " + format(valueAt(snapshot.marketSold(), i)) +
                    " • импорт " + format(valueAt(snapshot.marketImported(), i));
            g.drawString(font,
                clipToWidth(marketInfo, Math.max(120, right - left - 275)),
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
        String terminalStatus = snapshot.tradeTerminalSet()
            ? "Назначен • " + snapshot.tradeTerminalPosition()
            : "Не назначен";
        g.drawString(font,
            clipToWidth(terminalStatus, Math.max(120, right - 190 - (left + 38))),
            left + 38, y + 32,
            snapshot.tradeTerminalSet() ? POSITIVE : NEGATIVE, false);
        g.drawString(font,
            clipToWidth("Используется для физического входа и выхода грузов.",
                Math.max(120, right - 190 - (left + 38))),
            left + 38, y + 54, MUTED, false);

        drawButton(g, right - 160, y + 21, right - 14, y + 48,
            snapshot.tradeTerminalSet() ? "ПЕРЕНАЗНАЧИТЬ" : "НАЗНАЧИТЬ",
            snapshot.tradeTerminalSet() ? PANEL_3 : ACCENT_DARK,
            snapshot.tradeTerminalSet() ? TEXT : ACCENT,
            mouseX, mouseY, () -> sendTrade("trade_terminal_set", ""));
        y += 110;

        tradeOrderTop = y;
        boolean compactTrade = contentWidth(right, left) < 760;
        int orderBottom = y + (compactTrade ? 152 : 108);
        panel(g, left, y, right, orderBottom);

        g.drawString(font, "СОЗДАТЬ ЗАКУПКУ", left + 14, y + 11, TEXT, true);

        g.drawString(font, "Предмет", left + 14, y + 28, MUTED, false);
        if (compactTrade) {
            g.drawString(font, "Количество", left + 14, y + 68, MUTED, false);
            g.drawString(font, "Макс. цена / шт.", left + 116, y + 68, MUTED, false);
        } else {
            g.drawString(font, "Количество", left + 284, y + 28, MUTED, false);
            g.drawString(font, "Макс. цена / шт.", left + 382, y + 28, MUTED, false);
        }

        g.drawString(font,
            "Поиск по русскому названию или item ID.",
            left + 14, compactTrade ? y + 108 : y + 69, MUTED, false);

        boolean selectedItemValid = resolveSelectedTradeItemId() != null;
        int createButtonTop = compactTrade ? y + 110 : y + 68;
        drawButton(g, right - 148, createButtonTop, right - 10, createButtonTop + 27,
            "СОЗДАТЬ ЗАКАЗ",
            selectedItemValid ? ACCENT_DARK : PANEL_3,
            selectedItemValid ? ACCENT : MUTED,
            mouseX, mouseY,
            selectedItemValid
                ? () -> sendTrade("trade_order_create",
                    selectedTradeItemId + "|" + tradeAmount.getValue() + "|" + tradeMaxPrice.getValue())
                : null);

        g.drawString(font, "Деньги резервируются из казны страны.",
            left + 14, compactTrade ? y + 132 : y + 88, MUTED, false);

        y = orderBottom + 10;

        int gap = 10;
        int half = (right - left - gap) / 2;

        if (contentWidth(right, left) < 760) {
            tradeOwnOrdersTop = y;
            tradeOwnOrdersLeft = left;
            tradeOwnOrdersRight = right;
            int ownEnd = drawTradeOwnOrders(g, y, left, right, mouseX, mouseY);
            y = ownEnd + 10;

            // Open orders are a separate panel. Do not overwrite the anchor
            // used by the "Price / Batch" inputs in My Orders.
            int openEnd = drawTradeOpenOrders(g, y, left, right, mouseX, mouseY);
            y = openEnd + 10;
        } else {
            tradeOwnOrdersTop = y;
            tradeOwnOrdersLeft = left;
            tradeOwnOrdersRight = left + half;
            int ownEnd = drawTradeOwnOrders(g, y, left, left + half, mouseX, mouseY);
            int openEnd = drawTradeOpenOrders(g, y, left + half + gap, right, mouseX, mouseY);
            y = Math.max(ownEnd, openEnd) + 10;
        }

        return drawTradeShipments(g, y, left, right, mouseX, mouseY);
    }

    private int drawTradeOwnOrders(GuiGraphics g, int y, int left, int right, int mouseX, int mouseY) {
        int rowH = 84;
        int count = Math.max(1, snapshot.tradeOwnOrders().length);
        int bottom = y + 56 + count * rowH;
        panel(g, left, y, right, bottom);
        g.drawString(font, "МОИ ЗАКАЗЫ", left + 14, y + 12, TEXT, true);
        g.drawString(font, "Цена:", left + 14, y + 31, MUTED, false);
        g.drawString(font, "Партия:", left + 145, y + 31, MUTED, false);
        int rowY = y + 56;

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
            int rowTextRight = right - 110;
            g.drawString(font,
                "#" + row.id() + " • " +
                    clipToWidth(tradeItemName(row.itemId()), Math.max(90, rowTextRight - (left + 40))),
                left + 40, rowY + 8, TEXT, true);
            g.drawString(font,
                clipToWidth(
                    format(row.remaining()) + "/" + format(row.quantity()) +
                        " • max $" + row.maxPrice(),
                    Math.max(90, rowTextRight - (left + 40))
                ),
                left + 40, rowY + 26, MUTED, false);
            g.drawString(font,
                clipToWidth("Статус: " + tradeStatus(row.status()),
                    Math.max(90, rowTextRight - (left + 40))),
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
                canCancel ? () -> openCancelDialog(row.id()) : null);

            rowY += rowH;
        }
        return bottom;
    }

    private int drawTradeOpenOrders(GuiGraphics g, int y, int left, int right, int mouseX, int mouseY) {
        int rowH = 84;
        int count = Math.max(1, snapshot.tradeOpenOrders().length);
        int bottom = y + 56 + count * rowH;
        panel(g, left, y, right, bottom);
        g.drawString(font, "ДОСТУПНЫЕ ЗАКУПКИ", left + 14, y + 12, TEXT, true);
        g.drawString(font, "Можно стать поставщиком.", left + 14, y + 31, MUTED, false);
        int rowY = y + 56;

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
            int rowTextRight = right - 108;
            g.drawString(font,
                "#" + row.id() + " • " +
                    clipToWidth(tradeItemName(row.itemId()), Math.max(90, rowTextRight - (left + 40))),
                left + 40, rowY + 8, TEXT, true);
            g.drawString(font,
                "Покупатель: " +
                    clipToWidth(row.buyer(), Math.max(90, rowTextRight - (left + 40))),
                left + 40, rowY + 26, MUTED, false);
            g.drawString(font,
                clipToWidth(
                    "Нужно " + format(row.remaining()) + " • максимум $" + row.maxPrice() + "/шт",
                    Math.max(90, rowTextRight - (left + 40))
                ),
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
        int bottom = y + 56 + count * rowH;
        panel(g, left, y, right, bottom);
        g.drawString(font, "ЛОГИСТИКА", left + 14, y + 12, TEXT, true);
        g.drawString(font, "Логист получает оплату после физической доставки.",
            left + 14, y + 31, MUTED, false);
        int rowY = y + 56;

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

            boolean canHaul = "WAITING_LOGISTICS".equals(row.status()) &&
                !snapshot.countryName().equals(row.seller()) &&
                !snapshot.countryName().equals(row.buyer());

            int rowTextRight = canHaul ? right - 128 : right - 14;
            g.drawString(font,
                clipToWidth(
                    "#" + row.id() + " • заказ #" + row.orderId() + " • " + tradeItemName(row.itemId()),
                    Math.max(120, rowTextRight - (left + 40))
                ),
                left + 40, rowY + 8, TEXT, true);
            g.drawString(font,
                clipToWidth(
                    row.seller() + " → " + row.buyer() + " • ×" + format(row.quantity()),
                    Math.max(120, rowTextRight - (left + 40))
                ),
                left + 40, rowY + 26, MUTED, false);
            g.drawString(font,
                clipToWidth(
                    tradeStatus(row.status()) + " • " + row.originChunk() + " → " + row.destinationChunk(),
                    Math.max(120, rowTextRight - (left + 40))
                ),
                left + 40, rowY + 44, MUTED, false);

            if (canHaul) {
                drawButton(g, right - 116, rowY + 28, right - 14, rowY + 53,
                    "ВЗЯТЬ ГРУЗ", ACCENT_DARK, ACCENT, mouseX, mouseY,
                    () -> sendTrade("trade_shipment_haul", String.valueOf(row.id())));
            }

            rowY += rowH;
        }

        return bottom;
    }

    private int drawTradeHistory(GuiGraphics g, int y, int left, int right, int mouseX, int mouseY) {
        y = title(g, left, y, "ИСТОРИЯ ЗАКАЗОВ", "Завершённые и отменённые контракты государства");

        tradeHistoryTop = y;
        layoutHistorySearch();
        g.drawString(font, "Поиск по ID, предмету, стране или продавцу", left + 238, y + 6, MUTED, false);
        y += 38;

        String query = tradeHistorySearch.getValue().trim().toLowerCase(Locale.ROOT);
        int shown = 0;

        for (String raw : snapshot.tradeHistory()) {
            TradeHistoryRow row = parseTradeHistory(raw);
            if (row == null) continue;

            String haystack = (row.id() + " " + row.itemId() + " " + row.itemName() + " " +
                row.buyer() + " " + row.seller() + " " + row.reason()).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) continue;

            int bottom = y + 110;
            panel(g, left, y, right, bottom);

            ItemStack icon = itemStack(row.itemId());
            if (!icon.isEmpty()) g.renderItem(icon, left + 12, y + 13);

            g.drawString(font,
                "#" + row.id() + " • " + clipToWidth(row.itemName(), 220),
                left + 40, y + 11, TEXT, true);

            int infoLeft = left + 40;
            g.drawString(font,
                "Заказал: " + clipToWidth(row.buyer(), 190),
                infoLeft, y + 31, MUTED, false);
            g.drawString(font,
                "Выполнял: " + clipToWidth(row.seller(), 190),
                infoLeft, y + 49, MUTED, false);
            g.drawString(font,
                "Количество: " + format(row.quantity()) + " • максимум $" + row.maxPrice() + "/шт",
                infoLeft, y + 67, MUTED, false);

            int statusColor = "CANCELLED".equals(row.status()) ? NEGATIVE : POSITIVE;
            String status = tradeStatus(row.status());
            g.drawString(font, "Статус: " + status,
                right - 190, y + 12, statusColor, true);

            if ("CANCELLED".equals(row.status())) {
                g.drawString(font, "Причина:",
                    right - 190, y + 34, MUTED, false);
                g.drawString(font,
                    clipToWidth(row.reason().isBlank() ? "не указана" : row.reason(), 176),
                    right - 190, y + 49, NEGATIVE, false);
            } else {
                g.drawString(font, "Причина отмены:", right - 190, y + 34, MUTED, false);
                g.drawString(font, "— заказ выполнен", right - 190, y + 49, POSITIVE, false);
            }

            y = bottom + 8;
            shown++;
        }

        if (shown == 0) {
            panel(g, left, y, right, y + 64);
            g.drawString(font,
                query.isBlank() ? "История заказов пока пуста." : "По этому запросу ничего не найдено.",
                left + 14, y + 24, MUTED, false);
            y += 72;
        }

        return y + 4;
    }

    private int drawResearch(GuiGraphics g, int y, int left, int right, int mouseX, int mouseY) {
        y = title(g, left, y, "ИССЛЕДОВАНИЯ", "Технологии выбранного экономического направления");
        panel(g, left, y, right, y + 76);
        ItemStack rpIcon = itemStack("minecraft:knowledge_book");
        if (!rpIcon.isEmpty()) g.renderItem(rpIcon, left + 12, y + 12);
        g.drawString(font, "ОЧКИ ИССЛЕДОВАНИЙ", left + 42, y + 12, TEXT, true);
        g.drawString(font, format(snapshot.researchPoints()), left + 42, y + 31, GOLD, true);
        g.drawString(font, "Очки появляются за успешные экономические циклы.",
            left + 42, y + 50, MUTED, false);
        y += 88;

        int rowH = 92;
        String[] rows = snapshot.researchRows();
        for (int i = 0; i < rows.length; i++) {
            String[] parts = rows[i].split("\\|", -1);
            if (parts.length < 8) continue;
            String id=parts[0], name=parts[1], description=parts[2], status=parts[3];
            int rpCost=parseIntSafe(parts[4]), moneyCost=parseIntSafe(parts[5]), minLevel=parseIntSafe(parts[6]);
            panel(g, left, y, right, y + rowH - 6);
            boolean hover=inside(mouseX,mouseY,left,y,right,y+rowH-6);
            if(hover && !"COMPLETED".equals(status))g.fill(left+1,y+1,right-1,y+rowH-7,PANEL_2);
            ItemStack icon=itemStack(parts[7]);
            if(!icon.isEmpty())g.renderItem(icon,left+12,y+12);
            g.drawString(font,"I-"+(i+1),left+42,y+10,MUTED,true);
            g.drawString(font,clipToWidth(name,Math.max(160,right-left-250)),left+42,y+26,TEXT,true);
            String stateText; int stateColor;
            if("COMPLETED".equals(status)){stateText="ИССЛЕДОВАНО";stateColor=POSITIVE;}
            else if("AVAILABLE".equals(status)){stateText="ДОСТУПНО";stateColor=ACCENT;}
            else if("LEVEL".equals(status)){stateText="НУЖЕН УРОВЕНЬ "+minLevel;stateColor=MUTED;}
            else if("PREREQUISITE".equals(status)){stateText="НУЖНО ПРЕДЫДУЩЕЕ";stateColor=GOLD;}
            else if("POINTS".equals(status)){stateText="НУЖНЫ ОЧКИ";stateColor=GOLD;}
            else if("MONEY".equals(status)){stateText="НЕДОСТАТОЧНО ДЕНЕГ";stateColor=NEGATIVE;}
            else{stateText="ЗАБЛОКИРОВАНО";stateColor=MUTED;}
            g.drawString(font,stateText,right-170,y+12,stateColor,true);
            g.drawString(font,clipToWidth(description,Math.max(220,right-left-250)),left+42,y+46,MUTED,false);
            g.drawString(font,"Стоимость: "+rpCost+" очк. • $"+moneyCost+" • уровень "+minLevel,left+42,y+64,MUTED,false);
            if("AVAILABLE".equals(status)){
                drawButton(g,right-154,y+42,right-14,y+68,"ИССЛЕДОВАТЬ",ACCENT_DARK,ACCENT,mouseX,mouseY,
                    () -> EconomyNetwork.sendAction("research",id));
            }
            y+=rowH;
        }
        return y + 8;
    }

    private int drawDebts(GuiGraphics g, int y, int left, int right, int mouseX, int mouseY) {
        y = title(g, left, y, "ДОЛГИ", "Обязательства государства перед экономической системой");

        int moneyBottom = y + 92;
        panel(g, left, y, right, moneyBottom);
        ItemStack moneyIcon = itemStack("minecraft:emerald");
        if (!moneyIcon.isEmpty()) g.renderItem(moneyIcon, left + 12, y + 11);
        g.drawString(font, "ДЕНЕЖНЫЙ ДОЛГ", left + 40, y + 12, TEXT, true);
        g.drawString(font, "$" + formatDouble(snapshot.moneyDebt()),
            left + 40, y + 34,
            snapshot.moneyDebt() > 0 ? NEGATIVE : POSITIVE, true);
        g.drawString(font,
            snapshot.moneyDebt() > 0
                ? "Денежная задолженность требует покрытия."
                : "Денежных долгов нет.",
            left + 40, y + 54, MUTED, false);
        y = moneyBottom + 10;

        int debtRows = Math.max(1, materialDebtCount());
        int bottom = y + 60 + debtRows * 48 + 8;
        panel(g, left, y, right, bottom);
        ItemStack debtIcon = itemStack("minecraft:iron_ingot");
        if (!debtIcon.isEmpty()) g.renderItem(debtIcon, left + 12, y + 11);
        g.drawString(font, "МАТЕРИАЛЬНЫЕ ДОЛГИ", left + 40, y + 12, TEXT, true);
        g.drawString(font, "Эти предметы нужны для содержания инфраструктуры.",
            left + 14, y + 31, MUTED, false);

        int rowY = y + 58;
        int found = 0;
        for (int i = 0; i < materialCount(); i++) {
            int debt = valueAt(snapshot.materialDebt(), i);
            if (debt <= 0) continue;

            ItemStack icon = materialIconStack(valueAt(snapshot.materialIds(), i));
            if (!icon.isEmpty()) g.renderItem(icon, left + 12, rowY - 7);

            g.drawString(font,
                clipToWidth(valueAt(snapshot.materialNames(), i), Math.max(100, right - left - 150)),
                left + 36, rowY, TEXT, false);
            g.drawString(font, "Должно: " + debt,
                right - 110, rowY, NEGATIVE, true);
            rowY += 48;
            found++;
        }

        if (found == 0) {
            g.drawString(font, "Материальных долгов нет.", left + 14, rowY, POSITIVE, false);
        }

        return bottom + 10;
    }

    private void layoutHistorySearch() {
        if (tradeHistorySearch == null) return;
        tradeHistorySearch.setX(contentLeft());
        tradeHistorySearch.setY(tradeHistoryTop);
        tradeHistorySearch.setWidth(Math.min(220, Math.max(160, width - contentLeft() - 300)));
    }

    private int materialDebtCount() {
        int count = 0;
        for (int i = 0; i < materialCount(); i++) {
            if (valueAt(snapshot.materialDebt(), i) > 0) count++;
        }
        return count;
    }

    private int modalHeight(boolean cancelDialog) {
        return cancelDialog ? 238 : 224;
    }

    private int countMaterialDebts() {
        int count = 0;
        for (int i = 0; i < materialCount(); i++) {
            if (valueAt(snapshot.materialDebt(), i) > 0) count++;
        }
        return count;
    }

    private int countMaterialDeficits() {
        int count = 0;
        for (int i = 0; i < materialCount(); i++) {
            if (materialCycleDeficit(i) > 0) count++;
        }
        return count;
    }

    private void drawModal(GuiGraphics g, int mouseX, int mouseY) {
        boolean cancelDialog = "trade_cancel".equals(modalAction);
        g.fill(0, 0, width, height, 0xFF000000);

        int w = Math.min(cancelDialog ? 600 : 580, width - 32);
        int h = modalHeight(cancelDialog);
        int left = (width - w) / 2;
        int top = (height - h) / 2;

        panel(g, left, top, left + w, top + h);

        String iconId = cancelDialog ? "minecraft:barrier"
            : "direction".equals(modalAction) ? "minecraft:compass"
            : "government".equals(modalAction) ? "minecraft:iron_sword"
            : "minecraft:book";

        ItemStack dialogIcon = itemStack(iconId);
        if (!dialogIcon.isEmpty()) g.renderItem(dialogIcon, left + 14, top + 13);

        g.drawString(font,
            cancelDialog ? "ПОДТВЕРЖДЕНИЕ ОТМЕНЫ" : "ПОДТВЕРЖДЕНИЕ РЕФОРМЫ",
            left + 44, top + 17, MUTED, true);
        g.drawString(font, clipToWidth(modalTitle, w - 68),
            left + 44, top + 39, TEXT, true);

        if (cancelDialog) {
            g.drawString(font, "Игрок должен указать причину отмены заказа.",
                left + 18, top + 68, MUTED, false);
            g.drawString(font, "Причина", left + 18, top + 91, TEXT, true);
            tradeCancelReason.setX(left + 18);
            tradeCancelReason.setY(top + 108);
            tradeCancelReason.setWidth(w - 36);
            tradeCancelReason.visible = true;
        } else {
            boolean first = switch (modalAction) {
                case "direction" -> "Не выбрано".equals(snapshot.direction());
                case "government" -> "Не выбрано".equals(snapshot.government());
                default -> "Не выбрано".equals(snapshot.religion());
            };

            String description = first
                ? "Первый выбор данного параметра бесплатен."
                : "Это полноценная реформа. Сервер проверит деньги и материалы.";

            int textY = top + 66;
            for (String line : wrapText(description, w - 36)) {
                g.drawString(font, line, left + 18, textY, MUTED, false);
                textY += 12;
            }

            String cost = first
                ? "БЕСПЛАТНО"
                : CountryReformCostTable.summary(
                    modalAction, false, snapshot.population(), snapshot.developmentLevel());

            g.drawString(font, "СТОИМОСТЬ", left + 18, textY + 4, MUTED, true);
            textY += 20;

            int maxCostWidth = w - 36;
            for (String line : wrapText(cost, maxCostWidth)) {
                g.drawString(font, line, left + 18, textY, first ? POSITIVE : GOLD, true);
                textY += 12;
            }
        }

        int buttonY = top + h - 42;
        drawButtonVisual(g, left + w - 196, buttonY, left + w - 104, buttonY + 27,
            "ОТМЕНА", PANEL_3, TEXT, mouseX, mouseY);
        drawButtonVisual(g, left + w - 95, buttonY, left + w - 18, buttonY + 27,
            cancelDialog ? "ОТМЕНИТЬ" : "ПОДТВЕРДИТЬ",
            cancelDialog ? NEGATIVE_DARK : ACCENT_DARK,
            cancelDialog ? NEGATIVE : ACCENT, mouseX, mouseY);
    }

    private void closeModal() {
        modalAction = null;
        modalCommand = null;
        modalTitle = null;
        cancelOrderId = null;
        if (tradeCancelReason != null) {
            tradeCancelReason.setValue("");
            tradeCancelReason.setFocused(false);
        }
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
        g.drawString(font, clipToWidth(heading, 320), left, y + 1, TEXT, true);
        g.drawString(font, clipToWidth(subtitle, Math.max(180, width - left - 190)), left, y + 18, MUTED, false);
        return y + 40;
    }

    private void metric(GuiGraphics g, int x, int y, int w, String title, String value, String subtitle, String iconId, int accent) {
        panel(g, x, y, x + w, y + 76);
        g.fill(x, y, x + 3, y + 76, accent);
        ItemStack icon = itemStack(iconId);
        if (!icon.isEmpty()) g.renderItem(icon, x + 9, y + 11);
        g.drawString(font, title, x + 34, y + 10, MUTED, true);
        g.drawString(font, value, x + 34, y + 28, TEXT, true);
        g.drawString(font, clipToWidth(subtitle, Math.max(60, w - 46)), x + 34, y + 49, MUTED, false);
    }

    private void info(GuiGraphics g, int x, int y, int right, String name, String value, String iconId) {
        ItemStack icon = itemStack(iconId);
        if (!icon.isEmpty()) g.renderItem(icon, x, y - 3);
        int textX = x + 24;
        g.drawString(font, name, textX, y, MUTED, false);
        String v = clipToWidth(value, Math.max(80, right - textX - 12));
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

    private void drawPill(GuiGraphics g, String text, int x, int y, int fill, int accent, int maxWidth) {
        if (text == null || text.isBlank() || "Не выбрано".equals(text)) return;
        String value = clipToWidth(text, Math.max(40, maxWidth - 14));
        int w = Math.min(maxWidth, font.width(value) + 14);
        g.fill(x, y, x + w, y + 17, fill);
        outline(g, x, y, x + w, y + 17, accent);
        g.drawString(font, value, x + 7, y + 4, accent, true);
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
            boolean cancelDialog = "trade_cancel".equals(modalAction);
            int w = Math.min(cancelDialog ? 600 : 580, width - 32);
            int h = modalHeight(cancelDialog);
            int left = (width - w) / 2;
            int top = (height - h) / 2;
            int buttonY = top + h - 42;

            if (cancelDialog && tradeCancelReason != null &&
                inside(mouseX, mouseY, left + 18, top + 108, left + w - 18, top + 140)) {
                tradeCancelReason.mouseClicked(mouseX, mouseY, button);
                return true;
            }

            if (inside(mouseX, mouseY, left + w - 196, buttonY, left + w - 104, buttonY + 27)) {
                closeModal();
                return true;
            }

            if (inside(mouseX, mouseY, left + w - 95, buttonY, left + w - 18, buttonY + 27)) {
                if (cancelDialog) {
                    String reason = tradeCancelReason.getValue().trim();
                    if (cancelOrderId != null && !reason.isBlank()) {
                        EconomyNetwork.sendAction(
                            "trade_order_cancel",
                            cancelOrderId + "|" + reason
                        );
                        closeModal();
                    }
                } else {
                    EconomyNetwork.sendAction(modalAction, modalCommand);
                    closeModal();
                }
                return true;
            }

            if (cancelDialog && tradeCancelReason != null) {
                tradeCancelReason.setFocused(true);
            }
            return true;
        }

        if ((page == Page.TRADE || page == Page.TRADE_HISTORY) &&
            super.mouseClicked(mouseX, mouseY, button)) {
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
        if ("trade_cancel".equals(modalAction) && tradeCancelReason != null) {
            return tradeCancelReason.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private double estimatedMaxScroll() {
        int y = contentTop();
        int end;
        switch (page) {
            case OVERVIEW -> {
                int overviewWidth = width - contentLeft() - 16;
                int metrics = overviewWidth < 760 ? 170 : 86;
                int attention = 58;
                int workforce = workforceExpanded
                    ? 34 + 28 + WorkforceSector.values().length * 34 + 8
                    : 42;
                int warehouse = warehouseExpanded
                    ? 72 + Math.max(1, materialCount()) * 42 + 8
                    : 42;
                int total = 40 + metrics + attention + 10 + 114 + 114 + 10
                    + workforce + 10 + warehouse + 10;
                end = y + total;
            }
            case COUNTRY -> end = y + 40 +
                33 + CountryDirection.values().length * 42 +
                10 + 33 + GovernmentType.values().length * 42 +
                10 + 33 + ReligionType.values().length * 42 + 10;
            case EFFECTS -> end = y + 40 + 38 +
                Math.max(1, Math.max(effectCount(true), effectCount(false))) * 30 + 22;
            case CITIES -> end = y + 40 + Math.max(1, snapshot.cityNames().length) * 102 + 10;
            case MARKET -> end = y + 40 + 88 + Math.max(1, snapshot.marketItemIds().length) * 77 + 10;
            case TRADE_HISTORY -> end = y + 40 + 38 + Math.max(1, snapshot.tradeHistory().length) * 118 + 10;
            case DEBTS -> end = y + 40 + 102 + 70 + Math.max(1, materialDebtCount()) * 48 + 20;
            case RESEARCH -> end = y + 40 + 76 + 12 + Math.max(1, snapshot.researchRows().length) * 92 + 8;
            case TRADE -> {
                int tradeWidth = width - contentLeft() - 16;
                int ownHeight = 56 + Math.max(1, snapshot.tradeOwnOrders().length) * 84;
                int openHeight = 56 + Math.max(1, snapshot.tradeOpenOrders().length) * 84;
                int shipmentHeight = 56 + Math.max(1, snapshot.tradeShipments().length) * 84;

                if (tradeWidth < 760) {
                    end = y + 40 + 110 + 152
                        + ownHeight + 10
                        + openHeight + 10
                        + shipmentHeight + 10;
                } else {
                    end = y + 40 + 110 + 108
                        + Math.max(ownHeight, openHeight) + 10
                        + shipmentHeight + 10;
                }
            }
            default -> end = y;
        }
        return Math.max(0, end - (height - 12));
    }

    private int contentLeft() {
        return 176;
    }

    private static int contentWidth(int right, int left) {
        return right - left;
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
        for (int i = 0; i < snapshot.modifierValues().length; i++) {
            double value = valueAt(snapshot.modifierValues(), i);
            if (Math.abs(value) < 0.0001) continue;
            if (isEffectPositive(valueAt(snapshot.modifierNames(), i), value) == positive) count++;
        }
        return count;
    }

    private boolean isEffectPositive(String name, double value) {
        if (Math.abs(value) < 0.0001) return false;

        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        boolean lowerIsBetter =
            normalized.contains("содержание")
                || normalized.contains("расход")
                || normalized.contains("потребление")
                || normalized.contains("комиссия")
                || normalized.contains("стоимость");

        return lowerIsBetter ? value < 0 : value > 0;
    }

    private int materialCycleDeficit(int index) {
        double perCycle = valueAt(snapshot.materialPerCycle(), index);
        int stock = valueAt(snapshot.materialStockpile(), index);
        int needed = Math.max(0, (int) Math.ceil(perCycle - 1.0E-9D));
        return Math.max(0, needed - stock);
    }

    private List<Integer> sortedMaterialIndices() {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < materialCount(); i++) {
            order.add(i);
        }

        order.sort(
            Comparator
                .comparingInt((Integer i) -> valueAt(snapshot.materialDebt(), i) > 0 ? 0 : 1)
                .thenComparingInt(i -> materialCycleDeficit(i) > 0 ? 0 : 1)
                .thenComparingInt(i -> -valueAt(snapshot.materialDebt(), i))
                .thenComparingInt(i -> -materialCycleDeficit(i))
                .thenComparingDouble(i -> {
                    double perCycle = valueAt(snapshot.materialPerCycle(), i);
                    return perCycle <= 0 ? Double.POSITIVE_INFINITY
                        : valueAt(snapshot.materialStockpile(), i) / perCycle;
                })
                .thenComparing(i -> valueAt(snapshot.materialNames(), i), String.CASE_INSENSITIVE_ORDER)
        );

        return order;
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

    private List<String> wrapText(String value, int maxPixels) {
        List<String> lines = new ArrayList<>();
        if (value == null || value.isBlank()) {
            lines.add("");
            return lines;
        }
        if (maxPixels <= 0) {
            lines.add("");
            return lines;
        }

        StringBuilder line = new StringBuilder();
        for (String word : value.split("\s+")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (font.width(candidate) <= maxPixels) {
                line.setLength(0);
                line.append(candidate);
            } else {
                if (line.length() > 0) {
                    lines.add(line.toString());
                }
                if (font.width(word) <= maxPixels) {
                    line.setLength(0);
                    line.append(word);
                } else {
                    lines.add(clipToWidth(word, maxPixels));
                    line.setLength(0);
                }
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private String clip(String value, int maxChars) {
        if (value == null) return "";
        if (value.length() <= maxChars) return value;
        return value.substring(0, Math.max(0, maxChars - 1)) + "…";
    }

    private String clipToWidth(String value, int maxPixels) {
        if (value == null) return "";
        if (maxPixels <= 0) return "";
        if (font.width(value) <= maxPixels) return value;

        String ellipsis = "…";
        int limit = maxPixels - font.width(ellipsis);
        if (limit <= 0) return ellipsis;

        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end)) > limit) {
            end--;
        }
        return end <= 0 ? ellipsis : value.substring(0, end) + ellipsis;
    }

    private static int formatSafe(int value) {
        return Math.max(0, value);
    }

    private static String format(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static double parseDoubleSafe(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
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

    private static int parseIntSafe(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return 0; }
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

    private static TradeHistoryRow parseTradeHistory(String raw) {
        String[] p = raw.split("\\|", -1);
        if (p.length != 10) return null;
        try {
            return new TradeHistoryRow(
                Integer.parseInt(p[0]), p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                p[4], p[5], p[6], p[7], Long.parseLong(p[8]), Integer.parseInt(p[9])
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record TradeItemOption(String id, String name) {}

    private record TradeHistoryRow(
        int id, String itemId, int quantity, int maxPrice,
        String buyer, String seller, String status, String reason,
        long createdAt, int agreedPrice
    ) {
        String itemName() {
            return tradeItemName(itemId);
        }
    }

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