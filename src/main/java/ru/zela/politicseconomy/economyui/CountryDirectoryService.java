package ru.zela.politicseconomy.economyui;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.minecraft.server.MinecraftServer;
import ru.zela.politicseconomy.country.CountryDevelopmentService;
import ru.zela.politicseconomy.country.CountryWorkforceService;
import ru.zela.politicseconomy.country.WorkforceSector;
import ru.zela.politicseconomy.integration.MilitaryEconomyService;
import ru.zela.politicseconomy.integration.MillenaireIntegration;
import ru.zela.politicseconomy.integration.MillenaireStateSavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Unified global ranking of PoliticsMod countries and Millénaire states. */
public final class CountryDirectoryService {
    private static final int MAX_COUNTRIES = 48;

    private CountryDirectoryService() {}

    public record CountrySnapshot(
        String name,
        String type,
        long treasury,
        int population,
        int development,
        int militaryWorkers,
        double readiness,
        double score
    ) {}

    public static String resolveStateKey(
        MinecraftServer server,
        String type,
        String displayName
    ) {
        if (server == null || displayName == null || displayName.isBlank()) return null;
        String wantedType = type == null ? "" : type.trim();

        if ("PoliticsMod".equals(wantedType)) {
            PoliticsManager politics = PoliticsManager.get(server.overworld());
            if (politics == null) return null;
            Country country = politics.getCountry(displayName);
            return country == null ? null : country.getName();
        }

        if ("Millénaire".equals(wantedType)) {
            for (MillenaireIntegration.VillageSnapshot state : MillenaireIntegration.snapshots(server)) {
                if (displayName.equals(state.name())) return state.stateKey();
            }
            return null;
        }

        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics != null && politics.getCountry(displayName) != null) {
            return displayName;
        }
        for (MillenaireIntegration.VillageSnapshot state : MillenaireIntegration.snapshots(server)) {
            if (displayName.equals(state.name())) return state.stateKey();
        }
        return null;
    }

    public static List<CountrySnapshot> build(MinecraftServer server) {
        List<CountrySnapshot> result = new ArrayList<>();
        PoliticsManager politics = PoliticsManager.get(server.overworld());

        if (politics != null) {
            for (Country country : politics.getCountries().values()) {
                String key = country.getName();
                int population = ru.zela.politicseconomy.country.CountryPopulationService.population(server, key);
                int military = CountryWorkforceService.sectorWorkers(server, key, WorkforceSector.MILITARY);
                int development = CountryDevelopmentService.level(server, key);
                double readiness = MilitaryEconomyService.readiness(server, key);
                long treasury = Math.max(0L, country.balance);
                result.add(snapshot(key, "PoliticsMod", treasury, population, development, military, readiness));
            }
        }

        if (MillenaireIntegration.isAvailable()) {
            for (MillenaireIntegration.VillageSnapshot state : MillenaireIntegration.snapshots(server)) {
                String key = state.stateKey();
                MillenaireStateSavedData data = MillenaireStateSavedData.get(server);
                long treasury = Math.max(0L, data.treasury(state.villageId()));
                int population = Math.max(0, state.population());
                int military = CountryWorkforceService.sectorWorkers(server, key, WorkforceSector.MILITARY);
                int development = CountryDevelopmentService.level(server, key);
                double readiness = MilitaryEconomyService.readiness(server, key);
                result.add(snapshot(state.name(), "Millénaire", treasury, population, development, military, readiness));
            }
        }

        result.sort(Comparator.comparingDouble(CountrySnapshot::score).reversed()
            .thenComparing(CountrySnapshot::name));

        return result.size() <= MAX_COUNTRIES
            ? List.copyOf(result)
            : List.copyOf(result.subList(0, MAX_COUNTRIES));
    }

    private static CountrySnapshot snapshot(
        String name,
        String type,
        long treasury,
        int population,
        int development,
        int militaryWorkers,
        double readiness
    ) {
        double score =
            Math.log1p(Math.max(0L, treasury)) * 18.0D
            + Math.sqrt(Math.max(0, population)) * 8.0D
            + development * 45.0D
            + militaryWorkers * 12.0D
            + readiness * 1.15D;
        return new CountrySnapshot(
            name,
            type,
            treasury,
            population,
            development,
            militaryWorkers,
            readiness,
            score
        );
    }
}
