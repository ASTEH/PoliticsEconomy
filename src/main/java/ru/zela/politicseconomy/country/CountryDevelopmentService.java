package ru.zela.politicseconomy.country;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Economic-development progression shared by all country systems. */
public final class CountryDevelopmentService {
    private static final int[] THRESHOLDS = {0, 250, 750, 1750, 3500};

    private CountryDevelopmentService() {}

    public static CountryDevelopmentSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                CountryDevelopmentSavedData::create,
                CountryDevelopmentSavedData::load,
                null
            ),
            CountryDevelopmentSavedData.DATA_NAME
        );
    }

    public static int level(MinecraftServer server, String countryName) {
        return get(server).getLevel(countryName);
    }

    public static int points(MinecraftServer server, String countryName) {
        return get(server).getPoints(countryName);
    }

    public static int addActivity(MinecraftServer server, String countryName, int amount) {
        return get(server).addPoints(countryName, Math.max(0, amount));
    }

    public static int thresholdForLevel(int level) {
        if (level <= 1) return THRESHOLDS[0];
        if (level >= 5) return THRESHOLDS[4];
        return THRESHOLDS[level - 1];
    }

    public static int nextThreshold(MinecraftServer server, String countryName) {
        int level = level(server, countryName);
        return level >= 5 ? THRESHOLDS[4] : THRESHOLDS[level];
    }

    public static int progressToNext(MinecraftServer server, String countryName) {
        int current = points(server, countryName);
        int level = level(server, countryName);
        if (level >= 5) return 0;
        return Math.max(0, current - thresholdForLevel(level));
    }

    public static int requiredToNext(MinecraftServer server, String countryName) {
        int level = level(server, countryName);
        if (level >= 5) return 0;
        return Math.max(0, thresholdForLevel(level + 1) - thresholdForLevel(level));
    }

    public static boolean canUpgrade(MinecraftServer server, String countryName) {
        int level = level(server, countryName);
        return level < 5 && points(server, countryName) >= thresholdForLevel(level + 1);
    }

    public static boolean upgrade(MinecraftServer server, String countryName) {
        if (!canUpgrade(server, countryName)) return false;
        CountryDevelopmentSavedData data = get(server);
        data.setLevel(countryName, data.getLevel(countryName) + 1);
        return true;
    }

    public static String levelTitle(CountryDirection direction, int level) {
        return switch (direction) {
            case INDUSTRIAL -> switch (Math.min(5, Math.max(1, level))) {
                case 1 -> "Механизация";
                case 2 -> "Массовое производство";
                case 3 -> "Промышленная система";
                case 4 -> "Военно-промышленный комплекс";
                default -> "Высокотехнологичная промышленность";
            };
            case RESOURCE -> switch (Math.min(5, Math.max(1, level))) {
                case 1 -> "Освоение ресурсов";
                case 2 -> "Массовая добыча";
                case 3 -> "Национальный ресурсный фонд";
                case 4 -> "Агропромышленная база";
                default -> "Сырьевой гигант";
            };
            case TRADE -> switch (Math.min(5, Math.max(1, level))) {
                case 1 -> "Торговое государство";
                case 2 -> "Единый рынок";
                case 3 -> "Торговая система";
                case 4 -> "Транспортная сеть";
                default -> "Международный торговый узел";
            };
        };
    }

    public static String currentPerk(CountryDirection direction, int level) {
        return switch (direction) {
            case INDUSTRIAL -> switch (Math.min(5, Math.max(1, level))) {
                case 1 -> "Базовая индустриальная экономика";
                case 2 -> "+10% промышленного производства";
                case 3 -> "−10% содержания промышленной инфраструктуры";
                case 4 -> "+10% военного производства";
                default -> "−10% стоимости сложной промышленности";
            };
            case RESOURCE -> switch (Math.min(5, Math.max(1, level))) {
                case 1 -> "Базовая ресурсно-аграрная экономика";
                case 2 -> "+10% производства сырья";
                case 3 -> "−10% содержания ресурсной инфраструктуры";
                case 4 -> "+10% сельскохозяйственного производства";
                default -> "−5 п.п. потерь при добыче";
            };
            case TRADE -> switch (Math.min(5, Math.max(1, level))) {
                case 1 -> "Базовая торгово-транспортная экономика";
                case 2 -> "+10% дохода от торговли";
                case 3 -> "−10 п.п. торговой комиссии";
                case 4 -> "−10% содержания транспортной инфраструктуры";
                default -> "+10% прироста населения";
            };
        };
    }

    public static String nextPerk(CountryDirection direction, int level) {
        return level >= 5 ? "Все уровни развития открыты" : currentPerk(direction, level + 1);
    }

    public static String nextTitle(CountryDirection direction, int level) {
        return level >= 5 ? "Все уровни развития открыты" : levelTitle(direction, level + 1);
    }
}
