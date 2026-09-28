package ru.zela.politicseconomy.mixin;

import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zela.politicseconomy.integration.CreateProcessingContext;
import ru.zela.politicseconomy.integration.EconomicMachineControlService;

/**
 * Supplies the active Mechanical Crafter position while Create resolves the
 * real crafting result.  The context is server-side only, so client-side
 * recipe previews are never modified.
 */
@Mixin(value = MechanicalCrafterBlockEntity.class, remap = false)
public abstract class MechanicalCrafterBlockEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void politicseconomy$beginCrafterTick(CallbackInfo ci) {
        MechanicalCrafterBlockEntity self = (MechanicalCrafterBlockEntity) (Object) this;
        if (self.getLevel() instanceof ServerLevel serverLevel
            && EconomicMachineControlService.shouldSuspend(serverLevel, self.getBlockPos())) {
            ci.cancel();
            return;
        }
        if (self.getLevel() instanceof ServerLevel serverLevel) {
            CreateProcessingContext.begin(serverLevel, self.getBlockPos());
        }
    }

    @Inject(method = "tick", at = @At("RETURN"), remap = false)
    private void politicseconomy$endCrafterTick(CallbackInfo ci) {
        CreateProcessingContext.clear();
    }
}
