package ru.zela.politicseconomy.mixin;

import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zela.politicseconomy.integration.CreateProcessingContext;
import ru.zela.politicseconomy.integration.EconomicMachineControlService;

/**
 * Keeps the Spout machine position available while filling is performed.
 */
@Mixin(value = SpoutBlockEntity.class, remap = false)
public abstract class SpoutBlockEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void politicseconomy$beginSpoutTick(CallbackInfo ci) {
        SpoutBlockEntity self = (SpoutBlockEntity) (Object) this;
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
    private void politicseconomy$endSpoutTick(CallbackInfo ci) {
        CreateProcessingContext.clear();
    }
}
