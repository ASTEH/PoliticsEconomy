package ru.zela.politicseconomy.mixin;

import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zela.politicseconomy.integration.CreateProcessingContext;
import ru.zela.politicseconomy.integration.EconomicMachineControlService;

/**
 * Starts a short-lived context while Create actually applies a Basin recipe.
 *
 * We intentionally do not @Shadow getLevel/getBlockPos here. Those methods are
 * declared by Minecraft's BlockEntity superclass, not by Create's
 * BasinOperatingBlockEntity itself. The previous shadows therefore failed at
 * runtime on Create 6.0.10.
 */
@Mixin(value = BasinOperatingBlockEntity.class, remap = false)
public abstract class BasinOperatingBlockEntityMixin {
    private BlockEntity politicseconomy$blockEntity() {
        return (BlockEntity) (Object) this;
    }

    private Level politicseconomy$level() {
        return politicseconomy$blockEntity().getLevel();
    }

    private net.minecraft.core.BlockPos politicseconomy$blockPos() {
        return politicseconomy$blockEntity().getBlockPos();
    }

    @Inject(method = "applyBasinRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void politicseconomy$beginCreateProcessing(CallbackInfo ci) {
        if (politicseconomy$level() instanceof ServerLevel serverLevel) {
            if (EconomicMachineControlService.shouldSuspend(serverLevel, politicseconomy$blockPos())) {
                ci.cancel();
                return;
            }
            CreateProcessingContext.begin(serverLevel, politicseconomy$blockPos());
        }
    }

    @Inject(method = "applyBasinRecipe", at = @At("RETURN"), remap = false)
    private void politicseconomy$endCreateProcessing(CallbackInfo ci) {
        CreateProcessingContext.clear();
    }
}
