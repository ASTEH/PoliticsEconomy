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
import ru.zela.politicseconomy.network.EconomySnapshotPayload;

import java.util.Locale;

/** Modern country-economy dashboard built on Modern UI. */
public final class EconomyFragment extends Fragment {
    private static final int BG = 0xFF0B1118;
    private static final int SHEET = 0xFF111A24;
    private static final int PANEL = 0xFF172331;
    private static final int PANEL_2 = 0xFF202B39;
    private static final int TEXT = 0xFFF2F5F7;
    private static final int MUTED = 0xFF95A6B6;
    private static final int ACCENT = 0xFF54D6A1;
    private static final int WARNING = 0xFFFFC857;
    private static final int DANGER = 0xFFFF6B6B;
    private static final int INFO = 0xFF6CB6FF;
    private static final int PURPLE = 0xFFB78CFF;

    private final EconomySnapshotPayload snapshot;

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
        root.setPadding(dp(22), dp(18), dp(22), dp(16));
        FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(dp(930), dp(680));
        rootParams.gravity = Gravity.CENTER;
        background.addView(root, rootParams);

        LinearLayout header = column(context);
        TextView title = label(context, "ГОСУДАРСТВЕННАЯ ЭКОНОМИКА", 23, TEXT);
        TextView subtitle = label(context, snapshot.countryName() + "  •  " + snapshot.direction(), 14, ACCENT);
        header.addView(title);
        header.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, -2);
        headerParams.setMargins(0, 0, 0, dp(12));
        root.addView(header, headerParams);

        LinearLayout metrics = row(context);
        metrics.addView(metricCard(context, "КАЗНА", "$" + format(snapshot.treasury()), INFO),
            new LinearLayout.LayoutParams(0, dp(74), 1));
        metrics.addView(metricCard(context, "МАТЕРИАЛЫ", String.format(Locale.ROOT, "-%.2f / цикл", snapshot.totalMaterialPerCycle()), WARNING),
            marginWeight(1));
        metrics.addView(metricCard(context, "ИНФРАСТРУКТУРА", String.format(Locale.ROOT, "-%.2f $", snapshot.infrastructureCost()), WARNING),
            marginWeight(1));
        String diesel = String.format(Locale.ROOT, "%+.0f%%", snapshot.dieselModifier());
        metrics.addView(metricCard(context, "ДИЗЕЛЬ", diesel, snapshot.dieselModifier() < 0 ? ACCENT : MUTED),
            marginWeight(1));
        root.addView(metrics);

        LinearLayout development = column(context);
        development.setBackground(solid(PANEL, dp(12)));
        development.setPadding(dp(14), dp(11), dp(14), dp(11));
        LinearLayout.LayoutParams devParams = new LinearLayout.LayoutParams(-1, dp(118));
        devParams.setMargins(0, dp(12), 0, dp(10));
        root.addView(development, devParams);

        LinearLayout devTop = row(context);
        devTop.addView(label(context, "РАЗВИТИЕ ЭКОНОМИКИ", 11, MUTED), new LinearLayout.LayoutParams(0, -2, 1));
        devTop.addView(label(context, "УРОВЕНЬ " + snapshot.developmentLevel() + "/5", 16, PURPLE));
        development.addView(devTop);

        String progressText = snapshot.developmentLevel() >= 5
            ? "Все уровни развития открыты"
            : format(snapshot.developmentPoints()) + " / " + format(snapshot.developmentNextThreshold()) + " очков развития";
        development.addView(label(context, progressText, 12, TEXT), new LinearLayout.LayoutParams(-1, -2));
        if (snapshot.developmentLevel() < 5) {
            float progress = snapshot.developmentNextThreshold() <= 0 ? 1f :
                Math.min(1f, snapshot.developmentPoints() / (float) snapshot.developmentNextThreshold());
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(7));
            progressParams.setMargins(0, dp(7), 0, dp(7));
            development.addView(progressBar(context, progress, PURPLE), progressParams);
        }
        development.addView(label(context, "Бонус: " + snapshot.developmentPerk(), 12, ACCENT));
        if (snapshot.developmentLevel() < 5) {
            development.addView(label(context, "Далее: " + snapshot.developmentNextPerk(), 11, MUTED));
        }

        TextView section = label(context, "ГОСУДАРСТВЕННЫЙ СКЛАД", 15, TEXT);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
        sectionParams.setMargins(0, dp(4), 0, dp(8));
        root.addView(section, sectionParams);

        ScrollView scroll = new ScrollView(context);
        LinearLayout resources = column(context);
        for (int i = 0; i < snapshot.materialIds().length; i++) {
            addResourceRow(context, resources, i);
        }
        scroll.addView(resources);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = row(context);
        int totalDebt = total(snapshot.materialDebt());
        TextView debtText = label(context, "Ресурсный долг: " + format(totalDebt), totalDebt > 0 ? 14 : 13,
            totalDebt > 0 ? DANGER : ACCENT);
        footer.addView(debtText, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = new Button(context);
        close.setText("ЗАКРЫТЬ");
        close.setTextSize(13);
        close.setTextColor(TEXT);
        close.setBackground(solid(PANEL_2, dp(9)));
        close.setOnClickListener(v -> closeDashboard());
        footer.addView(close, new LinearLayout.LayoutParams(dp(124), dp(40)));
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, dp(42));
        footerParams.setMargins(0, dp(10), 0, 0);
        root.addView(footer, footerParams);

        return background;
    }

    private void closeDashboard() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(null));
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
        card.setBackground(solid(PANEL, dp(10)));
        card.setPadding(dp(14), dp(9), dp(14), dp(9));

        LinearLayout top = row(context);
        top.addView(label(context, name, 15, TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(label(context, format(stock) + " ед.", 15, stock > 0 ? INFO : DANGER));
        card.addView(top);

        TextView detail = label(context, String.format(Locale.ROOT, "Расход  -%.2f / цикл", cost) +
            (debt > 0 ? "    •    долг " + format(debt) : ""),
            12, debt > 0 ? DANGER : MUTED);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.setMargins(0, dp(3), 0, dp(7));
        card.addView(detail, detailParams);
        card.addView(progressBar(context, ratio, ratio > 0.25f ? ACCENT : DANGER));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(82));
        params.setMargins(0, 0, 0, dp(7));
        parent.addView(card, params);
    }

    private View metricCard(Context context, String title, String value, int accent) {
        LinearLayout card = column(context);
        card.setBackground(solid(PANEL, dp(10)));
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.addView(label(context, title, 10, MUTED));
        TextView bottom = label(context, value, 17, accent);
        bottom.setGravity(Gravity.BOTTOM | Gravity.START);
        card.addView(bottom, new LinearLayout.LayoutParams(-1, 0, 1));
        return card;
    }

    private View progressBar(Context context, float ratio, int color) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(solid(0xFF0C141D, dp(4)));
        int height = dp(7);
        float fill = Math.max(0.01f, Math.min(1.0f, ratio));
        View active = new View(context);
        active.setBackground(solid(color, dp(4)));
        View rest = new View(context);
        rest.setBackground(solid(0xFF263544, dp(4)));
        bar.addView(active, new LinearLayout.LayoutParams(0, height, fill));
        bar.addView(rest, new LinearLayout.LayoutParams(0, height, 1.0f - fill));
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

    private LinearLayout.LayoutParams marginWeight(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(74), weight);
        params.setMargins(dp(8), 0, 0, 0);
        return params;
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

    private static int valueAt(int[] values, int index) {
        return values != null && index >= 0 && index < values.length ? values[index] : 0;
    }

    private static int total(int[] values) {
        int result = 0;
        if (values != null) for (int value : values) result += Math.max(0, value);
        return result;
    }

    private static double doubleAt(double[] values, int index) {
        return values != null && index >= 0 && index < values.length ? values[index] : 0.0D;
    }

    private static String format(int value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0, value));
    }

    private int dp(int value) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(value * density));
    }
}
