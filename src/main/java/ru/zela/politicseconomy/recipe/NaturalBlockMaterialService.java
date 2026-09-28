package ru.zela.politicseconomy.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;

import java.util.Set;

/**
 * Gives material content to natural blocks that normally have no crafting recipe.
 * The represented material is the block's own item, one unit per placed block.
 *
 * Examples: logs, leaves, flowers, saplings, dirt, grass blocks, sand and stone.
 */
public final class NaturalBlockMaterialService {
    private static final Set<String> EXTRA_NATURAL_BLOCKS = Set.of(
        "minecraft:grass_block",
        "minecraft:dirt",
        "minecraft:coarse_dirt",
        "minecraft:rooted_dirt",
        "minecraft:podzol",
        "minecraft:mycelium",
        "minecraft:mud",
        "minecraft:muddy_mangrove_roots",
        "minecraft:moss_block",
        "minecraft:moss_carpet",
        "minecraft:gravel",
        "minecraft:clay",
        "minecraft:short_grass",
        "minecraft:tall_grass",
        "minecraft:fern",
        "minecraft:large_fern",
        "minecraft:dead_bush",
        "minecraft:sweet_berry_bush",
        "minecraft:azalea",
        "minecraft:flowering_azalea",
        "minecraft:vines",
        "minecraft:glow_lichen",
        "minecraft:weeping_vines",
        "minecraft:weeping_vines_plant",
        "minecraft:twisting_vines",
        "minecraft:twisting_vines_plant",
        "minecraft:cave_vines",
        "minecraft:cave_vines_plant",
        "minecraft:seagrass",
        "minecraft:tall_seagrass",
        "minecraft:kelp",
        "minecraft:kelp_plant",
        "minecraft:sugar_cane",
        "minecraft:bamboo",
        "minecraft:bamboo_sapling",
        "minecraft:cactus",
        "minecraft:ice",
        "minecraft:packed_ice",
        "minecraft:blue_ice"
    );

    private NaturalBlockMaterialService() {}

    public static Material analyze(String blockId) {
        ResourceLocation id;
        try {
            id = ResourceLocation.parse(blockId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null || block.asItem() == net.minecraft.world.item.Items.AIR) {
            return null;
        }

        if (!isNatural(block, id.toString())) {
            return null;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(block.asItem()).toString();
        return new Material(itemId, 1.0D);
    }

    private static boolean isNatural(Block block, String blockId) {
        if (EXTRA_NATURAL_BLOCKS.contains(blockId)) {
            return true;
        }

        return block.builtInRegistryHolder().is(BlockTags.LOGS)
            || block.builtInRegistryHolder().is(BlockTags.LEAVES)
            || block.builtInRegistryHolder().is(BlockTags.SAPLINGS)
            || block.builtInRegistryHolder().is(BlockTags.SMALL_FLOWERS)
            || block.builtInRegistryHolder().is(BlockTags.TALL_FLOWERS)
            || block.builtInRegistryHolder().is(BlockTags.FLOWERS)
            || block.builtInRegistryHolder().is(BlockTags.DIRT)
            || block.builtInRegistryHolder().is(BlockTags.SAND)
            || block.builtInRegistryHolder().is(BlockTags.BASE_STONE_OVERWORLD)
            || block.builtInRegistryHolder().is(BlockTags.BASE_STONE_NETHER)
            || block.builtInRegistryHolder().is(BlockTags.NYLIUM)
            || block.builtInRegistryHolder().is(BlockTags.WART_BLOCKS)
            || block.builtInRegistryHolder().is(BlockTags.BAMBOO_BLOCKS)
            || block.builtInRegistryHolder().is(BlockTags.SNOW)
            || block.builtInRegistryHolder().is(BlockTags.ICE)
            || block.builtInRegistryHolder().is(BlockTags.CORAL_BLOCKS)
            || block.builtInRegistryHolder().is(BlockTags.CORAL_PLANTS)
            || block.builtInRegistryHolder().is(BlockTags.CORALS)
            || block.builtInRegistryHolder().is(BlockTags.CAVE_VINES);
    }

    public record Material(String itemId, double unitsPerBlock) {}
}
