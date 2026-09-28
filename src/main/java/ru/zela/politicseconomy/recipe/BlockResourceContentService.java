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
        choices.sort(Comparator.comparing(MaterialChoice::key));

        return new BlockResourceContent(
            analysis.resultItemId(),
            analysis.recipeId(),
            resultCount,
            List.copyOf(choices)
        );
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
