package ru.zela.politicseconomy.economy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import ru.zela.politicseconomy.country.CountryDevelopmentService;
import ru.zela.politicseconomy.integration.PoliticsModIntegration;

import java.util.EnumMap;
import java.util.Map;

/** Moves real player inventory items into the country's strategic-resource stockpile. */
public final class NationalResourceInventoryService {
    private NationalResourceInventoryService() {}

    public record DepositResult(int totalUnits, Map<NationalResource, Integer> byResource, int developmentPoints) {}

    public static DepositResult deposit(ServerPlayer player, NationalResource only) {
        String countryName = PoliticsModIntegration.playerCountry(player)
            .map(country -> country.getName())
            .orElse(null);
        if (countryName == null) {
            return new DepositResult(0, Map.of(), 0);
        }

        NationalUpkeepLedgerSavedData ledger = NationalUpkeepService.getLedger(player.getServer());
        if (!ledger.hasCountry(countryName)) {
            var direction = ru.zela.politicseconomy.country.CountryDirectionManager.getDirection(
                player.getServer(), countryName
            );
            if (direction == null) return new DepositResult(0, Map.of(), 0);
            ledger.initializeCountry(countryName, direction);
        }

        EnumMap<NationalResource, Integer> added = new EnumMap<>(NationalResource.class);
        int total = 0;

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;

            var value = NationalResourceItemValue.classify(stack).orElse(null);
            if (value == null || (only != null && value.resource() != only)) continue;

            int units = Math.multiplyExact(stack.getCount(), value.unitsPerItem());
            if (units <= 0) continue;

            player.getInventory().setItem(slot, ItemStack.EMPTY);
            ledger.addStockpile(countryName, value.resource(), units);
            added.merge(value.resource(), units, Integer::sum);
            total += units;
        }

        if (total <= 0) {
            return new DepositResult(0, added, 0);
        }

        // Development rewards economic activity without making one huge delivery
        // level the country instantly. Each 50 units gives 1 point, capped per deposit.
        int developmentPoints = Math.min(100, Math.max(1, total / 50));
        CountryDevelopmentService.addActivity(player.getServer(), countryName, developmentPoints);
        ledger.setDirty();
        return new DepositResult(total, added, developmentPoints);
    }

    public static boolean isRecognized(ItemStack stack, NationalResource resource) {
        var value = NationalResourceItemValue.classify(stack).orElse(null);
        return value != null && value.resource() == resource;
    }

    public static void tellResult(ServerPlayer player, DepositResult result) {
        if (result.totalUnits() <= 0) {
            player.sendSystemMessage(Component.literal(
                "В инвентаре не найдено подходящих ресурсов."
            ));
            return;
        }

        StringBuilder message = new StringBuilder("Государственный склад: +");
        boolean first = true;
        for (var entry : result.byResource().entrySet()) {
            if (!first) message.append(", ");
            first = false;
            message.append(entry.getKey().displayName()).append(" ").append(entry.getValue());
        }
        message.append(" ед. | развитие +").append(result.developmentPoints()).append(" очк.");
        player.sendSystemMessage(Component.literal(message.toString()));
    }
}
