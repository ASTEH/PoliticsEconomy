package ru.zela.politicseconomy.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import com.simibubi.create.AllDataComponents;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.zela.politicseconomy.integration.CreateProcessingContext;
import ru.zela.politicseconomy.integration.CreateProductionService;
import ru.zela.politicseconomy.integration.EconomicMachineControlService;

/**
 * Applies the country production modifier to actual Spout item results.
 * This is especially important for Filling steps inside Sequenced Assembly,
 * because Spout filling does not use Create's RecipeApplier path.
 */
@Mixin(targets = "com.simibubi.create.content.fluids.spout.FillingBySpout", remap = false)
public abstract class FillingBySpoutMixin {
    @Inject(
        method = "fillItem(Lnet/minecraft/world/level/Level;ILnet/minecraft/world/item/ItemStack;Lnet/neoforged/neoforge/fluids/FluidStack;)Lnet/minecraft/world/item/ItemStack;",
        at = @At("RETURN"),
        cancellable = true,
        remap = false
    )
    private static void politicseconomy$modifySpoutOutput(
        Level level,
        int requiredAmount,
        ItemStack input,
        FluidStack availableFluid,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        CreateProcessingContext.Context context = CreateProcessingContext.current();
        if (context == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (EconomicMachineControlService.shouldSuspend(serverLevel, context.machinePos())) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        // A Sequenced Assembly fill produces an intermediate stack. The final
        // primary result is modified once by SequencedAssemblyRecipeMixin.
        if (input != null && (input.has(AllDataComponents.SEQUENCED_ASSEMBLY) ||
            (!input.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(input.getItem()).getPath().startsWith("incomplete_")))) {
            return;
        }

        ItemStack result = cir.getReturnValue();
        if (result == null || result.isEmpty()) {
            return;
        }

        java.util.List<ItemStack> outputs = new java.util.ArrayList<>();
        outputs.add(result.copy());
        CreateProductionService.applyItemProductionBonus(serverLevel, context.machinePos(), outputs);

        if (outputs.isEmpty()) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        // Spout produces one transported stack. If a multiplier creates more
        // than one stack, return the first stack here; the existing remainder
        // ledger still preserves fractional/overflow production for subsequent
        // operations.
        cir.setReturnValue(outputs.getFirst());
    }
}
