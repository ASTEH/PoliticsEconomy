package ru.zela.politicseconomy.integration;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Reflection bridge to the native Millénaire village relation and raid state.
 *
 * <p>PoliticsEconomy does not depend on Millénaire at compile time, so all
 * interaction with Village is performed reflectively.</p>
 */
public final class MillenaireCombatBridge {
    private MillenaireCombatBridge() {}

    public record Result(
        boolean relationChanged,
        boolean raidTriggered,
        String detail
    ) {}

    public record ArmyReport(
        int roleRecords,
        int liveMilitary,
        int liveAdults,
        int liveEntities
    ) {}

    public record RaidStatus(
        int relation,
        long planningStart,
        long start,
        long startGameTime,
        int strength,
        String target
    ) {}

    public static Result startWar(
        MinecraftServer server,
        String attackerKey,
        String defenderKey
    ) {
        if (server == null
            || !MillenaireIntegration.isStateKey(attackerKey)
            || !MillenaireIntegration.isStateKey(defenderKey)) {
            return new Result(false, false, "not_millenaire");
        }

        Object attacker = MillenaireIntegration.liveVillage(server, attackerKey);
        Object defender = MillenaireIntegration.liveVillage(server, defenderKey);
        if (attacker == null || defender == null) {
            return new Result(false, false, "village_unavailable");
        }

        Object defenderId = invoke(defender, "getId");
        if (defenderId == null) {
            return new Result(false, false, "defender_id_unavailable");
        }

        Object existingTarget = invoke(attacker, "getRaidTarget");
        if (sameId(existingTarget, defenderId)
            && longValue(invoke(attacker, "getRaidStart")) > 0L) {
            return new Result(true, true, "native_raid_already_active");
        }

        boolean relationChanged = setHostileRelation(
            server.overworld(),
            attacker,
            defender,
            defenderId
        );

        boolean raidTriggered = triggerImmediateRaid(
            attacker,
            defenderId,
            server.overworld().getGameTime()
        );

        String detail = raidTriggered
            ? "native_raid_forced"
            : relationChanged
                ? "hostile_relation_set"
                : "native_raid_api_failed";

        return new Result(relationChanged, raidTriggered, detail);
    }

    public static ArmyReport armyReport(
        MinecraftServer server,
        MillenaireIntegration.VillageSnapshot state
    ) {
        if (server == null || state == null) {
            return new ArmyReport(0, 0, 0, 0);
        }

        int roleRecords = state.workersBySector()
            .getOrDefault(
                ru.zela.politicseconomy.country.WorkforceSector.MILITARY,
                0
            );

        ServerLevel level = server.overworld();
        Set<UUID> seen = new HashSet<>();
        int military = 0;
        int adults = 0;
        int entities = 0;

        for (ChunkPos chunk : state.territory()) {
            double minX = chunk.getMinBlockX();
            double minZ = chunk.getMinBlockZ();
            AABB box = new AABB(
                minX,
                level.getMinBuildHeight(),
                minZ,
                minX + 16.0D,
                level.getMaxBuildHeight(),
                minZ + 16.0D
            );

            for (LivingEntity entity : level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                candidate -> candidate.isAlive()
                    && isMillenaireEntity(candidate)
            )) {
                if (!seen.add(entity.getUUID())) continue;

                entities++;
                if (isMilitaryEntity(entity)) military++;
                if (isAdult(entity)) adults++;
            }
        }

