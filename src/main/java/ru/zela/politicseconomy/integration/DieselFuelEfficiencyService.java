package ru.zela.politicseconomy.integration;

import net.krona.politicsmod.politics.Country;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import ru.zela.politicseconomy.country.CountryDirectionBonusService;
import ru.zela.politicseconomy.country.CountryDirectionProfile;

/**
 * Adjusts only the amount of fuel a Diesel Generators engine actually consumes.
 *
 * We intentionally do not mutate Diesel Generators' cached burn-rate field.
 * The mod reads that field directly inside each engine's tick() when it adds
 * to fuelDebt. We redirect that exact field read instead, so speed, capacity,
 * stress and fuel-cache logic remain completely untouched.
 */
public final class DieselFuelEfficiencyService {
    private DieselFuelEfficiencyService() {}

    public static float adjustBurnRate(BlockEntity engine, float baseBurnRate) {
        if (engine == null || baseBurnRate <= 0.0f) {
            return baseBurnRate;
        }

        if (!(engine.getLevel() instanceof ServerLevel level)) {
            return baseBurnRate;
        }

        Country country = CountryContext.machineCountry(level, engine.getBlockPos());
        if (country == null || level.getServer() == null) {
            return baseBurnRate;
        }

        CountryDirectionProfile profile = CountryDirectionBonusService.profile(
            level.getServer(), country.getName()
        );
        if (profile == null) {
            return baseBurnRate;
        }

        double multiplier = Math.max(
            0.0D,
            1.0D + profile.dieselFuelConsumption() / 100.0D
        );

        return (float) (baseBurnRate * multiplier);
    }
}
