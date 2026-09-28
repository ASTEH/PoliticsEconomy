package ru.zela.politicseconomy.recipe;

import java.util.List;

/** One ingredient slot from a crafting recipe. The list may contain alternatives from a tag. */
public record RecipeMaterialRequirement(
    int amount,
    List<String> acceptedItems
) {
    public RecipeMaterialRequirement {
        acceptedItems = List.copyOf(acceptedItems);
    }

    public String displayName() {
        if (acceptedItems.isEmpty()) {
            return "<unknown>";
        }
        if (acceptedItems.size() == 1) {
            return acceptedItems.get(0);
        }
        return "one of [" + String.join(", ", acceptedItems) + "]";
    }
}
