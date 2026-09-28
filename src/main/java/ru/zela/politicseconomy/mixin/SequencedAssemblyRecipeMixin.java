package ru.zela.politicseconomy.mixin;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.zela.politicseconomy.integration.CreateProcessingContext;
import ru.zela.politicseconomy.integration.CreateProductionService;
import ru.zela.politicseconomy.integration.EnterpriseMaterialControlService;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the industrial production multiplier to the PRIMARY output of a
 * completed Sequenced Assembly. Create's own rollResult() first decides
 * whether the batch succeeds or produces random salvage; we deliberately do
 * not change those odds. The multiplier is applied only when the rolled stack
 * is the recipe's primary result.
 */
@Mixin(value = SequencedAssemblyRecipe.class, remap = false)
public abstract class SequencedAssemblyRecipeMixin {
    @Inject(
        method = "rollResult(Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/item/ItemStack;",
        at = @At("RETURN"),
        cancellable = true,
        remap = false
    )
    private void politicseconomy$modifyPrimaryOutput(
        RandomSource random,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        CreateProcessingContext.Context context = CreateProcessingContext.current();
        if (context == null || !(context.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ItemStack result = cir.getReturnValue();
        if (result == null || result.isEmpty()) {
            return;
        }

        EnterpriseMaterialControlService.rememberRecipe(
            serverLevel,
            context.machinePos(),
            (SequencedAssemblyRecipe) (Object) this
        );
        if (EnterpriseMaterialControlService.shouldSuspend(serverLevel, context.machinePos())) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        // SequencedAssemblyRecipe#getResultItem ignores the registry argument
        // in Create 6.0.10 and simply returns the first result-pool entry.
        ItemStack primary = ((SequencedAssemblyRecipe) (Object) this).getResultItem(null);
        if (primary == null || primary.isEmpty()) {
            return;
        }

        // Only successful primary rolls are multiplied. Random salvage keeps
        // Create's native probabilities unchanged.
        if (!ItemStack.isSameItemSameComponents(result, primary)) {
            return;
        }

        List<ItemStack> outputs = new ArrayList<>();
        outputs.add(result.copy());
        CreateProductionService.applyItemProductionBonus(
            serverLevel,
            context.machinePos(),
            outputs
        );

        if (outputs.isEmpty()) {
            cir.setReturnValue(ItemStack.EMPTY);
        } else {
            cir.setReturnValue(outputs.getFirst());
        }
    }
}
