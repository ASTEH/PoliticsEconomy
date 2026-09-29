package ru.zela.politicseconomy.client;

import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.Ui;
import com.nstut.openui.controls.Dialog;
import com.nstut.openui.controls.Toast;
import com.nstut.openui.minecraft.UiScreen;
import com.nstut.openui.state.ReadableSignal;
import com.nstut.openui.state.Signal;
import com.nstut.openui.state.Signals;
import com.nstut.openui.theme.Theme;
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
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Politics Economy dashboard built on OpenUI MC.
 *
 * The screen itself is long-lived. Server snapshots only update Signals,
 * while OpenUI updates the affected components/lists instead of rebuilding
 * the whole screen tree.
 */
public final class EconomyScreen extends UiScreen {
    private enum Page {
        OVERVIEW, COUNTRY, EFFECTS, CITIES, MARKET
    }

    private final Signal<EconomySnapshotPayload> snapshotSignal;
    private final Signal<Page> pageSignal = Signals.of(Page.OVERVIEW);

    private final ReadableSignal<List<WorkforceRow>> workforceRows;
    private final ReadableSignal<List<MaterialRow>> materialRows;
    private final ReadableSignal<List<EffectRow>> positiveEffects;
    private final ReadableSignal<List<EffectRow>> negativeEffects;
    private final ReadableSignal<List<CityRow>> cityRows;
    private final ReadableSignal<List<MarketRow>> marketRows;

    public EconomyScreen(EconomySnapshotPayload snapshot) {
        super(Component.literal("Politics Economy"));
        this.snapshotSignal = Signals.of(snapshot);

        this.workforceRows = Signals.computed(() -> {
            EconomySnapshotPayload s = snapshotSignal.get();
            List<WorkforceRow> result = new ArrayList<>();

            WorkforceSector[] sectors = WorkforceSector.values();
            for (int i = 0; i < sectors.length; i++) {
                result.add(new WorkforceRow(
                    sectors[i],
                    valueAt(s.sectorAllocation(), i),
                    valueAt(s.sectorWorkers(), i),
                    valueAt(s.workplaceSlots(), i),
                    valueAt(s.workplaceCounts(), i),
                    valueAt(s.sectorBonuses(), i)
                ));
            }
            return List.copyOf(result);
        });

        this.materialRows = Signals.computed(() -> {
            EconomySnapshotPayload s = snapshotSignal.get();
            List<MaterialRow> result = new ArrayList<>();

            int count = Math.min(
                Math.min(s.materialIds().length, s.materialNames().length),
                Math.min(s.materialStockpile().length, s.materialDebt().length)
            );

            for (int i = 0; i < count; i++) {
                result.add(new MaterialRow(
                    s.materialIds()[i],
                    s.materialNames()[i],
                    valueAt(s.materialStockpile(), i),
                    valueAt(s.materialDebt(), i),
                    valueAt(s.materialPerCycle(), i)
                ));
            }

            return List.copyOf(result);
        });

        this.positiveEffects = effectSignal(true);
        this.negativeEffects = effectSignal(false);

        this.cityRows = Signals.computed(() -> {
            EconomySnapshotPayload s = snapshotSignal.get();
            List<CityRow> result = new ArrayList<>();
            int count = s.cityNames().length;

            for (int i = 0; i < count; i++) {
                result.add(new CityRow(
                    s.cityNames()[i],
                    valueAt(s.cityCountries(), i),
                    valueAt(s.cityMayors(), i),
                    valueAt(s.cityTreasuries(), i),
                    valueAt(s.cityIncome(), i),
                    valueAt(s.cityInfrastructure(), i),
                    valueAt(s.cityPopulation(), i),
                    valueAt(s.cityTaxBlocks(), i),
                    valueAt(s.cityCapitals(), i),
                    valueAt(s.cityMine(), i)
                ));
            }

            return List.copyOf(result);
        });

        this.marketRows = Signals.computed(() -> {
            EconomySnapshotPayload s = snapshotSignal.get();
            List<MarketRow> result = new ArrayList<>();
            int count = Math.min(
                Math.min(s.marketItemIds().length, s.marketItemNames().length),
                Math.min(s.marketBaseDemand().length, s.marketRemaining().length)
            );

            for (int i = 0; i < count; i++) {
                result.add(new MarketRow(
                    s.marketItemIds()[i],
                    s.marketItemNames()[i],
                    valueAt(s.marketBaseDemand(), i),
                    valueAt(s.marketRemaining(), i),
                    valueAt(s.marketSold(), i),
                    valueAt(s.marketImported(), i),
                    valueAt(s.marketPrices(), i)
                ));
            }

            return List.copyOf(result);
        });
    }

