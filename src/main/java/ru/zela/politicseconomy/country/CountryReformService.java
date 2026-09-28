package ru.zela.politicseconomy.country;

import net.krona.politicsmod.politics.Country;
import net.minecraft.server.MinecraftServer;
import ru.zela.politicseconomy.economy.NationalMaterialConsumptionService;
import ru.zela.politicseconomy.economy.NationalMaterialLedgerSavedData;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Paid reforms for an already configured country.
 * The first choice of each setting is free; later changes consume national
 * treasury/materials and cause a small development setback.
 */
public final class CountryReformService {
    private static final int DEVELOPMENT_LOSS_PERCENT = 5;

    private CountryReformService() {}

    public record Cost(int money, Map<String, Integer> materials) {
        public boolean free() {
            return money <= 0 && materials.isEmpty();
        }

        public String display() {
            if (free()) return "Первый выбор — бесплатно";

            StringBuilder text = new StringBuilder("$").append(money);
            for (Map.Entry<String, Integer> entry : materials.entrySet()) {
                String id = entry.getKey();
                String shortName = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
                text.append(" • ").append(entry.getValue()).append(" ").append(shortName);
            }
            return text.toString();
        }
    }

    public static Cost cost(String action, boolean firstChoice) {
        CountryReformCostTable.Cost base = CountryReformCostTable.cost(action, firstChoice);
        return new Cost(base.money(), base.materials());
    }

    public static Result apply(
        MinecraftServer server,
        Country country,
        String action,
        boolean firstChoice,
        Runnable changeAction
    ) {
        Cost cost = cost(action, firstChoice);
        if (!firstChoice && cost.money() > country.balance) {
            return new Result(false, "Недостаточно денег. Нужно " + cost.display() + ".");
        }

        NationalMaterialLedgerSavedData ledger =
            NationalMaterialConsumptionService.getLedger(server);
        ledger.initializeCountry(country.getName());

        for (Map.Entry<String, Integer> entry : cost.materials().entrySet()) {
            int available = ledger.getStockpile(country.getName(), entry.getKey());
            if (available < entry.getValue()) {
                return new Result(false,
                    "Недостаточно ресурсов на государственном складе: " +
                        entry.getKey() + " нужно " + entry.getValue() +
                        ", есть " + available + ".");
            }
        }

        if (!firstChoice) {
            country.balance -= cost.money();
            for (Map.Entry<String, Integer> entry : cost.materials().entrySet()) {
                int available = ledger.getStockpile(country.getName(), entry.getKey());
                ledger.setStockpile(
                    country.getName(),
                    entry.getKey(),
                    available - entry.getValue()
                );
            }

            int currentPoints = CountryDevelopmentService.points(server, country.getName());
            int setback = (int) Math.floor(currentPoints * (DEVELOPMENT_LOSS_PERCENT / 100.0D));
            if (setback > 0) {
                CountryDevelopmentSavedData development =
                    CountryDevelopmentService.get(server);
                development.setPoints(country.getName(), currentPoints - setback);
            }
        }

        changeAction.run();

        return new Result(
            true,
            firstChoice
                ? "Первоначальный выбор сделан бесплатно."
                : "Реформа проведена. Затрачено: " + cost.display() +
                    " • развитие −" + (int) DEVELOPMENT_LOSS_PERCENT + "%"
        );
    }

    public record Result(boolean success, String message) {}



}
