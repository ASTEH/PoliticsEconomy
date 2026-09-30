package ru.zela.politicseconomy.integration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.zela.politicseconomy.country.CountryWorkforceService;
import ru.zela.politicseconomy.country.WorkforceSector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** First real ground-combat layer for Millénaire settlements. */
public final class GroundWarService {
    private static final long TICK_INTERVAL = 1L;
    private static final long ATTACK_COOLDOWN = 20L;
    private static final double RALLY_RADIUS = 56.0D;
    private static final double PARTY_TRACK_RADIUS = 4096.0D;
    private static final double TARGET_RADIUS = 5.5D;
    private static final int MAX_PARTY = 12;
    private static final String PARTY_PREFIX = "pewar:";
    private static final Map<UUID, Long> NEXT_ATTACK_TICK = new HashMap<>();

    private GroundWarService() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld() == null) return;
        long now = server.overworld().getGameTime();
        if (now % TICK_INTERVAL != 0L) return;

        MilitaryWarSavedData wars = MilitaryWarSavedData.get(server);
        for (MilitaryWarSavedData.War war : wars.wars()) {
            if (war.type() != MilitaryWarSavedData.WarType.GROUND) continue;
            processWar(server, wars, war, now);
        }
    }

    private static void processWar(MinecraftServer server, MilitaryWarSavedData wars,
                                   MilitaryWarSavedData.War war, long now) {
        MillenaireIntegration.VillageSnapshot attacker =
            MillenaireIntegration.snapshotForStateKey(server, war.attacker());
        MillenaireIntegration.VillageSnapshot defender =
            MillenaireIntegration.snapshotForStateKey(server, war.defender());
        if (attacker == null || defender == null) return;

        int attackerPartySize = Math.min(MAX_PARTY,
            Math.max(3, partySize(attacker, server, true)));
        int defenderPartySize = Math.min(MAX_PARTY,
            Math.max(3, partySize(defender, server, false)));

        MillenaireCombatBridge.mobilizeForWar(server, attacker, attackerPartySize);
        MillenaireCombatBridge.mobilizeForWar(server, defender, defenderPartySize);

        MillenaireCombatBridge.Result nativeResult =
            MillenaireCombatBridge.startWar(server, war.attacker(), war.defender());
        if (nativeResult.raidTriggered()) return;

        ServerLevel level = server.overworld();
        Set<LivingEntity> attackers = party(level, attacker, war);
        Set<LivingEntity> defenders = ensureDefenderParty(level, defender, war);
        if (attackers.isEmpty()) return;

        Vec3 defenderCenter = defender.center().getCenter();
        for (LivingEntity mob : attackers) {
            if (!mob.isAlive()) continue;
            double distance = mob.position().distanceTo(defenderCenter);
            if (distance > TARGET_RADIUS) {
                moveToward(mob, defenderCenter, movementSpeed(attacker, defender));
            } else {
                LivingEntity target = nearestAlive(defenders, mob);
                if (target != null) attack(mob, target, now);
            }
        }

        for (LivingEntity mob : defenders) {
            if (!mob.isAlive()) continue;
            LivingEntity target = nearestAlive(attackers, mob);
            if (target == null) continue;
            double distance = mob.position().distanceTo(target.position());
            if (distance > TARGET_RADIUS) {
                moveToward(mob, target.position(), 1.05D);
            } else {
                attack(mob, target, now);
            }
        }

        cleanupAttackCooldowns(now);
        wars.setDirty();
    }

    private static Set<LivingEntity> party(ServerLevel level,
                                           MillenaireIntegration.VillageSnapshot state,
                                           MilitaryWarSavedData.War war) {
        String tag = partyTag(war, true);
        Set<LivingEntity> result = new HashSet<>();

        for (LivingEntity mob : candidates(level, state.center().getX(), state.center().getY(),
                state.center().getZ(), PARTY_TRACK_RADIUS)) {
            // Once a villager has joined a war party, it is allowed to leave
            // the village territory. Keep tracking it until the war ends.
            if (mob.getTags().contains(tag)) result.add(mob);
        }

        int limit = partySize(state, level.getServer(), true);
        for (LivingEntity mob : candidates(level, state.center().getX(), state.center().getY(),
                state.center().getZ(), RALLY_RADIUS)) {
            if (result.size() >= limit) break;
            if (!mob.isAlive() || !isInVillage(mob, state) || !isMilitaryVillager(mob)) continue;
            if (mob.getTags().stream().anyMatch(existing -> existing.startsWith(PARTY_PREFIX))) continue;
            mob.addTag(tag);
            result.add(mob);
        }
        return result;
    }

    private static Set<LivingEntity> ensureDefenderParty(ServerLevel level,
                                                         MillenaireIntegration.VillageSnapshot state,
                                                         MilitaryWarSavedData.War war) {
        String tag = partyTag(war, false);
        Set<LivingEntity> result = new HashSet<>();

        for (LivingEntity mob : candidates(level, state.center().getX(), state.center().getY(),
                state.center().getZ(), PARTY_TRACK_RADIUS)) {
            if (mob.getTags().contains(tag)) result.add(mob);
        }

        int limit = partySize(state, level.getServer(), false);
        for (LivingEntity mob : candidates(level, state.center().getX(), state.center().getY(),
                state.center().getZ(), RALLY_RADIUS)) {
            if (result.size() >= limit) break;
            if (!mob.isAlive() || !isInVillage(mob, state) || !isMilitaryVillager(mob)) continue;
            if (mob.getTags().stream().anyMatch(existing -> existing.startsWith(PARTY_PREFIX))) continue;
            mob.addTag(tag);
            result.add(mob);
        }
        return result;
    }

    private static List<LivingEntity> candidates(ServerLevel level, double x, double y, double z, double radius) {
        AABB box = new AABB(x - radius, y - 24.0D, z - radius,
            x + radius, y + 24.0D, z + radius);
        return level.getEntitiesOfClass(LivingEntity.class, box,
            mob -> mob.isAlive() && isMillenaireLivingEntity(mob));
    }

    private static boolean isMillenaireLivingEntity(LivingEntity entity) {
        if (entity == null) return false;
        String className = entity.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        if (className.startsWith("org.millenaire.")) return true;
        try {
            var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            return key != null && "millenaire".equals(key.getNamespace());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isInVillage(LivingEntity entity,
                                       MillenaireIntegration.VillageSnapshot state) {
        return state.territory().contains(new ChunkPos(entity.blockPosition()));
    }

    private static boolean isMilitaryVillager(LivingEntity entity) {
        if (!isMillenaireLivingEntity(entity)) return false;
        Object typeId = invoke(entity, "getVillagerTypeId");
        if (!(typeId instanceof net.minecraft.resources.ResourceLocation id)) return false;
        try {
            Class<?> cultures = Class.forName("org.millenaire.culture.ModCultures");
            java.lang.reflect.Method getType = cultures.getMethod("getVillagerType",
                net.minecraft.resources.ResourceLocation.class);
            Object type = getType.invoke(null, id);
            return type != null && (booleanResult(type, "hasTag", "isRaider")
                || booleanResult(type, "hasTag", "helpInAttacks"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean booleanResult(Object target, String methodName, Object argument) {
        if (target == null) return false;
        try {
            for (java.lang.reflect.Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 1) continue;
                Object value = method.invoke(target, argument);
                return value instanceof Boolean && (Boolean) value;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static int partySize(MillenaireIntegration.VillageSnapshot state,
                                 MinecraftServer server, boolean attacker) {
        int militaryWorkers = CountryWorkforceService.sectorWorkers(server, state.stateKey(), WorkforceSector.MILITARY);
        int size = Math.max(2, militaryWorkers * 2 + 2);
        if (!attacker) size += 2;
        return Math.min(MAX_PARTY, size);
    }

    private static double movementSpeed(MillenaireIntegration.VillageSnapshot attacker,
                                        MillenaireIntegration.VillageSnapshot defender) {
        return 1.10D;
    }

    /**
     * Millénaire's own AI can overwrite setDeltaMovement. Re-issuing a
     * navigation goal after its AI has ticked is much more reliable than
     * calling navigation.stop() and manually pushing the entity.
     */
    private static void moveToward(LivingEntity entity, Vec3 target, double speed) {
        if (entity instanceof Mob mob) {
            mob.getLookControl().setLookAt(target.x, target.y, target.z);
            mob.getNavigation().moveTo(target.x, target.y, target.z, speed);
            return;
        }

        Vec3 delta = target.subtract(entity.position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 0.25D) return;
        double step = Math.min(0.28D, Math.max(0.10D, speed * 0.12D));
        entity.setDeltaMovement(delta.x / horizontal * step, entity.getDeltaMovement().y,
            delta.z / horizontal * step);
        entity.hasImpulse = true;
    }

    private static LivingEntity nearestAlive(Set<LivingEntity> entities, LivingEntity from) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : entities) {
            if (entity == null || !entity.isAlive()) continue;
            double distance = from.distanceToSqr(entity);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    private static void attack(LivingEntity attacker, LivingEntity target, long now) {
        if (target == null || !target.isAlive()) return;
        long next = NEXT_ATTACK_TICK.getOrDefault(attacker.getUUID(), 0L);
        if (now < next) return;
        double damage = 2.5D;
        boolean hit = target.hurt(attacker.level().damageSources().mobAttack(attacker), (float) damage);
        if (!hit) return;
        NEXT_ATTACK_TICK.put(attacker.getUUID(), now + ATTACK_COOLDOWN);
        MilitaryEconomyService.recordMillCombatHit(target, damage, !target.isAlive());
    }

    private static void cleanupAttackCooldowns(long now) {
        if (NEXT_ATTACK_TICK.size() < 1024) return;
        NEXT_ATTACK_TICK.entrySet().removeIf(entry -> entry.getValue() + 200L < now);
    }

    private static String partyTag(MilitaryWarSavedData.War war, boolean attacker) {
        int hash = java.util.Objects.hash(war.attacker(), war.defender(), attacker);
        return PARTY_PREFIX + Integer.toHexString(hash);
    }

    private static Object invoke(Object target, String name, Object... args) {
        if (target == null) return null;
        try {
            for (java.lang.reflect.Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                Class<?>[] params = method.getParameterTypes();
                boolean compatible = true;
                for (int i = 0; i < params.length; i++) {
                    if (args[i] == null) continue;
                    Class<?> type = params[i].isPrimitive() ? wrap(params[i]) : params[i];
                    if (!type.isInstance(args[i])) { compatible = false; break; }
                }
                if (compatible) return method.invoke(target, args);
            }
        } catch (Throwable ignored) {}
        return null;
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
