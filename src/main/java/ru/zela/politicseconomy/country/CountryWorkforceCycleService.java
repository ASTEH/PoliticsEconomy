package ru.zela.politicseconomy.country;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.config.PoliticsConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.zela.politicseconomy.economy.NationalMaterialConsumptionService;
import ru.zela.politicseconomy.economy.NationalMaterialLedgerSavedData;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CountryWorkforceCycleService {
    private static final int MIN_CYCLE_TICKS=20;
    private CountryWorkforceCycleService(){}
    public static void onServerTick(ServerTickEvent.Post event){MinecraftServer server=event.getServer();ServerLevel level=server.overworld();if(level==null)return;int ticks=Math.max(MIN_CYCLE_TICKS,PoliticsConfig.get().economyCycleTicks);long cycle=level.getGameTime()/ticks;processCycle(server,cycle);}
    private static void processCycle(MinecraftServer server,long cycle){PoliticsManager politics=PoliticsManager.get(server.overworld());if(politics==null)return;NationalMaterialLedgerSavedData ledger=NationalMaterialConsumptionService.getLedger(server);for(Country country:politics.getCountries().values()){String name=country.getName();CountryPoliticalService.processCycle(server,name,cycle);grantDividend(server,name,ledger);}}
    private static void grantDividend(MinecraftServer server,String country,NationalMaterialLedgerSavedData ledger){int workers=(int)Math.floor(CountryWorkforceService.workingPopulation(server,country)*CountryPoliticalService.productiveWorkforceFactor(server,country));if(workers<=0)return;EnumMap<WorkforceSector,Integer> w=new EnumMap<>(WorkforceSector.class);for(WorkforceSector s:WorkforceSector.values())w.put(s,(int)Math.floor(CountryWorkforceService.sectorWorkers(server,country,s)*CountryPoliticalService.productiveWorkforceFactor(server,country)));Map<String,Integer> p=new LinkedHashMap<>();add(p,"minecraft:wheat",w.get(WorkforceSector.AGRICULTURE),400,factor(server,country,WorkforceSector.AGRICULTURE));add(p,"minecraft:coal",w.get(WorkforceSector.EXTRACTION),500,factor(server,country,WorkforceSector.EXTRACTION));add(p,"minecraft:iron_ingot",w.get(WorkforceSector.EXTRACTION),1000,factor(server,country,WorkforceSector.EXTRACTION));add(p,"minecraft:iron_ingot",w.get(WorkforceSector.INDUSTRY),700,factor(server,country,WorkforceSector.INDUSTRY));add(p,"minecraft:gunpowder",w.get(WorkforceSector.MILITARY),1000,factor(server,country,WorkforceSector.MILITARY));add(p,"minecraft:paper",w.get(WorkforceSector.TRADE_LOGISTICS),500,factor(server,country,WorkforceSector.TRADE_LOGISTICS));add(p,"minecraft:stone",w.get(WorkforceSector.CONSTRUCTION_SERVICES),500,factor(server,country,WorkforceSector.CONSTRUCTION_SERVICES));int cap=12+Math.min(10,workers/2000*2);int total=p.values().stream().mapToInt(Integer::intValue).sum();if(total<=0)return;if(total>cap){double scale=cap/(double)total;Map<String,Integer> limited=new LinkedHashMap<>();for(var e:p.entrySet()){int v=(int)Math.floor(e.getValue()*scale);if(v>0)limited.put(e.getKey(),v);}p=limited;}for(var e:p.entrySet())ledger.addStockpile(country,e.getKey(),e.getValue());ledger.setDirty();}
    private static void add(Map<String,Integer> target,String item,int workers,int workersPerItem,double factor){if(workers>0){int amount=(int)Math.floor(workers/(double)workersPerItem*factor);if(amount>0)target.merge(item,amount,Integer::sum);}}
    private static double factor(MinecraftServer server,String country,WorkforceSector s){CountryDirection d=CountryDirectionManager.getDirection(server,country);if(d==null)return 1D;return switch(d){case INDUSTRIAL->s==WorkforceSector.INDUSTRY||s==WorkforceSector.MILITARY?1.25D:1D;case RESOURCE->s==WorkforceSector.AGRICULTURE||s==WorkforceSector.EXTRACTION?1.25D:1D;case TRADE->s==WorkforceSector.TRADE_LOGISTICS?1.25D:1D;};}
}
