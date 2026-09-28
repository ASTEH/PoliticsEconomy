package ru.zela.politicseconomy.recipe;

import java.util.List;

/** Server-side description of the first matching crafting recipe for an item. */
public record RecipeAnalysis(
    String resultItemId,
    int resultCount,
    String recipeId,
    List<RecipeMaterialRequirement> ingredients
) {
    public RecipeAnalysis {
        ingredients = List.copyOf(ingredients);
    }
}
