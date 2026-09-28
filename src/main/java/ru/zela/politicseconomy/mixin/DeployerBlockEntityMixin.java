package ru.zela.politicseconomy.mixin;

import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zela.politicseconomy.integration.CreateProcessingContext;
import ru.zela.politicseconomy.integration.EconomicMachineControlService;

/**
 * Exposes the Deployer position while Create performs belt/deployer recipe
 * processing. This also covers Deployer steps used by Sequenced Assembly.
 */
@Mixin(value = DeployerBlockEntity.class, remap = false)
public abstract class DeployerBlockEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void politicseconomy$beginDeployerTick(CallbackInfo ci) {
        DeployerBlockEntity self = (DeployerBlockEntity) (Object) this;
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
    private void politicseconomy$endDeployerTick(CallbackInfo ci) {
        CreateProcessingContext.clear();
    }
}
