package ru.zela.politicseconomy.country;

import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.minecraft.server.level.ServerPlayer;
import ru.zela.politicseconomy.integration.PoliticsModIntegration;

/** Validates country-configuration actions coming from the client UI. */
public final class CountrySettingsService {
    private CountrySettingsService() {}
    public record Result(boolean success, String message) {}

    public static Result apply(ServerPlayer player, String action, String value) {
        Country country = PoliticsModIntegration.playerCountry(player).orElse(null);
        if (country == null) return new Result(false, "Ты не состоишь ни в одной стране.");
        boolean operator = player.hasPermissions(2) && player.isCreative();
        if (!operator && PoliticsModIntegration.role(player, country) != CountryRole.LEADER) {
            return new Result(false, "Изменять настройки страны может только лидер.");
        }
        String countryName = country.getName();
        return switch (action == null ? "" : action.toLowerCase(java.util.Locale.ROOT)) {
            case "direction" -> applyDirection(player, country, countryName, value, operator);
            case "government" -> applyGovernment(player, country, countryName, value, operator);
            case "religion" -> applyReligion(player, country, countryName, value, operator);
            default -> new Result(false, "Неизвестная настройка страны.");
        };
    }

    private static Result applyDirection(ServerPlayer player, Country country, String countryName, String rawValue, boolean operator) {
        CountryDirection value = CountryDirection.fromCommandName(rawValue);
        if (value == null) return new Result(false, "Неизвестное экономическое направление.");
        CountryDirection current = CountryDirectionManager.getDirection(player.getServer(), countryName);
        boolean firstChoice = current == null;
        if (!firstChoice && current == value) return new Result(false, "Это направление уже выбрано.");
        if (operator) {
            CountryDirectionManager.forceSetDirection(player.getServer(), countryName, value);
            return new Result(true, "Экономическое направление изменено: " + value.displayName());
        }
        CountryReformService.Result reform = CountryReformService.apply(player.getServer(), country, "direction", firstChoice,
            () -> CountryDirectionManager.forceSetDirection(player.getServer(), countryName, value));
        return reform.success() ? new Result(true, reform.message() + " Экономическое направление: " + value.displayName()) : new Result(false, reform.message());
    }

    private static Result applyGovernment(ServerPlayer player, Country country, String countryName, String rawValue, boolean operator) {
        GovernmentType value = GovernmentType.fromCommandName(rawValue);
        if (value == null) return new Result(false, "Неизвестная форма правления.");
        GovernmentType current = CountryPolicyManager.getGovernment(player.getServer(), countryName);
        boolean firstChoice = current == null;
        if (!firstChoice && current == value) return new Result(false, "Эта форма правления уже выбрана.");
        if (operator) {
            CountryPolicyManager.forceSetGovernment(player.getServer(), countryName, value);
            return new Result(true, "Форма правления изменена: " + value.displayName());
        }
        CountryReformService.Result reform = CountryReformService.apply(player.getServer(), country, "government", firstChoice,
            () -> CountryPolicyManager.forceSetGovernment(player.getServer(), countryName, value));
        return reform.success() ? new Result(true, reform.message() + " Форма правления: " + value.displayName()) : new Result(false, reform.message());
    }

    private static Result applyReligion(ServerPlayer player, Country country, String countryName, String rawValue, boolean operator) {
        ReligionType value = ReligionType.fromCommandName(rawValue);
        if (value == null) return new Result(false, "Неизвестная религия.");
        ReligionType current = CountryPolicyManager.getReligion(player.getServer(), countryName);
        boolean firstChoice = current == null;
        if (!firstChoice && current == value) return new Result(false, "Эта религия уже выбрана.");
        if (operator) {
            CountryPolicyManager.forceSetReligion(player.getServer(), countryName, value);
            return new Result(true, "Религия изменена: " + value.displayName());
        }
        CountryReformService.Result reform = CountryReformService.apply(player.getServer(), country, "religion", firstChoice,
            () -> CountryPolicyManager.forceSetReligion(player.getServer(), countryName, value));
        return reform.success() ? new Result(true, reform.message() + " Религия: " + value.displayName()) : new Result(false, reform.message());
    }
}
