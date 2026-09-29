package ru.zela.politicseconomy.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record EconomySnapshotPayload(
    String countryName, String direction, String government, String religion,
    int population, double populationWorkforceModifier,
    int workingPopulation, int employedPopulation, int unemployedPopulation, int workplaceCapacity,
    int[] workplaceCounts, int[] workplaceSlots, int[] sectorWorkers, int[] sectorAllocation,
    double[] sectorBonuses,
    String policySummary,
    int politicalUnrest, String politicalDemand, String politicalSupportSummary,
    int treasury, double infrastructureCost, double moneyDebt, double dieselModifier,
    double totalMaterialPerCycle, String[] materialIds, String[] materialNames,
    int[] materialStockpile, int[] materialDebt, double[] materialPerCycle,
    String[] modifierNames, double[] modifierValues,
    String[] cityNames, String[] cityCountries, String[] cityMayors, int[] cityTreasuries,
    int[] cityIncome, int[] cityInfrastructure, int[] cityPopulation, int[] cityTaxBlocks,
    boolean[] cityCapitals, boolean[] cityMine,
    int developmentLevel, int developmentPoints, int developmentNextThreshold,
    String developmentPerk, String developmentNextPerk,
    String[] marketItemIds, String[] marketItemNames,
    int[] marketBaseDemand, int[] marketRemaining, int[] marketSold,
    int[] marketImported, int[] marketPrices, long personalWallet
) implements CustomPacketPayload {
    public static final Type<EconomySnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("politicseconomy", "economy_snapshot"));
    private static final int MAX_MATERIALS = 32, MAX_MODIFIERS = 32, MAX_WORKFORCE = 8, MAX_MARKET_GOODS = 64;

    public static final StreamCodec<RegistryFriendlyByteBuf, EconomySnapshotPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public EconomySnapshotPayload decode(RegistryFriendlyByteBuf buf) {
            String countryName=buf.readUtf(128), direction=buf.readUtf(64), government=buf.readUtf(64), religion=buf.readUtf(64);
            int population=buf.readVarInt();
            double workforce=buf.readDouble();
            int workingPopulation=buf.readVarInt(), employedPopulation=buf.readVarInt(), unemployedPopulation=buf.readVarInt(), workplaceCapacity=buf.readVarInt();
            int[] workplaceCounts=readInts(buf,MAX_WORKFORCE), workplaceSlots=readInts(buf,MAX_WORKFORCE), sectorWorkers=readInts(buf,MAX_WORKFORCE), sectorAllocation=readInts(buf,MAX_WORKFORCE);
            double[] sectorBonuses=readDoubles(buf,MAX_WORKFORCE);
            String policySummary=buf.readUtf(512);
            int unrest=buf.readVarInt(); String demand=buf.readUtf(128), support=buf.readUtf(512);
            int treasury=buf.readInt(); double infrastructureCost=buf.readDouble(), moneyDebt=buf.readDouble(), dieselModifier=buf.readDouble(), totalMaterialPerCycle=buf.readDouble();
            String[] materialIds=readStrings(buf,MAX_MATERIALS,128), materialNames=readStrings(buf,MAX_MATERIALS,256);
            int[] materialStockpile=readInts(buf,MAX_MATERIALS), materialDebt=readInts(buf,MAX_MATERIALS); double[] materialPerCycle=readDoubles(buf,MAX_MATERIALS);
            String[] modifierNames=readStrings(buf,MAX_MODIFIERS,128); double[] modifierValues=readDoubles(buf,MAX_MODIFIERS);
            String[] cityNames=readStrings(buf,96,128), cityCountries=readStrings(buf,96,128), cityMayors=readStrings(buf,96,128);
            int[] cityTreasuries=readInts(buf,96), cityIncome=readInts(buf,96), cityInfrastructure=readInts(buf,96), cityPopulation=readInts(buf,96), cityTaxBlocks=readInts(buf,96);
            boolean[] cityCapitals=readBooleans(buf,96), cityMine=readBooleans(buf,96); int level=buf.readVarInt(), points=buf.readVarInt(), next=buf.readVarInt();
            String perk=buf.readUtf(256), nextPerk=buf.readUtf(256);
            String[] marketItemIds=readStrings(buf,MAX_MARKET_GOODS,128), marketItemNames=readStrings(buf,MAX_MARKET_GOODS,128);
            int[] marketBaseDemand=readInts(buf,MAX_MARKET_GOODS), marketRemaining=readInts(buf,MAX_MARKET_GOODS), marketSold=readInts(buf,MAX_MARKET_GOODS), marketImported=readInts(buf,MAX_MARKET_GOODS), marketPrices=readInts(buf,MAX_MARKET_GOODS);
            long personalWallet=buf.readLong();
            return new EconomySnapshotPayload(countryName,direction,government,religion,population,workforce,workingPopulation,employedPopulation,unemployedPopulation,workplaceCapacity,workplaceCounts,workplaceSlots,sectorWorkers,sectorAllocation,sectorBonuses,policySummary,unrest,demand,support,treasury,infrastructureCost,moneyDebt,dieselModifier,totalMaterialPerCycle,materialIds,materialNames,materialStockpile,materialDebt,materialPerCycle,modifierNames,modifierValues,cityNames,cityCountries,cityMayors,cityTreasuries,cityIncome,cityInfrastructure,cityPopulation,cityTaxBlocks,cityCapitals,cityMine,level,points,next,perk,nextPerk,marketItemIds,marketItemNames,marketBaseDemand,marketRemaining,marketSold,marketImported,marketPrices,personalWallet);
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, EconomySnapshotPayload v) {
            buf.writeUtf(limit(v.countryName,128),128); buf.writeUtf(limit(v.direction,64),64); buf.writeUtf(limit(v.government,64),64); buf.writeUtf(limit(v.religion,64),64);
            buf.writeVarInt(Math.max(0,v.population));
            buf.writeDouble(v.populationWorkforceModifier);
            buf.writeVarInt(Math.max(0,v.workingPopulation)); buf.writeVarInt(Math.max(0,v.employedPopulation)); buf.writeVarInt(Math.max(0,v.unemployedPopulation)); buf.writeVarInt(Math.max(0,v.workplaceCapacity));
            writeInts(buf,v.workplaceCounts,MAX_WORKFORCE); writeInts(buf,v.workplaceSlots,MAX_WORKFORCE); writeInts(buf,v.sectorWorkers,MAX_WORKFORCE); writeInts(buf,v.sectorAllocation,MAX_WORKFORCE); writeDoubles(buf,v.sectorBonuses,MAX_WORKFORCE);
            buf.writeUtf(limit(v.policySummary,512),512); buf.writeVarInt(Math.max(0,Math.min(100,v.politicalUnrest))); buf.writeUtf(limit(v.politicalDemand,128),128); buf.writeUtf(limit(v.politicalSupportSummary,512),512);
            buf.writeInt(v.treasury); buf.writeDouble(v.infrastructureCost); buf.writeDouble(v.moneyDebt); buf.writeDouble(v.dieselModifier); buf.writeDouble(v.totalMaterialPerCycle);
            writeStrings(buf,v.materialIds,MAX_MATERIALS,120); writeStrings(buf,v.materialNames,MAX_MATERIALS,240); writeInts(buf,v.materialStockpile,MAX_MATERIALS); writeInts(buf,v.materialDebt,MAX_MATERIALS); writeDoubles(buf,v.materialPerCycle,MAX_MATERIALS);
            writeStrings(buf,v.modifierNames,MAX_MODIFIERS,120); writeDoubles(buf,v.modifierValues,MAX_MODIFIERS); writeStrings(buf,v.cityNames,96,120); writeStrings(buf,v.cityCountries,96,120); writeStrings(buf,v.cityMayors,96,120); writeInts(buf,v.cityTreasuries,96); writeInts(buf,v.cityIncome,96); writeInts(buf,v.cityInfrastructure,96); writeInts(buf,v.cityPopulation,96); writeInts(buf,v.cityTaxBlocks,96); writeBooleans(buf,v.cityCapitals,96); writeBooleans(buf,v.cityMine,96);
            buf.writeVarInt(Math.max(1,v.developmentLevel)); buf.writeVarInt(Math.max(0,v.developmentPoints)); buf.writeVarInt(Math.max(0,v.developmentNextThreshold)); buf.writeUtf(limit(v.developmentPerk,256),256); buf.writeUtf(limit(v.developmentNextPerk,256),256);
            writeStrings(buf,v.marketItemIds,MAX_MARKET_GOODS,128); writeStrings(buf,v.marketItemNames,MAX_MARKET_GOODS,128); writeInts(buf,v.marketBaseDemand,MAX_MARKET_GOODS); writeInts(buf,v.marketRemaining,MAX_MARKET_GOODS); writeInts(buf,v.marketSold,MAX_MARKET_GOODS); writeInts(buf,v.marketImported,MAX_MARKET_GOODS); writeInts(buf,v.marketPrices,MAX_MARKET_GOODS); buf.writeLong(Math.max(0L,v.personalWallet));
        }
    };
    private static void writeInts(FriendlyByteBuf b,int[] v,int max){int n=Math.min(max,v==null?0:v.length);b.writeVarInt(n);for(int i=0;i<n;i++)b.writeVarInt(Math.max(0,v[i]));}
    private static int[] readInts(FriendlyByteBuf b,int max){int n=Math.min(max,Math.max(0,b.readVarInt()));int[] v=new int[n];for(int i=0;i<n;i++)v[i]=Math.max(0,b.readVarInt());return v;}
    private static void writeStrings(FriendlyByteBuf b,String[] v,int max,int chars){int n=Math.min(max,v==null?0:v.length);b.writeVarInt(n);for(int i=0;i<n;i++)b.writeUtf(limit(v[i],chars),chars);}
    private static String[] readStrings(FriendlyByteBuf b,int max,int chars){int n=Math.min(max,Math.max(0,b.readVarInt()));String[] v=new String[n];for(int i=0;i<n;i++)v[i]=b.readUtf(chars);return v;}
    private static void writeDoubles(FriendlyByteBuf b,double[] v,int max){int n=Math.min(max,v==null?0:v.length);b.writeVarInt(n);for(int i=0;i<n;i++)b.writeDouble(v[i]);}
    private static void writeBooleans(FriendlyByteBuf b,boolean[] v,int max){int n=Math.min(max,v==null?0:v.length);b.writeVarInt(n);for(int i=0;i<n;i++)b.writeBoolean(v[i]);}
    private static boolean[] readBooleans(FriendlyByteBuf b,int max){int n=Math.min(max,Math.max(0,b.readVarInt()));boolean[] v=new boolean[n];for(int i=0;i<n;i++)v[i]=b.readBoolean();return v;}
    private static double[] readDoubles(FriendlyByteBuf b,int max){int n=Math.min(max,Math.max(0,b.readVarInt()));double[] v=new double[n];for(int i=0;i<n;i++)v[i]=b.readDouble();return v;}
    private static String limit(String v,int max){if(v==null||v.isEmpty())return "";return v.length()<=max?v:v.substring(0,max);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