        return new ArmyReport(
            roleRecords,
            military,
            adults,
            entities
        );
    }

    public static List<String> discoverCombatApi(
        MinecraftServer server,
        String stateKey
    ) {
        Object village = MillenaireIntegration.liveVillage(server, stateKey);
        if (village == null) return List.of();

        List<String> methods = new ArrayList<>();
        for (Method method : village.getClass().getMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("raid")
                || name.contains("relation")
                || name.contains("fight")
                || name.contains("combat")
                || name.contains("guard")
                || name.contains("escort")
                || name.contains("fighter")) {
                methods.add(signature(method));
            }
        }

        methods.sort(String::compareTo);
        return List.copyOf(methods);
    }

    public static RaidStatus raidStatus(
        MinecraftServer server,
        String stateKey
    ) {
        Object village = MillenaireIntegration.liveVillage(server, stateKey);
        if (village == null) {
            return new RaidStatus(0, 0L, 0L, 0L, 0, "");
        }

        Object targetId = invoke(village, "getRaidTarget");
        Object relation = targetId == null
            ? null
            : invoke(village, "getRelation", targetId);

        return new RaidStatus(
            intValue(relation),
            longValue(invoke(village, "getRaidPlanningStart")),
            longValue(invoke(village, "getRaidStart")),
            longValue(invoke(village, "getRaidStartGameTime")),
            intValue(invoke(village, "getVillageRaidStrength")),
            targetId == null ? "" : targetId.toString()
        );
    }

    private static boolean setHostileRelation(
        ServerLevel level,
        Object attacker,
        Object defender,
        Object defenderId
    ) {
        invoke(attacker, "setRelation", defenderId, -100);

        Object attackerId = invoke(attacker, "getId");
        if (attackerId != null) {
            invoke(defender, "setRelation", attackerId, -100);
        }

        int relation = intValue(invoke(attacker, "getRelation", defenderId));
        return relation <= -20;
    }

    private static boolean triggerImmediateRaid(
        Object attacker,
        Object defenderId,
        long gameTime
    ) {
        try {
            invoke(attacker, "clearRaid");
            invoke(attacker, "setRaidTarget", defenderId);
            invoke(attacker, "setRaidPlanningStart", gameTime);
            invoke(attacker, "setRaidStart", gameTime);
            invoke(attacker, "setRaidStartGameTime", gameTime);

            Object target = invoke(attacker, "getRaidTarget");
            long raidStart = longValue(invoke(attacker, "getRaidStart"));
            long planningStart =
                longValue(invoke(attacker, "getRaidPlanningStart"));

            return sameId(target, defenderId)
                && raidStart == gameTime
                && planningStart == gameTime;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isMillenaireEntity(LivingEntity entity) {
        if (entity == null) return false;

        String className =
            entity.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        if (className.startsWith("org.millenaire.")) return true;

        try {
            var key =
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getKey(entity.getType());

            return key != null && "millenaire".equals(key.getNamespace());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isMilitaryEntity(LivingEntity entity) {
        StringBuilder text = new StringBuilder()
            .append(entity.getClass().getName()).append(' ')
            .append(String.valueOf(invoke(entity, "getRoleName"))).append(' ')
            .append(String.valueOf(invoke(entity, "getVillagerTypeId"))).append(' ')
            .append(String.valueOf(invoke(entity, "getTypeId")));

        Object record = invoke(entity, "getVillagerRecord");
        if (record == null) {
            record = invoke(entity, "getRecord");
        }

        if (record != null) {
            text.append(' ')
                .append(String.valueOf(invoke(record, "getRoleName")))
                .append(' ')
                .append(String.valueOf(invoke(record, "getVillagerTypeId")));
        }

        String value = text.toString().toLowerCase(java.util.Locale.ROOT);

        return value.contains("soldier")
            || value.contains("guard")
            || value.contains("warrior")
            || value.contains("general")
            || value.contains("army");
    }

    private static boolean isAdult(LivingEntity entity) {
        Object baby = invoke(entity, "isBaby");
        if (baby instanceof Boolean value) {
            return !value;
        }

        String value =
            entity.getClass().getName().toLowerCase(java.util.Locale.ROOT);

        return !value.contains("child")
            && !value.contains("boy")
            && !value.contains("girl");
    }

    private static boolean sameId(Object a, Object b) {
        return a != null
            && b != null
            && a.toString().equals(b.toString());
    }

    private static long longValue(Object value) {
        return value instanceof Number number
            ? number.longValue()
            : 0L;
    }

    private static int intValue(Object value) {
        return value instanceof Number number
            ? number.intValue()
            : 0;
    }

    private static Object invoke(
        Object target,
        String name,
        Object... args
    ) {
        if (target == null) return null;

        try {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(name)
                    || method.getParameterCount() != args.length) {
                    continue;
                }

                Class<?>[] params = method.getParameterTypes();
                boolean compatible = true;

                for (int i = 0; i < params.length; i++) {
                    if (args[i] == null) continue;

                    if (!wrap(params[i]).isInstance(args[i])) {
                        compatible = false;
                        break;
                    }
                }

                if (compatible) {
                    return method.invoke(target, args);
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static String signature(Method method) {
        StringBuilder result =
            new StringBuilder(method.getName()).append('(');

        Class<?>[] parameters = method.getParameterTypes();

        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) result.append(", ");
            result.append(parameters[i].getSimpleName());
        }

        return result.append(')').toString();
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }
}