    public void applySnapshot(EconomySnapshotPayload payload) {
        Signals.batch(() -> snapshotSignal.set(payload));

        if (uiRuntime() != null) {
            Toast.show(
                uiRuntime().overlays(),
                Toast.success("Экономика обновлена", "Данные государства синхронизированы.")
            );
        }
    }

    @Override
    protected void init() {
        super.init();
        uiRuntime().theme(Theme.highContrast());
    }

    @Override
    protected UIComponent buildUI() {
        return Ui.responsive(size ->
            Ui.padding(
                size.width() < 760 ? 8 : 14,
                Ui.card(
                    Ui.column(
                        header(),
                        Ui.divider(),
                        Ui.tabs(pageSignal)
                            .tab(Page.OVERVIEW, "Обзор")
                            .tab(Page.COUNTRY, "Государство")
                            .tab(Page.EFFECTS, "Эффекты")
                            .tab(Page.CITIES, "Города")
                            .tab(Page.MARKET, "Рынок")
                            .fillWidth(),
                        Ui.switcher(pageSignal)
                            .when(Page.OVERVIEW, this::overviewPage)
                            .when(Page.COUNTRY, this::countryPage)
                            .when(Page.EFFECTS, this::effectsPage)
                            .when(Page.CITIES, this::citiesPage)
                            .when(Page.MARKET, this::marketPage)
                            .flex()
                    ).gap(size.width() < 760 ? 6 : 9).fillWidth().fillHeight()
                )
                    .padding(size.width() < 760 ? 8 : 12)
                    .elevated(true)
                    .fillWidth()
                    .fillHeight()
            )
        );
    }

    private UIComponent header() {
        ReadableSignal<String> countryTitle = textSignal(s ->
            s.countryName().toUpperCase(Locale.ROOT)
                + "  /  " + s.direction()
        );

        ReadableSignal<String> populationText = textSignal(s ->
            "НАСЕЛЕНИЕ  " + format(s.population())
        );

        ReadableSignal<String> walletText = textSignal(s ->
            "Кошелёк  $" + formatLong(s.personalWallet())
        );

        return Ui.row(
            Ui.column(
                Ui.title("POLITICS ECONOMY"),
                Ui.text(countryTitle).nowrap(),
                Ui.text(populationText).nowrap()
            ).gap(2),
            Ui.column(
                Ui.text("ГОСУДАРСТВЕННЫЙ КОШЕЛЁК").nowrap(),
                Ui.text(walletText).nowrap()
            ).gap(1)
        ).gap(8).fillWidth();
    }

