package ru.zela.politicseconomy.economy;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Locale;

/**
 * Classifies extraction blocks using common cross-mod tags first, then naming
 * conventions as a fallback. This lets new mods participate without hardcoding
 * their block IDs.
 */
public final class ResourceExtractionClassifier {
    private static final TagKey<Block> COMMON_ORES = tag("c", "ores");
    private static final TagKey<Block> FORGE_ORES = tag("forge", "ores");
    private static final TagKey<Block> COMMON_LOGS = tag("c", "logs");
    private static final TagKey<Block> FORGE_LOGS = tag("forge", "logs");
    private static final TagKey<Block> COMMON_CROPS = tag("c", "crops");
    private static final TagKey<Block> FORGE_CROPS = tag("forge", "crops");
    private static final TagKey<Block> COMMON_FUELS = tag("c", "fuels");
    private static final TagKey<Block> FORGE_FUELS = tag("forge", "fuels");

    private ResourceExtractionClassifier() {}

    private static TagKey<Block> tag(String namespace, String path) {
        return TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(namespace, path)
        );
    }

    public static ResourceExtractionCategory classify(BlockState state) {
        ResourceExtractionCategory tagged = classifyByTags(state);
        if (tagged != null) {
            return tagged;
        }

        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (key == null) {
            return null;
        }

        String path = key.getPath().toLowerCase(Locale.ROOT);
        String id = key.toString().toLowerCase(Locale.ROOT);

        if (isOre(path)) {
            return ResourceExtractionCategory.ORE;
        }
        if (isWood(path)) {
            return ResourceExtractionCategory.WOOD;
        }
        if (isAgriculture(path)) {
            return ResourceExtractionCategory.AGRICULTURE;
        }
        if (isFuel(path, id)) {
            return ResourceExtractionCategory.FUEL;
        }
        if (isRawMaterial(path, id)) {
            return ResourceExtractionCategory.RAW_MATERIAL;
        }
        return null;
    }

    public static boolean isExtractive(BlockState state) {
        return classify(state) != null;
    }

    private static ResourceExtractionCategory classifyByTags(BlockState state) {
        if (state.is(BlockTags.LOGS) || state.is(COMMON_LOGS) || state.is(FORGE_LOGS)) {
            return ResourceExtractionCategory.WOOD;
        }
        if (state.is(COMMON_ORES) || state.is(FORGE_ORES)) {
            return ResourceExtractionCategory.ORE;
        }
        if (state.is(COMMON_CROPS) || state.is(FORGE_CROPS)) {
            return ResourceExtractionCategory.AGRICULTURE;
        }
        if (state.is(COMMON_FUELS) || state.is(FORGE_FUELS)) {
            return ResourceExtractionCategory.FUEL;
        }
        return null;
    }

    private static boolean isOre(String path) {
        return path.contains("_ore")
            || path.endsWith("ore")
            || path.contains("ore_")
            || path.contains("raw_")
            || path.contains("crystal_ore")
            || path.equals("ancient_debris");
    }

    private static boolean isWood(String path) {
        return path.contains("_log")
            || path.endsWith("_wood")
            || path.contains("log_")
            || path.contains("stripped_log")
            || path.contains("stripped_wood");
    }

    private static boolean isAgriculture(String path) {
        return path.contains("crop")
            || path.contains("wheat")
            || path.contains("carrot")
            || path.contains("potato")
            || path.contains("beetroot")
            || path.contains("nether_wart")
            || path.contains("cocoa")
            || path.equals("sugar_cane")
            || path.equals("cactus")
            || path.contains("berry_bush");
    }

    private static boolean isFuel(String path, String id) {
        return path.contains("coal")
            || path.contains("charcoal")
            || path.contains("coke")
            || path.contains("fuel")
            || path.contains("oil")
            || id.contains(":oil_");
    }

    private static boolean isRawMaterial(String path, String id) {
        return path.contains("salt")
            || path.contains("sulfur")
            || path.contains("sulphur")
            || path.contains("limestone") && !path.contains("brick")
            || id.contains(":raw_");
    }
}
