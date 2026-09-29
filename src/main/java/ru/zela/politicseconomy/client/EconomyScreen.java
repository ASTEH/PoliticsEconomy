package ru.zela.politicseconomy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import ru.zela.politicseconomy.country.CountryDirection;
import ru.zela.politicseconomy.country.CountryReformCostTable;
import ru.zela.politicseconomy.country.GovernmentType;
import ru.zela.politicseconomy.country.ReligionType;
import ru.zela.politicseconomy.country.WorkforceSector;
import ru.zela.politicseconomy.network.EconomyNetwork;
import ru.zela.politicseconomy.network.EconomySnapshotPayload;

import java.util.Locale;

/**
 * Native Minecraft economy dashboard.
 *
 * No external UI framework is used here on purpose:
 * - Minecraft owns the screen lifecycle;
 * - GuiGraphics renders vanilla item icons directly;
 * - server snapshots update this screen in place;
 * - scrolling, hover and transitions are deterministic and cheap.
 */
public final class EconomyScreen extends Screen {
    private static final int BG = 0xFF070B10;
    private static final int SHEET = 0xFF0E141C;
    private static final int PANEL = 0xFF141D28;
    private static final int PANEL_2 = 0xFF1B2734;
    private static final int PANEL_3 = 0xFF0D151F;
    private static final int LINE = 0xFF2B3948;
    private static final int TEXT = 0xFFE7EBEF;
    private static final int MUTED = 0xFF8A99A8;
    private static final int GOLD = 0xFFC7AA5A;
    private static final int STEEL = 0xFF7890AA;
    private static final int SUCCESS = 0xFF67A184;
    private static final int WARNING = 0xFFC89C4E;
    private static final int DANGER = 0xFFC85D5D;

    private static final int PANEL_W = 1000;
    private static final int PANEL_H = 700;
    private static final int NAV_W = 155;
    private static final int HEADER_H = 66;

    private EconomySnapshotPayload snapshot;
    private int activePage = 0;
    private double scrollOffset = 0.0D;
    private double maxScroll = 0.0D;

    private long openedAt;
    private long statusUntil;
    private String statusText = "";
    private int statusColor = TEXT;

    private String confirmAction;
    private String confirmCommand;
    private String confirmTitle;
    private boolean confirmFirstChoice;

    private int panelLeft;
    private int panelTop;
    private int contentLeft;
    private int contentTop;
    private int contentRight;
    private int contentBottom;

    public EconomyScreen(EconomySnapshotPayload snapshot) {
        super(Component.literal("Politics Economy"));
        this.snapshot = snapshot;
        this.openedAt = System.currentTimeMillis();
    }

    public void applySnapshot(EconomySnapshotPayload payload) {
        this.snapshot = payload;
        long updatedAt = System.currentTimeMillis();
        this.statusText = "Данные обновлены";
        this.statusColor = SUCCESS;
        this.statusUntil = updatedAt + 1400L;
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        updateLayout();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateLayout();

        graphics.fill(0, 0, width, height, BG);
        drawBackdrop(graphics);

        float progress = Mth.clamp(
            (System.currentTimeMillis() - openedAt) / 240.0F,
            0.0F,
            1.0F
        );
        float eased = 1.0F - (float) Math.pow(1.0F - progress, 3.0F);
        int drawTop = panelTop + Math.round((1.0F - eased) * 18.0F);

        int alpha = (int) Mth.lerp(eased, 0.0F, 255.0F);
        int bgColor = withAlpha(SHEET, alpha);
        graphics.fill(panelLeft, drawTop, panelLeft + PANEL_W, drawTop + PANEL_H, bgColor);

        drawTopBar(graphics, mouseX, mouseY, alpha, drawTop);
        drawNavigation(graphics, mouseX, mouseY, alpha, drawTop);

        contentTop = drawTop + HEADER_H + 6;
        contentBottom = drawTop + PANEL_H - 18;
        contentLeft = panelLeft + NAV_W + 14;
        contentRight = panelLeft + PANEL_W - 18;

        graphics.fill(
            contentLeft - 8,
            contentTop - 8,
            contentRight + 2,
            contentBottom + 2,
            withAlpha(PANEL_3, alpha)
        );

        graphics.enableScissor(contentLeft, contentTop, contentRight, contentBottom);
        int contentY = contentTop - (int) Math.round(scrollOffset);
        drawPage(graphics, mouseX, mouseY, contentY, alpha);
        graphics.disableScissor();

        drawScrollbar(graphics, alpha);

        if (confirmAction != null) {
            drawConfirmation(graphics, mouseX, mouseY, alpha);
        }

        drawStatus(graphics, alpha);

    }

    private void updateLayout() {
        panelLeft = (width - PANEL_W) / 2;
        panelTop = Math.max(12, (height - PANEL_H) / 2);
        contentLeft = panelLeft + NAV_W + 14;
        contentTop = panelTop + HEADER_H + 6;
        contentRight = panelLeft + PANEL_W - 18;
        contentBottom = panelTop + PANEL_H - 18;
    }

    private void drawBackdrop(GuiGraphics graphics) {
        int band = Math.max(1, height / 5);
        for (int i = 0; i < 5; i++) {
            int shade = 0xFF070B10 + (i * 0x00010101);
            graphics.fill(0, i * band, width, Math.min(height, (i + 1) * band), shade);
        }
    }

