package ru.zela.politicseconomy.economy;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.politics.Country;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.zela.politicseconomy.country.CountryDevelopmentService;

/**
 * Consumes exact material items required by the recipes of a country's placed infrastructure.
 * A deficit becomes debt and is handled later by the shutdown/destruction stage.
 */
public final class NationalMaterialConsumptionService {
    private static final int MIN_CYCLE_TICKS = 20;

    private NationalMaterialConsumptionService() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        int cycleTicks = Math.max(MIN_CYCLE_TICKS, PoliticsConfig.get().economyCycleTicks);
        long cycle = overworld.getGameTime() / cycleTicks;
        NationalMaterialLedgerSavedData ledger = getLedger(server);

        long lastCycle = ledger.lastProcessedCycle();
        if (cycle <= lastCycle) {
            return;
        }
        ledger.setLastProcessedCycle(cycle);
        applyCycle(server, ledger);
    }

    public static void chargeNow(MinecraftServer server) {
        applyCycle(server, getLedger(server));
    }

    private static void applyCycle(MinecraftServer server, NationalMaterialLedgerSavedData ledger) {
        PoliticsManager politics = PoliticsManager.get(server.overworld());
        if (politics == null) {
            return;
        }

        for (Country country : politics.getCountries().values()) {
            String countryName = country.getName();
            ledger.initializeCountry(countryName);

            NationalMaterialDemandService.CountryDemand demand =
                NationalMaterialDemandService.calculate(server.overworld(), countryName);

            for (NationalMaterialDemandService.MaterialDemand material : demand.materials()) {
                String choiceKey = material.key();
                double accumulated = ledger.getRemainder(countryName, choiceKey)
                    + material.perCycleConsumption();

                // A material requirement is a real economic obligation, not a
                // purely fractional statistic. If a country requires even a
                // fraction of one item during a cycle, at least one concrete
                // item must be available. This makes a zero-stock shortage
                // visible immediately instead of waiting many cycles for a
                // fractional remainder to reach 1.
                int currentUnitsDue = material.perCycleConsumption() > 1.0E-9D
                    ? Math.max(1, (int) Math.ceil(accumulated - 1.0E-9D))
                    : 0;

                // We intentionally do not carry a negative remainder. The
                // minimum-one-unit rule above has already converted fractional
                // demand into a concrete item obligation for this cycle.
                double nextRemainder = accumulated - currentUnitsDue;
                ledger.setRemainder(countryName, choiceKey, Math.max(0.0D, nextRemainder));

                int oldDebt = ledger.getDebt(countryName, choiceKey);
                long totalDueLong = (long) currentUnitsDue + oldDebt;
                int totalDue = totalDueLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalDueLong;

                if (totalDue <= 0) {
                    continue;
                }

                int paid = ledger.consumeAccepted(countryName, material.acceptedItemIds(), totalDue);
                ledger.setDebt(countryName, choiceKey, totalDue - paid);
            }

            MaterialShortageResolutionService.resolveCountry(server.overworld(), countryName, demand, ledger);

            if (!demand.materials().isEmpty() && !ledger.hasAnyDebt(countryName)) {
                CountryDevelopmentService.addActivity(server, countryName, 5);
            }
        }

        ledger.setDirty();
    }

    public static NationalMaterialLedgerSavedData getLedger(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                NationalMaterialLedgerSavedData::create,
                NationalMaterialLedgerSavedData::load,
                null
            ),
            NationalMaterialLedgerSavedData.DATA_NAME
        );
    }
}
