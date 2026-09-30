package ru.zela.politicseconomy.integration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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

/**
 * First real ground-combat layer.
 *
 * <p>Wars use live Millénaire mobs already present in the world. We do not
 * spawn a second hidden army: selected villagers form a temporary war party,
 * march toward the defender, and exchange real LivingEntity damage.</p>
 */
public final class GroundWarService {
    private static final long TICK_INTERVAL = 10L;
    private static final long ATTACK_COOLDOWN = 20L;
    private static final double RALLY_RADIUS = 56.0D;
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

    private static void processWar(
        MinecraftServer server,
        MilitaryWarSavedData wars,
        MilitaryWarSavedData.War war,
        long now
    ) {
        MillenaireIntegration.VillageSnapshot attacker =
            MillenaireIntegration.snapshotForStateKey(server, war.attacker());
        MillenaireIntegration.VillageSnapshot defender =
            MillenaireIntegration.snapshotForStateKey(server, war.defender());

        if (attacker == null || defender == null) {
            // PoliticsMod-country combat is intentionally left for the next
            // layer; Millénaire-vs-Millénaire is the first live implementation.
            return;
        }

        ServerLevel level = server.overworld();
        Set<LivingEntity> attackers = party(level, attacker, war);
        Set<LivingEntity> defenders = ensureDefenderParty(level, defender, war);

        if (attackers.isEmpty()) return;

        for (LivingEntity mob : attackers) {
            if (!mob.isAlive()) continue;

            double distance = mob.position().distanceTo(
                defender.center().getCenter()
            );
            if (distance > TARGET_RADIUS) {
                moveToward(
                    mob,
                    defender.center().getCenter(),
                    movementSpeed(attacker, defender)
                );
            } else {
                LivingEntity target = nearestAlive(defenders, mob);
                if (target != null) attack(server, mob, target, now);
            }
        }

        // Defenders counterattack once the assault is close enough.
        for (LivingEntity mob : defenders) {
            if (!mob.isAlive()) continue;
            LivingEntity target = nearestAlive(attackers, mob);
            if (target == null) continue;

            double distance = mob.position().distanceTo(target.position());
            if (distance > TARGET_RADIUS) {
                moveToward(
                    mob,
                    target.position(),
                    1.05D
                );
            } else {
                attack(server, mob, target, now);
            }
        }

        cleanupAttackCooldowns(now);
        wars.setDirty();
    }

    private static Set<LivingEntity> party(
        ServerLevel level,
        MillenaireIntegration.VillageSnapshot state,
        MilitaryWarSavedData.War war
    ) {
        String tag = partyTag(war, true);
        Set<LivingEntity> result = new HashSet<>();

        for (LivingEntity mob : candidates(level, state.center().getX(), state.center().getY(), state.center().getZ(), RALLY_RADIUS)) {
            if (!isMillenaireLivingEntity(mob)) continue;
            if (!isInVillage(mob, state)) continue;
            if (mob.getTags().stream().anyMatch(existing -> existing.startsWith(PARTY_PREFIX) && !existing.equals(tag))) {
                continue;
            }

            if (mob.getTags().contains(tag)) {
                result.add(mob);
            }
        }

        int limit = partySize(state, level.getServer(), true);
        if (result.size() < limit) {
            for (Mob mob : candidates(
                level,
                state.center().getX(),
                state.center().getY(),
                state.center().getZ(),
                RALLY_RADIUS
            )) {
                if (result.size() >= limit) break;
                if (!isMillenaireLivingEntity(mob) || !mob.isAlive()) continue;
                if (!isInVillage(mob, state)) continue;
                if (mob.getTags().stream().anyMatch(existing -> existing.startsWith(PARTY_PREFIX))) continue;

                mob.addTag(tag);
                result.add(mob);
            }
        }

        return result;
    }

