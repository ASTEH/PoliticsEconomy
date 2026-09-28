package ru.zela.politicseconomy.country;

import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.minecraft.server.level.ServerPlayer;
import ru.zela.politicseconomy.integration.PoliticsModIntegration;

/**
 * Validates country-configuration actions coming from the client UI.
 * All permissions are checked server-side; the client only requests an action.
 */
public final class CountrySettingsService {
    private CountrySettingsService() {}

    public record Result(boolean success, String message) {}

    public static Result apply(ServerPlayer player, String action, String value) {
        Country country = PoliticsModIntegration.playerCountry(player).orElse(null);
        if (country == null) {
            return new Result(false, "Ты не состоишь ни в одной стране.");
        }

        boolean operator = player.hasPermissions(2);
        if (!operator && PoliticsModIntegration.role(player, country) != CountryRole.LEADER) {
            return new Result(false, "Изменять настройки страны может только лидер.");
        }

        String countryName = country.getName();

        return switch (action == null ? "" : action.toLowerCase(java.util.Locale.ROOT)) {
            case "direction" -> applyDirection(player, countryName, value, operator);
            case "government" -> applyGovernment(player, countryName, value, operator);
            case "religion" -> applyReligion(player, countryName, value, operator);
            default -> new Result(false, "Неизвестная настройка страны.");
        };
    }

    private static Result applyDirection(
        ServerPlayer player,
        String countryName,
        String rawValue,
        boolean operator
    ) {
        CountryDirection value = CountryDirection.fromCommandName(rawValue);
        if (value == null) {
            return new Result(false, "Неизвестное экономическое направление.");
        }

        CountryDirection current = CountryDirectionManager.getDirection(player.getServer(), countryName);
        if (current != null && !operator) {
            return new Result(false, "Экономическое направление уже закреплено.");
        }

        if (operator) {
            CountryDirectionManager.forceSetDirection(player.getServer(), countryName, value);
            return new Result(true, "Экономическое направление изменено: " + value.displayName());
        }

        boolean changed = CountryDirectionManager.setDirection(
            player.getServer(),
            countryName,
            value
        );
        return changed
            ? new Result(true, "Экономическое направление выбрано: " + value.displayName())
            : new Result(false, "Экономическое направление уже закреплено.");
    }

    private static Result applyGovernment(
        ServerPlayer player,
        String countryName,
        String rawValue,
        boolean operator
    ) {
        GovernmentType value = GovernmentType.fromCommandName(rawValue);
        if (value == null) {
            return new Result(false, "Неизвестная форма правления.");
        }

        GovernmentType current = CountryPolicyManager.getGovernment(player.getServer(), countryName);
        if (current != null && !operator) {
            return new Result(false, "Форма правления уже закреплена.");
        }

        if (operator) {
            CountryPolicyManager.forceSetGovernment(player.getServer(), countryName, value);
            return new Result(true, "Форма правления изменена: " + value.displayName());
        }

        boolean changed = CountryPolicyManager.setGovernment(
            player.getServer(),
            countryName,
            value
        );
        return changed
            ? new Result(true, "Форма правления выбрана: " + value.displayName())
            : new Result(false, "Форма правления уже закреплена.");
    }

    private static Result applyReligion(
        ServerPlayer player,
        String countryName,
        String rawValue,
        boolean operator
    ) {
        ReligionType value = ReligionType.fromCommandName(rawValue);
        if (value == null) {
            return new Result(false, "Неизвестная религия.");
        }

        ReligionType current = CountryPolicyManager.getReligion(player.getServer(), countryName);
        if (current != null && !operator) {
            return new Result(false, "Религия уже закреплена.");
        }

        if (operator) {
            CountryPolicyManager.forceSetReligion(player.getServer(), countryName, value);
            return new Result(true, "Религия изменена: " + value.displayName());
        }

        boolean changed = CountryPolicyManager.setReligion(
            player.getServer(),
            countryName,
            value
        );
        return changed
            ? new Result(true, "Религия выбрана: " + value.displayName())
            : new Result(false, "Религия уже закреплена.");
    }
}
