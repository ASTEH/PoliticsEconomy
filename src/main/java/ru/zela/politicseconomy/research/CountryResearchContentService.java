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
            "Технология «" + technology.title() + "» ещё не исследована. (" + contentId + ")"
        ).withStyle(net.minecraft.ChatFormatting.RED));
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
        // which they happened to place the block. This also prevents bypassing
        // the tech tree by stepping outside national territory.
        if (player != null) {
            if (creativeOperator(player)) return;
            country = playerCountry(player);
        }

        // For automated placement without a player entity (e.g. Create), fall
        // back to the country owning the destination chunk.
        if (country == null) {
            var politics = PoliticsModIntegration.manager(level);
            if (politics == null) return;
            country = politics.getCountryAt(new ChunkPos(event.getPos()));
        }

        if (country == null || country.getName().isBlank()) return;

        if (CountryResearchService.completed(level.getServer(), country.getName()).contains(technology.id())) return;

        event.setCanceled(true);
        if (player != null) {
            deny(player, contentId, technology);
        }
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (creativeOperator(player)) return;

        ItemStack stack = player.getItemInHand(event.getHand());
        if (!(stack.getItem() instanceof BlockItem)) return;

        ResourceLocation contentId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());

        // The player's country determines which technologies they are allowed
        // to use. Destination territory must not become a tech-tree bypass.
        Country country = playerCountry(player);
        if (country == null) return;

        CountryResearch technology = requiredTechnology(contentId);
        if (technology != null && !unlocked(player.getServer(), country.getName(), contentId)) {
            event.setCanceled(true);
            deny(player, contentId, technology);
        }
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (creativeOperator(player)) return;

        ResourceLocation contentId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getItemInHand(event.getHand()).getItem());
        Country country = playerCountry(player);
        if (country == null) return;

        CountryResearch technology = requiredTechnology(contentId);
        if (technology != null && !unlocked(player.getServer(), country.getName(), contentId)) {
            event.setCanceled(true);
            deny(player, contentId, technology);
        }
    }

}