    private void drawTopBar(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        int alpha,
        int top
    ) {
        graphics.fill(
            panelLeft,
            top,
            panelLeft + PANEL_W,
            top + HEADER_H,
            withAlpha(0xFF111923, alpha)
        );

        drawString(
            graphics,
            "ГОСУДАРСТВО",
            panelLeft + 22,
            top + 15,
            TEXT,
            alpha,
            18
        );

        drawString(
            graphics,
            snapshot.countryName().toUpperCase(Locale.ROOT)
                + "  /  " + snapshot.direction(),
            panelLeft + 22,
            top + 38,
            GOLD,
            alpha,
            11
        );

        drawItem(graphics, "minecraft:player_head", panelLeft + PANEL_W - 155, top + 18, 26);
        drawString(
            graphics,
            format(snapshot.population()) + "  •  "
                + signed(snapshot.populationWorkforceModifier()),
            panelLeft + PANEL_W - 122,
            top + 27,
            STEEL,
            alpha,
            11
        );

        int closeX = panelLeft + PANEL_W - 45;
        int closeColor = inside(mouseX, mouseY, closeX, top + 14, closeX + 30, top + 46)
            ? 0xFF8A3434
            : PANEL_2;
        fillRounded(graphics, closeX, top + 14, closeX + 30, top + 46, closeColor, alpha);
        drawCentered(graphics, "×", closeX, top + 22, 30, 14, TEXT, alpha);
    }

    private void drawNavigation(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        int alpha,
        int top
    ) {
        String[] names = {"ОБЗОР", "СТРАНА", "ЭФФЕКТЫ", "ГОРОДА", "РЫНОК"};
        int[] pages = {0, 1, 2, 3, 5};

        int x = panelLeft + 14;
        int y = top + HEADER_H + 10;

        for (int i = 0; i < names.length; i++) {
            boolean selected = activePage == pages[i];
            boolean hovered = inside(mouseX, mouseY, x, y, x + NAV_W - 22, y + 38);

            int color = selected ? 0xFF273021 : hovered ? 0xFF22303F : PANEL_2;
            fillRounded(graphics, x, y, x + NAV_W - 22, y + 38, color, alpha);
            drawString(graphics, names[i], x + 12, y + 12, selected ? GOLD : TEXT, alpha, 10);

            y += 46;
        }

        if (Minecraft.getInstance().player != null
            && Minecraft.getInstance().player.isCreative()) {
            boolean selected = activePage == 4;
            boolean hovered = inside(mouseX, mouseY, x, y, x + NAV_W - 22, y + 38);
            fillRounded(
                graphics,
                x,
                y,
                x + NAV_W - 22,
                y + 38,
                selected ? 0xFF332B18 : hovered ? 0xFF2A2418 : PANEL_2,
                alpha
            );
            drawString(graphics, "АДМИН", x + 12, y + 12, GOLD, alpha, 10);
        }
    }

    private void drawPage(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        int y,
        int alpha
    ) {
        switch (activePage) {
            case 1 -> {
                y = drawCountryPage(graphics, mouseX, mouseY, y, alpha);
            }
            case 2 -> {
                y = drawEffectsPage(graphics, y, alpha);
            }
            case 3 -> {
                y = drawCitiesPage(graphics, y, alpha);
            }
            case 4 -> {
                y = drawAdminPage(graphics, y, alpha);
            }
            case 5 -> {
                y = drawMarketPage(graphics, mouseX, mouseY, y, alpha);
            }
            default -> {
                y = drawOverviewPage(graphics, mouseX, mouseY, y, alpha);
            }
        }

        int requiredHeight = y - contentTop + 24;
        maxScroll = Math.max(0.0D, requiredHeight - (contentBottom - contentTop));
        scrollOffset = Mth.clamp(scrollOffset, 0.0D, maxScroll);
    }

