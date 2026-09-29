package ru.zela.politicseconomy.integration;

import net.krona.politicsmod.politics.Country;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.core.registries.BuiltInRegistries;
import ru.zela.politicseconomy.country.CountryDirectionBonusService;
import ru.zela.politicseconomy.country.CountryDirectionProfile;
import ru.zela.politicseconomy.country.CountryPolicyBonusService;
import ru.zela.politicseconomy.country.CountryPolicyProfile;
import ru.zela.politicseconomy.country.CountryWorkforceService;
import ru.zela.politicseconomy.country.WorkforceSector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Applies country industrial modifiers to real Create outputs. */
public final class CreateProductionService {
    private CreateProductionService() {}

    public static double effectiveMultiplier(ServerLevel level, BlockPos machinePos) {
        Country country = CountryContext.machineCountry(level, machinePos);
        if (country == null) {
            return 1.0D;
        }

        MinecraftServer server = level.getServer();
        if (server == null) {
            return 1.0D;
        }

        CountryDirectionProfile profile = CountryDirectionBonusService.profile(server, country.getName());
        if (profile == null) {
            return 1.0D;
        }

        CountryPolicyProfile policy = CountryPolicyBonusService.profile(server, country.getName());
        double modifier = profile.industrialProduction() + policy.industrialProduction()
            + CountryWorkforceService.sectorBonusPercent(
                server, country.getName(), WorkforceSector.INDUSTRY
            );
        return Math.max(0.0D, 1.0D + modifier / 100.0D)
            * CountryPolicyBonusService.workforceMultiplier(server, country.getName());
    }

    /**
     * Builds a reduced copy of Basin fluid outputs for a negative production modifier.
     * The recipe's original FluidStacks are never modified. Persistent fractional
     * remainders are committed only when Create successfully accepts the reduced output.
     */
    public static List<FluidStack> buildReducedFluidOutputs(
        ServerLevel level,
        BlockPos machinePos,
        List<FluidStack> recipeOutputs
    ) {
        CreateProcessingContext.Context context = CreateProcessingContext.current();
        if (context == null) {
            return copyFluidOutputs(recipeOutputs);
        }

        double multiplier = effectiveMultiplier(level, machinePos);
        MinecraftServer server = level.getServer();
        if (server == null) {
            return copyFluidOutputs(recipeOutputs);
        }

        Country country = CountryContext.machineCountry(level, machinePos);
        if (country == null) {
            return copyFluidOutputs(recipeOutputs);
        }

        CreateProductionLedgerSavedData ledger = CreateProductionLedgerSavedData.get(server);
        String countryKey = country.getName();
        Map<String, Double> workingRemainders = new LinkedHashMap<>();
        List<FluidStack> scaled = new ArrayList<>(recipeOutputs.size());

        for (FluidStack original : recipeOutputs) {
            if (original == null || original.isEmpty()) {
                scaled.add(FluidStack.EMPTY);
                continue;
            }

            FluidStack copy = original.copy();
            String fluidId = BuiltInRegistries.FLUID.getKey(copy.getFluid()).toString();
            String key = countryKey + "|fluid|" + fluidId;
            double remainder = workingRemainders.containsKey(key)
                ? workingRemainders.get(key)
                : ledger.getRemainder(key);

            double raw = remainder + copy.getAmount() * multiplier;
            int newAmount = (int) Math.floor(raw + 1.0E-9D);
            double newRemainder = raw - newAmount;

            workingRemainders.put(key, newRemainder);
            copy.setAmount(Math.max(0, newAmount));
            scaled.add(copy);
        }

        context.pendingFluidRemainders().clear();
        context.pendingFluidRemainders().putAll(workingRemainders);
        return scaled;
    }

    /**
     * Positive modifier path: Create has already accepted its normal fluid output.
     * Add only the extra amount afterward, so the bonus can never make the original
     * recipe fail its own capacity checks.
     */
    public static void applyPositiveFluidBonusAfterBaseAccepted(
        ServerLevel level,
        BlockPos machinePos,
        com.simibubi.create.content.processing.basin.BasinBlockEntity basin,
        List<FluidStack> originalOutputs
    ) {
        CreateProcessingContext.Context context = CreateProcessingContext.current();
        if (context == null) {
            return;
        }

        double multiplier = effectiveMultiplier(level, machinePos);
        double bonusMultiplier = multiplier - 1.0D;
        if (bonusMultiplier <= 1.0E-9D || originalOutputs.isEmpty()) {
            return;
        }

        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }

        Country country = CountryContext.machineCountry(level, machinePos);
        if (country == null) {
            return;
        }

        CreateProductionLedgerSavedData ledger = CreateProductionLedgerSavedData.get(server);
        String countryKey = country.getName();
        Map<String, Double> workingRemainders = new LinkedHashMap<>();
        List<FluidStack> bonusOutputs = new ArrayList<>();

        for (FluidStack original : originalOutputs) {
            if (original == null || original.isEmpty()) {
                continue;
            }

            FluidStack bonus = original.copy();
            String fluidId = BuiltInRegistries.FLUID.getKey(bonus.getFluid()).toString();
            String key = countryKey + "|fluid|" + fluidId;
            double remainder = workingRemainders.containsKey(key)
                ? workingRemainders.get(key)
                : ledger.getRemainder(key);

            double rawBonus = remainder + bonus.getAmount() * bonusMultiplier;
            int bonusAmount = (int) Math.floor(rawBonus + 1.0E-9D);
            double newRemainder = rawBonus - bonusAmount;
            workingRemainders.put(key, newRemainder);

            if (bonusAmount <= 0) {
                continue;
            }

            bonus.setAmount(bonusAmount);
            bonusOutputs.add(bonus);
        }

