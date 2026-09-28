package ru.zela.politicseconomy.economy;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/** Converts real Minecraft inventory items into national-resource units. */
public final class NationalResourceItemValue {
    private static final TagKey<Item> C_INGOTS = tag("c", "ingots");
    private static final TagKey<Item> C_NUGGETS = tag("c", "nuggets");
    private static final TagKey<Item> C_FUELS = tag("c", "fuels");

    private NationalResourceItemValue() {}

    public record Value(NationalResource resource, int unitsPerItem) {}

    public static Optional<Value> classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        Item item = stack.getItem();

        // Food is deliberately first: edible modded foods become national food naturally.
        if (stack.has(DataComponents.FOOD)) {
            return Optional.of(new Value(NationalResource.FOOD, 4));
        }

        if (stack.is(C_INGOTS)) {
            return Optional.of(new Value(NationalResource.METAL, 10));
        }
        if (stack.is(C_NUGGETS)) {
            return Optional.of(new Value(NationalResource.METAL, 1));
        }
        if (item == Items.IRON_INGOT || item == Items.GOLD_INGOT || item == Items.COPPER_INGOT) {
            return Optional.of(new Value(NationalResource.METAL, 10));
        }

        if (stack.is(ItemTags.LOGS)) {
            return Optional.of(new Value(NationalResource.WOOD, 4));
        }
        if (stack.is(ItemTags.PLANKS)) {
            return Optional.of(new Value(NationalResource.WOOD, 1));
        }

        if (stack.is(C_FUELS) || item == Items.COAL || item == Items.CHARCOAL) {
            return Optional.of(new Value(NationalResource.FUEL, 10));
        }
        if (item == Items.COAL_BLOCK) {
            return Optional.of(new Value(NationalResource.FUEL, 90));
        }

        return Optional.empty();
    }

    private static TagKey<Item> tag(String namespace, String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