    private int drawOverviewPage(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        int y,
        int alpha
    ) {
        int cardW = (contentRight - contentLeft - 18) / 3;

        metricCard(graphics, contentLeft, y, cardW,
            "КАЗНА", "$" + format(snapshot.treasury()), "minecraft:emerald", GOLD, alpha);
        metricCard(graphics, contentLeft + cardW + 9, y, cardW,
            "СОДЕРЖАНИЕ",
            String.format(Locale.ROOT, "-%.2f $", snapshot.infrastructureCost()),
            "minecraft:anvil", WARNING, alpha);
        metricCard(graphics, contentLeft + (cardW + 9) * 2, y, cardW,
            "ДЕНЕЖНЫЙ ДОЛГ",
            "$" + formatDouble(snapshot.moneyDebt()),
            "minecraft:redstone",
            snapshot.moneyDebt() > 0 ? DANGER : SUCCESS,
            alpha);

        y += 88;

        panel(graphics, contentLeft, y, contentRight, y + 94, alpha);
        drawString(graphics, "ПОЛИТИЧЕСКИЙ ПРОФИЛЬ", contentLeft + 14, y + 12, TEXT, alpha, 12);
        infoLine(graphics, contentLeft + 14, y + 31, "Направление", snapshot.direction(), "minecraft:compass", alpha);
        infoLine(graphics, contentLeft + 300, y + 31, "Правление", snapshot.government(), "minecraft:iron_sword", alpha);
        infoLine(graphics, contentLeft + 14, y + 60, "Религия", snapshot.religion(), "minecraft:book", alpha);
        infoLine(graphics, contentLeft + 300, y + 60, "Население", format(snapshot.population()), "minecraft:player_head", alpha);

        y += 108;

        panel(graphics, contentLeft, y, contentRight, y + 78, alpha);
        drawString(
            graphics,
            "РАЗВИТИЕ  •  " + snapshot.developmentLevel() + "/5",
            contentLeft + 14,
            y + 12,
            TEXT,
            alpha,
            12
        );
        String progressText = snapshot.developmentLevel() >= 5
            ? "Максимальный уровень"
            : format(snapshot.developmentPoints()) + " / "
                + format(snapshot.developmentNextThreshold()) + " очков";
        drawString(graphics, progressText, contentLeft + 14, y + 36, MUTED, alpha, 10);
        drawString(graphics, snapshot.developmentPerk(), contentLeft + 280, y + 36, SUCCESS, alpha, 10);

        float progress = snapshot.developmentNextThreshold() <= 0
            ? 1.0F
            : Mth.clamp(
                snapshot.developmentPoints()
                    / (float) snapshot.developmentNextThreshold(),
                0.0F,
                1.0F
            );
        progressBar(
            graphics,
            contentLeft + 14,
            y + 57,
            contentRight - 14,
            y + 63,
            progress,
            GOLD,
            alpha
        );

        y += 92;

        panel(graphics, contentLeft, y, contentRight, y + 66, alpha);
        drawString(graphics, "РАБОЧАЯ СИЛА", contentLeft + 14, y + 11, TEXT, alpha, 12);
        drawString(
            graphics,
            format(snapshot.workingPopulation()) + " доступны  •  "
                + format(snapshot.employedPopulation()) + " заняты  •  "
                + format(snapshot.unemployedPopulation()) + " без места",
            contentLeft + 14,
            y + 34,
            snapshot.unemployedPopulation() > 0 ? WARNING : SUCCESS,
            alpha,
            10
        );
        drawRight(
            graphics,
            "Мест: " + format(snapshot.workplaceCapacity()),
            contentRight - 14,
            y + 34,
            MUTED,
            alpha,
            10
        );

        y += 78;

        for (int i = 0; i < WorkforceSector.values().length; i++) {
            WorkforceSector sector = WorkforceSector.values()[i];
            y = workforceRow(graphics, mouseX, mouseY, y, sector, i, alpha);
            y += 6;
        }

        y += 4;

        panel(graphics, contentLeft, y, contentRight, y + 70, alpha);
        drawString(graphics, "ГОСУДАРСТВЕННЫЙ СКЛАД", contentLeft + 14, y + 11, TEXT, alpha, 12);
        drawString(graphics, "Текущие запасы и ресурсные долги.", contentLeft + 14, y + 34, MUTED, alpha, 10);

        if (snapshot.materialIds().length > 0) {
            int shown = Math.min(snapshot.materialIds().length, 4);
            for (int i = 0; i < shown; i++) {
                int x = contentLeft + 14 + i * 170;
                drawItem(graphics, snapshot.materialIds()[i], x, y + 46, 18);
                drawString(
                    graphics,
                    format(valueAt(snapshot.materialStockpile(), i)),
                    x + 25,
                    y + 52,
                    valueAt(snapshot.materialDebt(), i) > 0 ? DANGER : STEEL,
                    alpha,
                    9
                );
            }
        }

        return y + 86;
    }

    private int workforceRow(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        int y,
        WorkforceSector sector,
        int index,
        int alpha
    ) {
        int allocation = valueAt(snapshot.sectorAllocation(), index);
        int workers = valueAt(snapshot.sectorWorkers(), index);
        int slots = valueAt(snapshot.workplaceSlots(), index);
        int workplaceCount = valueAt(snapshot.workplaceCounts(), index);
        double bonus = valueAt(snapshot.sectorBonuses(), index);

        panel(
            graphics,
            contentLeft,
            y,
            contentRight,
            y + 54,
            alpha
        );

        drawItem(graphics, sector.iconItemId(), contentLeft + 10, y + 10, 30);
        drawString(graphics, sector.displayName(), contentLeft + 48, y + 9, TEXT, alpha, 11);
        drawString(
            graphics,
            allocation + "%  •  " + format(workers) + "/" + format(slots)
                + " занято  •  мест " + format(workplaceCount)
                + "  •  бонус " + signed(bonus),
            contentLeft + 48,
            y + 28,
            bonus > 0.0001D ? SUCCESS : MUTED,
            alpha,
            9
        );

        int minusX = contentRight - 104;
        int plusX = contentRight - 52;

        drawActionButton(
            graphics,
            minusX,
            y + 10,
            minusX + 44,
            y + 42,
            "−",
            mouseX,
            mouseY,
            allocation > 0,
            alpha
        );
        drawActionButton(
            graphics,
            plusX,
            y + 10,
            plusX + 44,
            y + 42,
            "+",
            mouseX,
            mouseY,
            allocation < 100,
            alpha
        );

        return y + 54;
    }

    private int drawCountryPage(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        int y,
        int alpha
    ) {
        panel(graphics, contentLeft, y, contentRight, y + 70, alpha);
        drawString(graphics, "УПРАВЛЕНИЕ ГОСУДАРСТВОМ", contentLeft + 14, y + 12, TEXT, alpha, 13);
        drawString(
            graphics,
            "Первый выбор бесплатный. Повторная смена требует реформы.",
            contentLeft + 14,
            y + 38,
            MUTED,
            alpha,
            10
        );
        y += 84;

        y = choiceSection(
            graphics,
            mouseX,
            mouseY,
            y,
            "ЭКОНОМИЧЕСКОЕ НАПРАВЛЕНИЕ",
            "direction",
            alpha
        );
        y += 10;
        y = choiceSection(
            graphics,
            mouseX,
            mouseY,
            y,
            "ФОРМА ПРАВЛЕНИЯ",
            "government",
            alpha
        );
        y += 10;
        y = choiceSection(
            graphics,
            mouseX,
            mouseY,
            y,
            "РЕЛИГИЯ",
            "religion",
            alpha
        );

        return y + 12;
    }

