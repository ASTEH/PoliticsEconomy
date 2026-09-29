package ru.zela.politicseconomy.economy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import ru.zela.politicseconomy.country.CountryDevelopmentService;
import ru.zela.politicseconomy.integration.PoliticsModIntegration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Moves concrete material items from a player inventory into the national warehouse. */
public final class NationalMaterialInventoryService {
    private NationalMaterialInventoryService() {}

    public record DepositResult(int itemCount, Map<String, Integer> byItem) {}

    public static DepositResult deposit(ServerPlayer player, String exactItemId) {
        String countryName = PoliticsModIntegration.playerCountry(player)
            .map(country -> country.getName())
            .orElse(null);
        if (countryName == null) {
            return new DepositResult(0, Map.of());
        }

        NationalMaterialDemandService.CountryDemand demand =
            NationalMaterialDemandService.calculate(player.serverLevel(), countryName);
        List<String> accepted = demand.materials().stream()
            .flatMap(material -> material.acceptedItemIds().stream())
            .distinct()
            .toList();

        if (exactItemId != null && !accepted.contains(exactItemId)) {
            return new DepositResult(0, Map.of());
        }

        NationalMaterialLedgerSavedData ledger = NationalMaterialConsumptionService.getLedger(player.getServer());
        ledger.initializeCountry(countryName);

        Map<String, Integer> added = new LinkedHashMap<>();
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!accepted.contains(itemId)) {
                continue;
            }
            if (exactItemId != null && !exactItemId.equals(itemId)) {
                continue;
            }

            int count = stack.getCount();
            if (count <= 0) {
                continue;
            }

            player.getInventory().setItem(slot, ItemStack.EMPTY);
            ledger.addStockpile(countryName, itemId, count);
            added.merge(itemId, count, Integer::sum);
            total += count;
        }

        if (total > 0) {
            int developmentPoints = Math.min(100, Math.max(1, total / 32));
            CountryDevelopmentService.addActivity(player.getServer(), countryName, developmentPoints);

            // Depositing materials is also the payment action. Do not wait for
            // the next economy cycle: immediately consume warehouse stock
            // against all outstanding material debts so machines can resume
            // and the dashboard reflects the cleared debt right away.
            ledger.settleAllDebtsFromStockpile(countryName);
            ledger.setDirty();
        }

        return new DepositResult(total, Map.copyOf(added));
    }

    public static void tellResult(ServerPlayer player, DepositResult result) {
        if (result.itemCount() <= 0) {
            player.sendSystemMessage(Component.literal(
                "На складе нужны другие материалы: сдавать можно только предметы, входящие в рецепты сооружений твоей страны."
            ));
            return;
        }

        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : result.byItem().entrySet()) {
            parts.add(NationalMaterialDemandService.itemDisplayName(player.serverLevel(), entry.getKey())
                + " × " + entry.getValue());
        }
        player.sendSystemMessage(Component.literal("Государственный склад: +" + String.join(", ", parts)));
    }
}
