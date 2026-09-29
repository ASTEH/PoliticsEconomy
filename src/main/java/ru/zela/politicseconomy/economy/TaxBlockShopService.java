package ru.zela.politicseconomy.economy;

import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ru.zela.politicseconomy.integration.PoliticsModIntegration;

import java.util.Optional;

/**
 * Treasury-funded purchase of PoliticsMod Tax Blocks.
 *
 * <p>The marginal price rises with the total number of tax blocks already
 * registered in the country. Because the base price scales with PoliticsMod's
 * configured income per Tax Block, the curve remains sensible when the
 * upstream economy configuration changes.</p>
 */
public final class TaxBlockShopService {
    private static final String TAX_BLOCK_ID = "politicsmod:tax_block";
    private static final int PRICE_ROUNDING = 25;
    private static final int MAX_BATCH = 16;

    private TaxBlockShopService() {}

    public record PurchaseResult(
        boolean success,
        String message,
        long totalCost,
        int taxBlocksBefore,
        int taxBlocksAfter
    ) {}

    public static int maxBatch() {
        return MAX_BATCH;
    }

    public static long priceForNextTaxBlock(Country country) {
        return marginalPrice(country.taxBlocks.size());
    }

    public static long priceForBatch(Country country, int amount) {
        if (amount <= 0) {
            return 0L;
        }

        long total = 0L;
        int existing = country.taxBlocks.size();
        for (int i = 0; i < amount; i++) {
            long price = marginalPrice(existing + i);
            if (Long.MAX_VALUE - total < price) {
                return Long.MAX_VALUE;
            }
            total += price;
        }
        return total;
    }

    public static PurchaseResult buy(ServerPlayer player, int amount) {
        if (amount < 1 || amount > MAX_BATCH) {
            return new PurchaseResult(
                false,
                "Количество Tax Block должно быть от 1 до " + MAX_BATCH + ".",
                0L,
                0,
                0
            );
        }

        Optional<Country> countryOptional = PoliticsModIntegration.playerCountry(player);
        if (countryOptional.isEmpty()) {
            return new PurchaseResult(false, "Сначала вступи в государство.", 0L, 0, 0);
        }

        Country country = countryOptional.get();
        CountryRole role = PoliticsModIntegration.role(player, country);
        if (!player.isCreative() && role.getLevel() < CountryRole.MAYOR.getLevel()) {
            return new PurchaseResult(
                false,
                "Покупать налоговые блоки за казну могут только мэр или лидер.",
                0L,
                country.taxBlocks.size(),
                country.taxBlocks.size()
            );
        }

        Item item = BuiltInRegistries.ITEM
            .getOptional(ResourceLocation.parse(TAX_BLOCK_ID))
            .orElse(null);
        if (item == null) {
            return new PurchaseResult(false, "Tax Block из PoliticsMod не найден.", 0L, 0, 0);
        }

        int before = country.taxBlocks.size();
        long totalCost = priceForBatch(country, amount);
        if (totalCost > Integer.MAX_VALUE) {
            return new PurchaseResult(false, "Стоимость покупки слишком велика.", totalCost, before, before);
        }

        if (country.balance < totalCost) {
            return new PurchaseResult(
                false,
                "Недостаточно средств в казне: нужно $" + totalCost + ", доступно $" + country.balance + ".",
                totalCost,
                before,
                before
            );
        }

        country.balance -= (int) totalCost;
        ItemStack purchased = new ItemStack(item, amount);
        if (!player.addItem(purchased) && !purchased.isEmpty()) {
            player.drop(purchased, false);
        }
        var manager = ru.krona.politicsmod.PoliticsManager.get(player.serverLevel());
        if (manager != null) {
            manager.setDirty();
        }

        return new PurchaseResult(
            true,
            "Куплено Tax Block ×" + amount + " за $" + totalCost + ".",
            totalCost,
            before,
            before
        );
    }

    private static long marginalPrice(int existingTaxBlocks) {
        double configuredIncome = Math.max(1, PoliticsConfig.get().incomePerTaxBlock);

        /*
         * Current default (income = $50):
         * 0 existing  -> $250
         * 1 existing  -> $325
         * 10 existing -> $1,500
         * 50 existing -> $16,500
         * 100 existing -> $57,750
         *
         * The first block therefore repays in about five economy cycles,
         * while large tax networks become progressively capital-intensive.
         */
        double multiplier =
            5.0D
            + 1.5D * existingTaxBlocks
            + 0.10D * existingTaxBlocks * existingTaxBlocks;

        long raw = Math.max(PRICE_ROUNDING, Math.round(configuredIncome * multiplier));
        return Math.max(
            PRICE_ROUNDING,
            Math.round(raw / (double) PRICE_ROUNDING) * PRICE_ROUNDING
        );
    }
}
