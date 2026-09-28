package ru.zela.politicseconomy.country;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.EnumMap;
import java.util.Map;

public final class CountryPoliticalService {
    private static final int SUPPORT_STEP = 2;
    private static final int CRISIS_UNREST = 70;
    private static final int STRIKE_UNREST = 85;
    private static final int REFORM_LOCK_CYCLES = 3;
    private CountryPoliticalService() {}

    public static CountryPoliticalSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(CountryPoliticalSavedData::create, CountryPoliticalSavedData::load, null), CountryPoliticalSavedData.DATA_NAME);
    }

    public static void initialize(MinecraftServer server, String country) {
        CountryPoliticalSavedData data = get(server);
        if (data.hasSupport(country)) return;
        EnumMap<GovernmentType,Integer> target = targetSupport(CountryDirectionManager.getDirection(server,country), CountryWorkforceService.allocation(server,country));
        GovernmentType current = CountryPolicyManager.getGovernment(server,country);
        if (current != null) target.put(current,target.getOrDefault(current,0)+15);
        data.setSupport(country,target); data.setUnrest(country,0);
    }

    public static void processCycle(MinecraftServer server, String country, long cycle) {
        initialize(server,country); CountryPoliticalSavedData data=get(server);
        GovernmentType current=CountryPolicyManager.getGovernment(server,country);
        EnumMap<GovernmentType,Integer> target=targetSupport(CountryDirectionManager.getDirection(server,country),CountryWorkforceService.allocation(server,country));
        if(current!=null) target.put(current,target.getOrDefault(current,0)+10);
        EnumMap<GovernmentType,Integer> support=data.getSupport(country);
        for(GovernmentType t:GovernmentType.values()){int now=support.get(t), wanted=target.getOrDefault(t,0); support.put(t, now<wanted?Math.min(now+SUPPORT_STEP,wanted):Math.max(now-SUPPORT_STEP,wanted));}
        data.setSupport(country,support);
        if(current!=null){int unrest=data.getUnrest(country); int cs=data.getSupport(country,current); unrest += cs<35?3:(cs>=60?-3:-1); GovernmentType alt=strongestAlternative(support,current); if(unrest>=CRISIS_UNREST&&alt!=null&&support.get(alt)>=45&&support.get(alt)>=cs+8)data.setDemand(country,alt.commandName()); if(unrest>=STRIKE_UNREST&&!data.getDemand(country).isBlank()) unrest=STRIKE_UNREST; data.setUnrest(country,unrest);}
    }

    public static void onReform(MinecraftServer server,String country,String action,GovernmentType oldGovernment,GovernmentType newGovernment,long cycle){
        initialize(server,country); CountryPoliticalSavedData data=get(server); int unrest=data.getUnrest(country);
        if("government".equals(action)&&newGovernment!=null){int support=data.getSupport(country,newGovernment); int shock=support>=60?8:15+Math.max(0,35-support)/2; if(newGovernment.commandName().equals(data.getDemand(country))){shock-=20;data.setDemand(country,"");} unrest+=Math.max(0,shock);} else if("direction".equals(action)) unrest+=10; else if("religion".equals(action)) unrest+=8;
        data.setUnrest(country,unrest); data.setReformLock(country,cycle+REFORM_LOCK_CYCLES);
    }

    public static boolean reformLocked(MinecraftServer server,String country,long cycle){return cycle<get(server).getReformLock(country);}
    public static double productiveWorkforceFactor(MinecraftServer server,String country){int unrest=get(server).getUnrest(country); if(!get(server).getDemand(country).isBlank()&&unrest>=STRIKE_UNREST)return .10D; if(!get(server).getDemand(country).isBlank()&&unrest>=CRISIS_UNREST)return .55D; if(unrest>=60)return .85D; if(unrest>=40)return .93D; return 1D;}
    public static int affectedPopulation(MinecraftServer server,String country){int p=CountryPopulationService.population(server,country);return (int)Math.floor(p*(1D-productiveWorkforceFactor(server,country)));}
    public static String demandDisplay(MinecraftServer server,String country){GovernmentType t=GovernmentType.fromCommandName(get(server).getDemand(country));return t==null?"Нет активных требований":t.displayName();}
    public static String supportSummary(MinecraftServer server,String country){initialize(server,country);StringBuilder s=new StringBuilder();for(GovernmentType t:GovernmentType.values()){if(!s.isEmpty())s.append(" | ");s.append(t.displayName()).append(" ").append(get(server).getSupport(country,t)).append('%');}return s.toString();}

    private static GovernmentType strongestAlternative(Map<GovernmentType,Integer> support,GovernmentType current){GovernmentType best=null;int value=-1;for(GovernmentType t:GovernmentType.values())if(t!=current&&support.getOrDefault(t,0)>value){best=t;value=support.get(t);}return best;}
    private static EnumMap<GovernmentType,Integer> targetSupport(CountryDirection d,Map<WorkforceSector,Integer> a){int ag=a.getOrDefault(WorkforceSector.AGRICULTURE,0),in=a.getOrDefault(WorkforceSector.INDUSTRY,0),mil=a.getOrDefault(WorkforceSector.MILITARY,0),tr=a.getOrDefault(WorkforceSector.TRADE_LOGISTICS,0),sv=a.getOrDefault(WorkforceSector.CONSTRUCTION_SERVICES,0);EnumMap<GovernmentType,Integer> r=new EnumMap<>(GovernmentType.class);r.put(GovernmentType.DEMOCRACY,12+(int)Math.round(tr*.35+sv*.25));r.put(GovernmentType.COMMUNISM,10+(int)Math.round(in*.40+ag*.15));r.put(GovernmentType.MONARCHY,10+(int)Math.round(ag*.35+sv*.25));r.put(GovernmentType.FASCISM,6+(int)Math.round(mil*.60+in*.10));if(d==CountryDirection.TRADE)r.put(GovernmentType.DEMOCRACY,r.get(GovernmentType.DEMOCRACY)+8);if(d==CountryDirection.RESOURCE)r.put(GovernmentType.MONARCHY,r.get(GovernmentType.MONARCHY)+8);if(d==CountryDirection.INDUSTRIAL)r.put(GovernmentType.COMMUNISM,r.get(GovernmentType.COMMUNISM)+4);return r;}
}
