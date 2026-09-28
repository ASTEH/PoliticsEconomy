package ru.zela.politicseconomy.client;

import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import ru.zela.politicseconomy.country.CountryDirection;
import ru.zela.politicseconomy.country.GovernmentType;
import ru.zela.politicseconomy.country.ReligionType;
import ru.zela.politicseconomy.network.EconomyNetwork;
import ru.zela.politicseconomy.network.EconomySnapshotPayload;

import java.util.Locale;

/**
 * Country dashboard: overview, country settings and all active gameplay modifiers.
 * Political choices are submitted to the server; the client never applies them locally.
 */
public final class EconomyFragment extends Fragment {
    private static final int BG = 0xFF080D13;
    private static final int SHEET = 0xFF101923;
    private static final int PANEL = 0xFF172330;
    private static final int PANEL_2 = 0xFF202E3D;
    private static final int PANEL_3 = 0xFF0F1923;
    private static final int TEXT = 0xFFF3F6F8;
    private static final int MUTED = 0xFF8FA1B2;
    private static final int ACCENT = 0xFF55D6A2;
    private static final int WARNING = 0xFFFFC857;
    private static final int DANGER = 0xFFFF7070;
    private static final int INFO = 0xFF6EB7FF;
    private static final int PURPLE = 0xFFB58AFF;

    private final EconomySnapshotPayload snapshot;
    private LinearLayout pageHost;

    public EconomyFragment(EconomySnapshotPayload snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        Context context = requireContext();

        FrameLayout background = new FrameLayout(context);
        background.setBackground(solid(BG, 0));

        LinearLayout root = column(context);
        root.setBackground(solid(SHEET, dp(16)));
        root.setPadding(dp(20), dp(16), dp(20), dp(14));

        FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(dp(960), dp(700));
        rootParams.gravity = Gravity.CENTER;
        background.addView(root, rootParams);

        LinearLayout header = row(context);
        LinearLayout titleBlock = column(context);
        titleBlock.addView(label(context, "◈  ГОСУДАРСТВО", 22, TEXT));
        titleBlock.addView(label(context,
            snapshot.countryName() + "  •  " + snapshot.direction(),
            13, ACCENT));
        header.addView(titleBlock, new LinearLayout.LayoutParams(0, -2, 1));

        TextView population = label(context,
            "👥 " + format(snapshot.population()) + "  |  Рабочая сила " +
                signed(snapshot.populationWorkforceModifier()),
            12, INFO);
        header.addView(population);

        Button close = smallButton(context, "×");
        close.setOnClickListener(v -> closeDashboard());
        header.addView(close, new LinearLayout.LayoutParams(dp(42), dp(38)));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout tabs = row(context);
        Button overviewTab = tabButton(context, "▦  ОБЗОР");
        Button countryTab = tabButton(context, "⚙  СТРАНА");
        Button effectsTab = tabButton(context, "◆  ЭФФЕКТЫ");
        Button citiesTab = tabButton(context, "⌂  ГОРОДА");
        tabs.addView(overviewTab, new LinearLayout.LayoutParams(0, dp(42), 1));
        tabs.addView(countryTab, marginTab());
        tabs.addView(effectsTab, marginTab());
        tabs.addView(citiesTab, marginTab());
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(-1, dp(42));
        tabParams.setMargins(0, dp(8), 0, dp(8));
        root.addView(tabs, tabParams);

        pageHost = column(context);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(pageHost);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        overviewTab.setOnClickListener(v -> showOverview(context));
        countryTab.setOnClickListener(v -> showCountrySettings(context));
        effectsTab.setOnClickListener(v -> showEffects(context));
        citiesTab.setOnClickListener(v -> showCities(context));

        showOverview(context);
        return background;
    }

