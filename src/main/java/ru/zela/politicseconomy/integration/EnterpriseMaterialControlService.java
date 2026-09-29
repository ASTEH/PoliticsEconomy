package ru.zela.politicseconomy.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import ru.zela.politicseconomy.economy.NationalMaterialDemandService;
import ru.zela.politicseconomy.economy.NationalMaterialLedgerSavedData;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves whether a specific enterprise is affected by the country's
 * outstanding material debts.
 *
 * <p>Once a recipe is seen, its concrete item dependencies are remembered for
 * that machine position. This lets one enterprise continue operating while
 * another stops because its own material is in debt.</p>
 */
public final class EnterpriseMaterialControlService {
    private EnterpriseMaterialControlService() {}

    public static boolean shouldSuspendForRecipe(
        ServerLevel level,
        BlockPos machinePos,
        Recipe<?> recipe
    ) {
        if (level == null || machinePos == null || recipe == null) {
            return false;
        }

        rememberRecipe(level, machinePos, recipe);
        return shouldSuspend(level, machinePos);
    }

    public static void rememberRecipe(ServerLevel level, BlockPos machinePos, Recipe<?> recipe) {
        if (level == null || machinePos == null || recipe == null) {
            return;
        }

        Set<String> dependencies = new HashSet<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }

            List<String> accepted = concreteItemIds(ingredient);
            if (!accepted.isEmpty()) {
                dependencies.add(NationalMaterialDemandService.choiceKey(accepted));
            }
        }

        if (dependencies.isEmpty()) {
            return;
        }

        ledger(level.getServer()).setDependencies(machinePos.asLong(), dependencies);
    }

    public static boolean shouldSuspend(ServerLevel level, BlockPos machinePos) {
        if (level == null || machinePos == null) {
            return false;
        }

        String countryName = countryName(level, machinePos);
        if (countryName == null || countryName.isBlank()) {
            return false;
        }

        NationalMaterialLedgerSavedData materialLedger =
            ru.zela.politicseconomy.economy.NationalMaterialConsumptionService.getLedger(level.getServer());

        Set<String> dependencies = ledger(level.getServer()).getDependencies(machinePos.asLong());
        if (dependencies.isEmpty()) {
            // Unknown enterprise: keep the safe legacy behaviour until we have
            // observed one of its recipes.
            return materialLedger.hasAnyDebt(countryName);
        }

        for (String debtKey : materialLedger.getDebts(countryName).keySet()) {
            if (materialLedger.getDebt(countryName, debtKey) <= 0) {
                continue;
            }
            if (choicesIntersect(debtKey, dependencies)) {
                return true;
            }
        }

        return false;
    }

    public static void remove(ServerLevel level, BlockPos machinePos) {
        if (level == null || machinePos == null) {
            return;
        }
        ledger(level.getServer()).remove(machinePos.asLong());
    }

    private static boolean choicesIntersect(String debtKey, Set<String> dependencies) {
        if (debtKey == null || debtKey.isBlank()) {
            return false;
        }
        for (String debtItem : debtKey.split("\\|")) {
            if (debtItem.isBlank()) {
                continue;
            }
            for (String dependency : dependencies) {
                for (String requiredItem : dependency.split("\\|")) {
                    if (debtItem.equals(requiredItem)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<String> concreteItemIds(Ingredient ingredient) {
        return java.util.Arrays.stream(ingredient.getItems())
            .filter(stack -> stack != null && !stack.isEmpty())
            .map(ItemStack::getItem)
            .map(item -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString())
            .distinct()
            .sorted()
            .toList();
    }

    private static String countryName(ServerLevel level, BlockPos machinePos) {
        return CountryContext.machineStateName(level, machinePos);
    }

    private static EnterpriseMaterialLedgerSavedData ledger(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedDataFactoryHolder().factory(),
            EnterpriseMaterialLedgerSavedData.DATA_NAME
        );
    }

    /** Keeps the generic type inference readable on NeoForge 1.21.1. */
    private static final class SavedDataFactoryHolder {
        private net.minecraft.world.level.saveddata.SavedData.Factory<EnterpriseMaterialLedgerSavedData> factory() {
            return new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                EnterpriseMaterialLedgerSavedData::create,
                EnterpriseMaterialLedgerSavedData::load,
                null
            );
        }
    }
}
