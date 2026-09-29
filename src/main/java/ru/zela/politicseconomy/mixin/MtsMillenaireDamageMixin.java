package ru.zela.politicseconomy.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server-side fallback for MTS damage against Millénaire villagers.
 */
@Pseudo
@Mixin(
    targets = "mcinterface1211.WrapperEntity",
    remap = false
)
public abstract class MtsMillenaireDamageMixin {
    private static final Map<Entity, HealthSnapshot> POLITICSECONOMY$HEALTH_BEFORE =
        new WeakHashMap<>();

    @Shadow
    @Final
    protected Entity entity;

    @Inject(
        method = "attack",
        at = @At("HEAD"),
        remap = false
    )
    private void politicseconomy$captureHealth(
        @Coerce Object damage,
        CallbackInfo ci
    ) {
        if (!(entity instanceof LivingEntity living)
            || !isMillenaireVillager(entity)
            || living.level().isClientSide
            || damage == null) {
            return;
        }

        double amount = readNumber(damage, "amount");
        if (amount > 0.0D && Double.isFinite(amount)) {
            POLITICSECONOMY$HEALTH_BEFORE.put(
                entity,
                new HealthSnapshot(living.getHealth(), living.getAbsorptionAmount())
            );
        }
    }

    @Inject(
        method = "attack",
        at = @At("TAIL"),
        remap = false
    )
    private void politicseconomy$ensureDamage(
        @Coerce Object damage,
        CallbackInfo ci
    ) {
        if (!(entity instanceof LivingEntity living)
            || !isMillenaireVillager(entity)
            || living.level().isClientSide
            || damage == null) {
            return;
        }

        HealthSnapshot before = POLITICSECONOMY$HEALTH_BEFORE.remove(entity);
        if (before == null) {
            return;
        }

        double amount = readNumber(damage, "amount");
        if (!(amount > 0.0D) || !Double.isFinite(amount)) {
            return;
        }

        if (living.getHealth() >= before.health()
            && living.getAbsorptionAmount() >= before.absorption()) {
            float newHealth = Math.max(0.0F, before.health() - (float) amount);
            living.setHealth(newHealth);

            if (newHealth <= 0.0F && living.isAlive()) {
                DamageSource source = genericDamageSource(living);
                if (source != null) {
                    living.die(source);
                } else {
                    living.kill();
                }
            }
        }

        double beforeTotal =
            before.health() + before.absorption();
        double afterTotal =
            Math.max(0.0D, living.getHealth())
                + Math.max(0.0D, living.getAbsorptionAmount());
        double actualDamage =
            Math.max(0.0D, beforeTotal - afterTotal);
        boolean casualty =
            !living.isAlive() || living.getHealth() <= 0.0F;

        if (actualDamage > 0.0D || casualty) {
            ru.zela.politicseconomy.integration.MilitaryEconomyService
                .recordMillCombatHit(
                    living,
                    actualDamage > 0.0D ? actualDamage : amount,
                    casualty
                );
        }
    }

    private static boolean isMillenaireVillager(Entity entity) {
        return entity != null
            && entity.getClass().getName().equals(
                "org.millenaire.entity.MillVillager"
            );
    }

    private static double readNumber(Object target, String fieldName) {
        try {
            return ((Number) target.getClass()
                .getField(fieldName)
                .get(target)).doubleValue();
        } catch (Throwable ignored) {
            return 0.0D;
        }
    }

    private record HealthSnapshot(float health, float absorption) {}

    private static DamageSource genericDamageSource(LivingEntity entity) {
        try {
            Holder<DamageType> holder = entity.level()
                .registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(DamageTypes.GENERIC)
                .get();
            return new DamageSource(holder);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