    private void showOverview(Context context) {
        pageHost.removeAllViews();

        LinearLayout metrics = row(context);
        metrics.addView(metricCard(context, "₿  КАЗНА", "$" + format(snapshot.treasury()), INFO),
            new LinearLayout.LayoutParams(0, dp(78), 1));
        metrics.addView(metricCard(context, "◆  МАТЕРИАЛЫ",
                String.format(Locale.ROOT, "-%.2f / цикл", snapshot.totalMaterialPerCycle()), WARNING),
            marginWeight(1));
        metrics.addView(metricCard(context, "⚙  СОДЕРЖАНИЕ",
                String.format(Locale.ROOT, "-%.2f $", snapshot.infrastructureCost()), WARNING),
            marginWeight(1));
        metrics.addView(metricCard(context, "◈  ДОЛГ",
                "$" + formatDouble(snapshot.moneyDebt()), snapshot.moneyDebt() > 0 ? DANGER : ACCENT),
            marginWeight(1));
        pageHost.addView(metrics);

        LinearLayout state = panel(context);
        state.addView(sectionTitle(context, "СОСТОЯНИЕ ГОСУДАРСТВА"));
        state.addView(infoLine(context, "⚑", "Направление", snapshot.direction()));
        state.addView(infoLine(context, "⌂", "Форма правления", snapshot.government()));
        state.addView(infoLine(context, "◇", "Религия", snapshot.religion()));
        state.addView(infoLine(context, "👥", "Население", format(snapshot.population())));
        state.addView(infoLine(context, "⚡", "Рабочая сила", signed(snapshot.populationWorkforceModifier())));
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-1, -2);
        stateParams.setMargins(0, dp(10), 0, 0);
        pageHost.addView(state, stateParams);

