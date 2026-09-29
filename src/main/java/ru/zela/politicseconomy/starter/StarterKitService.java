package ru.zela.politicseconomy.starter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.function.Supplier;

/**
 * Gives every player a one-time survival starter kit.
 *
 * <p>The marker is stored in the player's persistent NBT, so the kit survives
 * reconnects and server restarts without creating a repeatable source of items.</p>
 */
public final class StarterKitService {
    private static final String STARTER_KIT_TAG = "politicseconomy_starter_kit_211";

    private StarterKitService() {}

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.getPersistentData().getBoolean(STARTER_KIT_TAG)) {
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

        player.getPersistentData().putBoolean(STARTER_KIT_TAG, true);
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
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("politicsmod", path);
        var item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            return;
        }
        give(player, () -> new ItemStack(item, amount));
    }
}