    private int choiceSection(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        int y,
        String title,
        String action,
        int alpha
    ) {
        Object[] values = switch (action) {
            case "direction" -> CountryDirection.values();
            case "government" -> GovernmentType.values();
            default -> ReligionType.values();
        };

        int height = 36 + values.length * 50;
        panel(graphics, contentLeft, y, contentRight, y + height, alpha);
        drawString(graphics, title, contentLeft + 14, y + 10, TEXT, alpha, 12);

        int rowY = y + 30;
        for (Object value : values) {
            String display;
            String command;
            switch (value) {
                case CountryDirection direction -> {
                    display = direction.displayName();
                    command = direction.commandName();
                }
                case GovernmentType government -> {
                    display = government.displayName();
                    command = government.commandName();
                }
                case ReligionType religion -> {
                    display = religion.displayName();
                    command = religion.commandName();
                }
                default -> {
                    display = value.toString();
                    command = display;
                }
            }

            boolean selected = switch (action) {
                case "direction" -> snapshot.direction().equals(display);
                case "government" -> snapshot.government().equals(display);
                default -> snapshot.religion().equals(display);
            };
            boolean hover = inside(mouseX, mouseY, contentLeft + 10, rowY, contentRight - 10, rowY + 42);

            int rowColor = selected ? 0xFF273021 : hover ? 0xFF22303F : PANEL_2;
            fillRounded(graphics, contentLeft + 10, rowY, contentRight - 10, rowY + 42, rowColor, alpha);
            drawString(graphics, display, contentLeft + 22, rowY + 10, selected ? GOLD : TEXT, alpha, 10);

            String costText = selected
                ? "Текущий выбор"
                : isFirstChoice(action)
                    ? "Первый выбор • бесплатно"
                    : CountryReformCostTable.summary(
                        action,
                        false,
                        snapshot.population(),
                        snapshot.developmentLevel()
                    );

            drawString(graphics, costText, contentLeft + 22, rowY + 26,
                selected ? SUCCESS : MUTED, alpha, 8);

            int buttonX = contentRight - 105;
            drawActionButton(
                graphics,
                buttonX,
                rowY + 6,
                buttonX + 90,
                rowY + 36,
                selected ? "ВЫБРАНО" : "ВЫБРАТЬ",
                mouseX,
                mouseY,
                !selected,
                alpha
            );

            rowY += 50;
        }

        return y + height;
    }

    private int drawEffectsPage(
        GuiGraphics graphics,
        int y,
        int alpha
    ) {
        panel(graphics, contentLeft, y, contentRight, y + 64, alpha);
        drawString(graphics, "ЭФФЕКТЫ ГОСУДАРСТВА", contentLeft + 14, y + 12, TEXT, alpha, 13);
        drawString(
            graphics,
            "Итоговые значения всех экономических модификаторов.",
            contentLeft + 14,
            y + 36,
            MUTED,
            alpha,
            10
        );
        y += 78;

        y = effectSection(graphics, y, "ПОЛОЖИТЕЛЬНЫЕ ЭФФЕКТЫ", true, alpha);
        y += 10;
        y = effectSection(graphics, y, "ОТРИЦАТЕЛЬНЫЕ ЭФФЕКТЫ", false, alpha);

        return y + 12;
    }

    private int effectSection(
        GuiGraphics graphics,
        int y,
        String title,
        boolean positive,
        int alpha
    ) {
        int count = 0;
        for (double value : snapshot.modifierValues()) {
            if ((positive && value > 0.0001D) || (!positive && value < -0.0001D)) {
                count++;
            }
        }

        int height = 34 + Math.max(1, count) * 34;
        panel(graphics, contentLeft, y, contentRight, y + height, alpha);
        drawString(graphics, title, contentLeft + 14, y + 10, TEXT, alpha, 11);

        int rowY = y + 30;
        if (count == 0) {
            drawString(graphics, "Нет активных эффектов.", contentLeft + 14, rowY + 7, MUTED, alpha, 9);
        } else {
            for (int i = 0; i < snapshot.modifierNames().length; i++) {
                double value = valueAt(snapshot.modifierValues(), i);
                if ((positive && value <= 0.0001D) || (!positive && value >= -0.0001D)) {
                    continue;
                }
                fillRounded(
                    graphics,
                    contentLeft + 10,
                    rowY,
                    contentRight - 10,
                    rowY + 28,
                    PANEL_3,
                    alpha
                );
                drawString(
                    graphics,
                    snapshot.modifierNames()[i],
                    contentLeft + 20,
                    rowY + 9,
                    TEXT,
                    alpha,
                    9
                );
                drawRight(
                    graphics,
                    signed(value),
                    contentRight - 20,
                    rowY + 9,
                    value > 0 ? SUCCESS : DANGER,
                    alpha,
                    9
                );
                rowY += 34;
            }
        }

        return y + height;
    }

