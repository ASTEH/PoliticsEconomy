package ru.zela.politicseconomy.research;

import ru.zela.politicseconomy.country.CountryDirection;
import java.util.List;

public enum CountryResearch {
    INDUSTRIAL_MECHANIZATION("industrial_mechanization",CountryDirection.INDUSTRIAL,"Механизация производства","Совершенствование производственных процессов.",1,2,150,"minecraft:iron_ingot",List.of()),
    INDUSTRIAL_ELECTRIFICATION("industrial_electrification",CountryDirection.INDUSTRIAL,"Электрификация","Снижение расхода топлива промышленностью.",2,3,250,"minecraft:redstone",List.of("industrial_mechanization")),
    INDUSTRIAL_MACHINERY("industrial_machinery",CountryDirection.INDUSTRIAL,"Машиностроение","Снижение содержания промышленной инфраструктуры.",3,4,400,"minecraft:anvil",List.of("industrial_electrification")),
    INDUSTRIAL_MILITARY("industrial_military",CountryDirection.INDUSTRIAL,"Военная промышленность","Усиление военного производства.",4,5,600,"minecraft:tnt",List.of("industrial_machinery")),
    INDUSTRIAL_ADVANCED("industrial_advanced",CountryDirection.INDUSTRIAL,"Передовая промышленность","Снижение стоимости сложной промышленности.",5,7,900,"minecraft:diamond",List.of("industrial_military")),

    RESOURCE_MINING("resource_mining",CountryDirection.RESOURCE,"Глубокая добыча","Снижение потерь при добыче ресурсов.",1,2,150,"minecraft:iron_pickaxe",List.of()),
    RESOURCE_REFINING("resource_refining",CountryDirection.RESOURCE,"Обогащение сырья","Увеличение производства сырья.",2,3,250,"minecraft:raw_iron",List.of("resource_mining")),
    RESOURCE_AGRO("resource_agro",CountryDirection.RESOURCE,"Агропромышленность","Усиление сельскохозяйственного производства.",3,4,400,"minecraft:wheat",List.of("resource_refining")),
    RESOURCE_OIL("resource_oil",CountryDirection.RESOURCE,"Нефтяная промышленность","Снижение расхода дизельного топлива.",4,5,600,"minecraft:bucket",List.of("resource_agro")),
    RESOURCE_RESERVE("resource_reserve",CountryDirection.RESOURCE,"Стратегические резервы","Снижение содержания ресурсной инфраструктуры.",5,7,900,"minecraft:chest",List.of("resource_oil")),

    TRADE_MARKET("trade_market",CountryDirection.TRADE,"Единый рынок","Увеличение дохода от торговли.",1,2,150,"minecraft:emerald",List.of()),
    TRADE_LOGISTICS("trade_logistics",CountryDirection.TRADE,"Логистика","Снижение торговой комиссии.",2,3,250,"minecraft:minecart",List.of("trade_market")),
    TRADE_RAIL("trade_rail",CountryDirection.TRADE,"Железнодорожная сеть","Снижение содержания транспортной инфраструктуры.",3,4,400,"minecraft:rail",List.of("trade_logistics")),
    TRADE_MOTOR("trade_motor",CountryDirection.TRADE,"Автотранспорт","Ускорение роста населения.",4,5,600,"minecraft:minecart",List.of("trade_rail")),
    TRADE_HUB("trade_hub",CountryDirection.TRADE,"Международный торговый узел","Дополнительный доход от торговли.",5,7,900,"minecraft:compass",List.of("trade_motor"));

    private final String id,title,description,icon;
    private final CountryDirection direction;
    private final int minLevel,researchCost,moneyCost;
    private final List<String> prerequisites;

    CountryResearch(String id,CountryDirection direction,String title,String description,int minLevel,int researchCost,int moneyCost,String icon,List<String> prerequisites){
        this.id=id;this.direction=direction;this.title=title;this.description=description;this.minLevel=minLevel;this.researchCost=researchCost;this.moneyCost=moneyCost;this.icon=icon;this.prerequisites=List.copyOf(prerequisites);
    }
    public String id(){return id;} public CountryDirection direction(){return direction;} public String title(){return title;}
    public String description(){return description;} public int minLevel(){return minLevel;} public int researchCost(){return researchCost;}
    public int moneyCost(){return moneyCost;} public String icon(){return icon;} public List<String> prerequisites(){return prerequisites;}
}