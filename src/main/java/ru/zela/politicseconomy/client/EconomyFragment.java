package ru.zela.politicseconomy.client;

import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.BitmapFactory;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ImageView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import ru.zela.politicseconomy.country.CountryDirection;
import ru.zela.politicseconomy.country.CountryReformCostTable;
import ru.zela.politicseconomy.country.GovernmentType;
import ru.zela.politicseconomy.country.ReligionType;
import ru.zela.politicseconomy.network.EconomyNetwork;
import ru.zela.politicseconomy.network.EconomySnapshotPayload;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class EconomyFragment extends Fragment {
    // Military-political palette: graphite, steel blue, muted gold, controlled red.
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
    private static final int PURPLE = 0xFF9485B0;
    private static final Map<String, WeakReference<Image>> ITEM_ICON_CACHE = new HashMap<>();

    private final EconomySnapshotPayload snapshot;
    private FrameLayout screenRoot;
    private LinearLayout pageHost;
    private FrameLayout popupOverlay;

    public EconomyFragment(EconomySnapshotPayload snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public View onCreateView(
        LayoutInflater inflater,
        ViewGroup container,
        DataSet savedInstanceState
    ) {
        Context context = requireContext();

        screenRoot = new FrameLayout(context);
        screenRoot.setBackground(solid(BG, 0));

        LinearLayout root = column(context);
        root.setBackground(solid(SHEET, dp(16)));
        root.setPadding(dp(20), dp(16), dp(20), dp(14));

        FrameLayout.LayoutParams rootParams =
            new FrameLayout.LayoutParams(dp(980), dp(710));
        rootParams.gravity = Gravity.CENTER;
        screenRoot.addView(root, rootParams);

        root.addView(buildHeader(context), new LinearLayout.LayoutParams(-1, dp(60)));

        LinearLayout tabs = row(context);
        Button overview = tabButton(context, "ОБЗОР");
        Button country = tabButton(context, "СТРАНА");
        Button effects = tabButton(context, "ЭФФЕКТЫ");
        Button cities = tabButton(context, "ГОРОДА");

        tabs.addView(overview, new LinearLayout.LayoutParams(0, dp(40), 1));
        tabs.addView(country, marginTab());
        tabs.addView(effects, marginTab());
        tabs.addView(cities, marginTab());

        if (Minecraft.getInstance().player != null
            && Minecraft.getInstance().player.isCreative()) {
            Button admin = tabButton(context, "АДМИН");
            admin.setTextColor(GOLD);
            admin.setOnClickListener(v -> showAdmin(context));
            tabs.addView(admin, marginTab());
        }

        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(-1, dp(40));
        tabsParams.setMargins(0, dp(8), 0, dp(10));
        root.addView(tabs, tabsParams);

        pageHost = column(context);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(pageHost);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        overview.setOnClickListener(v -> showOverview(context));
        country.setOnClickListener(v -> showCountrySettings(context));
        effects.setOnClickListener(v -> showEffects(context));
        cities.setOnClickListener(v -> showCities(context));

        if (needsInitialCountrySetup()) {
            showCountrySettings(context);
        } else {
            showOverview(context);
        }

        return screenRoot;
    }

    private View buildHeader(Context context) {
        LinearLayout header = row(context);

        LinearLayout title = column(context);
        title.addView(label(context, "ГОСУДАРСТВО", 21, TEXT));
        title.addView(label(
            context,
            snapshot.countryName().toUpperCase(Locale.ROOT)
                + "  /  " + snapshot.direction(),
            12,
            GOLD
        ));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout status = row(context);
        status.addView(itemIcon(context, "minecraft:player_head", 28),
            new LinearLayout.LayoutParams(dp(30), dp(30)));
        status.addView(label(
            context,
            format(snapshot.population()) + "  •  " + signed(snapshot.populationWorkforceModifier()),
            11,
            STEEL
        ));

        header.addView(status);

        Button close = smallButton(context, "×");
        close.setOnClickListener(v -> closeDashboard());
        header.addView(close, new LinearLayout.LayoutParams(dp(40), dp(38)));

        return header;
    }

    private boolean needsInitialCountrySetup() {
        return "Не выбрано".equals(snapshot.direction())
            || "Не выбрано".equals(snapshot.government())
            || "Не выбрано".equals(snapshot.religion());
    }

    private void showOverview(Context context) {
        pageHost.removeAllViews();

        LinearLayout metrics = row(context);
        metrics.addView(metricCard(context, "КАЗНА", "$" + format(snapshot.treasury()),
            "minecraft:emerald", GOLD), new LinearLayout.LayoutParams(0, dp(78), 1));
        metrics.addView(metricCard(context, "РАСХОД МАТЕРИАЛОВ",
            String.format(Locale.ROOT, "-%.2f / цикл", snapshot.totalMaterialPerCycle()),
            "minecraft:iron_ingot", WARNING), marginWeight(1));
        metrics.addView(metricCard(context, "СОДЕРЖАНИЕ",
            String.format(Locale.ROOT, "-%.2f $", snapshot.infrastructureCost()),
            "minecraft:anvil", WARNING), marginWeight(1));
        metrics.addView(metricCard(context, "ДЕНЕЖНЫЙ ДОЛГ",
            "$" + formatDouble(snapshot.moneyDebt()),
            "minecraft:redstone", snapshot.moneyDebt() > 0 ? DANGER : SUCCESS),
            marginWeight(1));
        pageHost.addView(metrics);

        LinearLayout state = panel(context);
        state.addView(sectionTitle(context, "ПОЛИТИЧЕСКИЙ ПРОФИЛЬ"));
        state.addView(infoLine(context, "minecraft:compass", "Экономическое направление", snapshot.direction()));
        state.addView(infoLine(context, "minecraft:iron_sword", "Форма правления", snapshot.government()));
        state.addView(infoLine(context, "minecraft:book", "Религия", snapshot.religion()));
        state.addView(infoLine(context, "minecraft:player_head", "Население", format(snapshot.population())));
        state.addView(infoLine(context, "minecraft:bread", "Рабочая сила",
            signed(snapshot.populationWorkforceModifier())));
        state.setPadding(dp(14), dp(12), dp(14), dp(12));
        pageHost.addView(state, marginPanel());

        LinearLayout development = panel(context);
        development.addView(sectionTitle(context,
            "РАЗВИТИЕ ГОСУДАРСТВА  •  " + snapshot.developmentLevel() + "/5"));
        development.addView(label(
            context,
            snapshot.developmentLevel() >= 5
                ? "Максимальный уровень"
                : format(snapshot.developmentPoints()) + " / "
                    + format(snapshot.developmentNextThreshold()) + " очков",
            12,
            TEXT
        ));

        if (snapshot.developmentLevel() < 5) {
            float progress = snapshot.developmentNextThreshold() <= 0
                ? 1f
                : Math.min(1f, snapshot.developmentPoints()
                    / (float) snapshot.developmentNextThreshold());
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(7));
            p.setMargins(0, dp(8), 0, dp(8));
            development.addView(progressBar(context, progress, GOLD), p);
        }

        development.addView(label(context, "Текущий эффект: " + snapshot.developmentPerk(), 12, SUCCESS));
        if (snapshot.developmentLevel() < 5) {
            development.addView(label(context, "Следующий: " + snapshot.developmentNextPerk(),
                11, MUTED));
        }
        pageHost.addView(development, marginPanel());

        LinearLayout warehouse = panel(context);
        warehouse.addView(sectionTitle(context, "ГОСУДАРСТВЕННЫЙ СКЛАД"));
        warehouse.addView(label(context,
            "Ресурсы автоматически расходуются на содержание инфраструктуры.",
            11, MUTED));

        for (int i = 0; i < snapshot.materialIds().length; i++) {
            addResourceRow(context, warehouse, i);
        }
        pageHost.addView(warehouse, marginPanel());
    }

    private void showCountrySettings(Context context) {
        pageHost.removeAllViews();

        LinearLayout intro = panel(context);
        intro.addView(sectionTitle(context, "УПРАВЛЕНИЕ ГОСУДАРСТВОМ"));
        intro.addView(label(context,
            "Первый выбор каждого параметра бесплатный. Повторная смена — это реформа: " +
                "она требует денег и ресурсов и снижает накопленное развитие.",
            12, MUTED));
        pageHost.addView(intro);

        LinearLayout direction = panel(context);
        direction.addView(sectionTitle(context, "ЭКОНОМИЧЕСКОЕ НАПРАВЛЕНИЕ"));
        direction.addView(label(context,
            "Определяет специализацию национальной экономики.",
            11, MUTED));
        for (CountryDirection value : CountryDirection.values()) {
            addChoice(direction, context, value.displayName(), value.commandName(),
                "direction", snapshot.direction().equals(value.displayName()));
        }
        pageHost.addView(direction, marginPanel());

        LinearLayout government = panel(context);
        government.addView(sectionTitle(context, "ФОРМА ПРАВЛЕНИЯ"));
        government.addView(label(context,
            "Определяет набор политико-экономических игровых модификаторов.",
            11, MUTED));
        for (GovernmentType value : GovernmentType.values()) {
            addChoice(government, context, value.displayName(), value.commandName(),
                "government", snapshot.government().equals(value.displayName()));
        }
        pageHost.addView(government, marginPanel());

        LinearLayout religion = panel(context);
        religion.addView(sectionTitle(context, "РЕЛИГИЯ"));
        religion.addView(label(context,
            "Даёт дополнительные игровые экономические эффекты.",
            11, MUTED));
        for (ReligionType value : ReligionType.values()) {
            addChoice(religion, context, value.displayName(), value.commandName(),
                "religion", snapshot.religion().equals(value.displayName()));
        }
        pageHost.addView(religion, marginPanel());
    }

    private void showEffects(Context context) {
        pageHost.removeAllViews();

        LinearLayout intro = panel(context);
        intro.addView(sectionTitle(context, "ЭФФЕКТЫ ГОСУДАРСТВА"));
        intro.addView(label(context,
            "Итоговые значения учитывают экономическое направление, развитие, политику и население.",
            12, MUTED));
        pageHost.addView(intro);

        LinearLayout buffs = panel(context);
        buffs.addView(sectionTitle(context, "ПОЛОЖИТЕЛЬНЫЕ ЭФФЕКТЫ"));
        boolean hasBuff = false;
        for (int i = 0; i < snapshot.modifierNames().length; i++) {
            double value = valueAt(snapshot.modifierValues(), i);
            if (value > 0.0001D) {
                buffs.addView(effectRow(context, snapshot.modifierNames()[i], value));
                hasBuff = true;
            }
        }
        if (!hasBuff) buffs.addView(label(context,
            "Нет активных положительных эффектов.", 12, MUTED));
        pageHost.addView(buffs, marginPanel());

        LinearLayout debuffs = panel(context);
        debuffs.addView(sectionTitle(context, "ОТРИЦАТЕЛЬНЫЕ ЭФФЕКТЫ"));
        boolean hasDebuff = false;
        for (int i = 0; i < snapshot.modifierNames().length; i++) {
            double value = valueAt(snapshot.modifierValues(), i);
            if (value < -0.0001D) {
                debuffs.addView(effectRow(context, snapshot.modifierNames()[i], value));
                hasDebuff = true;
            }
        }
        if (!hasDebuff) debuffs.addView(label(context,
            "Нет активных отрицательных эффектов.", 12, MUTED));
        pageHost.addView(debuffs, marginPanel());
    }

    private void showCities(Context context) {
        pageHost.removeAllViews();

        LinearLayout intro = panel(context);
        intro.addView(sectionTitle(context, "ГОРОДА СЕРВЕРА"));
        intro.addView(label(context,
            "Экономические показатели городов всех государств на сервере.",
            12, MUTED));
        pageHost.addView(intro);

        for (int i = 0; i < snapshot.cityNames().length; i++) {
            LinearLayout card = panel(context);
            String title = snapshot.cityCapitals()[i]
                ? "СТОЛИЦА  •  " + snapshot.cityNames()[i]
                : snapshot.cityNames()[i];

            if (snapshot.cityMine()[i]) {
                title += "  •  ВАША";
            }

            card.addView(label(context,
                title,
                14,
                snapshot.cityCapitals()[i] ? GOLD : TEXT
            ));
            card.addView(label(context, snapshot.cityCountries()[i], 11, MUTED));

            LinearLayout metrics = row(context);
            metrics.addView(cityStat(context, "minecraft:emerald",
                "Казна", "$" + format(snapshot.cityTreasuries()[i])),
                new LinearLayout.LayoutParams(0, dp(54), 1));
            metrics.addView(cityStat(context, "minecraft:paper",
                "Доход", "$" + format(snapshot.cityIncome()[i])), marginWeightCity(1));
            metrics.addView(cityStat(context, "minecraft:player_head",
                "Население", format(snapshot.cityPopulation()[i])), marginWeightCity(1));
            metrics.addView(cityStat(context, "minecraft:iron_ingot",
                "Инфраструктура", format(snapshot.cityInfrastructure()[i])),
                marginWeightCity(1));
            card.addView(metrics);

            card.addView(infoLine(context, "minecraft:lectern", "Налоговые блоки",
                format(snapshot.cityTaxBlocks()[i])));
            card.addView(infoLine(context, "minecraft:player_head", "Мэр",
                snapshot.cityMayors()[i]));

            pageHost.addView(card, marginPanel());
        }

        if (snapshot.cityNames().length == 0) {
            LinearLayout empty = panel(context);
            empty.addView(label(context,
                "На сервере пока нет зарегистрированных городов.",
                12, MUTED));
            pageHost.addView(empty, marginPanel());
        }
    }

    private void addChoice(
        LinearLayout parent,
        Context context,
        String title,
        String command,
        String action,
        boolean selected
    ) {
        LinearLayout row = row(context);
        row.setBackground(solid(selected ? 0xFF273021 : PANEL_2, dp(8)));
        row.setPadding(dp(10), dp(4), dp(10), dp(4));

        row.addView(itemIcon(context, iconForChoice(action), 26),
            new LinearLayout.LayoutParams(dp(30), dp(36)));

        LinearLayout textBlock = column(context);
        textBlock.addView(label(context, title, 12, selected ? GOLD : TEXT));
        textBlock.addView(label(context,
            selected ? "Текущий выбор" : reformCostText(action, isFirstChoice(action)),
            10,
            selected ? SUCCESS : MUTED
        ));
        row.addView(textBlock, new LinearLayout.LayoutParams(0, dp(40), 1));

        Button choose = smallButton(context, selected ? "ВЫБРАНО" : "ВЫБРАТЬ");
        choose.setTextColor(selected ? SUCCESS : TEXT);
        choose.setEnabled(!selected);
        if (!selected) {
            choose.setOnClickListener(v -> showConfirmation(
                context, action, command, title, isFirstChoice(action)
            ));
        }
        row.addView(choose, new LinearLayout.LayoutParams(dp(92), dp(38)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.setMargins(0, dp(6), 0, 0);
        parent.addView(row, params);
    }

    private boolean isFirstChoice(String action) {
        return switch (action) {
            case "direction" -> "Не выбрано".equals(snapshot.direction());
            case "government" -> "Не выбрано".equals(snapshot.government());
            case "religion" -> "Не выбрано".equals(snapshot.religion());
            default -> true;
        };
    }

    private String reformCostText(String action, boolean firstChoice) {
        if (firstChoice) return "ПЕРВЫЙ ВЫБОР • БЕСПЛАТНО";
        return CountryReformCostTable
            .cost(
                action,
                false,
                snapshot.population(),
                snapshot.developmentLevel()
            )
            .summary();
    }

    private void showConfirmation(
        Context context,
        String action,
        String command,
        String title,
        boolean firstChoice
    ) {
        removePopup();

        popupOverlay = new FrameLayout(context);
        popupOverlay.setBackground(solid(0xB8000000, 0));
        popupOverlay.setOnClickListener(v -> removePopup());

        LinearLayout modal = column(context);
        modal.setBackground(solid(SHEET, dp(14)));
        modal.setPadding(dp(20), dp(18), dp(20), dp(16));

        FrameLayout.LayoutParams modalParams =
            new FrameLayout.LayoutParams(dp(540), -2);
        modalParams.gravity = Gravity.CENTER;
        popupOverlay.addView(modal, modalParams);

        modal.addView(label(context, "ПОДТВЕРЖДЕНИЕ РЕФОРМЫ", 18, TEXT));
        modal.addView(label(context, title.toUpperCase(Locale.ROOT), 14, GOLD));

        String description = firstChoice
            ? "Это первый выбор данного параметра. Он устанавливается бесплатно."
            : "Это полноценная реформа государства. Стоимость будет списана из казны и государственного склада.";

        TextView desc = label(context, description, 12, MUTED);
        desc.setGravity(Gravity.START | Gravity.TOP);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, dp(52));
        descParams.setMargins(0, dp(10), 0, dp(8));
        modal.addView(desc, descParams);

        LinearLayout costBox = column(context);
        costBox.setBackground(solid(PANEL, dp(9)));
        costBox.setPadding(dp(12), dp(10), dp(12), dp(10));
        costBox.addView(label(context, "СТОИМОСТЬ", 10, MUTED));

        if (firstChoice) {
            costBox.addView(label(context, "Бесплатно", 16, SUCCESS));
        } else {
            CountryReformCostTable.Cost cost =
                CountryReformCostTable.cost(
                    action,
                    false,
                    snapshot.population(),
                    snapshot.developmentLevel()
                );

            modalCostLine(
                context,
                costBox,
                "minecraft:emerald",
                "$" + Integer.toString(cost.money()),
                "Казна",
                snapshot.treasury() >= cost.money()
            );

            for (Map.Entry<String, Integer> entry : cost.materials().entrySet()) {
                modalCostLine(
                    context,
                    costBox,
                    entry.getKey(),
                    Integer.toString(entry.getValue()),
                    "государственный склад",
                    true
                );
            }

            costBox.addView(label(
                context,
                "Стоимость зависит от населения и уровня развития страны.",
                10,
                MUTED
            ));
        }

        modal.addView(costBox);

        LinearLayout buttons = row(context);
        Button cancel = smallButton(context, "ОТМЕНА");
        cancel.setTextColor(MUTED);
        cancel.setOnClickListener(v -> removePopup());
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, dp(42), 1));

        Button confirm = smallButton(context, "ПОДТВЕРДИТЬ");
        confirm.setTextColor(firstChoice ? SUCCESS : GOLD);
        confirm.setBackground(solid(firstChoice ? 0xFF1E352A : 0xFF332B18, dp(8)));
        confirm.setOnClickListener(v -> {
            removePopup();
            EconomyNetwork.sendAction(action, command);
        });
        buttons.addView(confirm, marginButton());
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(-1, dp(42));
        buttonsParams.setMargins(0, dp(12), 0, 0);
        modal.addView(buttons, buttonsParams);

        screenRoot.addView(popupOverlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private void modalCostLine(
        Context context,
        LinearLayout parent,
        String itemId,
        String amount,
        String label,
        boolean enabled
    ) {
        LinearLayout line = row(context);
        line.addView(itemIcon(context, itemId, 24),
            new LinearLayout.LayoutParams(dp(28), dp(28)));
        line.addView(label(context, amount, 13, enabled ? TEXT : MUTED),
            new LinearLayout.LayoutParams(dp(60), dp(28)));
        line.addView(label(context, label, 12, MUTED),
            new LinearLayout.LayoutParams(0, dp(28), 1));
        parent.addView(line);
    }

    private void showAdmin(Context context) {
        pageHost.removeAllViews();

        LinearLayout admin = panel(context);
        admin.addView(sectionTitle(context, "АДМИНИСТРАТОРСКИЙ РЕЖИМ"));
        admin.addView(label(context,
            "Панель доступна только в Creative. Все действия дополнительно проверяются сервером.",
            12, MUTED));
        admin.addView(label(context,
            "Обычная стоимость реформ отключается только для Creative-оператора.",
            11, GOLD));
        pageHost.addView(admin);
    }

    private View effectRow(Context context, String name, double value) {
        LinearLayout row = row(context);
        int color = value > 0.0001D ? SUCCESS
            : value < -0.0001D ? DANGER : MUTED;

        row.addView(itemIcon(context, iconForModifier(name), 26),
            new LinearLayout.LayoutParams(dp(30), dp(34)));
        row.addView(label(context, name, 12, TEXT),
            new LinearLayout.LayoutParams(0, dp(34), 1));

        TextView valueView = label(context, signed(value), 13, color);
        valueView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(valueView, new LinearLayout.LayoutParams(dp(72), dp(34)));

        row.setBackground(solid(PANEL_3, dp(7)));
        row.setPadding(dp(10), 0, dp(10), 0);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(38));
        params.setMargins(0, dp(5), 0, 0);
        LinearLayout wrapper = column(context);
        wrapper.addView(row, params);
        return wrapper;
    }

    private void addResourceRow(Context context, LinearLayout parent, int index) {
        String name = index < snapshot.materialNames().length
            ? snapshot.materialNames()[index] : "Материал";
        String itemId = index < snapshot.materialIds().length
            ? snapshot.materialIds()[index] : "";
        int stock = valueAt(snapshot.materialStockpile(), index);
        int debt = valueAt(snapshot.materialDebt(), index);
        double cost = doubleAt(snapshot.materialPerCycle(), index);

        int max = Math.max(1, (int) Math.ceil(cost * 20.0D));
        float ratio = Math.min(1.0f, stock / (float) max);

        LinearLayout card = column(context);
        card.setBackground(solid(PANEL_3, dp(8)));
        card.setPadding(dp(10), dp(8), dp(10), dp(8));

        LinearLayout top = row(context);
        top.addView(itemIcon(context, itemId, 30),
            new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout title = column(context);
        title.addView(label(context, name, 13, TEXT));
        title.addView(label(context,
            String.format(Locale.ROOT, "-%.2f ед. / цикл", cost),
            10,
            debt > 0 ? DANGER : MUTED
        ));
        top.addView(title, new LinearLayout.LayoutParams(0, dp(36), 1));

        top.addView(label(context, format(stock) + " ед.", 13,
            stock > 0 ? STEEL : DANGER));
        card.addView(top);

        if (debt > 0) {
            card.addView(label(context,
                "Ресурсный долг: " + format(debt),
                10,
                DANGER
            ));
        }

        card.addView(progressBar(context, ratio, ratio > 0.25f ? GOLD : DANGER));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, 0);
        parent.addView(card, params);
    }

    private View metricCard(
        Context context,
        String title,
        String value,
        String iconItem,
        int accent
    ) {
        LinearLayout card = column(context);
        card.setBackground(solid(PANEL, dp(9)));
        card.setPadding(dp(10), dp(8), dp(10), dp(8));

        LinearLayout head = row(context);
        head.addView(itemIcon(context, iconItem, 22),
            new LinearLayout.LayoutParams(dp(25), dp(25)));
        head.addView(label(context, title, 9, MUTED));
        card.addView(head);

        TextView bottom = label(context, value, 15, accent);
        bottom.setGravity(Gravity.BOTTOM | Gravity.START);
        card.addView(bottom, new LinearLayout.LayoutParams(-1, 0, 1));
        return card;
    }

    private View cityStat(Context context, String icon, String title, String value) {
        LinearLayout box = column(context);
        box.setBackground(solid(PANEL_3, dp(7)));
        box.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout top = row(context);
        top.addView(itemIcon(context, icon, 20),
            new LinearLayout.LayoutParams(dp(22), dp(22)));
        top.addView(label(context, title, 9, MUTED));
        box.addView(top);
        box.addView(label(context, value, 12, TEXT));
        return box;
    }

    private View infoLine(Context context, String iconItem, String name, String value) {
        LinearLayout line = row(context);
        line.addView(itemIcon(context, iconItem, 22),
            new LinearLayout.LayoutParams(dp(26), dp(28)));
        line.addView(label(context, name, 11, MUTED),
            new LinearLayout.LayoutParams(0, dp(30), 1));
        TextView valueView = label(context, value, 11, TEXT);
        valueView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        line.addView(valueView, new LinearLayout.LayoutParams(dp(285), dp(30)));
        return line;
    }

    private View itemIcon(Context context, String itemId, int size) {
        FrameLayout box = new FrameLayout(context);
        box.setBackground(solid(PANEL_3, dp(6)));

        Image image = loadItemImage(itemId);
        if (image != null) {
            ImageView imageView = new ImageView(context);
            imageView.setImageDrawable(new ImageDrawable(image));
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

            FrameLayout.LayoutParams iconParams =
                new FrameLayout.LayoutParams(-1, -1);
            iconParams.setMargins(dp(3), dp(3), dp(3), dp(3));
            box.addView(imageView, iconParams);
        } else {
            TextView fallback = label(context, iconFallback(itemId), 9, STEEL);
            fallback.setGravity(Gravity.CENTER);
            box.addView(
                fallback,
                new FrameLayout.LayoutParams(-1, -1)
            );
        }

        return box;
    }

    /**
     * Loads a real Minecraft item/block texture through Minecraft's ResourceManager.
     * This bypasses the unreliable legacy Image.create(namespace, path) bridge.
     */
    private Image loadItemImage(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }

        WeakReference<Image> cached = ITEM_ICON_CACHE.get(itemId);
        if (cached != null) {
            Image image = cached.get();
            if (image != null && !image.isClosed()) {
                return image;
            }
        }

        try {
            ResourceLocation id = ResourceLocation.parse(itemId);

            Image image = loadTexture(
                ResourceLocation.fromNamespaceAndPath(
                    id.getNamespace(),
                    "textures/item/" + id.getPath() + ".png"
                )
            );

            if (image == null) {
                image = loadTexture(
                    ResourceLocation.fromNamespaceAndPath(
                        id.getNamespace(),
                        "textures/block/" + id.getPath() + ".png"
                    )
                );
            }

            if (image != null) {
                ITEM_ICON_CACHE.put(itemId, new WeakReference<>(image));
            }

            return image;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Image loadTexture(ResourceLocation location) {
        try {
            var resource = Minecraft.getInstance()
                .getResourceManager()
                .getResource(location)
                .orElse(null);

            if (resource == null) {
                return null;
            }

            try (var stream = resource.open();
                 var bitmap = BitmapFactory.decodeStream(stream)) {
                if (bitmap == null) {
                    return null;
                }
                return Image.createTextureFromBitmap(bitmap);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private String iconFallback(String itemId) {
        if (itemId == null) return "?";

        String path = itemId;
        int colon = path.indexOf(':');
        if (colon >= 0) {
            path = path.substring(colon + 1);
        }

        return switch (path) {
            case "emerald" -> "EM";
            case "iron_ingot" -> "FE";
            case "gold_ingot" -> "AU";
            case "coal" -> "CO";
            case "gunpowder" -> "GP";
            case "paper" -> "PA";
            case "wheat" -> "WH";
            case "bread" -> "BR";
            case "book" -> "BK";
            case "compass" -> "CP";
            case "iron_sword" -> "SW";
            case "player_head" -> "PO";
            case "anvil" -> "AN";
            case "redstone" -> "RS";
            case "lectern" -> "LT";
            default -> {
                String clean = path.replace('_', ' ').trim();
                yield clean.isEmpty()
                    ? "?"
                    : clean.substring(0, Math.min(2, clean.length()))
                        .toUpperCase(Locale.ROOT);
            }
        };
    }

    private String iconForChoice(String action) {
        return switch (action) {
            case "direction" -> "minecraft:compass";
            case "government" -> "minecraft:iron_sword";
            case "religion" -> "minecraft:book";
            default -> "minecraft:paper";
        };
    }

    private String iconForModifier(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("промышлен")) return "minecraft:iron_ingot";
        if (n.contains("военн")) return "minecraft:iron_sword";
        if (n.contains("ресурс") || n.contains("добыч")) return "minecraft:coal";
        if (n.contains("сель")) return "minecraft:wheat";
        if (n.contains("торгов")) return "minecraft:emerald";
        if (n.contains("дизел")) return "minecraft:coal";
        if (n.contains("населен") || n.contains("рабоч")) return "minecraft:bread";
        if (n.contains("содержан") || n.contains("стоимость")) return "minecraft:iron_ingot";
        return "minecraft:paper";
    }

    private LinearLayout panel(Context context) {
        LinearLayout panel = column(context);
        panel.setBackground(solid(PANEL, dp(10)));
        panel.setPadding(dp(14), dp(11), dp(14), dp(11));
        return panel;
    }

    private TextView sectionTitle(Context context, String text) {
        return label(context, text, 13, TEXT);
    }

    private Button tabButton(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextSize(10);
        button.setTextColor(TEXT);
        button.setBackground(solid(PANEL_2, dp(7)));
        return button;
    }

    private Button smallButton(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextSize(11);
        button.setTextColor(TEXT);
        button.setBackground(solid(PANEL_2, dp(7)));
        return button;
    }

    private LinearLayout.LayoutParams marginWeight(float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(78), weight);
        p.setMargins(dp(7), 0, 0, 0);
        return p;
    }

    private LinearLayout.LayoutParams marginWeightCity(float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(54), weight);
        p.setMargins(dp(6), 0, 0, 0);
        return p;
    }

    private LinearLayout.LayoutParams marginTab() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(40), 1);
        p.setMargins(dp(6), 0, 0, 0);
        return p;
    }

    private LinearLayout.LayoutParams marginPanel() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(8), 0, 0);
        return p;
    }

    private LinearLayout.LayoutParams marginButton() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(42), 1);
        p.setMargins(dp(8), 0, 0, 0);
        return p;
    }

    private View progressBar(Context context, float ratio, int color) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(solid(0xFF0A1017, dp(4)));

        float fill = Math.max(0.01f, Math.min(1.0f, ratio));
        View active = new View(context);
        active.setBackground(solid(color, dp(4)));
        View rest = new View(context);
        rest.setBackground(solid(LINE, dp(4)));

        bar.addView(active, new LinearLayout.LayoutParams(0, dp(6), fill));
        bar.addView(rest, new LinearLayout.LayoutParams(0, dp(6), 1.0f - fill));
        return bar;
    }

    private static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private static TextView label(Context context, String text, float size, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return view;
    }

    private void removePopup() {
        if (popupOverlay != null) {
            ViewGroup parent = (ViewGroup) popupOverlay.getParent();
            if (parent != null) {
                parent.removeView(popupOverlay);
            }
            popupOverlay = null;
        }
    }

    private void closeDashboard() {
        removePopup();
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(null));
    }

    private static int valueAt(int[] values, int index) {
        return values != null && index >= 0 && index < values.length ? values[index] : 0;
    }

    private static double valueAt(double[] values, int index) {
        return values != null && index >= 0 && index < values.length ? values[index] : 0.0D;
    }

    private static double doubleAt(double[] values, int index) {
        return valueAt(values, index);
    }

    private static String format(int value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0, value));
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%,.2f", Math.max(0, value));
    }

    private static String signed(double value) {
        if (Math.abs(value) < 0.0001D) return "0%";
        return String.format(Locale.ROOT, "%+.0f%%", value);
    }

    private int dp(int value) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(value * density));
    }

    private ShapeDrawable solid(int color, int radius) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setShape(ShapeDrawable.RECTANGLE);
        drawable.setColor(color);
        if (radius > 0) {
            drawable.setCornerRadius(radius);
        }
        return drawable;
    }
}
