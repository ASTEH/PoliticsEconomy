package ru.zela.politicseconomy.research;

import net.krona.politicsmod.politics.Country;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import ru.zela.politicseconomy.integration.PoliticsModIntegration;

import java.util.List;

public final class CountryResearchContentService {
    private CountryResearchContentService() {}

    public static boolean unlocked(MinecraftServer server, String countryName, ResourceLocation contentId) {
        CountryResearch technology = requiredTechnology(contentId);
        return technology == null
            || CountryResearchService.completed(server, countryName).contains(technology.id());
    }

    public static CountryResearch requiredTechnology(ResourceLocation contentId) {
        if (contentId == null) return null;
        for (CountryResearch technology : CountryResearch.values()) {
            for (String rule : technology.contentRules()) {
                if (matches(rule, contentId)) {
                    return technology;
                }
            }
        }
        return null;
    }

    public static List<String> contentFor(CountryResearch technology) {
        return technology.contentRules();
    }

    private static boolean matches(String rule, ResourceLocation id) {
        int separator = rule.indexOf(':');
        if (separator <= 0 || separator >= rule.length() - 1) return false;

        String namespace = rule.substring(0, separator);
        String path = rule.substring(separator + 1);

        if (!namespace.equals(id.getNamespace())) return false;
        if ("*".equals(path)) return true;

        if (path.endsWith("*")) {
            return id.getPath().startsWith(path.substring(0, path.length() - 1));
        }

        return path.equals(id.getPath());
    }

    private static boolean creativeOperator(ServerPlayer player) {
        return player.isCreative() && player.hasPermissions(2);
    }

    private static Country playerCountry(ServerPlayer player) {
        return PoliticsModIntegration.playerCountry(player).orElse(null);
    }

    private static void deny(ServerPlayer player, ResourceLocation contentId, CountryResearch technology) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "Доступ заблокирован: ветка «" + technology.direction().displayName()
                + "» → технология «" + technology.title() + "» ещё не исследована."
                + (contentId == null ? "" : " [" + contentId + "]")
        ).withStyle(net.minecraft.ChatFormatting.RED));
    }

    /** Returns the technology required by an item, also checking the block id for BlockItems. */
    public static CountryResearch requiredTechnology(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        CountryResearch result = requiredTechnology(itemId);
        if (result != null) return result;

        if (stack.getItem() instanceof BlockItem blockItem) {
            ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
            return requiredTechnology(blockId);
        }
        return null;
    }

    /**
     * Final server-side placement check. Unlike a right-click check, this also
     * catches blocks placed by automation (for example Create Deployers).
     */
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        ResourceLocation contentId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
            event.getPlacedBlock().getBlock()
        );
        CountryResearch technology = requiredTechnology(contentId);
        if (technology == null) return;

        Country country = null;
        ServerPlayer player = event.getEntity() instanceof ServerPlayer serverPlayer ? serverPlayer : null;

        // A player's technology belongs to their country, not to the chunk in
        // which they happened to place the block. This prevents stepping outside
        // national territory from becoming a technology-tree bypass.
        if (player != null) {
            if (creativeOperator(player)) return;
            country = playerCountry(player);
        }

        // Players without a country must not be able to use locked technology.
        // Automation has no player, so it falls back to the destination chunk owner.
        if (country == null) {
            var politics = PoliticsModIntegration.manager(level);
            if (politics != null) {
                country = politics.getCountryAt(new ChunkPos(event.getPos()));
            }
        }

        boolean unlocked = country != null
            && CountryResearchService.completed(level.getServer(), country.getName()).contains(technology.id());
        if (unlocked) return;

        event.setCanceled(true);
        if (player != null) {
            deny(player, contentId, technology);
        }
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (creativeOperator(player)) return;

        ItemStack held = player.getItemInHand(event.getHand());
        Country country = playerCountry(player);

        // Gate the block being interacted with as well as the held item. This
        // prevents an already-placed locked machine from being used with an
        // empty hand or a non-block tool.
        ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
            event.getLevel().getBlockState(event.getPos()).getBlock()
        );
        CountryResearch blockTechnology = requiredTechnology(blockId);
        if (blockTechnology != null) {
            boolean allowed = country != null
                && CountryResearchService.completed(player.getServer(), country.getName()).contains(blockTechnology.id());
            if (!allowed) {
                event.setCanceled(true);
                deny(player, blockId, blockTechnology);
                return;
            }
        }

        CountryResearch itemTechnology = requiredTechnology(held);
        if (itemTechnology != null) {
            boolean allowed = country != null
                && CountryResearchService.completed(player.getServer(), country.getName()).contains(itemTechnology.id());
            if (!allowed) {
                event.setCanceled(true);
                ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
                deny(player, itemId, itemTechnology);
            }
        }
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (creativeOperator(player)) return;

        ItemStack held = player.getItemInHand(event.getHand());
        ResourceLocation contentId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
        CountryResearch technology = requiredTechnology(held);
        if (technology == null) return;

        Country country = playerCountry(player);
        boolean allowed = country != null
            && CountryResearchService.completed(player.getServer(), country.getName()).contains(technology.id());
        if (!allowed) {
            event.setCanceled(true);
            deny(player, contentId, technology);
        }
    }

    public static void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (creativeOperator(player)) return;
        denyIfLocked(player, event.getItemStack(), event);
    }

    public static void onRightClickEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (creativeOperator(player)) return;
        denyIfLocked(player, event.getItemStack(), event);
    }

    private static void denyIfLocked(
        ServerPlayer player,
        ItemStack stack,
        net.neoforged.neoforge.event.entity.player.PlayerInteractEvent event
    ) {
        CountryResearch technology = requiredTechnology(stack);
        if (technology == null) return;

        Country country = playerCountry(player);
        boolean allowed = country != null
            && CountryResearchService.completed(player.getServer(), country.getName()).contains(technology.id());
        if (allowed) return;

        event.setCanceled(true);
        deny(player, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()), technology);
    }

    public static void onAttackEntity(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (creativeOperator(player)) return;
        ItemStack held = player.getMainHandItem();
        CountryResearch technology = requiredTechnology(held);
        if (technology == null) return;

        Country country = playerCountry(player);
        boolean allowed = country != null
            && CountryResearchService.completed(player.getServer(), country.getName()).contains(technology.id());
        if (allowed) return;

        event.setCanceled(true);
        deny(player, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()), technology);
    }

}
