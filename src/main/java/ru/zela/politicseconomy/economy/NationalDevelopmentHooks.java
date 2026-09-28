package ru.zela.politicseconomy.economy;

import ru.zela.politicseconomy.country.CountryDevelopmentService;
import ru.zela.politicseconomy.country.CountryDirection;

/** Small integration hooks for rewarding development from economy activity. */
public final class NationalDevelopmentHooks {
    private NationalDevelopmentHooks() {}

    public static void rewardSuccessfulCycle(
        net.minecraft.server.MinecraftServer server,
        String countryName,
        NationalUpkeepLedgerSavedData ledger,
        CountryDirection direction
    ) {
        boolean debtFree = true;
        for (NationalResource resource : NationalResource.values()) {
            if (ledger.getDebt(countryName, resource) > 0) {
                debtFree = false;
                break;
            }
        }
        if (debtFree) {
            CountryDevelopmentService.addActivity(server, countryName, 5);
        }
    }
}