    private UIComponent overviewPage() {
        ReadableSignal<Double> developmentProgress = Signals.computed(() -> {
            EconomySnapshotPayload s = snapshotSignal.get();
            if (s.developmentLevel() >= 5) return 1.0D;
            if (s.developmentNextThreshold() <= 0) return 1.0D;
            return Math.max(
                0.0D,
                Math.min(
                    1.0D,
                    s.developmentPoints() / (double) s.developmentNextThreshold()
                )
            );
        });

        UIComponent workforceList = Ui.list(
            workforceRows,
            this::workforceRow
        ).key(WorkforceRow::sector).itemHeight(58).height(250);

        UIComponent materialList = Ui.list(
            materialRows,
            this::materialRow
        ).key(MaterialRow::itemId).itemHeight(48).height(180);

        UIComponent metrics = metricLayout(
            metric(
                "КАЗНА",
                textSignal(s -> "$" + format(s.treasury())),
                "minecraft:emerald"
            ),
            metric(
                "СОДЕРЖАНИЕ",
                textSignal(s -> String.format(Locale.ROOT, "-%.2f $", s.infrastructureCost())),
                "minecraft:anvil"
            ),
            metric(
                "ДЕНЕЖНЫЙ ДОЛГ",
                textSignal(s -> "$" + formatDouble(s.moneyDebt())),
                "minecraft:redstone"
            ),
            metric(
                "НАСЕЛЕНИЕ",
                textSignal(s -> format(s.population())),
                "minecraft:player_head"
            )
        );

        UIComponent profile = Ui.card(
            Ui.column(
                Ui.heading("ПОЛИТИЧЕСКИЙ ПРОФИЛЬ"),
                Ui.row(
                    infoBlock("Направление", textSignal(EconomySnapshotPayload::direction), "minecraft:compass"),
                    infoBlock("Правление", textSignal(EconomySnapshotPayload::government), "minecraft:iron_sword"),
                    infoBlock("Религия", textSignal(EconomySnapshotPayload::religion), "minecraft:book")
                ).gap(8)
            ).gap(8)
        ).padding(10).elevated(true).fillWidth();

        UIComponent workforce = Ui.card(
            Ui.column(
                Ui.row(
                    Ui.heading("РАБОЧАЯ СИЛА"),
                    Ui.spacer(),
                    Ui.text(textSignal(s ->
                        format(s.workingPopulation()) + " доступны  •  "
                            + format(s.employedPopulation()) + " заняты  •  "
                            + format(s.unemployedPopulation()) + " без места"
                    )).nowrap()
                ).fillWidth(),
                workforceList
            ).gap(7).fillWidth().fillHeight()
        ).padding(10).elevated(true).fillWidth().flex();

        UIComponent development = Ui.card(
            Ui.column(
                Ui.row(
                    Ui.column(
                        Ui.heading("РАЗВИТИЕ"),
                        Ui.text(textSignal(s ->
                            s.developmentLevel() >= 5
                                ? "Максимальный уровень"
                                : format(s.developmentPoints()) + " / "
                                    + format(s.developmentNextThreshold()) + " очков"
                        ))
                    ).gap(2),
                    Ui.spacer(),
                    Ui.text(textSignal(EconomySnapshotPayload::developmentPerk)).nowrap()
                ).fillWidth(),
                Ui.progress(developmentProgress).fillWidth().height(8)
            ).gap(7)
        ).padding(10).elevated(true).fillWidth();

        UIComponent stockpile = Ui.card(
            Ui.column(
                Ui.row(
                    Ui.heading("ГОСУДАРСТВЕННЫЙ СКЛАД"),
                    Ui.spacer(),
                    Ui.text(textSignal(s ->
                        "Материалов: " + s.materialIds().length
                    )).nowrap()
                ).fillWidth(),
                materialList
            ).gap(7).fillWidth().fillHeight()
        ).padding(10).elevated(true).fillWidth().height(220);

        return Ui.scroll(
            Ui.column(
                metrics,
                profile,
                development,
                workforce,
                stockpile
            ).gap(9).fillWidth()
        ).flex();
    }

    private UIComponent countryPage() {
        return Ui.scroll(
            Ui.column(
                Ui.card(
                    Ui.column(
                        Ui.title("УПРАВЛЕНИЕ ГОСУДАРСТВОМ"),
                        Ui.text("Первые выборы бесплатны. Повторные изменения считаются реформами.")
                    ).gap(4)
                ).padding(10).elevated(true).fillWidth(),

                choiceSection(
                    "ЭКОНОМИЧЕСКОЕ НАПРАВЛЕНИЕ",
                    "direction",
                    List.of(CountryDirection.values())
                ),

                choiceSection(
                    "ФОРМА ПРАВЛЕНИЯ",
                    "government",
                    List.of(GovernmentType.values())
                ),

                choiceSection(
                    "РЕЛИГИЯ",
                    "religion",
                    List.of(ReligionType.values())
                )
            ).gap(9).fillWidth()
        ).flex();
    }