    private int drawCitiesPage(
        GuiGraphics graphics,
        int y,
        int alpha
    ) {
        panel(graphics, contentLeft, y, contentRight, y + 62, alpha);
        drawString(graphics, "ГОРОДА СЕРВЕРА", contentLeft + 14, y + 12, TEXT, alpha, 13);
        drawString(
            graphics,
            "Экономические показатели городов всех государств.",
            contentLeft + 14,
            y + 36,
            MUTED,
            alpha,
            10
        );
        y += 76;

        for (int i = 0; i < snapshot.cityNames().length; i++) {
            int cardH = 112;
            panel(graphics, contentLeft, y, contentRight, y + cardH, alpha);

            String title = snapshot.cityCapitals()[i]
                ? "СТОЛИЦА  •  " + snapshot.cityNames()[i]
                : snapshot.cityNames()[i];
            if (snapshot.cityMine()[i]) {
                title += "  •  ВАША";
            }

            drawString(
                graphics,
                title,
                contentLeft + 14,
                y + 12,
                snapshot.cityCapitals()[i] ? GOLD : TEXT,
                alpha,
                11
            );
            drawString(
                graphics,
                snapshot.cityCountries()[i],
                contentLeft + 14,
                y + 31,
                MUTED,
                alpha,
                9
            );

            int colW = (contentRight - contentLeft - 42) / 4;
            cityMetric(graphics, contentLeft + 12, y + 48, colW,
                "Казна", "$" + format(snapshot.cityTreasuries()[i]), "minecraft:emerald", alpha);
            cityMetric(graphics, contentLeft + 22 + colW, y + 48, colW,
                "Доход", "$" + format(snapshot.cityIncome()[i]), "minecraft:paper", alpha);
            cityMetric(graphics, contentLeft + 32 + colW * 2, y + 48, colW,
                "Население", format(snapshot.cityPopulation()[i]), "minecraft:player_head", alpha);
            cityMetric(graphics, contentLeft + 42 + colW * 3, y + 48, colW,
                "Инфра", format(snapshot.cityInfrastructure()[i]), "minecraft:iron_ingot", alpha);

            drawString(
                graphics,
                "Налоговые блоки: " + format(snapshot.cityTaxBlocks()[i])
                    + "  •  Мэр: " + snapshot.cityMayors()[i],
                contentLeft + 14,
                y + 94,
                MUTED,
                alpha,
                8
            );

            y += cardH + 8;
        }

        if (snapshot.cityNames().length == 0) {
            panel(graphics, contentLeft, y, contentRight, y + 70, alpha);
            drawString(
                graphics,
                "На сервере пока нет зарегистрированных городов.",
                contentLeft + 14,
                y + 28,
                MUTED,
                alpha,
                10
            );
            y += 82;
        }

        return y + 8;
    }

    private int drawAdminPage(GuiGraphics graphics, int y, int alpha) {
        panel(graphics, contentLeft, y, contentRight, y + 120, alpha);
        drawString(graphics, "АДМИНИСТРАТОРСКИЙ РЕЖИМ", contentLeft + 14, y + 14, GOLD, alpha, 13);
        drawString(
            graphics,
            "Экран доступен только в Creative. Сервер всё равно повторно проверяет права.",
            contentLeft + 14,
            y + 40,
            MUTED,
            alpha,
            10
        );
        drawString(
            graphics,
            "Обычная стоимость реформ отключается только серверной логикой Creative.",
            contentLeft + 14,
            y + 64,
            TEXT,
            alpha,
            9
        );
        drawString(
            graphics,
            "Админ-панель оставлена как отдельная вкладка, без зависимости от UI-фреймворка.",
            contentLeft + 14,
            y + 86,
            STEEL,
            alpha,
            9
        );
        return y + 136;
    }

    private int drawMarketPage(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        int y,
        int alpha
    ) {
        panel(graphics, contentLeft, y, contentRight, y + 92, alpha);
        drawItem(graphics, "minecraft:emerald", contentLeft + 14, y + 18, 30);
        drawString(graphics, "ВНУТРЕННИЙ РЫНОК", contentLeft + 54, y + 14, TEXT, alpha, 13);
        drawString(
            graphics,
            "Твоё личное состояние",
            contentLeft + 54,
            y + 36,
            MUTED,
            alpha,
            9
        );
        drawString(
            graphics,
            "$" + formatLong(snapshot.personalWallet()),
            contentLeft + 54,
            y + 55,
            GOLD,
            alpha,
            16
        );
        drawRight(
            graphics,
            "Население: " + format(snapshot.population()),
            contentRight - 14,
            y + 22,
            STEEL,
            alpha,
            9
        );
        drawRight(
            graphics,
            "Товаров в спросе: " + snapshot.marketItemIds().length,
            contentRight - 14,
            y + 42,
            MUTED,
            alpha,
            9
        );

        y += 106;

        panel(graphics, contentLeft, y, contentRight, y + 48, alpha);
        drawString(graphics, "ТОВАРЫ", contentLeft + 14, y + 10, TEXT, alpha, 11);
        drawString(
            graphics,
            "Цена зависит от того, сколько спроса ещё не покрыто.",
            contentLeft + 14,
            y + 28,
            MUTED,
            alpha,
            8
        );
        y += 58;

        if (snapshot.marketItemIds().length == 0) {
            panel(graphics, contentLeft, y, contentRight, y + 72, alpha);
            drawString(
                graphics,
                "Спрос пока не сформирован. Нужны жители в стране.",
                contentLeft + 14,
                y + 28,
                MUTED,
                alpha,
                10
            );
            return y + 84;
        }

        int rowH = 58;
        for (int i = 0; i < snapshot.marketItemIds().length; i++) {
            marketRow(graphics, mouseX, mouseY, y, i, rowH, alpha);
            y += rowH + 6;
        }

        return y + 8;
    }

