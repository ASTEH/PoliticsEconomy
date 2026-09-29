package ru.zela.politicseconomy.integration;

import net.krona.politicsmod.config.PoliticsConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.zela.politicseconomy.research.CountryResearchService;

import java.util.List;

/**
 * Autonomous technology progression for Millénaire states.
 *
 * <p>Each economic cycle, every active Millénaire state gets one opportunity
 * to complete the next affordable technology in its current economic
 * direction. Technology points and money are both real costs.</p>
 */
public final class MillenaireResearchService {
    private static final int MIN_CYCLE_TICKS = 20;

    private MillenaireResearchService() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (level == null || !MillenaireIntegration.isAvailable()) return;

        int cycleTicks = Math.max(
            MIN_CYCLE_TICKS,
            PoliticsConfig.get().economyCycleTicks
        );
        long cycle = level.getGameTime() / cycleTicks;

        MillenaireResearchSavedData saved =
            MillenaireResearchSavedData.get(server);
        if (cycle <= saved.lastProcessedCycle()) return;

        saved.setLastProcessedCycle(cycle);

        List<MillenaireIntegration.VillageSnapshot> states =
            MillenaireIntegration.snapshots(server);

        for (MillenaireIntegration.VillageSnapshot state : states) {
            if (state.population() <= 0) continue;

            CountryResearchService.researchMillenaire(
                server,
                state.stateKey()
            );
        }

        saved.setDirty();
    }
}
