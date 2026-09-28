package ru.zela.politicseconomy.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads vanilla and modded crafting recipes from the server RecipeManager.
 * This deliberately does not assume a particular mod's namespace, so Create
 * and future mods can be analyzed without hardcoded block lists.
 */
public final class RecipeAnalyzer {
    private RecipeAnalyzer() {}

    public static Optional<RecipeAnalysis> findCraftingRecipe(ServerLevel level, String resultItemId) {
        ResourceLocation target;
        try {
            target = ResourceLocation.parse(resultItemId);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }

        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<CraftingRecipe>> recipes = manager.getAllRecipesFor(RecipeType.CRAFTING);

        for (RecipeHolder<CraftingRecipe> holder : recipes) {
            CraftingRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty()) {
                continue;
            }

            ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
            if (!target.equals(resultId)) {
                continue;
            }

            List<RecipeMaterialRequirement> ingredients = new ArrayList<>();
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) {
                    continue;
                }

                ItemStack[] stacks = ingredient.getItems();
                List<String> accepted = new ArrayList<>();
                for (ItemStack stack : stacks) {
                    if (stack.isEmpty()) {
                        continue;
                    }
                    accepted.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                }
                accepted = accepted.stream().distinct().sorted().toList();
                ingredients.add(new RecipeMaterialRequirement(1, accepted));
            }

            return Optional.of(new RecipeAnalysis(
                resultId.toString(),
                result.getCount(),
                holder.id().toString(),
                ingredients
            ));
        }

        return Optional.empty();
    }

    public static Optional<RecipeAnalysis> findCraftingRecipeForBlock(ServerLevel level, String blockId) {
        ResourceLocation blockKey;
        try {
            blockKey = ResourceLocation.parse(blockId);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }

        var block = BuiltInRegistries.BLOCK.getOptional(blockKey).orElse(null);
        if (block == null) {
            return Optional.empty();
        }

        var item = block.asItem();
        if (item == net.minecraft.world.item.Items.AIR) {
            return Optional.empty();
        }

        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        return findCraftingRecipe(level, itemId);
    }
}
