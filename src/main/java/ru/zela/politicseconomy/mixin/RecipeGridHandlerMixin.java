package ru.zela.politicseconomy.mixin;

import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.zela.politicseconomy.integration.CreateProcessingContext;
import ru.zela.politicseconomy.integration.CreateProductionService;
import ru.zela.politicseconomy.integration.EconomicMachineControlService;

/** Applies the country's industrial modifier to real Mechanical Crafter output. */
@Mixin(value = RecipeGridHandler.class, remap = false)
public abstract class RecipeGridHandlerMixin {
    @Inject(method = "tryToApplyRecipe", at = @At("RETURN"), remap = false)
    private static void politicseconomy$modifyCrafterResult(
            Level level,
            RecipeGridHandler.GroupedItems items,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        CreateProcessingContext.Context context = CreateProcessingContext.current();
        if (context == null || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        if (EconomicMachineControlService.shouldSuspend(serverLevel, context.machinePos())) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        ItemStack result = cir.getReturnValue();
        if (result == null || result.isEmpty()) {
            return;
        }

        java.util.ArrayList<ItemStack> outputs = new java.util.ArrayList<>();
        outputs.add(result.copy());
        CreateProductionService.applyItemProductionBonus(serverLevel, context.machinePos(), outputs);

        if (outputs.isEmpty()) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        // Mechanical Crafter expects a single ItemStack here.  Large outputs
        // are constrained by the normal crafting result shape; a multiplier
        // below 1 can produce an empty result, while >1 is retained up to the
        // item's normal stack limit.
        ItemStack adjusted = outputs.get(0);
        if (outputs.size() > 1) {
            // The shared service may split stacks when a result exceeds the
            // max stack size.  Mechanical Crafter has only one result slot, so
            // keep the first stack and leave the remainder for later versions
            // when multi-stack crafter output is supported by Create itself.
            adjusted = adjusted.copy();
        }
        cir.setReturnValue(adjusted);
    }
}
