package ru.zela.politicseconomy.mixin;

import com.jesz.createdieselgenerators.content.diesel_engine.modular.ModularDieselEngineBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.level.ServerLevel;
import ru.zela.politicseconomy.integration.EconomicMachineControlService;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.zela.politicseconomy.integration.DieselFuelEfficiencyService;

@Mixin(ModularDieselEngineBlockEntity.class)
public abstract class ModularDieselEngineBlockEntityMixin {
    @Redirect(
        method = "tick",
        at = @At(
            value = "FIELD",
            target = "Lcom/jesz/createdieselgenerators/content/diesel_engine/modular/ModularDieselEngineBlockEntity;cachedBurnRate:F",
            opcode = org.objectweb.asm.Opcodes.GETFIELD
        )
    )
    private float politicseconomy$adjustFuelDrain(ModularDieselEngineBlockEntity engine) {
        return DieselFuelEfficiencyService.adjustBurnRate(engine, engine.getCachedBurnRate());
    }
    @Inject(method = "tick", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true, remap = false)
    private void politicseconomy$pauseForMaterialDebt(CallbackInfo ci) {
        ModularDieselEngineBlockEntity self = (ModularDieselEngineBlockEntity) (Object) this;
        if (self.getLevel() instanceof ServerLevel level
            && EconomicMachineControlService.shouldSuspend(level, self.getBlockPos())) {
            ci.cancel();
        }
    }

}
