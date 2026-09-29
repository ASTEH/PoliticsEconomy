package ru.zela.politicseconomy.research;

import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerPlayer;
import ru.zela.politicseconomy.country.CountryDevelopmentService;
import ru.zela.politicseconomy.country.CountryDirection;
import ru.zela.politicseconomy.country.CountryDirectionManager;
import ru.zela.politicseconomy.integration.PoliticsModIntegration;
import java.util.*;

public final class CountryResearchService {
    private CountryResearchService(){}
    public record Result(boolean success,String message){}

    public static CountryResearchSavedData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(
            new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                CountryResearchSavedData::create,CountryResearchSavedData::load,null
            ),CountryResearchSavedData.DATA_NAME);
    }
    public static Set<String> completed(MinecraftServer server,String country){return get(server).getCompleted(country);}
    public static int points(MinecraftServer server,String country){return get(server).getPoints(country);}
    public static void addPoints(MinecraftServer server,String country,int amount){get(server).addPoints(country,amount);}
    public static List<CountryResearch> forDirection(CountryDirection direction){return Arrays.stream(CountryResearch.values()).filter(r->r.direction()==direction).toList();}
    public static CountryResearch byId(String id){for(CountryResearch r:CountryResearch.values())if(r.id().equals(id))return r;return null;}

    public static String status(MinecraftServer server,String country,CountryResearch r){
        if(get(server).has(country,r.id()))return "COMPLETED";
        if(CountryDirectionManager.getDirection(server,country)!=r.direction())return "LOCKED";
        if(CountryDevelopmentService.level(server,country)<r.minLevel())return "LEVEL";
        if(!get(server).getCompleted(country).containsAll(r.prerequisites()))return "PREREQUISITE";
        if(points(server,country)<r.researchCost())return "POINTS";
        Country actual=countryObject(server,country);
        if(actual==null||actual.balance<r.moneyCost())return "MONEY";
        return "AVAILABLE";
    }

    public static Result research(ServerPlayer player,String id){
        Country country=PoliticsModIntegration.playerCountry(player).orElse(null);
        if(country==null)return new Result(false,"Ты не состоишь ни в одной стране.");
        boolean operator=player.isCreative()&&player.hasPermissions(2);
        CountryResearch r=byId(id);
        if(r==null)return new Result(false,"Исследование не найдено.");
        if(!operator&&PoliticsModIntegration.role(player,country)!=CountryRole.LEADER)
            return new Result(false,"Исследования может проводить только лидер страны.");
        String status=status(player.getServer(),country.getName(),r);
        if(!operator&&!status.equals("AVAILABLE")){
            return switch(status){
                case "COMPLETED"->new Result(false,"Это исследование уже завершено.");
                case "LEVEL"->new Result(false,"Для исследования нужен уровень экономики "+r.minLevel()+".");
                case "PREREQUISITE"->new Result(false,"Сначала нужно завершить предыдущее исследование.");
                case "POINTS"->new Result(false,"Недостаточно очков исследований. Нужно "+r.researchCost()+".");
                case "MONEY"->new Result(false,"Недостаточно денег. Нужно $"+r.moneyCost()+".");
                default->new Result(false,"Исследование пока недоступно.");
            };
        }
        if(!operator){
            country.balance-=r.moneyCost();
            get(player.getServer()).addPoints(country.getName(),-r.researchCost());
            var politics=net.krona.politicsmod.politics.PoliticsManager.get(player.serverLevel());
            if(politics!=null)politics.setDirty();
        }
        get(player.getServer()).complete(country.getName(),r.id());
        return new Result(true,"Исследование завершено: "+r.title());
    }

    private static Country countryObject(MinecraftServer server,String name){
        var politics=net.krona.politicsmod.politics.PoliticsManager.get(server.overworld());
        return politics==null?null:politics.getCountry(name);
    }

    public static String[] dashboard(MinecraftServer server,String country){
        CountryDirection direction=CountryDirectionManager.getDirection(server,country);
        if(direction==null)return new String[0];
        List<String> rows=new ArrayList<>();
        for(CountryResearch r:forDirection(direction)){
            rows.add(String.join("|",r.id(),r.title(),r.description(),status(server,country,r),
                Integer.toString(r.researchCost()),Integer.toString(r.moneyCost()),Integer.toString(r.minLevel()),
                r.icon(),String.join(",",r.prerequisites())));
        }
        return rows.toArray(String[]::new);
    }
}