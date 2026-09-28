package ru.zela.politicseconomy.economy;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.BlockEntity;
import ru.zela.politicseconomy.infrastructure.InfrastructureManager;
import ru.zela.politicseconomy.integration.EnterpriseMaterialControlService;
import ru.zela.politicseconomy.recipe.BlockResourceContentService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Turns an unpaid material debt into physical consequences.
 *
 * <p>Ordinary infrastructure is allowed to decay: for each material that is
 * still unpaid after an economy cycle, at most one contributing non-machine
 * structure is destroyed. Create/compatible machine blocks are never destroyed;
 * their operation is suspended separately while the country has any debt.</p>
 */
public final class MaterialShortageResolutionService {
    private MaterialShortageResolutionService() {}

    public static void resolveCountry(
        ServerLevel level,
        String countryName,
        NationalMaterialDemandService.CountryDemand demand,
        NationalMaterialLedgerSavedData ledger
    ) {
        if (countryName == null || countryName.isBlank() || !ledger.hasAnyDebt(countryName)) {
            return;
        }

        List<PlacedBlock> blocks = collectCountryBlocks(level, countryName);
        if (blocks.isEmpty()) {
            return;
        }

        List<Map.Entry<String, Integer>> debts = new ArrayList<>(ledger.getDebts(countryName).entrySet());
        debts.removeIf(entry -> entry.getValue() == null || entry.getValue() <= 0);
        debts.sort(Map.Entry.comparingByKey());

        for (Map.Entry<String, Integer> debt : debts) {
            String materialKey = debt.getKey();
            if (materialKey == null || materialKey.isBlank()) {
                continue;
            }

            // One physical loss per unresolved material and cycle. The debt is
            // intentionally NOT erased: the country still has to repay it from
            // the national warehouse before suspended machines can resume.
            for (PlacedBlock placed : blocks) {
                if (placed.machine()) {
                    continue;
                }
                if (placed.protectedBlock()) {
                    continue;
                }
                if (!choicesIntersect(materialKey, placed.materialChoiceKeys())) {
                    continue;
                }

                if (destroyInfrastructureBlock(level, placed.pos())) {
                    break;
                }
            }
        }
    }

    private static List<PlacedBlock> collectCountryBlocks(ServerLevel level, String countryName) {
        PoliticsManager politics = PoliticsManager.get(level);
        if (politics == null) {
            return List.of();
        }

        List<PlacedBlock> result = new ArrayList<>();
        for (Map.Entry<Long, Map<Long, String>> chunkEntry :
            InfrastructureManager.get(level.getServer()).getDimension(level).entrySet()) {
            var chunkPos = new net.minecraft.world.level.ChunkPos(chunkEntry.getKey());
            Country country = politics.getCountryAt(chunkPos);
            if (country == null || !country.getName().equals(countryName)) {
                continue;
            }

            for (Map.Entry<Long, String> entry : chunkEntry.getValue().entrySet()) {
                BlockPos pos = BlockPos.of(entry.getKey());
                if (!level.hasChunkAt(pos)) {
                    continue;
                }

                var state = level.getBlockState(pos);
                String actualBlockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(state.getBlock()).toString();
                if (!actualBlockId.equals(entry.getValue())) {
                    // Stale registry entry: let the normal world state win.
                    continue;
                }

                BlockResourceContentService.BlockResourceContent content =
                    BlockResourceContentService.analyze(level, entry.getValue());
                if (!content.hasContent()) {
                    continue;
                }

                String blockId = entry.getValue();
                result.add(new PlacedBlock(
                    pos,
                    blockId,
                    content.choices().stream().map(BlockResourceContentService.MaterialChoice::key).toList(),
                    content.choices().stream()
                        .mapToDouble(BlockResourceContentService.MaterialChoice::unitsPerBlock)
                        .sum(),
                    isMachine(level, pos),
                    isProtectedSystemBlock(blockId)
                ));
            }
        }

        result.sort(Comparator.comparingLong(block -> block.pos().asLong()));
        return result;
    }

    private static boolean choicesIntersect(String debtKey, List<String> materialChoices) {
        if (debtKey == null || debtKey.isBlank()) return false;
        for (String debtItem : debtKey.split("\\|")) {
            if (debtItem.isBlank()) continue;
            for (String choice : materialChoices) {
                for (String requiredItem : choice.split("\\|")) {
                    if (debtItem.equals(requiredItem)) return true;
                }
            }
        }
        return false;
    }

    private static boolean isProtectedSystemBlock(String blockId) {
        return switch (blockId.toLowerCase(java.util.Locale.ROOT)) {
            case "politicsmod:founding_stone",
                 "politicsmod:city_stone",
                 "politicsmod:trade_warehouse",
                 "politicsmod:tax_block",
                 "politicsmod:embassy_block",
                 "politicsmod:radar_block",
                 "politicsmod:vault_block" -> true;
            default -> false;
        };
    }

    private static boolean isMachine(ServerLevel level, BlockPos pos) {
        // Prefer the actual BlockEntity type when one exists.
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            String className = blockEntity.getClass().getName().toLowerCase(java.util.Locale.ROOT);
            if (className.startsWith("com.simibubi.create.")
                || className.startsWith("com.jesz.createdieselgenerators.")
                || className.startsWith("com.simibubi.createaddition.")
                || className.startsWith("com.rabbitminers.")
                || className.contains("createbigcannons")) {
                return true;
            }
        }

        // Some Create/addon blocks do not expose a BlockEntity at the exact
        // position that gets registered by the infrastructure tracker. Their
        // namespace still identifies them reliably, so never destroy them as
        // ordinary infrastructure during material decay.
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getKey(level.getBlockState(pos).getBlock()).toString().toLowerCase(java.util.Locale.ROOT);

        return blockId.startsWith("create:")
            || blockId.startsWith("createdieselgenerators:")
            || blockId.startsWith("createaddition:")
            || blockId.startsWith("createdeco:")
            || blockId.startsWith("createbigcannons:")
            || blockId.startsWith("create_connected:")
            || blockId.startsWith("create_enchantment_industry:");
    }

    private static boolean destroyInfrastructureBlock(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }

        if (level.getBlockState(pos).isAir()) {
            InfrastructureManager.remove(level, pos);
            return false;
        }

        boolean destroyed = level.destroyBlock(pos, false);
        if (destroyed) {
            EnterpriseMaterialControlService.remove(level, pos);
            InfrastructureManager.remove(level, pos);
        }
        return destroyed;
    }

    private record PlacedBlock(
        BlockPos pos,
        String blockId,
        List<String> materialChoiceKeys,
        double resourceScore,
        boolean machine,
        boolean protectedBlock
    ) {}
}
