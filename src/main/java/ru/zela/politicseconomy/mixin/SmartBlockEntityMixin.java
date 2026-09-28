package ru.zela.politicseconomy.mixin;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zela.politicseconomy.integration.EconomicMachineControlService;

/** Pauses Create/extension machines that inherit Create's SmartBlockEntity tick. */
@Mixin(value = SmartBlockEntity.class, remap = false)
public abstract class SmartBlockEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void politicseconomy$pauseForMaterialDebt(CallbackInfo ci) {
        SmartBlockEntity self = (SmartBlockEntity) (Object) this;
        if (self.getLevel() instanceof ServerLevel level
            && EconomicMachineControlService.shouldSuspend(level, self.getBlockPos())) {
            ci.cancel();
        }
    }
}