    private UIComponent choiceSection(
        String title,
        String action,
        List<?> values
    ) {
        List<UIComponent> rows = new ArrayList<>();
        rows.add(Ui.heading(title));

        for (Object value : values) {
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

            rows.add(choiceRow(action, command, display));
        }

        return Ui.card(
            Ui.column(rows.toArray(UIComponent[]::new)).gap(6)
        ).padding(10).elevated(true).fillWidth();
    }

    private UIComponent choiceRow(String action, String command, String display) {
        ReadableSignal<Boolean> selected = Signals.computed(() -> {
            EconomySnapshotPayload s = snapshotSignal.get();
            return switch (action) {
                case "direction" -> s.direction().equals(display);
                case "government" -> s.government().equals(display);
                default -> s.religion().equals(display);
            };
        });

        ReadableSignal<String> cost = Signals.computed(() -> {
            EconomySnapshotPayload s = snapshotSignal.get();
            boolean firstChoice = switch (action) {
                case "direction" -> "Не выбрано".equals(s.direction());
                case "government" -> "Не выбрано".equals(s.government());
                default -> "Не выбрано".equals(s.religion());
            };

            if (firstChoice) {
                return "Первый выбор • бесплатно";
            }

            return CountryReformCostTable.summary(
                action,
                false,
                s.population(),
                s.developmentLevel()
            );
        });

        return Ui.card(
            Ui.row(
                Ui.column(
                    Ui.text(display).nowrap(),
                    Ui.text(cost).nowrap()
                ).gap(2).flex(),
                Ui.switcher(selected)
                    .when(true, () ->
                        Ui.button("ВЫБРАНО", () -> {})
                            .success()
                            .small()
                            .enabled(false)
                    )
                    .when(false, () ->
                        Ui.button("ВЫБРАТЬ", () ->
                            openReformDialog(action, command, display)
                        ).primary().small()
                    )
            ).gap(8).fillWidth()
        ).padding(8).elevated(true).fillWidth();
    }

    private void openReformDialog(
        String action,
        String command,
        String title
    ) {
        EconomySnapshotPayload s = snapshotSignal.get();

        boolean firstChoice = switch (action) {
            case "direction" -> "Не выбрано".equals(s.direction());
            case "government" -> "Не выбрано".equals(s.government());
            default -> "Не выбрано".equals(s.religion());
        };

        String description = firstChoice
            ? "Это первый выбор данного параметра. Он устанавливается бесплатно."
            : "Это полноценная реформа государства. Стоимость списывается из казны и государственного склада.";

        String cost = firstChoice
            ? "БЕСПЛАТНО"
            : CountryReformCostTable.summary(
                action,
                false,
                s.population(),
                s.developmentLevel()
            );

        Dialog.confirm(
            uiRuntime().overlays(),
            "Сменить параметр",
            title + "\n\n" + description + "\n\nСтоимость: " + cost,
            () -> {
                EconomyNetwork.sendAction(action, command);
                Toast.show(
                    uiRuntime().overlays(),
                    Toast.info("Реформа отправлена", "Сервер проверяет стоимость и условия.")
                );
            },
            () -> {}
        );
    }