        // Nothing extra was ready yet; committing the fractional remainder is safe
        // because the normal Create recipe has already succeeded.
        if (bonusOutputs.isEmpty()) {
            workingRemainders.forEach(ledger::setRemainder);
            return;
        }

        // Test the extra output first. If it fits, execute it. If it cannot fit,
        // keep the old remainder so the bonus can be attempted again later.
        if (!basin.acceptOutputs(java.util.Collections.emptyList(), bonusOutputs, true)) {
            return;
        }

        if (basin.acceptOutputs(java.util.Collections.emptyList(), bonusOutputs, false)) {
            workingRemainders.forEach(ledger::setRemainder);
        }
    }

    public static void commitReducedFluidOutputs() {
        CreateProcessingContext.Context context = CreateProcessingContext.current();
        if (context == null || context.pendingFluidRemainders().isEmpty()) {
            return;
        }

        MinecraftServer server = context.level().getServer();
        if (server == null) {
            return;
        }

        CreateProductionLedgerSavedData ledger = CreateProductionLedgerSavedData.get(server);
        context.pendingFluidRemainders().forEach(ledger::setRemainder);
        context.pendingFluidRemainders().clear();
    }

    public static void discardReducedFluidOutputs() {
        CreateProcessingContext.Context context = CreateProcessingContext.current();
        if (context != null) {
            context.pendingFluidRemainders().clear();
        }
    }

    /**
     * Final Create item-output multiplier. Normal Create production uses the
     * direction's industrial modifier. Create Big Cannons is additionally treated
     * as military production, so its outputs also receive militaryProduction.
     * This gives the already-existing profile stat a concrete purpose without
     * changing cannon damage, fire rate, or projectile physics.
     */
    private static double itemProductionMultiplier(
        ServerLevel level,
        BlockPos machinePos,
        ItemStack output
    ) {
        Country country = CountryContext.machineCountry(level, machinePos);
        if (country == null) {
            return 1.0D;
        }

        MinecraftServer server = level.getServer();
        if (server == null) {
            return 1.0D;
        }

        CountryDirectionProfile profile = CountryDirectionBonusService.profile(server, country.getName());
        if (profile == null) {
            return 1.0D;
        }

        CountryPolicyProfile policy = CountryPolicyBonusService.profile(server, country.getName());
        double modifier = profile.industrialProduction() + policy.industrialProduction();
        var itemKey = BuiltInRegistries.ITEM.getKey(output.getItem());
        if (itemKey != null && "createbigcannons".equals(itemKey.getNamespace())) {
            modifier += profile.militaryProduction() + policy.militaryProduction()
                + CountryWorkforceService.sectorBonusPercent(
                    server, country.getName(), WorkforceSector.MILITARY
                );
        }

        return Math.max(0.0D, 1.0D + modifier / 100.0D)
            * CountryPolicyBonusService.workforceMultiplier(server, country.getName());
    }

    private static List<FluidStack> copyFluidOutputs(List<FluidStack> outputs) {
        List<FluidStack> copies = new ArrayList<>(outputs.size());
        for (FluidStack stack : outputs) {
            copies.add(stack == null ? FluidStack.EMPTY : stack.copy());
        }
        return copies;
    }

    /** Applies the country's industrial modifier to real Create item outputs. */
    public static void applyItemProductionBonus(ServerLevel level, BlockPos machinePos, List<ItemStack> outputs) {
        if (outputs.isEmpty()) {
            return;
        }

        double multiplier = effectiveMultiplier(level, machinePos);
        if (Math.abs(multiplier - 1.0D) < 1.0E-9D) {
            return;
        }

        Country country = CountryContext.machineCountry(level, machinePos);
        if (country == null) {
            return;
        }

        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }

        CreateProductionLedgerSavedData ledger = CreateProductionLedgerSavedData.get(server);
        String countryKey = country.getName();

        int originalSize = outputs.size();
        for (int i = 0; i < originalSize; i++) {
            ItemStack original = outputs.get(i);
            if (original.isEmpty()) {
                continue;
            }

            String itemId = BuiltInRegistries.ITEM.getKey(original.getItem()).toString();
            double itemMultiplier = itemProductionMultiplier(level, machinePos, original);
            String key = countryKey + "|" + itemId;
            double raw = ledger.getRemainder(key) + original.getCount() * itemMultiplier;
            int newCount = (int) Math.floor(raw + 1.0E-9D);
            ledger.setRemainder(key, raw - newCount);

            if (newCount <= 0) {
                outputs.set(i, ItemStack.EMPTY);
                continue;
            }

            ItemStack adjusted = original.copy();
            adjusted.setCount(Math.min(newCount, adjusted.getMaxStackSize()));
            outputs.set(i, adjusted);

            int remaining = newCount - adjusted.getCount();
            while (remaining > 0) {
                ItemStack extra = original.copy();
                int amount = Math.min(remaining, extra.getMaxStackSize());
                extra.setCount(amount);
                outputs.add(extra);
                remaining -= amount;
            }
        }
    }
}