    private void marketRow(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        int y,
        int index,
        int rowH,
        int alpha
    ) {
        String itemId = stringAt(snapshot.marketItemIds(), index);
        String name = stringAt(snapshot.marketItemNames(), index);
        int base = valueAt(snapshot.marketBaseDemand(), index);
        int remaining = valueAt(snapshot.marketRemaining(), index);
        int sold = valueAt(snapshot.marketSold(), index);
        int imported = valueAt(snapshot.marketImported(), index);
        int price = valueAt(snapshot.marketPrices(), index);

        boolean hover = inside(mouseX, mouseY, contentLeft + 4, y, contentRight - 4, y + rowH);
        fillRounded(
            graphics,
            contentLeft + 4,
            y,
            contentRight - 4,
            y + rowH,
            hover ? 0xFF1B2A38 : PANEL,
            alpha
        );

        drawItem(graphics, itemId, contentLeft + 12, y + 10, 34);
        drawString(graphics, name, contentLeft + 54, y + 8, TEXT, alpha, 10);
        drawString(
            graphics,
            "Спрос " + format(remaining) + "/" + format(base)
                + "  •  продано " + format(sold)
                + "  •  импорт " + format(imported),
            contentLeft + 54,
            y + 27,
            remaining > 0 ? WARNING : SUCCESS,
            alpha,
            8
        );
        drawString(
            graphics,
            "$" + format(price) + " / шт.",
            contentLeft + 54,
            y + 42,
            GOLD,
            alpha,
            9
        );

        int oneX = contentRight - 120;
        int manyX = contentRight - 62;
        drawActionButton(
            graphics,
            oneX,
            y + 13,
            oneX + 50,
            y + 43,
            "×1",
            mouseX,
            mouseY,
            remaining > 0,
            alpha
        );
        drawActionButton(
            graphics,
            manyX,
            y + 13,
            manyX + 54,
            y + 43,
            "×16",
            mouseX,
            mouseY,
            remaining > 0,
            alpha
        );
    }

    private void drawConfirmation(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        int alpha
    ) {
        graphics.fill(0, 0, width, height, withAlpha(0xB9000000, alpha));

        int w = 560;
        int h = 250;
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        fillRounded(graphics, x, y, x + w, y + h, SHEET, alpha);
        drawString(graphics, "ПОДТВЕРЖДЕНИЕ РЕФОРМЫ", x + 22, y + 20, TEXT, alpha, 15);
        drawString(graphics, confirmTitle.toUpperCase(Locale.ROOT), x + 22, y + 48, GOLD, alpha, 11);

        String description = confirmFirstChoice
            ? "Это первый выбор данного параметра. Он устанавливается бесплатно."
            : "Это полноценная реформа государства: стоимость списывается из казны и государственного склада.";

        drawString(graphics, description, x + 22, y + 82, MUTED, alpha, 9);

        if (confirmFirstChoice) {
            drawString(graphics, "БЕСПЛАТНО", x + 22, y + 116, SUCCESS, alpha, 14);
        } else {
            String summary = CountryReformCostTable.summary(
                confirmAction,
                false,
                snapshot.population(),
                snapshot.developmentLevel()
            );
            drawString(graphics, summary, x + 22, y + 116, TEXT, alpha, 10);
        }

        int cancelX = x + 22;
        int confirmX = x + w - 172;
        drawActionButton(
            graphics,
            cancelX,
            y + h - 58,
            cancelX + 132,
            y + h - 22,
            "ОТМЕНА",
            mouseX,
            mouseY,
            true,
            alpha
        );
        drawActionButton(
            graphics,
            confirmX,
            y + h - 58,
            confirmX + 150,
            y + h - 22,
            "ПОДТВЕРДИТЬ",
            mouseX,
            mouseY,
            true,
            alpha
        );
    }

