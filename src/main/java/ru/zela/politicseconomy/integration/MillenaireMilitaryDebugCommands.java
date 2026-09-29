package ru.zela.politicseconomy.integration;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

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
            if (state.villageId().equalsIgnoreCase(id)
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