        LinearLayout development = panel(context);
        development.addView(sectionTitle(context, "РАЗВИТИЕ  •  УРОВЕНЬ " + snapshot.developmentLevel() + "/5"));
        String progressText = snapshot.developmentLevel() >= 5
            ? "Все уровни развития открыты"
            : format(snapshot.developmentPoints()) + " / " +
                format(snapshot.developmentNextThreshold()) + " очков развития";
        development.addView(label(context, progressText, 12, TEXT));
        if (snapshot.developmentLevel() < 5) {
            float progress = snapshot.developmentNextThreshold() <= 0 ? 1f :
                Math.min(1f, snapshot.developmentPoints() /
                    (float) snapshot.developmentNextThreshold());
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(7));
            p.setMargins(0, dp(8), 0, dp(8));
            development.addView(progressBar(context, progress, PURPLE), p);
        }
        development.addView(label(context, "✓ " + snapshot.developmentPerk(), 12, ACCENT));
        if (snapshot.developmentLevel() < 5) {
            development.addView(label(context, "→ Далее: " + snapshot.developmentNextPerk(), 11, MUTED));
        }
        LinearLayout.LayoutParams devParams = new LinearLayout.LayoutParams(-1, -2);
        devParams.setMargins(0, dp(10), 0, 0);
        pageHost.addView(development, devParams);

        LinearLayout warehouse = panel(context);
        warehouse.addView(sectionTitle(context, "ГОСУДАРСТВЕННЫЙ СКЛАД"));
        for (int i = 0; i < snapshot.materialIds().length; i++) {
            addResourceRow(context, warehouse, i);
        }
        LinearLayout.LayoutParams whParams = new LinearLayout.LayoutParams(-1, -2);
        whParams.setMargins(0, dp(10), 0, 0);
        pageHost.addView(warehouse, whParams);
    }

    private void showCountrySettings(Context context) {
        pageHost.removeAllViews();

        LinearLayout intro = panel(context);
        intro.addView(sectionTitle(context, "⚙  НАСТРОЙКА СТРАНЫ"));
        intro.addView(label(context,
            "Здесь лидер государства выбирает основные параметры страны. " +
                "После выбора обычным игрокам изменить их нельзя.",
            12, MUTED));
        pageHost.addView(intro);

        LinearLayout direction = panel(context);
        direction.addView(sectionTitle(context, "⚑  ЭКОНОМИЧЕСКОЕ НАПРАВЛЕНИЕ"));
        direction.addView(label(context,
            "Определяет основной профиль экономики государства.",
            11, MUTED));

        for (CountryDirection value : CountryDirection.values()) {
            String current = snapshot.direction();
            addChoice(direction, context, value.displayName(),
                value.commandName(), "direction", current.equals(value.displayName()));
        }
        pageHost.addView(direction, marginPanel());

        LinearLayout government = panel(context);
        government.addView(sectionTitle(context, "♜  ФОРМА ПРАВЛЕНИЯ"));
        government.addView(label(context,
            "Игровой набор экономических модификаторов государства.",
            11, MUTED));
        for (GovernmentType value : GovernmentType.values()) {
            addChoice(government, context, value.displayName(),
                value.commandName(), "government",
                snapshot.government().equals(value.displayName()));
        }
        pageHost.addView(government, marginPanel());

        LinearLayout religion = panel(context);
        religion.addView(sectionTitle(context, "◇  РЕЛИГИЯ"));
        religion.addView(label(context,
            "Игровой набор дополнительных экономических модификаторов.",
            11, MUTED));
        for (ReligionType value : ReligionType.values()) {
            addChoice(religion, context, value.displayName(),
                value.commandName(), "religion",
                snapshot.religion().equals(value.displayName()));
        }
        pageHost.addView(religion, marginPanel());

        LinearLayout note = panel(context);
        note.addView(sectionTitle(context, "◆  ПОДСКАЗКА"));
        note.addView(label(context,
            "После выбора изменения вступают в силу на сервере, а экран автоматически обновится.",
            12, TEXT));
        pageHost.addView(note, marginPanel());
    }

    private void showEffects(Context context) {
        pageHost.removeAllViews();

        LinearLayout intro = panel(context);
        intro.addView(sectionTitle(context, "◆  АКТИВНЫЕ ЭФФЕКТЫ"));
        intro.addView(label(context,
            "Здесь показан итоговый игровой эффект направления, развития, политики и населения.",
            12, MUTED));
        pageHost.addView(intro);

        LinearLayout buffs = panel(context);
        buffs.addView(sectionTitle(context, "▲  БАФФЫ"));
        boolean hasBuff = false;
        for (int i = 0; i < snapshot.modifierNames().length; i++) {
            double value = valueAt(snapshot.modifierValues(), i);
            if (value > 0.0001D) {
                buffs.addView(effectRow(context, snapshot.modifierNames()[i], value, true));
                hasBuff = true;
            }
        }
        if (!hasBuff) buffs.addView(label(context, "Активных положительных эффектов нет.", 12, MUTED));
        pageHost.addView(buffs, marginPanel());

        LinearLayout debuffs = panel(context);
        debuffs.addView(sectionTitle(context, "▼  ДЕБАФФЫ"));
        boolean hasDebuff = false;
        for (int i = 0; i < snapshot.modifierNames().length; i++) {
            double value = valueAt(snapshot.modifierValues(), i);
            if (value < -0.0001D) {
                debuffs.addView(effectRow(context, snapshot.modifierNames()[i], value, false));
                hasDebuff = true;
            }
        }
        if (!hasDebuff) debuffs.addView(label(context, "Активных отрицательных эффектов нет.", 12, MUTED));
        pageHost.addView(debuffs, marginPanel());

        LinearLayout neutral = panel(context);
        neutral.addView(sectionTitle(context, "○  БЕЗ ИЗМЕНЕНИЙ"));
        boolean hasNeutral = false;
        for (int i = 0; i < snapshot.modifierNames().length; i++) {
            double value = valueAt(snapshot.modifierValues(), i);
            if (Math.abs(value) <= 0.0001D) {
                neutral.addView(effectRow(context, snapshot.modifierNames()[i], 0, true));
                hasNeutral = true;
            }
        }
        if (!hasNeutral) neutral.addView(label(context, "Все отображаемые параметры имеют влияние.", 12, MUTED));
        pageHost.addView(neutral, marginPanel());

        LinearLayout policy = panel(context);
        policy.addView(sectionTitle(context, "◎  ПОЛИТИКА"));
        policy.addView(label(context,
            snapshot.government() + "  •  " + snapshot.religion(),
            13, TEXT));
        policy.addView(label(context, snapshot.policySummary(), 11, MUTED));
        pageHost.addView(policy, marginPanel());
    }

    private void showCities(Context context) {
        pageHost.removeAllViews();

        LinearLayout intro = panel(context);
        intro.addView(sectionTitle(context, "⌂  ГОРОДА СЕРВЕРА"));
        intro.addView(label(context,
            "Сводка по городам всех государств. Экономика показывает текущую городскую казну и доход за цикл.",
            12, MUTED));
        pageHost.addView(intro);

        for (int i = 0; i < snapshot.cityNames().length; i++) {
            LinearLayout card = panel(context);
            String title = (snapshot.cityCapitals()[i] ? "★ " : "⌂ ") + snapshot.cityNames()[i];
            if (snapshot.cityMine()[i]) title += "  •  ВАШ";
            card.addView(label(context, title + "  •  " + snapshot.cityCountries()[i], 13, TEXT));
            card.addView(infoLine(context, "₿", "Казна", "$" + format(snapshot.cityTreasuries()[i])));
            card.addView(infoLine(context, "↗", "Доход / цикл", "$" + format(snapshot.cityIncome()[i])));
            card.addView(infoLine(context, "👥", "Население", format(snapshot.cityPopulation()[i])));
            card.addView(infoLine(context, "▦", "Инфраструктура", format(snapshot.cityInfrastructure()[i])));
            card.addView(infoLine(context, "⌂", "Налоговые блоки", format(snapshot.cityTaxBlocks()[i])));
            card.addView(infoLine(context, "♟", "Мэр", snapshot.cityMayors()[i]));
            pageHost.addView(card, marginPanel());
        }

        if (snapshot.cityNames().length == 0) {
            LinearLayout empty = panel(context);
            empty.addView(label(context, "На сервере пока нет зарегистрированных городов.", 12, MUTED));
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
        Button button = new Button(context);
        button.setText((selected ? "✓  " : "○  ") + title);
        button.setTextSize(12);
        button.setTextColor(selected ? ACCENT : TEXT);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setBackground(solid(selected ? 0xFF18392F : PANEL_2, dp(8)));
        button.setEnabled(!selected);
        if (!selected) {
            button.setOnClickListener(v -> EconomyNetwork.sendAction(action, command));
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(40));
        params.setMargins(0, dp(6), 0, 0);
        parent.addView(button, params);
    }

    private View effectRow(Context context, String name, double value, boolean positive) {
        LinearLayout row = row(context);
        String icon = value > 0.0001D ? "▲" : value < -0.0001D ? "▼" : "○";
        int color = value > 0.0001D ? ACCENT : value < -0.0001D ? DANGER : MUTED;
        TextView iconView = label(context, icon, 14, color);
        row.addView(iconView, new LinearLayout.LayoutParams(dp(28), dp(34)));

        row.addView(label(context, name, 12, TEXT),
            new LinearLayout.LayoutParams(0, dp(34), 1));

        TextView valueView = label(context, signed(value), 13, color);
        valueView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(valueView, new LinearLayout.LayoutParams(dp(72), dp(34)));

        row.setBackground(solid(PANEL_3, dp(7)));
        row.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(38));
        params.setMargins(0, dp(5), 0, 0);
        return wrap(row, params);
    }

    private View wrap(View view, LinearLayout.LayoutParams params) {
        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.addView(view, params);
        return wrapper;
    }

    private void addResourceRow(Context context, LinearLayout parent, int index) {
        String name = index < snapshot.materialNames().length
            ? snapshot.materialNames()[index] : "Материал";
        int stock = valueAt(snapshot.materialStockpile(), index);
        int debt = valueAt(snapshot.materialDebt(), index);
        double cost = doubleAt(snapshot.materialPerCycle(), index);
        int max = Math.max(1, (int) Math.ceil(cost * 20.0D));
        float ratio = Math.min(1.0f, stock / (float) max);

        LinearLayout card = column(context);
        card.setBackground(solid(PANEL_3, dp(8)));
        card.setPadding(dp(12), dp(8), dp(12), dp(8));

        LinearLayout top = row(context);
        top.addView(label(context, "◆  " + name, 13, TEXT),
            new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(label(context, format(stock) + " ед.", 13,
            stock > 0 ? INFO : DANGER));
        card.addView(top);

        card.addView(label(context,
            String.format(Locale.ROOT, "Расход  -%.2f / цикл", cost) +
                (debt > 0 ? "   •   долг " + format(debt) : ""),
            11, debt > 0 ? DANGER : MUTED));
        card.addView(progressBar(context, ratio, ratio > 0.25f ? ACCENT : DANGER));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(68));
        params.setMargins(0, dp(5), 0, 0);
        parent.addView(card, params);
    }

    private View infoLine(Context context, String icon, String name, String value) {
        LinearLayout line = row(context);
        line.addView(label(context, icon, 13, ACCENT),
            new LinearLayout.LayoutParams(dp(28), dp(32)));
        line.addView(label(context, name, 12, MUTED),
            new LinearLayout.LayoutParams(0, dp(32), 1));
        TextView valueView = label(context, value, 12, TEXT);
        valueView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        line.addView(valueView, new LinearLayout.LayoutParams(dp(250), dp(32)));
        return line;
    }

    private LinearLayout panel(Context context) {
        LinearLayout panel = column(context);
        panel.setBackground(solid(PANEL, dp(11)));
        panel.setPadding(dp(14), dp(11), dp(14), dp(11));
        return panel;
    }

    private TextView sectionTitle(Context context, String text) {
        return label(context, text, 13, TEXT);
    }

    private Button tabButton(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextSize(11);
        button.setTextColor(TEXT);
        button.setBackground(solid(PANEL_2, dp(8)));
        return button;
    }

    private Button smallButton(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextSize(18);
        button.setTextColor(MUTED);
        button.setBackground(solid(PANEL_2, dp(8)));
        return button;
    }

    private LinearLayout.LayoutParams marginWeight(float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(78), weight);
        p.setMargins(dp(7), 0, 0, 0);
        return p;
    }

    private LinearLayout.LayoutParams marginTab() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(42), 1);
        p.setMargins(dp(7), 0, 0, 0);
        return p;
    }

    private LinearLayout.LayoutParams marginPanel() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(9), 0, 0);
        return p;
    }

    private View metricCard(Context context, String title, String value, int accent) {
        LinearLayout card = column(context);
        card.setBackground(solid(PANEL, dp(9)));
        card.setPadding(dp(11), dp(8), dp(11), dp(8));
        card.addView(label(context, title, 9, MUTED));
        TextView bottom = label(context, value, 16, accent);
        bottom.setGravity(Gravity.BOTTOM | Gravity.START);
        card.addView(bottom, new LinearLayout.LayoutParams(-1, 0, 1));
        return card;
    }

    private View progressBar(Context context, float ratio, int color) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(solid(0xFF0A121B, dp(4)));
        float fill = Math.max(0.01f, Math.min(1.0f, ratio));
        View active = new View(context);
        active.setBackground(solid(color, dp(4)));
        View rest = new View(context);
        rest.setBackground(solid(0xFF263544, dp(4)));
        bar.addView(active, new LinearLayout.LayoutParams(0, dp(7), fill));
        bar.addView(rest, new LinearLayout.LayoutParams(0, dp(7), 1.0f - fill));
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

    private void closeDashboard() {
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

    private static int total(int[] values) {
        int result = 0;
        if (values != null) for (int value : values) result += Math.max(0, value);
        return result;
    }

    private static String format(int value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0, value));
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%,.2f", value);
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
        if (radius > 0) drawable.setCornerRadius(radius);
        return drawable;
    }
}