    private UIComponent effectsPage() {
        UIComponent positive = Ui.card(
            Ui.column(
                Ui.heading("ПОЛОЖИТЕЛЬНЫЕ ЭФФЕКТЫ"),
                Ui.list(positiveEffects, this::effectRow)
                    .key(EffectRow::name)
                    .itemHeight(34)
                    .flex()
            ).gap(7).fillWidth().fillHeight()
        ).padding(10).elevated(true).fillWidth().flex();

        UIComponent negative = Ui.card(
            Ui.column(
                Ui.heading("ОТРИЦАТЕЛЬНЫЕ ЭФФЕКТЫ"),
                Ui.list(negativeEffects, this::effectRow)
                    .key(EffectRow::name)
                    .itemHeight(34)
                    .flex()
            ).gap(7).fillWidth().fillHeight()
        ).padding(10).elevated(true).fillWidth().flex();

        return Ui.column(
            Ui.card(
                Ui.column(
                    Ui.title("ЭФФЕКТЫ ГОСУДАРСТВА"),
                    Ui.text("Все значения ниже рассчитываются сервером из направления, политики, развития и населения.")
                ).gap(4)
            ).padding(10).elevated(true).fillWidth(),
            Ui.row(positive, negative).gap(9).fillWidth().flex()
        ).gap(9).fillWidth().fillHeight();
    }

    private UIComponent effectRow(EffectRow row) {
        return Ui.card(
            Ui.row(
                Ui.text(row.name()).nowrap().flex(),
                Ui.badge(signed(row.value()))
            ).gap(8).fillWidth()
        ).padding(6).fillWidth();
    }

    private UIComponent citiesPage() {
        return Ui.column(
            Ui.card(
                Ui.column(
                    Ui.title("ГОРОДА СЕРВЕРА"),
                    Ui.text("Экономические показатели зарегистрированных городов.")
                ).gap(4)
            ).padding(10).elevated(true).fillWidth(),
            Ui.list(cityRows, this::cityRow)
                .key(CityRow::name)
                .itemHeight(118)
                .flex()
        ).gap(9).fillWidth().fillHeight();
    }

    private UIComponent cityRow(CityRow row) {
        String title = row.capital()
            ? "СТОЛИЦА  •  " + row.name()
            : row.name();

        if (row.mine()) {
            title += "  •  ВАША";
        }

        return Ui.card(
            Ui.column(
                Ui.row(
                    Ui.text(title).nowrap().flex(),
                    Ui.badge("$" + format(row.treasury()))
                ).gap(8).fillWidth(),
                Ui.text(row.country()).nowrap(),
                Ui.row(
                    miniMetric("Доход", "$" + format(row.income()), "minecraft:paper"),
                    miniMetric("Население", format(row.population()), "minecraft:player_head"),
                    miniMetric("Инфра", format(row.infrastructure()), "minecraft:iron_ingot"),
                    miniMetric("Налоги", format(row.taxBlocks()), "minecraft:emerald")
                ).gap(6).fillWidth(),
                Ui.text("Мэр: " + row.mayor()).nowrap()
            ).gap(5).fillWidth()
        ).padding(8).elevated(true).fillWidth();
    }

    private UIComponent marketPage() {
        UIComponent marketList = Ui.list(
            marketRows,
            this::marketRow
        ).key(MarketRow::itemId).itemHeight(74).flex();

        return Ui.column(
            Ui.card(
                Ui.column(
                    Ui.row(
                        Ui.column(
                            Ui.title("ВНУТРЕННИЙ РЫНОК"),
                            Ui.text("Население покупает реальные предметы. Ты можешь продавать товары своей стране.")
                        ).gap(3).flex(),
                        Ui.text(textSignal(s ->
                            "$" + formatLong(s.personalWallet())
                        )).nowrap()
                    ).gap(8).fillWidth(),
                    Ui.row(
                        Ui.chip("Цена = дефицит"),
                        Ui.chip("Импорт после ½ цикла"),
                        Ui.chip("Trade Warehouse = внешний рынок")
                    ).gap(5).fillWidth()
                ).gap(7).fillWidth()
            ).padding(10).elevated(true).fillWidth(),

            Ui.card(
                Ui.column(
                    Ui.row(
                        Ui.heading("ТОВАРЫ"),
                        Ui.spacer(),
                        Ui.text(textSignal(s ->
                            "В спросе: " + s.marketItemIds().length
                        )).nowrap()
                    ).fillWidth(),
                    marketList
                ).gap(7).fillWidth().fillHeight()
            ).padding(10).elevated(true).fillWidth().flex()
        ).gap(9).fillWidth().fillHeight();
    }

