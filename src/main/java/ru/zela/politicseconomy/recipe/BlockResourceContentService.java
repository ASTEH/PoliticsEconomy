package ru.zela.politicseconomy.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a block's real crafting recipe into concrete material requirements.
 * The result is normalized to one placed block, so recipes producing multiple
 * blocks/items are handled correctly.
 */
public final class BlockResourceContentService {
    private BlockResourceContentService() {}

    public static BlockResourceContent analyze(ServerLevel level, String blockId) {
        NaturalBlockMaterialService.Material natural = NaturalBlockMaterialService.analyze(blockId);
        if (natural != null) {
            return new BlockResourceContent(
                blockId,
                "natural",
                1,
                List.of(new MaterialChoice(List.of(natural.itemId()), natural.unitsPerBlock()))
            );
        }

        RecipeAnalysis analysis = RecipeAnalyzer.findCraftingRecipeForBlock(level, blockId).orElse(null);
        if (analysis == null || analysis.ingredients().isEmpty()) {
            return new BlockResourceContent(blockId, "", 1, List.of());
        }

        int resultCount = Math.max(1, analysis.resultCount());
        Map<List<String>, Integer> slotCounts = new LinkedHashMap<>();
        for (RecipeMaterialRequirement ingredient : analysis.ingredients()) {
            if (ingredient.acceptedItems().isEmpty()) {
                continue;
            }
            List<String> accepted = ingredient.acceptedItems().stream()
                .filter(id -> BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.parse(id)).isPresent())
                .distinct()
                .sorted()
                .toList();
            if (accepted.isEmpty()) {
                continue;
            }
            slotCounts.merge(accepted, Math.max(1, ingredient.amount()), Integer::sum);
        }

        List<MaterialChoice> choices = new ArrayList<>();
        for (Map.Entry<List<String>, Integer> entry : slotCounts.entrySet()) {
            double perBlock = entry.getValue() / (double) resultCount;
            choices.add(new MaterialChoice(entry.getKey(), perBlock));
        }
        choices.sort(Comparator.comparingDouble(BlockResourceContentService::supportCost));

        if (!choices.isEmpty()) {
            MaterialChoice selected = choices.get(0);
            String cheapestItem = selected.acceptedItemIds().stream()
                .min(Comparator.comparingDouble(BlockResourceContentService::itemCost))
                .orElse(selected.representativeItemId());

            selected = new MaterialChoice(
                cheapestItem.isBlank() ? selected.acceptedItemIds() : List.of(cheapestItem),
                selected.unitsPerBlock()
            );

            return new BlockResourceContent(
                analysis.resultItemId(),
                analysis.recipeId(),
                resultCount,
                List.of(selected)
            );
        }

        return new BlockResourceContent(
            analysis.resultItemId(),
            analysis.recipeId(),
            resultCount,
            List.of()
        );
    }

    /**
     * Infrastructure upkeep represents one practical component of a recipe,
     * not every ingredient. Common building materials are deliberately cheaper
     * than rare/advanced items so upkeep does not punish a new country.
     */
    private static double supportCost(MaterialChoice choice) {
        if (choice == null || choice.acceptedItemIds().isEmpty()) return Double.POSITIVE_INFINITY;
        return choice.unitsPerBlock() * choice.acceptedItemIds().stream()
            .mapToDouble(BlockResourceContentService::itemCost)
            .min()
            .orElse(Double.POSITIVE_INFINITY);
    }

    private static double itemCost(String itemId) {
        if (itemId == null || itemId.isBlank()) return 1000.0D;

        String normalized = itemId.toLowerCase(java.util.Locale.ROOT);
        double cost = 10.0D;

        if (normalized.contains("netherite")) cost = 100.0D;
        else if (normalized.contains("diamond")) cost = 70.0D;
        else if (normalized.contains("emerald")) cost = 55.0D;
        else if (normalized.contains("gold")) cost = 28.0D;
        else if (normalized.contains("brass")) cost = 22.0D;
        else if (normalized.contains("steel")) cost = 20.0D;
        else if (normalized.contains("iron")) cost = 16.0D;
        else if (normalized.contains("copper") || normalized.contains("zinc")) cost = 12.0D;
        else if (normalized.contains("coal") || normalized.contains("charcoal")) cost = 8.0D;
        else if (normalized.contains("stone") || normalized.contains("cobblestone")
            || normalized.contains("deepslate") || normalized.contains("gravel")) cost = 3.0D;
        else if (normalized.contains("brick") || normalized.contains("terracotta")) cost = 6.0D;
        else if (normalized.contains("plank") || normalized.contains("log")
            || normalized.contains("wood") || normalized.contains("stick")) cost = 2.0D;
        else if (normalized.contains("dirt") || normalized.contains("sand")
            || normalized.contains("clay")) cost = 1.0D;

        return cost;
    }

    public record MaterialChoice(List<String> acceptedItemIds, double unitsPerBlock) {
        public MaterialChoice {
            acceptedItemIds = acceptedItemIds.stream().distinct().sorted().toList();
        }

        public String key() {
            return String.join("|", acceptedItemIds);
        }

        public boolean isExact() {
            return acceptedItemIds.size() == 1;
        }

        public String representativeItemId() {
            if (acceptedItemIds.isEmpty()) {
                return "";
            }
            for (String itemId : acceptedItemIds) {
                if (itemId.startsWith("minecraft:")) {
                    return itemId;
                }
            }
            return acceptedItemIds.get(0);
        }
    }

    public record BlockResourceContent(
        String blockId,
        String recipeId,
        int resultCount,
        List<MaterialChoice> choices
    ) {
        public BlockResourceContent {
            choices = List.copyOf(choices);
        }

        public boolean hasContent() {
            return !choices.isEmpty();
        }
    }
}
