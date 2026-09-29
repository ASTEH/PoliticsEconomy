package ru.zela.politicseconomy.research;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public final class CountryResearchSavedData extends SavedData {
    public static final String DATA_NAME="politicseconomy_country_research";
    private final Map<String,Integer> points=new HashMap<>();
    private final Map<String,Set<String>> completed=new HashMap<>();

    public static CountryResearchSavedData create(){return new CountryResearchSavedData();}
    public static CountryResearchSavedData load(CompoundTag tag,HolderLookup.Provider registries){
        CountryResearchSavedData data=create();
        if(!tag.contains("countries",Tag.TAG_COMPOUND))return data;
        CompoundTag countries=tag.getCompound("countries");
        for(String country:countries.getAllKeys()){
            CompoundTag c=countries.getCompound(country);
            data.points.put(country,Math.max(0,c.getInt("points")));
            Set<String> ids=new HashSet<>();
            if(c.contains("completed",Tag.TAG_LIST)){
                ListTag list=c.getList("completed",Tag.TAG_STRING);
                for(int i=0;i<list.size();i++)ids.add(list.getString(i));
            }
            data.completed.put(country,ids);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        CompoundTag countries=new CompoundTag();
        Set<String> names=new HashSet<>(points.keySet());names.addAll(completed.keySet());
        for(String country:names){
            CompoundTag c=new CompoundTag();c.putInt("points",getPoints(country));
            ListTag list=new ListTag();for(String id:getCompleted(country))list.add(StringTag.valueOf(id));
            c.put("completed",list);countries.put(country,c);
        }
        tag.put("countries",countries);return tag;
    }
    public int getPoints(String country){return Math.max(0,points.getOrDefault(country,0));}
    public void addPoints(String country,int amount){if(amount>0){points.put(country,getPoints(country)+amount);setDirty();}}
    public boolean has(String country,String id){return completed.getOrDefault(country,Set.of()).contains(id);}
    public Set<String> getCompleted(String country){return Set.copyOf(completed.getOrDefault(country,Set.of()));}
    public void complete(String country,String id){completed.computeIfAbsent(country,k->new HashSet<>()).add(id);setDirty();}
}