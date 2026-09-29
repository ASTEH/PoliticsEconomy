package ru.zela.politicseconomy;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import ru.zela.politicseconomy.command.PoliticsEconomyCommands;
import ru.zela.politicseconomy.country.CountryPopulationService;
import ru.zela.politicseconomy.country.CountryWorkforceCycleService;
import ru.zela.politicseconomy.economy.NationalMaterialConsumptionService;
import ru.zela.politicseconomy.economy.ResourceExtractionEvents;
import ru.zela.politicseconomy.infrastructure.EconomyCycleEvents;
import ru.zela.politicseconomy.infrastructure.InfrastructureEvents;
import ru.zela.politicseconomy.network.EconomyNetwork;
import ru.zela.politicseconomy.starter.StarterKitService;

@Mod(PoliticsEconomy.MOD_ID)
public final class PoliticsEconomy {
    public static final String MOD_ID = "politicseconomy";
    public static final String VERSION = "2.1.1";
    private static final Logger LOGGER = LogUtils.getLogger();

    public PoliticsEconomy(IEventBus modEventBus, ModContainer modContainer) {
        EconomyNetwork.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(PoliticsEconomyCommands::register);
        NeoForge.EVENT_BUS.addListener(InfrastructureEvents::onPlace);
        NeoForge.EVENT_BUS.addListener(InfrastructureEvents::onBreak);
        NeoForge.EVENT_BUS.addListener(CountryPopulationService::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(EconomyCycleEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(NationalMaterialConsumptionService::onServerTick);
        NeoForge.EVENT_BUS.addListener(ResourceExtractionEvents::onBlockDrops);
        NeoForge.EVENT_BUS.addListener(CountryWorkforceCycleService::onServerTick);
        NeoForge.EVENT_BUS.addListener(StarterKitService::onPlayerLoggedIn);
        LOGGER.info("Politics Economy {} loaded for Minecraft 1.21.1 / NeoForge 21.1.x", VERSION);
        LOGGER.info("PoliticsMod integration enabled.");
    }
}
