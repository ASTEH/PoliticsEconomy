package ru.zela.politicseconomy.integration;

import net.krona.politicsmod.PoliticsManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;

/**
 * Keeps PoliticsMod diplomacy synchronized with PoliticsEconomy wars when both
 * participants are real PoliticsMod countries.
 *
 * <p>Millénaire-only states have their own military state key and cannot be
 * inserted into PoliticsMod's country registry, so those wars remain in the
 * PoliticsEconomy war store until a shared diplomacy layer is introduced.</p>
 */
public final class MilitaryDiplomacyBridge {
    private static final String DIPLOMACY_CLASS =
        "net.krona.politicsmod.politics.DiplomacyStatus";

    private MilitaryDiplomacyBridge() {}

    public static void setWar(MinecraftServer server, String attacker, String defender) {
        if (!canSync(server, attacker, defender)) return;
        setStatus(server, attacker, defender, "WAR");
    }

    public static void setPeace(MinecraftServer server, String a, String b) {
        if (!canSync(server, a, b)) return;
        setStatus(server, a, b, "NEUTRAL");
    }

    private static boolean canSync(
        MinecraftServer server,
        String a,
        String b
    ) {
        if (server == null
            || a == null || b == null
            || a.isBlank() || b.isBlank()
            || MillenaireIntegration.isStateKey(a)
            || MillenaireIntegration.isStateKey(b)) {
            return false;
        }

        try {
            PoliticsManager manager = PoliticsManager.get(server.overworld());
            return manager != null
                && manager.getCountry(a) != null
                && manager.getCountry(b) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setStatus(
        MinecraftServer server,
        String a,
        String b,
        String statusName
    ) {
        try {
            PoliticsManager manager = PoliticsManager.get(server.overworld());
            if (manager == null) return;

            Class<?> enumClass = Class.forName(DIPLOMACY_CLASS);
            if (!enumClass.isEnum()) return;

            Object status = Enum.valueOf(
                (Class<? extends Enum>) enumClass,
                statusName
            );

            for (Method method : manager.getClass().getMethods()) {
                if (!"setDiplomacy".equals(method.getName())) continue;

                Class<?>[] params = method.getParameterTypes();

                if (params.length == 3
                    && params[0] == String.class
                    && params[1] == String.class
                    && params[2].isEnum()) {
                    method.invoke(manager, a, b, status);
                    method.invoke(manager, b, a, status);
                    return;
                }

                if (params.length == 4
                    && params[0] == String.class
                    && params[1] == String.class
                    && params[2].isEnum()
                    && ServerLevel.class.isAssignableFrom(params[3])) {
                    ServerLevel level = server.overworld();
                    method.invoke(manager, a, b, status, level);
                    method.invoke(manager, b, a, status, level);
                    return;
                }
            }
        } catch (Throwable ignored) {
            // PoliticsMod API may change independently; the main war state must
            // remain functional even if this optional synchronization fails.
        }
    }
}