    private UIComponent marketRow(MarketRow row) {
        double fulfilled = row.baseDemand() <= 0
            ? 1.0D
            : 1.0D - row.remaining() / (double) row.baseDemand();

        UIComponent icon = Ui.icon(itemStack(row.itemId()))
            .width(34)
            .height(34);

        UIComponent stats = Ui.column(
            Ui.text(row.name()).nowrap(),
            Ui.text(
                "Спрос " + format(row.remaining()) + "/" + format(row.baseDemand())
                    + "  •  продано " + format(row.sold())
                    + "  •  импорт " + format(row.imported())
            ).nowrap(),
            Ui.progress(Signals.of(fulfilled)).height(5).fillWidth(),
            Ui.text("$" + format(row.price()) + " / шт.").nowrap()
        ).gap(2).flex();

        return Ui.card(
            Ui.row(
                icon,
                stats,
                Ui.column(
                    Ui.button("×1", () -> sellMarket(row, 1))
                        .small()
                        .outline(),
                    Ui.button("×16", () -> sellMarket(row, 16))
                        .small()
                        .primary()
                ).gap(4)
            ).gap(8).fillWidth()
        ).padding(6).fillWidth();
    }

    private UIComponent workforceRow(WorkforceRow row) {
        return Ui.card(
            Ui.row(
                Ui.icon(iconStack(row.sector().iconItemId()))
                    .width(30)
                    .height(30),
                Ui.column(
                    Ui.text(row.sector().displayName()).nowrap(),
                    Ui.text(
                        row.allocation() + "% распределено  •  "
                            + format(row.workers()) + "/" + format(row.slots()) + " занято"
                            + "  •  мест " + format(row.workplaceCount())
                            + "  •  бонус " + signed(row.bonus())
                    ).nowrap()
                ).gap(2).flex(),
                Ui.button("−", () -> changeWorkforce(row.sector(), -5))
                    .small()
                    .outline(),
                Ui.button("+", () -> changeWorkforce(row.sector(), 5))
                    .small()
                    .success()
            ).gap(7).fillWidth()
        ).padding(6).fillWidth();
    }

    private UIComponent materialRow(MaterialRow row) {
        String detail = String.format(
            Locale.ROOT,
            "%s  •  запас %d  •  расход %.2f / цикл",
            row.name(),
            row.stock(),
            row.perCycle()
        );

        return Ui.card(
            Ui.row(
                Ui.icon(itemStack(row.itemId())).width(26).height(26),
                Ui.column(
                    Ui.text(detail).nowrap(),
                    Ui.text(
                        row.debt() > 0
                            ? "Ресурсный долг: " + format(row.debt())
                            : "Долга нет"
                    ).nowrap()
                ).gap(2).flex(),
                Ui.badge(format(row.stock()))
            ).gap(7).fillWidth()
        ).padding(5).fillWidth();
    }

    private UIComponent metricLayout(UIComponent... cards) {
        return Ui.responsive(size -> {
            if (size.width() < 760) {
                return Ui.column(cards).gap(7).fillWidth();
            }
            return Ui.row(cards).gap(7).fillWidth();
        });
    }

    private UIComponent metric(
        String title,
        ReadableSignal<String> value,
        String itemId
    ) {
        return Ui.card(
            Ui.row(
                Ui.icon(iconStack(itemId)).width(26).height(26),
                Ui.column(
                    Ui.text(title).nowrap(),
                    Ui.text(value).nowrap()
                ).gap(2)
            ).gap(6).fillWidth()
        ).padding(8).elevated(true).flex();
    }

