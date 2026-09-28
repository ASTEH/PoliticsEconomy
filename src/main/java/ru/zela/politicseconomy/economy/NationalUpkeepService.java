package ru.zela.politicseconomy.economy;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.politics.Country;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.zela.politicseconomy.country.CountryDirection;
import ru.zela.politicseconomy.country.CountryDirectionManager;

import java.util.EnumMap;

/** Applies national strategic-resource upkeep once per PoliticsMod economy cycle. */
public final class NationalUpkeepService {
    private static final int MIN_CYCLE_TICKS = 20;

    private NationalUpkeepService() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        int cycleTicks = Math.max(MIN_CYCLE_TICKS, PoliticsConfig.get().economyCycleTicks);
        long cycle = overworld.getGameTime() / cycleTicks;
        NationalUpkeepLedgerSavedData ledger = getLedger(server);

        if (!ledger.initialized()) {
            ledger.setInitialized(true);
            ledger.setLastProcessedCycle(cycle);
            return;
        }

        if (cycle <= ledger.lastProcessedCycle()) {
            return;
        }

        ledger.setLastProcessedCycle(cycle);
        applyCycle(server, ledger);
    }

    public static void chargeNow(MinecraftServer server) {
        NationalUpkeepLedgerSavedData ledger = getLedger(server);
        applyCycle(server, ledger);
    }

    private static void applyCycle(MinecraftServer server, NationalUpkeepLedgerSavedData ledger) {
        ServerLevel overworld = server.overworld();
        PoliticsManager politics = PoliticsManager.get(overworld);
        if (politics == null) {
            return;
        }

        for (Country country : politics.getCountries().values()) {
            String countryName = country.getName();
            CountryDirection direction = CountryDirectionManager.getDirection(server, countryName);
            if (direction == null) {
                continue;
            }

            if (!ledger.hasCountry(countryName)) {
                ledger.initializeCountry(countryName, direction);
            }

            for (NationalResource resource : NationalResource.values()) {
                int baseCost = NationalUpkeepCalculator.cost(direction, resource);
                int oldDebt = ledger.getDebt(countryName, resource);
                long totalDueLong = (long) baseCost + oldDebt;
                int totalDue = totalDueLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalDueLong;

                int reserve = ledger.getStockpile(countryName, resource);
                int paid = Math.min(reserve, totalDue);
                int unpaid = totalDue - paid;

                ledger.setStockpile(countryName, resource, reserve - paid);
                ledger.setDebt(countryName, resource, unpaid);
            }

            NationalDevelopmentHooks.rewardSuccessfulCycle(server, countryName, ledger, direction);
        }

        ledger.setDirty();
    }

    public static NationalUpkeepLedgerSavedData getLedger(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                NationalUpkeepLedgerSavedData::create,
                NationalUpkeepLedgerSavedData::load,
                null
            ),
            NationalUpkeepLedgerSavedData.DATA_NAME
        );
    }

    public record ResourceStatus(
        NationalResource resource,
        int perCycleCost,
        int stockpile,
        int debt
    ) {}
}
