package ru.zela.politicseconomy.country;

import net.krona.politicsmod.politics.Country;
import net.minecraft.server.MinecraftServer;
import ru.zela.politicseconomy.economy.NationalMaterialConsumptionService;
import ru.zela.politicseconomy.economy.NationalMaterialLedgerSavedData;

import java.util.Map;

/** Handles paid country reforms and their political consequences. */
public final class CountryReformService {
    private static final int DEVELOPMENT_LOSS_PERCENT = 5;

    private CountryReformService() {}

    public record Cost(int money, Map<String, Integer> materials) {
        public boolean free() { return money <= 0 && materials.isEmpty(); }
        public String display() {
            if (free()) return "Первый выбор — бесплатно";
            StringBuilder text = new StringBuilder("$").append(money);
            for (Map.Entry<String, Integer> entry : materials.entrySet()) {
                String id = entry.getKey();
                String shortName = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
                text.append(" • ").append(entry.getValue()).append(' ').append(shortName);
            }
            return text.toString();
        }
    }

    public static Cost cost(String action, boolean firstChoice) {
        return cost(action, firstChoice, 0, 1);
    }

    public static Cost cost(String action, boolean firstChoice, int population, int developmentLevel) {
        CountryReformCostTable.Cost base = CountryReformCostTable.cost(action, firstChoice, population, developmentLevel);
        return new Cost(base.money(), base.materials());
    }

    public static Result apply(MinecraftServer server, Country country, String action, boolean firstChoice, Runnable changeAction) {
        int population = CountryPopulationService.population(server, country.getName());
        int development = CountryDevelopmentService.level(server, country.getName());
        Cost cost = cost(action, firstChoice, population, development);

        if (!firstChoice && cost.money() > country.balance) {
            return new Result(false, "Недостаточно денег. Нужно " + cost.display() + ".");
        }

        NationalMaterialLedgerSavedData ledger = NationalMaterialConsumptionService.getLedger(server);
        ledger.initializeCountry(country.getName());
        for (Map.Entry<String, Integer> entry : cost.materials().entrySet()) {
            int available = ledger.getStockpile(country.getName(), entry.getKey());
            if (available < entry.getValue()) {
                return new Result(false, "Недостаточно ресурсов на государственном складе: " + entry.getKey()
                    + " нужно " + entry.getValue() + ", есть " + available + ".");
            }
        }

        if (!firstChoice) {
            country.balance -= cost.money();
            for (Map.Entry<String, Integer> entry : cost.materials().entrySet()) {
                int available = ledger.getStockpile(country.getName(), entry.getKey());
                ledger.setStockpile(country.getName(), entry.getKey(), available - entry.getValue());
            }

            int currentPoints = CountryDevelopmentService.points(server, country.getName());
            int setback = (int) Math.floor(currentPoints * (DEVELOPMENT_LOSS_PERCENT / 100.0D));
            if (setback > 0) CountryDevelopmentService.get(server).setPoints(country.getName(), currentPoints - setback);
        }

        changeAction.run();

        // A reform is a political shock. The larger the population, the larger
        // the number of people who must adapt, but the effect is intentionally
        // logarithmic rather than directly proportional to population.
        if (!firstChoice) {
            int populationPressure = Math.min(20, (int) Math.floor(Math.log10(population + 10) * 4.0D));
            int shock = 10 + populationPressure;
            if ("government".equals(action)) shock += 8;
            else if ("religion".equals(action)) shock += 5;
            CountryPoliticalSavedData politics = CountryPoliticalService.get(server);
            int unrest = politics.getUnrest(country.getName());
            politics.setUnrest(country.getName(), Math.min(100, unrest + shock));
        }

        return new Result(true, firstChoice
            ? "Первоначальный выбор сделан бесплатно."
            : "Реформа проведена. Затрачено: " + cost.display()
                + " • развитие −" + DEVELOPMENT_LOSS_PERCENT + "%");
    }

    public record Result(boolean success, String message) {}
}