    private UIComponent infoBlock(
        String title,
        ReadableSignal<String> value,
        String iconId
    ) {
        return Ui.card(
            Ui.row(
                Ui.icon(iconStack(iconId)).width(22).height(22),
                Ui.column(
                    Ui.text(title).nowrap(),
                    Ui.text(value).nowrap()
                ).gap(2)
            ).gap(5).fillWidth()
        ).padding(7).fillWidth().flex();
    }

    private UIComponent miniMetric(
        String title,
        String value,
        String itemId
    ) {
        return Ui.card(
            Ui.row(
                Ui.icon(iconStack(itemId)).width(18).height(18),
                Ui.column(
                    Ui.text(title).nowrap(),
                    Ui.text(value).nowrap()
                ).gap(1)
            ).gap(4).fillWidth().flex()
        ).padding(5).fillWidth().flex();
    }

    private ReadableSignal<List<EffectRow>> effectSignal(boolean positive) {
        return Signals.computed(() -> {
            EconomySnapshotPayload s = snapshotSignal.get();
            List<EffectRow> result = new ArrayList<>();

            for (int i = 0; i < s.modifierNames().length; i++) {
                double value = valueAt(s.modifierValues(), i);
                if ((positive && value > 0.0001D)
                    || (!positive && value < -0.0001D)) {
                    result.add(new EffectRow(s.modifierNames()[i], value));
                }
            }

            return List.copyOf(result);
        });
    }

    private <T> ReadableSignal<String> textSignal(
        Function<EconomySnapshotPayload, T> mapper
    ) {
        return Signals.computed(() -> String.valueOf(mapper.apply(snapshotSignal.get())));
    }

    private void changeWorkforce(WorkforceSector sector, int delta) {
        EconomyNetwork.sendAction(
            "workforce",
            sector.commandName() + ":" + delta
        );

        Toast.show(
            uiRuntime().overlays(),
            Toast.info("Изменение отправлено", sector.displayName() + " " + (delta > 0 ? "+5%" : "-5%"))
        );
    }

    private void sellMarket(MarketRow row, int amount) {
        EconomyNetwork.sendAction(
            "market_sell",
            amount + "|" + row.itemId()
        );

        Toast.show(
            uiRuntime().overlays(),
            Toast.info(
                "Продажа отправлена",
                row.name() + " ×" + amount
            )
        );
    }

    private static ItemStack iconStack(String itemId) {
        try {
            ResourceLocation id = ResourceLocation.parse(itemId);
            return BuiltInRegistries.ITEM.getOptional(id)
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack itemStack(String itemId) {
        return iconStack(itemId);
    }

    private static int valueAt(int[] values, int index) {
        return values != null && index >= 0 && index < values.length
            ? values[index]
            : 0;
    }

    private static double valueAt(double[] values, int index) {
        return values != null && index >= 0 && index < values.length
            ? values[index]
            : 0.0D;
    }

    private static boolean valueAt(boolean[] values, int index) {
        return values != null && index >= 0 && index < values.length && values[index];
    }

    private static String valueAt(String[] values, int index) {
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
        if (Math.abs(value) < 0.0001D) {
            return "0%";
        }
        return String.format(Locale.ROOT, "%+.0f%%", value);
    }

    private record WorkforceRow(
        WorkforceSector sector,
        int allocation,
        int workers,
        int slots,
        int workplaceCount,
        double bonus
    ) {}

    private record MaterialRow(
        String itemId,
        String name,
        int stock,
        int debt,
        double perCycle
    ) {}

    private record EffectRow(
        String name,
        double value
    ) {}

    private record CityRow(
        String name,
        String country,
        String mayor,
        int treasury,
        int income,
        int infrastructure,
        int population,
        int taxBlocks,
        boolean capital,
        boolean mine
    ) {}

    private record MarketRow(
        String itemId,
        String name,
        int baseDemand,
        int remaining,
        int sold,
        int imported,
        int price
    ) {}
}