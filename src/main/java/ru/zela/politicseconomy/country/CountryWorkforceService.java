package ru.zela.politicseconomy.country;

import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import ru.zela.politicseconomy.integration.PoliticsModIntegration;

import java.util.EnumMap;
import java.util.Locale;

public final class CountryWorkforceService {
    private static final double BASE_WORKFORCE_SHARE = 0.50D;
    private static final double MIN_WORKFORCE_SHARE = 0.20D;
    private static final double MAX_WORKFORCE_SHARE = 0.75D;

    private static final double MAX_SECTOR_BONUS = 35.0D;
    private static final double MAX_TRADE_BONUS = 25.0D;
    private static final double MAX_MARKET_EFFICIENCY_BONUS = 30.0D;
    private static final double MAX_CONSTRUCTION_EFFICIENCY_BONUS = 15.0D;

    private CountryWorkforceService() {}

    public static CountryWorkforceSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                CountryWorkforceSavedData::create,
                CountryWorkforceSavedData::load,
                null
            ),
            CountryWorkforceSavedData.DATA_NAME
        );
    }

    public static EnumMap<WorkforceSector, Integer> allocation(
        MinecraftServer server,
        String countryName
    ) {
        CountryDirection direction = CountryDirectionManager.getDirection(server, countryName);
        if (direction == null) direction = CountryDirection.INDUSTRIAL;
        return get(server).getOrCreate(countryName, direction);
    }

    public static EnumMap<WorkforceSector, Integer> defaultAllocation(CountryDirection direction) {
        EnumMap<WorkforceSector, Integer> result = new EnumMap<>(WorkforceSector.class);
        for (WorkforceSector sector : WorkforceSector.values()) result.put(sector, 0);

        switch (direction == null ? CountryDirection.INDUSTRIAL : direction) {
            case INDUSTRIAL -> {
                result.put(WorkforceSector.AGRICULTURE, 10);
                result.put(WorkforceSector.EXTRACTION, 10);
                result.put(WorkforceSector.INDUSTRY, 45);
                result.put(WorkforceSector.MILITARY, 20);
                result.put(WorkforceSector.TRADE_LOGISTICS, 5);
                result.put(WorkforceSector.CONSTRUCTION_SERVICES, 10);
            }
            case RESOURCE -> {
                result.put(WorkforceSector.AGRICULTURE, 35);
                result.put(WorkforceSector.EXTRACTION, 30);
                result.put(WorkforceSector.INDUSTRY, 15);
                result.put(WorkforceSector.MILITARY, 5);
                result.put(WorkforceSector.TRADE_LOGISTICS, 5);
                result.put(WorkforceSector.CONSTRUCTION_SERVICES, 10);
            }
            case TRADE -> {
                result.put(WorkforceSector.AGRICULTURE, 20);
                result.put(WorkforceSector.EXTRACTION, 10);
                result.put(WorkforceSector.INDUSTRY, 10);
                result.put(WorkforceSector.MILITARY, 5);
                result.put(WorkforceSector.TRADE_LOGISTICS, 45);
                result.put(WorkforceSector.CONSTRUCTION_SERVICES, 10);
            }
        }
        return result;
    }

    public static int workingPopulation(MinecraftServer server, String countryName) {
        int population = CountryPopulationService.population(server, countryName);
        if (population <= 0) return 0;

        double policyMultiplier = CountryPolicyBonusService.workforceMultiplier(server, countryName);
        double share = BASE_WORKFORCE_SHARE * policyMultiplier;
        share = Math.max(MIN_WORKFORCE_SHARE, Math.min(MAX_WORKFORCE_SHARE, share));
        return Math.max(0, (int) Math.floor(population * share));
    }

    public static int allocatedSectorWorkers(
        MinecraftServer server,
        String countryName,
        WorkforceSector sector
    ) {
        int workers = workingPopulation(server, countryName);
        if (workers <= 0) return 0;

        int share = allocation(server, countryName).getOrDefault(sector, 0);
        return (int) Math.floor(workers * share / 100.0D);
    }

    public static int sectorWorkers(
        MinecraftServer server,
        String countryName,
        WorkforceSector sector
    ) {
        int requested = allocatedSectorWorkers(server, countryName, sector);
        int capacity = CountryWorkplaceService.snapshot(server, countryName)
            .workplaceSlots()
            .getOrDefault(sector, 0);
        return Math.min(requested, capacity);
    }

    public static int employedPopulation(MinecraftServer server, String countryName) {
        int total = 0;
        for (WorkforceSector sector : WorkforceSector.values()) {
            total += sectorWorkers(server, countryName, sector);
        }
        return total;
    }

    public static int unemployedPopulation(MinecraftServer server, String countryName) {
        return Math.max(0, workingPopulation(server, countryName)
            - employedPopulation(server, countryName));
    }

    public static int workplaceCapacity(MinecraftServer server, String countryName) {
        return CountryWorkplaceService.snapshot(server, countryName).totalSlots();
    }

    public static double employmentRatePercent(MinecraftServer server, String countryName) {
        int workforce = workingPopulation(server, countryName);
        if (workforce <= 0) return 0.0D;
        return employedPopulation(server, countryName) * 100.0D / workforce;
    }

    public static double sectorBonusPercent(
        MinecraftServer server,
        String countryName,
        WorkforceSector sector
    ) {
        double value = sectorWorkers(server, countryName, sector)
            / 100.0D * sector.bonusPer100Workers();
        return Math.min(MAX_SECTOR_BONUS, Math.max(0.0D, value));
    }

    public static double tradeIncomeBonusPercent(
        MinecraftServer server,
        String countryName
    ) {
        double value = sectorWorkers(server, countryName, WorkforceSector.TRADE_LOGISTICS)
            / 100.0D * 0.65D;
        return Math.min(MAX_TRADE_BONUS, Math.max(0.0D, value));
    }

    public static double marketEfficiencyBonusPercent(
        MinecraftServer server,
        String countryName
    ) {
        double value = sectorWorkers(server, countryName, WorkforceSector.TRADE_LOGISTICS)
            / 100.0D * 0.90D;
        return Math.min(MAX_MARKET_EFFICIENCY_BONUS, Math.max(0.0D, value));
    }

    public static double constructionEfficiencyBonusPercent(
        MinecraftServer server,
        String countryName
    ) {
        double value = sectorWorkers(server, countryName, WorkforceSector.CONSTRUCTION_SERVICES)
            / 100.0D * 0.30D;
        return Math.min(MAX_CONSTRUCTION_EFFICIENCY_BONUS, Math.max(0.0D, value));
    }

    public static Result apply(ServerPlayer player, String rawValue, boolean operator) {
        Country country = PoliticsModIntegration.playerCountry(player).orElse(null);
        if (country == null) {
            return new Result(false, "Ты не состоишь ни в одной стране.");
        }
        if (!operator && PoliticsModIntegration.role(player, country) != CountryRole.LEADER) {
            return new Result(false, "Распределять рабочую силу может только лидер.");
        }

        String[] split = rawValue == null ? new String[0] : rawValue.split(":", 2);
        if (split.length != 2) {
            return new Result(false, "Формат: sector:delta.");
        }

        WorkforceSector sector = WorkforceSector.fromCommandName(split[0]);
        if (sector == null) {
            return new Result(false, "Неизвестный сектор рабочей силы.");
        }

        int delta;
        try {
            delta = Integer.parseInt(split[1]);
        } catch (NumberFormatException exception) {
            return new Result(false, "Некорректное изменение процента.");
        }

        if (Math.abs(delta) > 25) {
            return new Result(false, "За одно действие можно изменить не более 25%.");
        }

        MinecraftServer server = player.getServer();
        String countryName = country.getName();
        EnumMap<WorkforceSector, Integer> current = allocation(server, countryName);
        int target = Math.max(0, Math.min(100, current.getOrDefault(sector, 0) + delta));
        setSectorShare(server, countryName, sector, target);

        return new Result(true, "Сектор «" + sector.displayName() + "»: "
            + target + "% рабочей силы.");
    }

    public static void setSectorShare(
        MinecraftServer server,
        String countryName,
        WorkforceSector sector,
        int target
    ) {
        EnumMap<WorkforceSector, Integer> current = allocation(server, countryName);
        int oldTarget = current.getOrDefault(sector, 0);
        int oldOthers = 100 - oldTarget;
        int remaining = 100 - Math.max(0, Math.min(100, target));

        current.put(sector, Math.max(0, Math.min(100, target)));

        if (remaining == 0) {
            for (WorkforceSector other : WorkforceSector.values()) {
                if (other != sector) current.put(other, 0);
            }
        } else if (oldOthers <= 0) {
            int others = WorkforceSector.values().length - 1;
            int base = remaining / others;
            int extra = remaining % others;
            for (WorkforceSector other : WorkforceSector.values()) {
                if (other == sector) continue;
                current.put(other, base + (extra-- > 0 ? 1 : 0));
            }
        } else {
            EnumMap<WorkforceSector, Double> fractions = new EnumMap<>(WorkforceSector.class);
            int sum = 0;
            for (WorkforceSector other : WorkforceSector.values()) {
                if (other == sector) continue;
                double raw = current.getOrDefault(other, 0) * remaining / (double) oldOthers;
                int floor = (int) Math.floor(raw);
                current.put(other, floor);
                fractions.put(other, raw - floor);
                sum += floor;
            }

            int left = remaining - sum;
            while (left > 0) {
                WorkforceSector best = null;
                double bestFraction = -1.0D;
                for (WorkforceSector other : WorkforceSector.values()) {
                    if (other == sector) continue;
                    double fraction = fractions.getOrDefault(other, 0.0D);
                    if (fraction > bestFraction) {
                        best = other;
                        bestFraction = fraction;
                    }
                }
                if (best == null) break;
                current.put(best, current.get(best) + 1);
                fractions.put(best, -1.0D);
                left--;
            }
        }

        get(server).setAllocation(countryName, current);
    }

    public static String formatBonus(double value) {
        if (Math.abs(value) < 0.0001D) return "0%";
        return String.format(Locale.ROOT, "%+.1f%%", value);
    }

    public record Result(boolean success, String message) {}
}