    private void drawScrollbar(GuiGraphics graphics, int alpha) {
        if (maxScroll <= 0.0D) {
            return;
        }

        int trackX = contentRight - 5;
        int trackTop = contentTop + 6;
        int trackBottom = contentBottom - 6;
        int trackH = trackBottom - trackTop;

        graphics.fill(trackX, trackTop, trackX + 3, trackBottom, withAlpha(LINE, alpha));

        float ratio = (float) scrollOffset / (float) maxScroll;
        int thumbH = Math.max(34, trackH * trackH
            / Math.max(trackH, trackH + (int) maxScroll));
        int thumbY = trackTop + Math.round((trackH - thumbH) * ratio);
        graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, withAlpha(GOLD, alpha));
    }

    private void drawStatus(GuiGraphics graphics, int alpha) {
        if (statusUntil < System.currentTimeMillis()) {
            return;
        }

        int w = Math.max(170, font.width(statusText) + 28);
        int x = (width - w) / 2;
        int y = height - 34;
        float remaining = Math.max(0.0F, statusUntil - System.currentTimeMillis()) / 1400.0F;
        int toastAlpha = (int) Mth.clamp(remaining * 255.0F, 0.0F, 255.0F);

        fillRounded(
            graphics,
            x,
            y,
            x + w,
            y + 24,
            0xFF151F28,
            Math.min(alpha, toastAlpha)
        );
        drawCentered(
            graphics,
            statusText,
            x,
            y + 7,
            w,
            10,
            statusColor,
            Math.min(alpha, toastAlpha)
        );
    }

    private void metricCard(
        GuiGraphics graphics,
        int x,
        int y,
        int w,
        String title,
        String value,
        String icon,
        int accent,
        int alpha
    ) {
        panel(graphics, x, y, x + w, y + 78, alpha);
        drawItem(graphics, icon, x + 12, y + 12, 22);
        drawString(graphics, title, x + 44, y + 15, MUTED, alpha, 8);
        drawString(graphics, value, x + 12, y + 48, accent, alpha, 13);
    }

    private void cityMetric(
        GuiGraphics graphics,
        int x,
        int y,
        int w,
        String title,
        String value,
        String icon,
        int alpha
    ) {
        fillRounded(graphics, x, y, x + w, y + 38, PANEL_3, alpha);
        drawItem(graphics, icon, x + 5, y + 7, 20);
        drawString(graphics, title, x + 30, y + 8, MUTED, alpha, 7);
        drawString(graphics, value, x + 30, y + 22, TEXT, alpha, 8);
    }

    private void infoLine(
        GuiGraphics graphics,
        int x,
        int y,
        String title,
        String value,
        String icon,
        int alpha
    ) {
        drawItem(graphics, icon, x, y, 18);
        drawString(graphics, title, x + 26, y + 3, MUTED, alpha, 8);
        drawRight(graphics, value, x + 245, y + 3, TEXT, alpha, 8);
    }

    private void panel(
        GuiGraphics graphics,
        int x1,
        int y1,
        int x2,
        int y2,
        int alpha
    ) {
        fillRounded(graphics, x1, y1, x2, y2, PANEL, alpha);
        graphics.fill(x1, y1, x2, y1 + 1, withAlpha(LINE, alpha));
    }

    private void progressBar(
        GuiGraphics graphics,
        int x1,
        int y1,
        int x2,
        int y2,
        float ratio,
        int color,
        int alpha
    ) {
        ratio = Mth.clamp(ratio, 0.0F, 1.0F);
        graphics.fill(x1, y1, x2, y2, withAlpha(0xFF0A1017, alpha));
        int active = x1 + Math.round((x2 - x1) * ratio);
        if (active > x1) {
            graphics.fill(x1, y1, active, y2, withAlpha(color, alpha));
        }
    }

    private void drawActionButton(
        GuiGraphics graphics,
        int x1,
        int y1,
        int x2,
        int y2,
        String text,
        int mouseX,
        int mouseY,
        boolean enabled,
        int alpha
    ) {
        boolean hover = enabled && inside(mouseX, mouseY, x1, y1, x2, y2);
        int color = !enabled
            ? 0xFF18212A
            : hover ? 0xFF2A3A49 : PANEL_2;

        fillRounded(graphics, x1, y1, x2, y2, color, alpha);
        drawCentered(
            graphics,
            text,
            x1,
            y1 + 10,
            x2 - x1,
            9,
            enabled ? TEXT : MUTED,
            alpha
        );
    }

    private void drawItem(
        GuiGraphics graphics,
        String itemId,
        int x,
        int y,
        int size
    ) {
        try {
            ResourceLocation id = ResourceLocation.parse(itemId);
            var item = BuiltInRegistries.ITEM.get(id);
            if (item != null && item != BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecraft:air"))) {
                graphics.renderItem(new ItemStack(item), x, y);
            }
        } catch (Exception ignored) {
            // Keep the dashboard usable even if an optional mod item disappeared.
        }
    }

    private boolean isFirstChoice(String action) {
        return switch (action) {
            case "direction" -> "Не выбрано".equals(snapshot.direction());
            case "government" -> "Не выбрано".equals(snapshot.government());
            case "religion" -> "Не выбрано".equals(snapshot.religion());
            default -> true;
        };
    }

    private void openConfirmation(String action, String command, String title) {
        confirmAction = action;
        confirmCommand = command;
        confirmTitle = title;
        confirmFirstChoice = isFirstChoice(action);
    }

    private void sendWorkforce(int index, int delta) {
        WorkforceSector sector = WorkforceSector.values()[index];
        statusText = "Изменение отправлено…";
        statusColor = GOLD;
        statusUntil = System.currentTimeMillis() + 1200L;
        EconomyNetwork.sendAction("workforce", sector.commandName() + ":" + delta);
    }

    private void sendMarketSale(int index, int amount) {
        if (index < 0 || index >= snapshot.marketItemIds().length) return;
        statusText = "Покупатель ищет товар…";
        statusColor = GOLD;
        statusUntil = System.currentTimeMillis() + 1200L;
        EconomyNetwork.sendAction(
            "market_sell",
            amount + "|" + snapshot.marketItemIds()[index]
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (confirmAction != null) {
            int w = 560;
            int h = 250;
            int x = (width - w) / 2;
            int y = (height - h) / 2;

            if (inside(mouseX, mouseY, x + 22, y + h - 58, x + 154, y + h - 22)) {
                confirmAction = null;
                return true;
            }

            if (inside(mouseX, mouseY, x + w - 172, y + h - 58, x + w - 22, y + h - 22)) {
                EconomyNetwork.sendAction(confirmAction, confirmCommand);
                statusText = "Реформа отправлена…";
                statusColor = GOLD;
                statusUntil = System.currentTimeMillis() + 1200L;
                confirmAction = null;
                return true;
            }

            return true;
        }

        if (inside(mouseX, mouseY, panelLeft + PANEL_W - 45, panelTop + 14, panelLeft + PANEL_W - 15, panelTop + 46)) {
            onClose();
            return true;
        }

        int navX = panelLeft + 14;
        int navY = panelTop + HEADER_H + 10;
        String[] names = {"ОБЗОР", "СТРАНА", "ЭФФЕКТЫ", "ГОРОДА", "РЫНОК"};
        int[] pages = {0, 1, 2, 3, 5};

        for (int i = 0; i < names.length; i++) {
            if (inside(mouseX, mouseY, navX, navY, navX + NAV_W - 22, navY + 38)) {
                activePage = pages[i];
                scrollOffset = 0.0D;
                return true;
            }
            navY += 46;
        }

        if (Minecraft.getInstance().player != null
            && Minecraft.getInstance().player.isCreative()
            && inside(mouseX, mouseY, navX, navY, navX + NAV_W - 22, navY + 38)) {
            activePage = 4;
            scrollOffset = 0.0D;
            return true;
        }

        if (!inside(mouseX, mouseY, contentLeft, contentTop, contentRight, contentBottom)) {
            return true;
        }

        double localY = mouseY + scrollOffset - contentTop;

        if (activePage == 0) {
            double workforceStart = 78 + 88 + 108 + 92 + 78;
            double rowStart = workforceStart;
            int rowH = 60;

            int index = (int) ((localY - rowStart) / rowH);
            if (index >= 0 && index < WorkforceSector.values().length) {
                double rowY = rowStart + index * rowH;
                int minusX = contentRight - 104;
                int plusX = contentRight - 52;
                if (inside(mouseX, mouseY + scrollOffset, minusX, contentTop + rowY,
                    minusX + 44, contentTop + rowY + 54)) {
                    int allocation = valueAt(snapshot.sectorAllocation(), index);
                    if (allocation > 0) {
                        sendWorkforce(index, -5);
                    }
                    return true;
                }
                if (inside(mouseX, mouseY + scrollOffset, plusX, contentTop + rowY,
                    plusX + 44, contentTop + rowY + 54)) {
                    int allocation = valueAt(snapshot.sectorAllocation(), index);
                    if (allocation < 100) {
                        sendWorkforce(index, 5);
                    }
                    return true;
                }
            }
        }

        if (activePage == 1) {
            handleCountryClick(mouseX, mouseY);
            return true;
        }

        if (activePage == 5) {
            handleMarketClick(mouseX, mouseY);
            return true;
        }

        return true;
    }

    private void handleMarketClick(double mouseX, double mouseY) {
        double localY = mouseY + scrollOffset - contentTop;
        double rowsStart = 106 + 58;
        int rowH = 64;
        int index = (int) ((localY - rowsStart) / rowH);
        if (index < 0 || index >= snapshot.marketItemIds().length) return;

        int rowY = contentTop + (int) rowsStart + index * rowH - (int) scrollOffset;
        int oneX = contentRight - 120;
        int manyX = contentRight - 62;

        int remaining = valueAt(snapshot.marketRemaining(), index);
        if (remaining <= 0) return;

        if (inside(mouseX, mouseY, oneX, rowY + 13, oneX + 50, rowY + 43)) {
            sendMarketSale(index, 1);
        } else if (inside(mouseX, mouseY, manyX, rowY + 13, manyX + 54, rowY + 43)) {
            sendMarketSale(index, 16);
        }
    }

    private void handleCountryClick(double mouseX, double mouseY) {
        double localY = mouseY + scrollOffset - contentTop;
        double y = 84;

        y = countryClickSection(mouseX, mouseY, localY, y, "direction", CountryDirection.values());
        if (y < 0) return;
        y += 10;
        y = countryClickSection(mouseX, mouseY, localY, y, "government", GovernmentType.values());
        if (y < 0) return;
        y += 10;
        countryClickSection(mouseX, mouseY, localY, y, "religion", ReligionType.values());
    }

    private double countryClickSection(
        double mouseX,
        double mouseY,
        double localY,
        double y,
        String action,
        Object[] values
    ) {
        double height = 36 + values.length * 50;
        if (localY >= y + 30 && localY < y + height) {
            int index = (int) ((localY - (y + 30)) / 50);
            if (index >= 0 && index < values.length) {
                Object value = values[index];
                String display;
                String command;
                if (value instanceof CountryDirection direction) {
                    display = direction.displayName();
                    command = direction.commandName();
                } else if (value instanceof GovernmentType government) {
                    display = government.displayName();
                    command = government.commandName();
                } else {
                    ReligionType religion = (ReligionType) value;
                    display = religion.displayName();
                    command = religion.commandName();
                }

                boolean selected = switch (action) {
                    case "direction" -> snapshot.direction().equals(display);
                    case "government" -> snapshot.government().equals(display);
                    default -> snapshot.religion().equals(display);
                };

                if (!selected) {
                    openConfirmation(action, command, display);
                }
                return -1;
            }
        }
        return y + height;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(mouseX, mouseY, contentLeft, contentTop, contentRight, contentBottom)) {
            scrollOffset = Mth.clamp(
                scrollOffset - scrollY * 34.0D,
                0.0D,
                maxScroll
            );
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawCentered(
        GuiGraphics graphics,
        String text,
        int x,
        int y,
        int width,
        int size,
        int color,
        int alpha
    ) {
        int textWidth = font.width(text);
        int drawX = x + Math.max(0, (width - textWidth) / 2);
        drawString(graphics, text, drawX, y, color, alpha, size);
    }

    private void drawRight(
        GuiGraphics graphics,
        String text,
        int right,
        int y,
        int color,
        int alpha,
        int size
    ) {
        drawString(
            graphics,
            text,
            right - font.width(text),
            y,
            color,
            alpha,
            size
        );
    }

    private void drawString(
        GuiGraphics graphics,
        String text,
        int x,
        int y,
        int color,
        int alpha,
        int size
    ) {
        int argb = withAlpha(color, alpha);
        graphics.drawString(font, Component.literal(text), x, y, argb);
    }

    private boolean inside(
        double mouseX,
        double mouseY,
        double x1,
        double y1,
        double x2,
        double y2
    ) {
        return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
    }

    private static int valueAt(int[] values, int index) {
        return values != null && index >= 0 && index < values.length ? values[index] : 0;
    }

    private static String stringAt(String[] values, int index) {
        return values != null && index >= 0 && index < values.length && values[index] != null
            ? values[index]
            : "";
    }

    private static String format(int value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0, value));
    }

    private static String formatLong(long value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, value));
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%,.2f", Math.max(0.0D, value));
    }

    private static String signed(double value) {
        if (Math.abs(value) < 0.0001D) return "0%";
        return String.format(Locale.ROOT, "%+.0f%%", value);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static void fillRounded(
        GuiGraphics graphics,
        int x1,
        int y1,
        int x2,
        int y2,
        int color,
        int alpha
    ) {
        graphics.fill(x1, y1, x2, y2, withAlpha(color, alpha));
    }
}
