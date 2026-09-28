package ru.zela.politicseconomy.economy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import ru.zela.politicseconomy.infrastructure.InfrastructureManager;
import ru.zela.politicseconomy.recipe.BlockResourceContentService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Calculates exact material content and recurring material consumption for a country. */
public final class NationalMaterialDemandService {
    /** 1% of the material content of infrastructure is consumed per economy cycle. */
    public static final double BLOCK_MATERIAL_CONSUMPTION_RATE = 0.01D;
    private static final int MAX_DASHBOARD_MATERIALS = 32;

    private NationalMaterialDemandService() {}

    public static CountryDemand calculate(ServerLevel level, String countryName) {
        var stats = InfrastructureManager.getCountryStats(level.getServer(), countryName);
        Map<String, AggregateDemand> aggregated = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> blockEntry : stats.blocks().entrySet()) {
            int blockCount = Math.max(0, blockEntry.getValue());
            if (blockCount <= 0) {
                continue;
            }

            BlockResourceContentService.BlockResourceContent content =
                BlockResourceContentService.analyze(level, blockEntry.getKey());
            for (BlockResourceContentService.MaterialChoice choice : content.choices()) {
                String key = choice.key();
                double materialContent = choice.unitsPerBlock() * blockCount;
                AggregateDemand aggregate = aggregated.get(key);
                if (aggregate == null) {
                    aggregate = new AggregateDemand(choice.acceptedItemIds());
                    aggregated.put(key, aggregate);
                }
                aggregate.content += materialContent;
                aggregate.blocksContributing += blockCount;
            }
        }

        List<MaterialDemand> demands = new ArrayList<>();
        for (AggregateDemand aggregate : aggregated.values()) {
            double perCycle = aggregate.content * BLOCK_MATERIAL_CONSUMPTION_RATE;
            demands.add(new MaterialDemand(
                aggregate.acceptedItemIds,
                aggregate.content,
                perCycle,
                aggregate.blocksContributing
            ));
        }
        demands.sort(Comparator.comparingDouble(MaterialDemand::perCycleConsumption).reversed());

        return new CountryDemand(stats.totalBlocks(), List.copyOf(demands));
    }

    public static List<MaterialDemand> dashboard(ServerLevel level, String countryName) {
        return calculate(level, countryName).materials().stream()
            .limit(MAX_DASHBOARD_MATERIALS)
            .toList();
    }

    public static String choiceKey(List<String> acceptedItemIds) {
        return String.join("|", acceptedItemIds);
    }

    public static boolean acceptsItem(MaterialDemand demand, String itemId) {
        return demand.acceptedItemIds().contains(itemId);
    }

    public static String displayName(ServerLevel level, List<String> acceptedItemIds) {
        if (acceptedItemIds.isEmpty()) {
            return "Неизвестный материал";
        }
        if (acceptedItemIds.size() == 1) {
            return itemDisplayName(level, acceptedItemIds.get(0));
        }

        StringBuilder result = new StringBuilder("Один из: ");
        for (int i = 0; i < acceptedItemIds.size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(itemDisplayName(level, acceptedItemIds.get(i)));
            if (result.length() > 140) {
                result.append("…");
                break;
            }
        }
        return result.toString();
    }

    public static String itemDisplayName(ServerLevel level, String itemId) {
        try {
            var key = net.minecraft.resources.ResourceLocation.parse(itemId);
            var item = BuiltInRegistries.ITEM.getOptional(key).orElse(null);
            if (item == null) {
                return itemId;
            }
            return new ItemStack(item).getHoverName().getString();
        } catch (IllegalArgumentException ignored) {
            return itemId;
        }
    }

    private static final class AggregateDemand {
        private final List<String> acceptedItemIds;
        private double content;
        private int blocksContributing;

        private AggregateDemand(List<String> acceptedItemIds) {
            this.acceptedItemIds = List.copyOf(acceptedItemIds);
        }
    }

    public record CountryDemand(
        int trackedInfrastructureBlocks,
        List<MaterialDemand> materials
    ) {}

    public record MaterialDemand(
        List<String> acceptedItemIds,
        double materialContent,
        double perCycleConsumption,
        int contributingBlocks
    ) {
        public MaterialDemand {
            acceptedItemIds = List.copyOf(acceptedItemIds);
        }

        public String key() {
            return choiceKey(acceptedItemIds);
        }

        public boolean isExact() {
            return acceptedItemIds.size() == 1;
        }
    }
}