    private static Set<LivingEntity> ensureDefenderParty(
        ServerLevel level,
        MillenaireIntegration.VillageSnapshot state,
        MilitaryWarSavedData.War war
    ) {
        String tag = partyTag(war, false);
        Set<Mob> result = new HashSet<>();

        for (Mob mob : candidates(
            level,
            state.center().getX(),
            state.center().getY(),
            state.center().getZ(),
            RALLY_RADIUS
        )) {
            if (!isMillenaireMob(mob) || !isInVillage(mob, state)) continue;
            if (mob.getTags().contains(tag)) result.add(mob);
        }

        int limit = partySize(state, level.getServer(), false);
        if (result.size() < limit) {
            for (Mob mob : candidates(
                level,
                state.center().getX(),
                state.center().getY(),
                state.center().getZ(),
                RALLY_RADIUS
            )) {
                if (result.size() >= limit) break;
                if (!isMillenaireMob(mob) || !mob.isAlive()) continue;
                if (!isInVillage(mob, state)) continue;
                if (mob.getTags().stream().anyMatch(existing -> existing.startsWith(PARTY_PREFIX))) continue;

                mob.addTag(tag);
                result.add(mob);
            }
        }

        return result;
    }

    private static List<LivingEntity> candidates(
        ServerLevel level,
        double x,
        double y,
        double z,
        double radius
    ) {
        AABB box = new AABB(
            x - radius, y - 24.0D, z - radius,
            x + radius, y + 24.0D, z + radius
        );
        return level.getEntitiesOfClass(
            LivingEntity.class,
            box,
            mob -> mob.isAlive() && isMillenaireLivingEntity(mob)
        );
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

    private static boolean isInVillage(
        Mob mob,
        MillenaireIntegration.VillageSnapshot state
    ) {
        return state.territory().contains(new ChunkPos(entity.blockPosition()));
    }

    private static int partySize(
        MillenaireIntegration.VillageSnapshot state,
        MinecraftServer server,
        boolean attacker
    ) {
        int militaryWorkers = CountryWorkforceService.sectorWorkers(
            server,
            state.stateKey(),
            WorkforceSector.MILITARY
        );

        int size = Math.max(2, militaryWorkers * 2 + 2);
        if (!attacker) size += 2;
        return Math.min(MAX_PARTY, size);
    }

    private static double movementSpeed(
        MillenaireIntegration.VillageSnapshot attacker,
        MillenaireIntegration.VillageSnapshot defender
    ) {
        return 1.10D;
    }

    private static void moveToward(
        LivingEntity entity,
        Vec3 target,
        double speed
    ) {
        Vec3 delta = target.subtract(entity.position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 0.25D) return;

        double step = Math.min(0.34D, Math.max(0.10D, speed * 0.14D));
        double vx = delta.x / horizontal * step;
        double vz = delta.z / horizontal * step;

        double vy = entity.getDeltaMovement().y;
        if (Math.abs(delta.y) > 2.0D) {
            vy += Math.max(-0.08D, Math.min(0.08D, delta.y * 0.01D));
        }

        entity.setDeltaMovement(vx, vy, vz);
        entity.hasImpulse = true;

        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(target.x, target.y, target.z);
        }
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

    private static void attack(
        MinecraftServer server,
        LivingEntity attacker,
        LivingEntity target,
        long now
    ) {
        if (target == null || !target.isAlive()) return;

        long next = NEXT_ATTACK_TICK.getOrDefault(attacker.getUUID(), 0L);
        if (now < next) return;

        double damage = 2.5D;
        boolean hit = target.hurt(
            attacker.level().damageSources().mobAttack(attacker),
            (float) damage
        );
        if (!hit) return;

        NEXT_ATTACK_TICK.put(
            attacker.getUUID(),
            now + ATTACK_COOLDOWN
        );

        boolean casualty = !target.isAlive();
        MilitaryEconomyService.recordMillCombatHit(
            target,
            damage,
            casualty
        );
    }

    private static void cleanupAttackCooldowns(long now) {
        if (NEXT_ATTACK_TICK.size() < 1024) return;
        NEXT_ATTACK_TICK.entrySet().removeIf(entry -> entry.getValue() + 200L < now);
    }

    private static String partyTag(
        MilitaryWarSavedData.War war,
        boolean attacker
    ) {
        int hash = java.util.Objects.hash(
            war.attacker(),
            war.defender(),
            attacker
        );
        return PARTY_PREFIX + Integer.toHexString(hash);
    }
}
