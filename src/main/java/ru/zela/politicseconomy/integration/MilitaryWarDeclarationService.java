package ru.zela.politicseconomy.integration;

import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import ru.zela.politicseconomy.economyui.CountryDirectoryService;
import ru.zela.politicseconomy.event.NewsService;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Player-facing declaration of war. War creation is intentionally centralized here. */
public final class MilitaryWarDeclarationService {
    private static final long WAR_DECLARATION_READINESS_COST = 7L;
    private static final long MILLENAIRE_WAR_COST = 150L;
    private static final double MIN_READINESS = 35.0D;

    private MilitaryWarDeclarationService() {}

    public static Result declare(
        ServerPlayer player,
        String targetType,
        String targetName
    ) {
        if (player == null || player.getServer() == null) {
            return fail("Игрок не найден.");
        }

        MinecraftServer server = player.getServer();
        String attacker = CountryContext.playerStateName(player);
        if (attacker == null || attacker.isBlank()) {
            return fail("Ты не состоишь ни в одном государстве.");
        }

        if (!canDeclare(player, attacker)) {
            return fail("Объявлять войну может только лидер страны.");
        }

        String defender = CountryDirectoryService.resolveStateKey(
            server, targetType, targetName
        );
        if (defender == null || defender.isBlank()) {
            return fail("Выбранное государство не найдено.");
        }

        if (attacker.equals(defender)) {
            return fail("Нельзя объявить войну самому себе.");
        }

        MilitaryWarSavedData wars = MilitaryWarSavedData.get(server);
        if (wars.isAtWar(attacker, defender)) {
            return fail("Эти государства уже находятся в состоянии войны.");
        }

        if (MilitaryEconomyService.readiness(server, attacker) < MIN_READINESS) {
            return fail("Недостаточная военная готовность. Нужно не менее "
                + (int) MIN_READINESS + "%.");
        }

        if (ru.zela.politicseconomy.economy.NationalMaterialConsumptionService
            .getLedger(server).hasAnyDebt(attacker)) {
            return fail("Нельзя начать войну при непогашенном материальном долге.");
        }

        if (MillenaireIntegration.isStateKey(attacker)) {
            var snapshot = MillenaireIntegration.snapshotForStateKey(server, attacker);
            if (snapshot == null) return fail("Государство Millénaire не найдено.");

            long treasury = MillenaireStateSavedData.get(server)
                .treasury(snapshot.villageId());

            if (treasury < MILLENAIRE_WAR_COST) {
                return fail("В казне недостаточно средств. Нужно $" + MILLENAIRE_WAR_COST + ".");
            }

            MillenaireStateSavedData.get(server)
                .addTreasury(snapshot.villageId(), -MILLENAIRE_WAR_COST);
        }

        long now = server.overworld().getGameTime();
        wars.startWar(
            attacker,
            defender,
            MilitaryWarSavedData.WarType.GROUND,
            MilitaryWarSavedData.WarCause.STRATEGIC_OPPORTUNITY,
            representativeChunk(server, defender, player.chunkPosition()),
            now
        );
        wars.setNextDecisionTick(
            attacker,
            now + 1200L
        );

        MilitaryReadinessSavedData.get(server)
            .reduceReadiness(attacker, WAR_DECLARATION_READINESS_COST);

        MilitaryDiplomacyBridge.setWar(server, attacker, defender);

        String attackerName = MillenaireIntegration.displayName(server, attacker);
        String defenderDisplayName = MillenaireIntegration.displayName(server, defender);

        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            String state = CountryContext.playerStateName(online);
            if (attacker.equals(state) || defender.equals(state)) {
                online.sendSystemMessage(
                    Component.literal(
                        "§c" + attackerName + " §fобъявляет войну §c"
                            + defenderDisplayName + "§f."
                    )
                );
            }
        }

        NewsService.add(
            server,
            now,
            "ВОЙНА",
            attackerName + " объявляет войну " + defenderDisplayName,
            "Война началась. Войсковое снабжение и военная готовность будут снижаться по мере боевых раундов."
        );

        return new Result(
            true,
            "Война объявлена: " + defenderDisplayName
        );
    }

    private static boolean canDeclare(ServerPlayer player, String attacker) {
        if (player.isCreative() && player.hasPermissions(2)) return true;

        if (MillenaireIntegration.isStateKey(attacker)) {
            return true;
        }

        Country country = CountryContext.playerCountry(player);
        return country != null
            && CountryRole.LEADER.equals(
                ru.zela.politicseconomy.integration.PoliticsModIntegration
                    .role(player, country)
            );
    }

    private static ChunkPos representativeChunk(
        MinecraftServer server,
        String stateKey,
        ChunkPos fallback
    ) {
        if (MillenaireIntegration.isStateKey(stateKey)) {
            var snapshot = MillenaireIntegration.snapshotForStateKey(server, stateKey);
            if (snapshot != null) return new ChunkPos(snapshot.center());
        }

        var manager = net.krona.politicsmod.PoliticsManager.get(server.overworld());
        if (manager != null) {
            AtomicReference<ChunkPos> found = new AtomicReference<>();
            manager.forEachClaim((pos, color) -> {
                if (found.get() != null) return;
                String owner = manager.getCountryNameAt(pos);
                if (stateKey.equals(owner)) found.set(pos);
            });
            if (found.get() != null) return found.get();
        }

        return fallback == null ? new ChunkPos(0, 0) : fallback;
    }

    private static Result fail(String message) {
        return new Result(false, message);
    }

    public record Result(boolean success, String message) {}
}
