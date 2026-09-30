package ru.zela.politicseconomy.integration;

import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Optional bridge to Millénaire's native diplomacy/raid system.
 *
 * <p>Millénaire 9.x already contains village-versus-village raids. We use
 * reflection because PoliticsEconomy intentionally has no compile-time
 * dependency on Millénaire's classes.</p>
 */
public final class MillenaireCombatBridge {
    private MillenaireCombatBridge() {}

    public record Result(
        boolean relationChanged,
        boolean raidTriggered,
        String detail
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

        boolean relationChanged = setHostileRelation(attacker, defender);
        boolean raidTriggered = planRaid(attacker, defender);

        String detail = raidTriggered
            ? "native_raid_triggered"
            : relationChanged
                ? "hostile_relation_set"
                : "no_native_raid_api_found";

        return new Result(relationChanged, raidTriggered, detail);
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

    private static boolean setHostileRelation(
        Object attacker,
        Object defender
    ) {
        UUID defenderId = villageUuid(defender);
        String[] names = {"setRelation", "setVillageRelation", "setRelations"};

        for (String name : names) {
            for (Method method : attacker.getClass().getMethods()) {
                if (!method.getName().equals(name)) continue;

                Object[] args = buildArguments(
                    method.getParameterTypes(),
                    defender,
                    defenderId,
                    -100
                );
                if (args == null) continue;

                try {
                    method.invoke(attacker, args);
                    return true;
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    private static boolean planRaid(
        Object attacker,
        Object defender
    ) {
        UUID defenderId = villageUuid(defender);
        String[] names = {
            "planRaid",
            "startRaid",
            "launchRaid",
            "prepareRaid",
            "setRaidTarget"
        };

        for (String name : names) {
            for (Method method : attacker.getClass().getMethods()) {
                if (!method.getName().equals(name)) continue;

                Object[] args = buildArguments(
                    method.getParameterTypes(),
                    defender,
                    defenderId,
                    0
                );
                if (args == null) continue;

                try {
                    method.invoke(attacker, args);
                    return true;
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    private static Object[] buildArguments(
        Class<?>[] parameterTypes,
        Object targetVillage,
        UUID targetId,
        int relationValue
    ) {
        Object[] args = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = wrap(parameterTypes[i]);

            if (type.isInstance(targetVillage)) {
                args[i] = targetVillage;
            } else if (type == UUID.class) {
                args[i] = targetId;
            } else if (type == String.class) {
                args[i] = targetId == null ? "" : targetId.toString();
            } else if (type == Integer.class) {
                args[i] = relationValue;
            } else if (type == Long.class) {
                args[i] = (long) relationValue;
            } else if (type == Boolean.class) {
                args[i] = true;
            } else {
                return null;
            }
        }

        return args;
    }

    private static UUID villageUuid(Object village) {
        if (village == null) return null;

        try {
            for (Method method : village.getClass().getMethods()) {
                if (!method.getName().equals("getId")
                    || method.getParameterCount() != 0) continue;

                Object id = method.invoke(village);
                if (id instanceof UUID uuid) return uuid;

                if (id != null) {
                    for (Method sub : id.getClass().getMethods()) {
                        if (!sub.getName().equals("uuid")
                            || sub.getParameterCount() != 0) continue;
                        Object uuid = sub.invoke(id);
                        if (uuid instanceof UUID value) return value;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String signature(Method method) {
        StringBuilder result = new StringBuilder(method.getName()).append('(');
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
