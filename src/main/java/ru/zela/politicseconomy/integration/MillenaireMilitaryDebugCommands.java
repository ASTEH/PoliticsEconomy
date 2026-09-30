package ru.zela.politicseconomy.integration;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import ru.zela.politicseconomy.country.CountryWorkforceService;
import ru.zela.politicseconomy.country.WorkforceSector;
import ru.zela.politicseconomy.economy.NationalMaterialConsumptionService;

import java.util.List;

/** Debug tools for testing Millénaire military AI without moving villages. */
public final class MillenaireMilitaryDebugCommands {
    private MillenaireMilitaryDebugCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("pe")
                .then(Commands.literal("millenaire")
                    .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("id")
                            .executes(context -> showId(context.getSource())))
                        .then(Commands.literal("neighbors")
                            .executes(context -> showNeighbors(context.getSource())))
                        .then(Commands.literal("status")
                            .executes(context -> showStatus(context.getSource())))
                        .then(Commands.literal("army")
                            .executes(context -> showArmy(context.getSource())))
                        .then(Commands.literal("combatapi")
                            .executes(context -> showCombatApi(context.getSource())))
                        .then(Commands.literal("readiness")
                            .executes(context -> showReadiness(context.getSource()))
                            .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 100.0D))
                                .executes(context -> setReadiness(
                                    context.getSource(),
                                    DoubleArgumentType.getDouble(context, "value")
                                ))))
                        .then(Commands.literal("war")
                            .then(Commands.argument("target", StringArgumentType.word())
                                .executes(context -> forceWar(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "target")
                                ))))
                        .then(Commands.literal("wars")
                            .executes(context -> showWars(context.getSource())))
                        .then(Commands.literal("endwar")
                            .then(Commands.argument("target", StringArgumentType.word())
                                .executes(context -> endWar(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "target")
                                ))))
                        .then(Commands.literal("link")
                            .then(Commands.argument("target", StringArgumentType.word())
                                .executes(context -> linkNeighbor(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "target")
                                ))))
                        .then(Commands.literal("clear")
                            .executes(context -> clearLinks(context.getSource())))
                    )
                )
        );
    }

    private static int showId(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            MillenaireIntegration.VillageSnapshot current = currentVillage(player);
            if (current == null) {
                source.sendFailure(Component.literal(
                    "Ты должен находиться на территории поселения Millénaire."
                ));
                return 0;
            }

            String id = current.villageId().toString();
            Component copy = Component.literal("§e[СКОПИРОВАТЬ ID]")
                .withStyle(style -> style.withClickEvent(
                    new net.minecraft.network.chat.ClickEvent(
                        net.minecraft.network.chat.ClickEvent.Action.COPY_TO_CLIPBOARD,
                        id
                    )
                ));
            source.sendSuccess(() -> Component.literal(
                "§6Поселение: §f" + current.name()
            ), false);
            source.sendSuccess(() -> Component.literal(
                "§7Village ID: §f" + id + " §8→ "
            ).append(copy), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Команда доступна только игроку."));
            return 0;
        }
    }

    private static int showNeighbors(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            MillenaireIntegration.VillageSnapshot current = currentVillage(player);
            if (current == null) {
                source.sendFailure(Component.literal(
                    "Ты должен находиться на территории поселения Millénaire."
                ));
                return 0;
            }

            source.sendSuccess(() -> Component.literal(
                "§6Millénaire debug: §f" + current.name()
                    + " §7(" + current.villageId() + ")"
            ), false);

            List<MillenaireIntegration.VillageSnapshot> states =
                MillenaireIntegration.snapshots(player.server);

            for (MillenaireIntegration.VillageSnapshot state : states) {
                if (state.villageId().equals(current.villageId())) continue;

                int distance = chunkDistance(current, state);
                boolean realNeighbour = distance <= 1;
                boolean debugNeighbour = MilitaryAiService.isDebugNeighbor(
                    current.stateKey(), state.stateKey()
                );

                String marker = realNeighbour ? "§a✓" : debugNeighbour ? "§e◆" : "§7•";
                String suffix = realNeighbour
                    ? "§a реальный сосед"
                    : debugNeighbour
                        ? "§e debug-сосед"
                        : "§7 " + distance + " чанков";

                source.sendSuccess(() -> Component.literal(
                    marker + " §f" + state.name()
                        + " §8[" + state.villageId() + "]§r"
                        + suffix
                ), false);
            }

            source.sendSuccess(() -> Component.literal(
                "§7Чтобы временно связать поселение с другим для AI: "
                    + "§f/pe millenaire debug link <villageId>"
            ), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Команда доступна только игроку."));
            return 0;
        }
    }

    private static int linkNeighbor(CommandSourceStack source, String targetId) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            MillenaireIntegration.VillageSnapshot current = currentVillage(player);
            if (current == null) {
                source.sendFailure(Component.literal(
                    "Ты должен находиться на территории поселения Millénaire."
                ));
                return 0;
            }

            MillenaireIntegration.VillageSnapshot target =
                findVillage(player, targetId);
            if (target == null) {
                source.sendFailure(Component.literal(
                    "Поселение с villageId '" + targetId + "' не найдено."
                ));
                return 0;
            }
            if (target.villageId().equals(current.villageId())) {
                source.sendFailure(Component.literal(
                    "Нельзя связать поселение само с собой."
                ));
                return 0;
            }

            MilitaryAiService.addDebugNeighbor(
                current.stateKey(),
                target.stateKey()
            );

            source.sendSuccess(() -> Component.literal(
                "§eDebug-соседство включено: §f"
                    + current.name() + " §7↔ §f" + target.name()
                    + "§7."
            ), true);
            source.sendSuccess(() -> Component.literal(
                "§7Теперь военный AI может рассматривать "
                    + "" + target.name() + " как соседнюю цель."
            ), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Не удалось создать debug-связь."));
            return 0;
        }
    }

    private static int clearLinks(CommandSourceStack source) {
        MilitaryAiService.clearDebugNeighbors();
        source.sendSuccess(() -> Component.literal(
            "§aВсе временные debug-соседства Millénaire очищены."
        ), true);
        return 1;
    }


    private static int showStatus(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            MillenaireIntegration.VillageSnapshot current = currentVillage(player);
            if (current == null) {
                source.sendFailure(Component.literal(
                    "Ты должен находиться на территории поселения Millénaire."
                ));
                return 0;
            }

            MinecraftServer server = player.server;
            int militaryWorkers = CountryWorkforceService.sectorWorkers(
                server,
                current.stateKey(),
                WorkforceSector.MILITARY
            );
            double readiness = MilitaryEconomyService.readiness(
                server,
                current.stateKey()
            );
            double supply = MilitaryEconomyService.supplyPercent(
                server,
                current.stateKey()
            );
            long treasury = MillenaireStateSavedData.get(server)
                .treasury(current.villageId());
            boolean debt = NationalMaterialConsumptionService.getLedger(server)
                .hasAnyDebt(current.stateKey());

            source.sendSuccess(() -> Component.literal("§6=== Военный статус ==="), false);
            source.sendSuccess(() -> Component.literal(
                "§7Поселение: §f" + current.name()), false);
            source.sendSuccess(() -> Component.literal(
                "§7Население: §f" + current.population()
                    + " §8(взрослых: " + current.adults()
                    + ", детей: " + current.children() + ")"), false);
            source.sendSuccess(() -> Component.literal(
                "§7Военные рабочие: §f" + militaryWorkers), false);
            source.sendSuccess(() -> Component.literal(
                "§7Военная готовность: §f"
                    + String.format(java.util.Locale.ROOT, "%.1f", readiness)
                    + "/100"), false);
            source.sendSuccess(() -> Component.literal(
                "§7Снабжение: §f"
                    + String.format(java.util.Locale.ROOT, "%.1f", supply)
                    + "%"), false);
            source.sendSuccess(() -> Component.literal(
                "§7Казна: §f" + treasury), false);
            source.sendSuccess(() -> Component.literal(
                "§7Материальный долг: "
                    + (debt ? "§cесть" : "§aнет")), false);
            source.sendSuccess(() -> Component.literal(
                "§8Debug-война игнорирует эти ограничения."), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Команда доступна только игроку."));
            return 0;
        }
    }

    private static int showArmy(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            MillenaireIntegration.VillageSnapshot current = currentVillage(player);
            if (current == null) {
                source.sendFailure(Component.literal(
                    "Ты должен находиться на территории поселения Millénaire."
                ));
                return 0;
            }

            MillenaireCombatBridge.ArmyReport report =
                MillenaireCombatBridge.armyReport(player.server, current);

            source.sendSuccess(() -> Component.literal(
                "§6=== Армия Millénaire ==="
            ), false);
            source.sendSuccess(() -> Component.literal(
                "§7Поселение: §f" + current.name()
            ), false);
            source.sendSuccess(() -> Component.literal(
                "§7Военные рабочие экономики: §f" + report.roleRecords()
            ), false);
            source.sendSuccess(() -> Component.literal(
                "§7Живые военные NPC: §f" + report.liveMilitary()
            ), false);
            source.sendSuccess(() -> Component.literal(
                "§7Живые взрослые NPC: §f" + report.liveAdults()
            ), false);
            source.sendSuccess(() -> Component.literal(
                "§7Всего найденных Millénaire NPC: §f" + report.liveEntities()
            ), false);
            source.sendSuccess(() -> Component.literal(
                "§8Сначала используются реальные бойцы; мобилизация взрослых "
                    + "включается только при нехватке бойцов."
            ), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal(
                "Не удалось получить состав армии."
            ));
            return 0;
        }
    }

    private static int showCombatApi(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            MillenaireIntegration.VillageSnapshot current = currentVillage(player);
            if (current == null) {
                source.sendFailure(Component.literal(
                    "Ты должен находиться на территории поселения Millénaire."
                ));
                return 0;
            }

            List<String> methods = MillenaireCombatBridge.discoverCombatApi(
                player.server,
                current.stateKey()
            );

            source.sendSuccess(() -> Component.literal(
                "§6=== Millénaire combat API ==="
            ), false);

            if (methods.isEmpty()) {
                source.sendFailure(Component.literal(
                    "В публичных методах Village не найдено raid/relation/combat API."
                ));
                return 0;
            }

            for (String method : methods) {
                source.sendSuccess(
                    () -> Component.literal("§7" + method),
                    false
                );
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal(
                "Не удалось прочитать combat API."
            ));
            return 0;
        }
    }

    private static int showReadiness(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            MillenaireIntegration.VillageSnapshot current = currentVillage(player);
            if (current == null) {
                source.sendFailure(Component.literal(
                    "Ты должен находиться на территории поселения Millénaire."
                ));
                return 0;
            }

            double readiness = MilitaryEconomyService.readiness(
                player.server,
                current.stateKey()
            );
            source.sendSuccess(() -> Component.literal(
                "§6Военная готовность §f"
                    + String.format(java.util.Locale.ROOT, "%.1f", readiness)
                    + "/100"
            ), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Команда доступна только игроку."));
            return 0;
        }
    }

    private static int setReadiness(CommandSourceStack source, double value) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            MillenaireIntegration.VillageSnapshot current = currentVillage(player);
            if (current == null) {
                source.sendFailure(Component.literal(
                    "Ты должен находиться на территории поселения Millénaire."
                ));
                return 0;
            }

            MilitaryReadinessSavedData.get(player.server)
                .setReadiness(current.stateKey(), value);
            source.sendSuccess(() -> Component.literal(
                "§aВоенная готовность §f"
                    + String.format(java.util.Locale.ROOT, "%.1f", value)
                    + "/100 §aустановлена для §f" + current.name() + "§a."
            ), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Не удалось изменить военную готовность."));
            return 0;
        }
    }

    private static int forceWar(CommandSourceStack source, String targetId) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            MillenaireIntegration.VillageSnapshot attacker = currentVillage(player);
            if (attacker == null) {
                source.sendFailure(Component.literal(
                    "Ты должен находиться на территории поселения Millénaire."
                ));
                return 0;
            }

            MillenaireIntegration.VillageSnapshot defender =
                findVillage(player, targetId);
            if (defender == null) {
                source.sendFailure(Component.literal(
                    "Поселение с villageId '" + targetId + "' не найдено."
                ));
                return 0;
            }

            if (MilitaryWarSavedData.get(player.server)
                .isAtWar(attacker.stateKey(), defender.stateKey())) {
                source.sendFailure(Component.literal(
                    "Эти поселения уже находятся в состоянии войны."
                ));
                return 0;
            }

            if (!MilitaryAiService.debugForceStartWar(
                player.server,
                attacker,
                defender
            )) {
                source.sendFailure(Component.literal(
                    "Не удалось начать debug-войну."
                ));
                return 0;
            }

            source.sendSuccess(() -> Component.literal(
                "§cDebug-война начата: §f"
                    + attacker.name() + " §c→ §f" + defender.name()
            ), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Не удалось начать debug-войну."));
            return 0;
        }
    }

    private static int showWars(CommandSourceStack source) {
        try {
            List<MilitaryWarSavedData.War> wars =
                MilitaryWarSavedData.get(source.getServer()).wars();
            if (wars.isEmpty()) {
                source.sendSuccess(() -> Component.literal(
                    "§7Активных войн нет."), false);
                return 1;
            }

            source.sendSuccess(() -> Component.literal(
                "§6=== Активные войны ==="), false);
            for (MilitaryWarSavedData.War war : wars) {
                String attacker = MillenaireIntegration.displayName(
                    source.getServer(), war.attacker());
                String defender = MillenaireIntegration.displayName(
                    source.getServer(), war.defender());
                source.sendSuccess(() -> Component.literal(
                    "§c" + attacker + " §f→ §c" + defender
                        + " §7(" + war.type().name()
                        + ", " + war.cause().name() + ")"
                ), false);
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Не удалось прочитать список войн."));
            return 0;
        }
    }

    private static int endWar(CommandSourceStack source, String targetId) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            MillenaireIntegration.VillageSnapshot current = currentVillage(player);
            if (current == null) {
                source.sendFailure(Component.literal(
                    "Ты должен находиться на территории поселения Millénaire."
                ));
                return 0;
            }

            MillenaireIntegration.VillageSnapshot target =
                findVillage(player, targetId);
            if (target == null) {
                source.sendFailure(Component.literal(
                    "Поселение с villageId '" + targetId + "' не найдено."
                ));
                return 0;
            }

            boolean ended = MilitaryWarSavedData.get(player.server)
                .endWar(current.stateKey(), target.stateKey());
            if (!ended) {
                source.sendFailure(Component.literal(
                    "Война между этими поселениями не найдена."
                ));
                return 0;
            }

            MilitaryDiplomacyBridge.setPeace(
                player.server,
                current.stateKey(),
                target.stateKey()
            );

            source.sendSuccess(() -> Component.literal(
                "§aDebug-война завершена: §f"
                    + current.name() + " §7↔ §f" + target.name()
            ), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Не удалось завершить войну."));
            return 0;
        }
    }

    private static MillenaireIntegration.VillageSnapshot currentVillage(ServerPlayer player) {
        ChunkPos currentChunk = new ChunkPos(player.blockPosition());
        for (MillenaireIntegration.VillageSnapshot state :
            MillenaireIntegration.snapshots(player.server)) {
            if (state.territory().contains(currentChunk)) return state;
        }
        return null;
    }

    private static MillenaireIntegration.VillageSnapshot findVillage(
        ServerPlayer player,
        String id
    ) {
        for (MillenaireIntegration.VillageSnapshot state :
            MillenaireIntegration.snapshots(player.server)) {
            if (state.villageId().toString().equalsIgnoreCase(id)
                || state.stateKey().equalsIgnoreCase(id)
                || state.name().equalsIgnoreCase(id)) {
                return state;
            }
        }
        return null;
    }

    private static int chunkDistance(
        MillenaireIntegration.VillageSnapshot a,
        MillenaireIntegration.VillageSnapshot b
    ) {
        int best = Integer.MAX_VALUE;
        for (ChunkPos ca : a.territory()) {
            for (ChunkPos cb : b.territory()) {
                int distance = Math.max(
                    Math.abs(ca.x - cb.x),
                    Math.abs(ca.z - cb.z)
                );
                if (distance < best) best = distance;
                if (best == 1) return 1;
            }
        }
        return best == Integer.MAX_VALUE ? 999999 : best;
    }
}
