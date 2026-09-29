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
            chargeState(server, ledger, countryName, maintenance, Math.max(0, country.balance),
                amount -> country.balance = amount);
        }

        // Millénaire states use their own treasury, while maintenance is still
        // charged through the same persistent debt ledger.
        for (ru.zela.politicseconomy.integration.MillenaireIntegration.VillageSnapshot state
            : ru.zela.politicseconomy.integration.MillenaireIntegration.snapshots(server)) {
            String countryName = state.stateKey();
            double maintenance =
                ru.zela.politicseconomy.integration.MillenaireIntegration.maintenanceCost(
                    server, country
                );
            chargeState(
                server,
                ledger,
                countryName,
                maintenance,
                (int) Math.min(Integer.MAX_VALUE,
                    ru.zela.politicseconomy.integration.MillenaireStateSavedData
                        .get(server).treasury(state.villageId())),
                amount -> {
                    long current = ru.zela.politicseconomy.integration.MillenaireStateSavedData
                        .get(server).treasury(state.villageId());
                    long paid = Math.max(0, current - amount);
                    if (paid > 0) {
                        ru.zela.politicseconomy.integration.MillenaireStateSavedData
                            .get(server)
                            .setTreasury(state.villageId(), amount);
                    }
                }
            );
        }

        politics.saveData();
        ledger.setDirty();
    }

    private static void chargeState(
        MinecraftServer server,
        MaintenanceLedgerSavedData ledger,
        String stateKey,
        double maintenance,
        int balanceBefore,
        java.util.function.IntConsumer setBalance
    ) {
        if (maintenance <= EPSILON && ledger.getDebt(stateKey) <= EPSILON) {
            return;
        }

        double pending = ledger.getPending(stateKey) + maintenance;
        int wholeMaintenance = (int) Math.floor(pending + EPSILON);
        pending -= wholeMaintenance;
        ledger.setPending(stateKey, pending);

        double existingDebt = ledger.getDebt(stateKey);
        int wholeDebt = (int) Math.floor(existingDebt + EPSILON);
        double debtFraction = existingDebt - wholeDebt;

        long totalDueLong = (long) wholeMaintenance + wholeDebt;
        int totalDue = totalDueLong > Integer.MAX_VALUE
            ? Integer.MAX_VALUE
            : (int) totalDueLong;

        int paid = Math.min(Math.max(0, balanceBefore), totalDue);
        int unpaid = totalDue - paid;

        setBalance(Math.max(0, balanceBefore - paid));
        ledger.setDebt(stateKey, unpaid + debtFraction);
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
