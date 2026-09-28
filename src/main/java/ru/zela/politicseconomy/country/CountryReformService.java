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
    private static final int DIRECTION_MONEY = 1500;
    private static final int GOVERNMENT_MONEY = 2000;
    private static final int RELIGION_MONEY = 1200;
    private static final double DEVELOPMENT_LOSS_PERCENT = 0.05D;

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
        if (firstChoice) return new Cost(0, Map.of());

        return switch (normalize(action)) {
            case "direction" -> new Cost(
                DIRECTION_MONEY,
                materials(Map.entry("minecraft:iron_ingot", 32), Map.entry("minecraft:gold_ingot", 16))
            );
            case "government" -> new Cost(
                GOVERNMENT_MONEY,
                materials(Map.entry("minecraft:iron_ingot", 16), Map.entry("minecraft:gold_ingot", 16),
                    Map.entry("minecraft:paper", 16))
            );
            case "religion" -> new Cost(
                RELIGION_MONEY,
                materials(Map.entry("minecraft:gold_ingot", 8), Map.entry("minecraft:paper", 32),
                    Map.entry("minecraft:wheat", 16))
            );
            default -> new Cost(0, Map.of());
        };
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
            int setback = (int) Math.floor(currentPoints * DEVELOPMENT_LOSS_PERCENT);
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
                    " • развитие −" + (int) Math.round(DEVELOPMENT_LOSS_PERCENT * 100) + "%"
        );
    }

    public record Result(boolean success, String message) {}

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    @SafeVarargs
    private static Map<String, Integer> materials(Map.Entry<String, Integer>... entries) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : entries) {
            result.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(result);
    }
}
