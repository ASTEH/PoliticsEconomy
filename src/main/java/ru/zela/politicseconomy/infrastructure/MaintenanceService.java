package ru.zela.politicseconomy.infrastructure;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.krona.politicsmod.config.PoliticsConfig;

import java.util.ArrayList;
import java.util.List;

/** Applies infrastructure maintenance to the PoliticsMod country treasuries. */
public final class MaintenanceService {
    private static final double EPSILON = 1.0e-9;

    private MaintenanceService() {}

    /**
     * Called every server tick. Charges exactly once when the configured
     * PoliticsMod economy cycle advances.
     */
    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        int cycleTicks = Math.max(20, PoliticsConfig.get().economyCycleTicks);
        long cycle = overworld.getGameTime() / cycleTicks;

        MaintenanceLedgerSavedData ledger = getLedger(server);

        // When the addon is first added to an existing world, start accounting
        // from the current cycle instead of charging an artificial backlog.
        if (!ledger.initialized()) {
            ledger.setInitialized(true);
            ledger.setLastProcessedCycle(cycle);
            return;
        }

        if (cycle <= ledger.lastProcessedCycle()) {
            return;
        }

        ledger.setLastProcessedCycle(cycle);
        applyCycle(server, overworld, ledger);
    }

    /**
     * Admin/test entry point. Applies one maintenance charge immediately,
     * regardless of the current cycle.
     */
    public static void chargeNow(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        applyCycle(server, overworld, getLedger(server));
    }

    private static void applyCycle(
        MinecraftServer server,
        ServerLevel overworld,
        MaintenanceLedgerSavedData ledger
    ) {
        PoliticsManager politics = PoliticsManager.get(overworld);
        if (politics == null) {
            return;
        }

        for (Country country : politics.getCountries().values()) {
            String countryName = country.getName();
            InfrastructureManager.CountryInfrastructureStats stats =
                InfrastructureManager.getCountryStats(server, countryName);

            double maintenance = Math.max(0.0, stats.adjustedMaintenance());
            if (maintenance <= EPSILON && ledger.getDebt(countryName) <= EPSILON) {
                continue;
            }

            double pending = ledger.getPending(countryName) + maintenance;
            int wholeMaintenance = (int) Math.floor(pending + EPSILON);
            pending -= wholeMaintenance;
            ledger.setPending(countryName, pending);

            double existingDebt = ledger.getDebt(countryName);
            int wholeDebt = (int) Math.floor(existingDebt + EPSILON);
            double debtFraction = existingDebt - wholeDebt;

            long totalDueLong = (long) wholeMaintenance + wholeDebt;
            int totalDue = totalDueLong > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) totalDueLong;

            int balanceBefore = Math.max(0, country.balance);
            int paid = Math.min(balanceBefore, totalDue);
            int unpaid = totalDue - paid;

            country.balance = balanceBefore - paid;

            // All unpaid whole dollars become debt. Keep the fractional portion
            // separate so that it can eventually become a whole dollar too.
            ledger.setDebt(countryName, unpaid + debtFraction);
        }

        politics.saveData();
        ledger.setDirty();
    }

    public static MaintenanceLedgerSavedData getLedger(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                MaintenanceLedgerSavedData::create,
                MaintenanceLedgerSavedData::load,
                null
            ),
            MaintenanceLedgerSavedData.DATA_NAME
        );
    }

    public record EconomyChargeResult(
        String countryName,
        double maintenance,
        double debtBefore,
        int paid,
        double debtAfter
    ) {}
}
