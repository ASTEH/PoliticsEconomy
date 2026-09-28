package ru.zela.politicseconomy.mixin;

import net.minecraft.world.item.ItemStack;
import com.simibubi.create.AllDataComponents;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.zela.politicseconomy.integration.CreateProcessingContext;
import ru.zela.politicseconomy.integration.CreateProductionService;
import ru.zela.politicseconomy.integration.EconomicMachineControlService;
import ru.zela.politicseconomy.integration.EnterpriseMaterialControlService;

import java.util.List;

/**
 * Applies country production modifiers to real item results produced through
 * Create's shared RecipeApplier path (pressing, cutting, milling, sawing, etc.).
 */
@Mixin(targets = "com.simibubi.create.foundation.recipe.RecipeApplier", remap = false)
public abstract class RecipeApplierMixin {
    @Inject(
        method = "applyRecipeOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/crafting/Recipe;Z)Ljava/util/List;",
        at = @At("RETURN"),
        cancellable = true,
        remap = false
    )
    private static void politicseconomy$modifyProcessingOutputs(
        Level level,
        ItemStack input,
        Recipe<?> recipe,
        boolean returnProcessingRemainder,
        CallbackInfoReturnable<List<ItemStack>> cir
    ) {
        CreateProcessingContext.Context context = CreateProcessingContext.current();
        if (context == null || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        if (EnterpriseMaterialControlService.shouldSuspendForRecipe(serverLevel, context.machinePos(), recipe)) {
            cir.setReturnValue(java.util.List.of());
            return;
        }

        List<ItemStack> outputs = cir.getReturnValue();
        if (outputs == null || outputs.isEmpty()) {
            return;
        }

        // Deployer/press/spout steps inside Sequenced Assembly produce the
        // transitional item, not the final economic output. Those intermediate
        // stacks must not receive the industrial multiplier. The final primary
        // result is handled by SequencedAssemblyRecipeMixin instead.
        if (input != null && input.has(AllDataComponents.SEQUENCED_ASSEMBLY)) {
            return;
        }
        for (ItemStack output : outputs) {
            if (output != null && !output.isEmpty() && output.has(AllDataComponents.SEQUENCED_ASSEMBLY)) {
                return;
            }
            if (output != null && !output.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(output.getItem())
                .getPath().startsWith("incomplete_")) {
                return;
            }
        }

        List<ItemStack> mutable = new java.util.ArrayList<>(outputs);
        CreateProductionService.applyItemProductionBonus(serverLevel, context.machinePos(), mutable);
        cir.setReturnValue(mutable);
    }
}
