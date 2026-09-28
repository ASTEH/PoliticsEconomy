package ru.zela.politicseconomy.mixin;

import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.zela.politicseconomy.integration.CreateProcessingContext;
import ru.zela.politicseconomy.integration.EconomicMachineControlService;

/**
 * Supplies the machine location while a Mechanical Press asks Create's
 * RecipeApplier to generate its real processing outputs.
 */
@Mixin(value = MechanicalPressBlockEntity.class, remap = false)
public abstract class MechanicalPressBlockEntityMixin {
    @Inject(method = "tryProcessInWorld", at = @At("HEAD"), cancellable = true, remap = false)
    private void politicseconomy$beginWorldProcessing(
        net.minecraft.world.entity.item.ItemEntity itemEntity,
        boolean simulate,
        CallbackInfoReturnable<Boolean> cir
    ) {
        Level level = itemEntity.level();
        if (level instanceof ServerLevel serverLevel && !simulate) {
            if (EconomicMachineControlService.shouldSuspend(serverLevel, ((MechanicalPressBlockEntity) (Object) this).getBlockPos())) {
                cir.setReturnValue(false);
                return;
            }
            CreateProcessingContext.begin(serverLevel, ((MechanicalPressBlockEntity) (Object) this).getBlockPos());
        }
    }

    @Inject(method = "tryProcessInWorld", at = @At("RETURN"), remap = false)
    private void politicseconomy$endWorldProcessing(
        net.minecraft.world.entity.item.ItemEntity itemEntity,
        boolean simulate,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!simulate) {
            CreateProcessingContext.clear();
        }
    }

    @Inject(method = "tryProcessOnBelt", at = @At("HEAD"), cancellable = true, remap = false)
    private void politicseconomy$beginBeltProcessing(
        com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack input,
        java.util.List<net.minecraft.world.item.ItemStack> outputList,
        boolean simulate,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (((MechanicalPressBlockEntity) (Object) this).getLevel() instanceof ServerLevel serverLevel && !simulate) {
            if (EconomicMachineControlService.shouldSuspend(serverLevel, ((MechanicalPressBlockEntity) (Object) this).getBlockPos())) {
                cir.setReturnValue(false);
                return;
            }
            CreateProcessingContext.begin(serverLevel, ((MechanicalPressBlockEntity) (Object) this).getBlockPos());
        }
    }

    @Inject(method = "tryProcessOnBelt", at = @At("RETURN"), remap = false)
    private void politicseconomy$endBeltProcessing(
        com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack input,
        java.util.List<net.minecraft.world.item.ItemStack> outputList,
        boolean simulate,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!simulate) {
            CreateProcessingContext.clear();
        }
    }
}
