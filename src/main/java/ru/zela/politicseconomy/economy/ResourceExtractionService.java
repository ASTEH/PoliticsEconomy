package ru.zela.politicseconomy.economy;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import ru.zela.politicseconomy.country.CountryDirectionBonusService;
import ru.zela.politicseconomy.integration.CountryContext;
import ru.zela.politicseconomy.country.CountryDirectionProfile;
import ru.zela.politicseconomy.country.CountryPolicyBonusService;
import ru.zela.politicseconomy.country.CountryPolicyProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies direction-specific production modifiers and extraction losses to the
 * final server-side block drops.
 *
 * <p>The order is intentional:</p>
 * <ol>
 *   <li>Minecraft calculates the normal drops first (including Fortune/Silk Touch).</li>
 *   <li>Direction production/loss multipliers are applied to that final amount.</li>
 *   <li>Fractional results are accumulated per player/category/item, so small drops
 *       are not rounded away every time.</li>
 * </ol>
 */
public final class ResourceExtractionService {
    private static final double EPSILON = 1.0E-9D;

    private ResourceExtractionService() {}

    public static void apply(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) {
            return;
        }

        if (event.getDrops().isEmpty()) {
            return;
        }

        ServerLevel level = event.getLevel();
        BlockState state = event.getState();
        ResourceExtractionCategory category = ResourceExtractionClassifier.classify(state);
        if (category == null) {
            return;
        }

        PoliticsManager politics = PoliticsManager.get(level);
        if (politics == null) {
            return;
        }

        // Economic extraction modifiers belong to the player's country, not to
        // the chunk being mined. A player must not be able to bypass a country's
        // production penalties simply by mining outside its borders.
        Country playerCountry = CountryContext.playerCountry(player);
        if (playerCountry == null) {
            return;
        }

        CountryDirectionProfile profile = CountryDirectionBonusService.profile(
            player.getServer(), playerCountry.getName()
        );
        if (profile == null) {
            return;
        }

        CountryPolicyProfile policy = CountryPolicyBonusService.profile(
            player.getServer(), playerCountry.getName()
        );
        double productionModifier = profile.extractionProduction(category)
            + (category == ResourceExtractionCategory.AGRICULTURE
                ? policy.agriculturalProduction()
                : policy.resourceProduction());
        double lossPercent = clamp(profile.extractionLoss(category), -100.0D, 100.0D);
        double productionMultiplier = Math.max(0.0D, 1.0D + productionModifier / 100.0D);
        double lossMultiplier = Math.max(0.0D, 1.0D - lossPercent / 100.0D);
        double totalMultiplier = productionMultiplier
            * lossMultiplier
            * CountryPolicyBonusService.workforceMultiplier(player.getServer(), playerCountry.getName());

        // No effective change: avoid touching drops or creating ledger data.
        if (Math.abs(totalMultiplier - 1.0D) <= EPSILON) {
            return;
        }

        ResourceTaxLedgerSavedData ledger = ResourceTaxLedgerSavedData.get(player.getServer());
        List<ItemEntity> originalDrops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();

        for (ItemEntity entity : originalDrops) {
            ItemStack originalStack = entity.getItem();
            if (originalStack.isEmpty()) {
                continue;
            }

            ResourceLocation key = BuiltInRegistries.ITEM.getKey(originalStack.getItem());
            if (key == null) {
                event.getDrops().add(entity);
                continue;
            }

            String itemId = key.toString();
            double remainder = ledger.getRemainder(player.getUUID(), category, itemId);
            double rawResult = remainder + originalStack.getCount() * totalMultiplier;
            int finalCount = (int) Math.floor(rawResult + EPSILON);
            double newRemainder = rawResult - finalCount;
            ledger.setRemainder(player.getUUID(), category, itemId, newRemainder);

            if (finalCount <= 0) {
                continue;
            }

            int maxStack = Math.max(1, originalStack.getMaxStackSize());
            int remaining = finalCount;
            double x = entity.getX();
            double y = entity.getY();
            double z = entity.getZ();

            while (remaining > 0) {
                int count = Math.min(maxStack, remaining);
                ItemStack output = originalStack.copy();
                output.setCount(count);

                ItemEntity outputEntity = new ItemEntity(level, x, y, z, output);
                outputEntity.setDefaultPickUpDelay();
                event.getDrops().add(outputEntity);

                remaining -= count;
            }
        }

        ledger.setDirty();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
