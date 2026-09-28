package ru.zela.politicseconomy.mixin;

import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.BeltDeployerCallbacks;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zela.politicseconomy.integration.CreateProcessingContext;

/**
 * Provides the machine-owner context around the exact Create Deployer belt
 * processing call. The previous tick-wide context was cleared before
 * BeltDeployerCallbacks.activate() ran, so Sequenced Assembly could bypass
 * the industrial production modifier.
 */
@Mixin(value = BeltDeployerCallbacks.class, remap = false)
public abstract class BeltDeployerCallbacksMixin {
    @Inject(method = "activate", at = @At("HEAD"), remap = false)
    private static void politicseconomy$beginDeployerProcessing(
            TransportedItemStack transported,
            TransportedItemStackHandlerBehaviour handler,
            DeployerBlockEntity blockEntity,
            Recipe<?> recipe,
            CallbackInfo ci
    ) {
        if (blockEntity.getLevel() instanceof ServerLevel serverLevel) {
            CreateProcessingContext.begin(serverLevel, blockEntity.getBlockPos());
        }
    }

    @Inject(method = "activate", at = @At("RETURN"), remap = false)
    private static void politicseconomy$endDeployerProcessing(
            TransportedItemStack transported,
            TransportedItemStackHandlerBehaviour handler,
            DeployerBlockEntity blockEntity,
            Recipe<?> recipe,
            CallbackInfo ci
    ) {
        CreateProcessingContext.clear();
    }
}
