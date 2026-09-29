package ru.zela.politicseconomy.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * MTS compatibility for Millénaire villagers.
 *
 * MTS performs its own external-entity hit test. Millénaire villagers are
 * normal LivingEntity instances, but their custom implementation can be
 * missed by that hit path. This mixin restores the villager wrapper to the
 * generated MTS hit list without making MTS a hard dependency of the mod.
 */
@Pseudo
@Mixin(
    targets = "mcinterface1211.WrapperWorld",
    remap = false
)
public abstract class MtsMillenaireCombatMixin {
    private static final String MTS_WRAPPER_ENTITY =
        "mcinterface1211.WrapperEntity";

    @Inject(
        method = "attackEntities",
        at = @At("RETURN"),
        remap = false
    )
    private void politicseconomy$addMillenaireVillagers(
        @Coerce Object damage,
        @Coerce Object motion,
        boolean generateList,
        CallbackInfoReturnable<List<?>> cir
    ) {
        if (!generateList || cir.getReturnValue() == null || damage == null) {
            return;
        }

        Object box = readField(damage, "box");
        if (box == null) {
            return;
        }

        Vec3 center = pointToVec3(readField(box, "globalCenter"));
        if (center == null) {
            return;
        }

        double width = number(readField(box, "widthRadius"));
        double height = number(readField(box, "heightRadius"));
        double depth = number(readField(box, "depthRadius"));

        Vec3 motionVec = pointToVec3(motion);
        Vec3 end = motionVec == null ? center : center.add(motionVec);
        double padding = 0.05D;

        AABB search = new AABB(
            Math.min(center.x, end.x) - width - padding,
            Math.min(center.y, end.y) - height - padding,
            Math.min(center.z, end.z) - depth - padding,
            Math.max(center.x, end.x) + width + padding,
            Math.max(center.y, end.y) + height + padding,
            Math.max(center.z, end.z) + depth + padding
        );

        Level level = world;
        if (level == null) {
            return;
        }

        List<? extends LivingEntity> villagers =
            level.getEntitiesOfClass(
                LivingEntity.class,
                search,
                entity -> isMillenaireVillager(entity)
            );
        if (villagers.isEmpty()) {
            return;
        }

        List<?> hits = cir.getReturnValue();
        /*
         * Do not perform our own ray-trace here.
         *
         * MTS does a second, authoritative intersection test immediately
         * after attackEntities() returns (EntityBullet uses
         * entity.getBounds().getIntersection(position, endPoint)).
         * The previous compatibility layer duplicated that test using the
         * Damage box center, which is not guaranteed to be identical to the
         * bullet's actual position and could therefore miss Millénaire
         * villagers even though the projectile visually crossed them.
         */
        @SuppressWarnings("unchecked")
        List<Object> mutableHits = (List<Object>) hits;
        for (LivingEntity villager : villagers) {
            Object wrapper = createMtsWrapper(villager);
            if (wrapper != null && !hits.contains(wrapper)) {
                mutableHits.add(wrapper);
            }
        }
    }

    @org.spongepowered.asm.mixin.Shadow
    @Final
    protected Level world;

    private static boolean isMillenaireVillager(Entity entity) {
        return entity != null
            && entity.getClass().getName().equals(
                "org.millenaire.entity.MillVillager"
            );
    }

    private static Object createMtsWrapper(Entity entity) {
        try {
            Class<?> wrapperClass = Class.forName(MTS_WRAPPER_ENTITY);
            Method method =
                wrapperClass.getMethod("getWrapperFor", Entity.class);
            return method.invoke(null, entity);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getField(fieldName);
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0D;
    }

    private static Vec3 pointToVec3(Object point) {
        if (point == null) {
            return null;
        }
        Object x = readField(point, "x");
        Object y = readField(point, "y");
        Object z = readField(point, "z");
        if (!(x instanceof Number) || !(y instanceof Number) || !(z instanceof Number)) {
            return null;
        }
        return new Vec3(
            ((Number) x).doubleValue(),
            ((Number) y).doubleValue(),
            ((Number) z).doubleValue()
        );
    }
}
