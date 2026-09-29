package ru.zela.politicseconomy.starter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Gives a player the Survival starter kit exactly once per world.
 * The receipt marker lives in SavedData rather than the player entity NBT.
 */
public final class StarterKitService {
    private static final String LEGACY_STARTER_KIT_TAG = "politicseconomy_starter_kit_211";

    private StarterKitService() {}

    private static StarterKitSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                StarterKitSavedData::create,
                StarterKitSavedData::load,
                null
            ),
            StarterKitSavedData.DATA_NAME
        );
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        StarterKitSavedData data = get(server);
        UUID playerId = player.getUUID();

        // Migrate the old persistent marker if a previous build already gave this player the kit.
        if (player.getPersistentData().getBoolean(LEGACY_STARTER_KIT_TAG)) {
            data.markReceived(playerId);
            return;
        }

        if (data.hasReceived(playerId)) {
            return;
        }

        give(player, () -> new ItemStack(Items.STONE_SWORD));
        give(player, () -> new ItemStack(Items.STONE_PICKAXE));
        give(player, () -> new ItemStack(Items.STONE_AXE));
        give(player, () -> new ItemStack(Items.STONE_SHOVEL));
        give(player, () -> new ItemStack(Items.STONE_HOE));

        give(player, () -> new ItemStack(Items.BREAD, 16));

        give(player, () -> new ItemStack(Items.LEATHER_HELMET));
        give(player, () -> new ItemStack(Items.LEATHER_CHESTPLATE));
        give(player, () -> new ItemStack(Items.LEATHER_LEGGINGS));
        give(player, () -> new ItemStack(Items.LEATHER_BOOTS));

        givePoliticsItem(player, "tax_block", 1);
        givePoliticsItem(player, "city_stone", 1);
        givePoliticsItem(player, "founding_stone", 1);

        data.markReceived(playerId);

        // Keep the old marker too, so older/newer builds remain compatible.
        player.getPersistentData().putBoolean(LEGACY_STARTER_KIT_TAG, true);

        player.sendSystemMessage(Component.literal(
            "Politics Economy: стартовый набор выдан — каменные инструменты, 16 хлеба, кожаная броня, "
                + "Налоговый блок, Городской камень и Камень основания."
        ));
    }

    private static void give(ServerPlayer player, Supplier<ItemStack> stackSupplier) {
        ItemStack stack = stackSupplier.get();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void givePoliticsItem(ServerPlayer player, String path, int amount) {
        ResourceLocation id = ResourceLocation.parse("politicsmod:" + path);
        var item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            return;
        }
        give(player, () -> new ItemStack(item, amount));
    }
}
